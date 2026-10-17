package br.com.helpdev.velocimetroalerta.gps

import java.util.ArrayDeque
import kotlin.math.max
import kotlin.math.min

/** A single altitude fix. Speed is intentionally handled by the tracking/pause policy. */
data class ElevationSample(
    val altitudeMeters: Double?,
    val verticalAccuracyMeters: Double?,
    val fixElapsedRealtimeNanos: Long
)

enum class ElevationQuality { WAITING, ACTIVE, STALE, LOW_CONFIDENCE }

data class ElevationEstimate(
    val filteredAltitudeMeters: Double?,
    val gainMeters: Double,
    val lossMeters: Double,
    val quality: ElevationQuality,
    val acceptedSamples: Long
)

data class ElevationConfig(
    val medianWindowSize: Int = 5,
    val fallbackThresholdMeters: Double = 10.0,
    val minimumVerticalThresholdMeters: Double = 5.0,
    val maximumVerticalThresholdMeters: Double = 20.0,
    val maximumVerticalAccuracyMeters: Double = 25.0,
    val maximumFixGapNanos: Long = 30_000_000_000L
) {
    init {
        require(medianWindowSize > 0 && medianWindowSize % 2 == 1)
        require(fallbackThresholdMeters > 0.0)
        require(minimumVerticalThresholdMeters > 0.0)
        require(maximumVerticalThresholdMeters >= minimumVerticalThresholdMeters)
        require(maximumVerticalAccuracyMeters > 0.0)
        require(maximumFixGapNanos > 0L)
    }
}

/**
 * Constant-memory, causal elevation accumulator. It never reads the route history, so each
 * accepted fix costs only a small fixed-window median plus one state transition.
 */
class ElevationEstimator(private val config: ElevationConfig = ElevationConfig()) {
    private enum class Direction { UP, DOWN }

    private val recentAltitudes = ArrayDeque<Double>(config.medianWindowSize)
    private val recentVerticalAccuracies = ArrayDeque<Double>(config.medianWindowSize)
    private var lastFixElapsedRealtimeNanos: Long? = null
    private var lastReliableFixElapsedRealtimeNanos: Long? = null
    private var filteredAltitude: Double? = null
    private var valley: Double? = null
    private var peak: Double? = null
    private var direction: Direction? = null
    private var gain = 0.0
    private var loss = 0.0
    private var acceptedSamples = 0L
    private var quality = ElevationQuality.WAITING
    private var paused = false
    private var finished = false

    @Synchronized
    fun accept(sample: ElevationSample): ElevationEstimate {
        if (paused || finished) return snapshot()
        val altitude = sample.altitudeMeters?.takeIf { it.isFinite() } ?: return snapshot()
        val time = sample.fixElapsedRealtimeNanos
        if (time <= 0L || lastFixElapsedRealtimeNanos?.let { time <= it } == true) return snapshot()

        lastFixElapsedRealtimeNanos = time
        val previousReliableTime = lastReliableFixElapsedRealtimeNanos
        val gapDetected = previousReliableTime != null && time - previousReliableTime > config.maximumFixGapNanos
        if (gapDetected) {
            resetSegment()
            quality = ElevationQuality.STALE
        }

        val verticalAccuracy = sample.verticalAccuracyMeters?.takeIf { it.isFinite() && it > 0.0 }
        if (verticalAccuracy != null && verticalAccuracy > config.maximumVerticalAccuracyMeters) {
            quality = ElevationQuality.LOW_CONFIDENCE
            return snapshot()
        }

        lastReliableFixElapsedRealtimeNanos = time

        if (verticalAccuracy != null) {
            recentVerticalAccuracies.addLast(verticalAccuracy)
            while (recentVerticalAccuracies.size > config.medianWindowSize) recentVerticalAccuracies.removeFirst()
        }
        recentAltitudes.addLast(altitude)
        while (recentAltitudes.size > config.medianWindowSize) recentAltitudes.removeFirst()
        filteredAltitude = median(recentAltitudes)
        acceptedSamples++

        if (valley == null) {
            valley = filteredAltitude
            peak = filteredAltitude
        } else {
            updateExcursion(requireNotNull(filteredAltitude), thresholdFor(verticalAccuracy))
        }
        if (!gapDetected) quality = ElevationQuality.ACTIVE
        return snapshot()
    }

    @Synchronized
    fun pause(): ElevationEstimate {
        if (!finished && !paused) {
            paused = true
            resetSegment()
        }
        return snapshot()
    }

    @Synchronized
    fun resume(): ElevationEstimate {
        if (!finished && paused) {
            paused = false
            resetSegment()
            quality = ElevationQuality.STALE
        }
        return snapshot()
    }

    @Synchronized
    fun finish(): ElevationEstimate {
        finished = true
        return snapshot()
    }

    @Synchronized
    fun snapshot(): ElevationEstimate = ElevationEstimate(
        filteredAltitudeMeters = filteredAltitude,
        gainMeters = gain,
        lossMeters = loss,
        quality = quality,
        acceptedSamples = acceptedSamples
    )

    @Synchronized
    fun reset() {
        resetSegment()
        lastFixElapsedRealtimeNanos = null
        lastReliableFixElapsedRealtimeNanos = null
        filteredAltitude = null
        gain = 0.0
        loss = 0.0
        acceptedSamples = 0L
        quality = ElevationQuality.WAITING
        paused = false
        finished = false
    }

    private fun updateExcursion(altitude: Double, threshold: Double) {
        when (direction) {
            null -> {
                valley = min(requireNotNull(valley), altitude)
                peak = max(requireNotNull(peak), altitude)
                val climb = altitude - requireNotNull(valley)
                val descent = requireNotNull(peak) - altitude
                if (climb >= threshold) {
                    gain += climb
                    peak = altitude
                    direction = Direction.UP
                } else if (descent >= threshold) {
                    loss += descent
                    valley = altitude
                    direction = Direction.DOWN
                }
            }
            Direction.UP -> {
                if (altitude > requireNotNull(peak)) {
                    gain += altitude - requireNotNull(peak)
                    peak = altitude
                } else if (requireNotNull(peak) - altitude >= threshold) {
                    loss += requireNotNull(peak) - altitude
                    valley = altitude
                    direction = Direction.DOWN
                }
            }
            Direction.DOWN -> {
                if (altitude < requireNotNull(valley)) {
                    loss += requireNotNull(valley) - altitude
                    valley = altitude
                } else if (altitude - requireNotNull(valley) >= threshold) {
                    gain += altitude - requireNotNull(valley)
                    peak = altitude
                    direction = Direction.UP
                }
            }
        }
    }

    private fun thresholdFor(currentVerticalAccuracy: Double?): Double {
        if (currentVerticalAccuracy == null) return config.fallbackThresholdMeters
        val scaled = median(recentVerticalAccuracies) * 1.5
        return scaled.coerceIn(config.minimumVerticalThresholdMeters, config.maximumVerticalThresholdMeters)
    }

    private fun median(values: Collection<Double>): Double {
        val sorted = values.sorted()
        val middle = sorted.size / 2
        return if (sorted.size % 2 == 1) sorted[middle]
        else (sorted[middle - 1] + sorted[middle]) / 2.0
    }

    private fun resetSegment() {
        recentAltitudes.clear()
        recentVerticalAccuracies.clear()
        valley = null
        peak = null
        direction = null
    }
}

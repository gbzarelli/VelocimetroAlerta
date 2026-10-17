package br.com.helpdev.velocimetroalerta.gps

import java.util.ArrayDeque

/** Applies activity pause decisions around the speed independent elevation estimator. */
class ElevationTrackingSession(config: ElevationConfig = ElevationConfig()) {
    private val estimator = ElevationEstimator(config)
    private val maximumFallbackAccuracyMeters = config.maximumVerticalAccuracyMeters
    private val automaticPauseSamples = ArrayDeque<ElevationSample>()
    private var holdingForAutomaticPause = false
    private var paused = false
    private var finished = false

    @Synchronized
    fun beginAutomaticPauseCandidate() {
        if (paused || finished) return
        holdingForAutomaticPause = true
    }

    @Synchronized
    fun accept(sample: ElevationSample): ElevationEstimate {
        if (paused || finished) return estimator.snapshot()
        if (holdingForAutomaticPause) automaticPauseSamples.addLast(sample)
        else estimator.accept(sample)
        return estimator.snapshot()
    }

    /**
     * Accepts altitude using vertical accuracy when available, falling back to horizontal
     * accuracy on Android versions that do not report vertical accuracy. Poor reported vertical
     * accuracy still reaches the estimator so it can expose low confidence in the live estimate.
     */
    @Synchronized
    fun acceptLocation(sample: ElevationSample, horizontalAccuracyMeters: Double): ElevationEstimate {
        if (paused || finished) return estimator.snapshot()
        val verticalAccuracy = sample.verticalAccuracyMeters
        if (verticalAccuracy == null) {
            if (!horizontalAccuracyMeters.isFinite() || horizontalAccuracyMeters <= 0.0 ||
                horizontalAccuracyMeters > maximumFallbackAccuracyMeters
            ) {
                return estimator.snapshot()
            }
        } else if (!verticalAccuracy.isFinite() || verticalAccuracy <= 0.0) {
            return estimator.snapshot()
        }
        return accept(sample)
    }

    /** Replays the short low speed debounce interval when the app decides the ride continued. */
    @Synchronized
    fun cancelAutomaticPauseCandidate(): ElevationEstimate {
        if (paused || finished) {
            discardPendingSamples()
            return estimator.snapshot()
        }
        holdingForAutomaticPause = false
        while (automaticPauseSamples.isNotEmpty()) estimator.accept(automaticPauseSamples.removeFirst())
        return estimator.snapshot()
    }

    /** Drops stationary debounce fixes so GPS drift cannot be counted as a climb while paused. */
    @Synchronized
    fun confirmAutomaticPause(): ElevationEstimate {
        discardPendingSamples()
        paused = true
        return estimator.pause()
    }

    @Synchronized
    fun pauseManually(): ElevationEstimate {
        discardPendingSamples()
        paused = true
        return estimator.pause()
    }

    @Synchronized
    fun resume(): ElevationEstimate {
        if (finished) return estimator.snapshot()
        discardPendingSamples()
        paused = false
        return estimator.resume()
    }

    @Synchronized
    fun finish(): ElevationEstimate {
        discardPendingSamples()
        finished = true
        return estimator.finish()
    }

    @Synchronized
    fun snapshot(): ElevationEstimate = estimator.snapshot()

    @Synchronized
    fun reset() {
        discardPendingSamples()
        estimator.reset()
        paused = false
        finished = false
    }

    private fun discardPendingSamples() {
        holdingForAutomaticPause = false
        automaticPauseSamples.clear()
    }
}

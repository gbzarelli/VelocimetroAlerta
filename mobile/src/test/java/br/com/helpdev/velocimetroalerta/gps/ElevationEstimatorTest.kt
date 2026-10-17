package br.com.helpdev.velocimetroalerta.gps

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ElevationEstimatorTest {
    @Test
    fun causalMedianRejectsAnIsolatedAltitudeSpike() {
        val estimator = ElevationEstimator()

        feed(estimator, List(6) { 100.0 } + 106.0 + List(8) { 100.0 })

        assertEquals(0.0, estimator.snapshot().gainMeters, 0.0)
        assertEquals(0.0, estimator.snapshot().lossMeters, 0.0)
    }

    @Test
    fun gradualTwelveMeterClimbIsCountedAfterTheFilterSettles() {
        val estimator = ElevationEstimator()

        feed(estimator, (100..112).map(Int::toDouble) + List(4) { 112.0 })

        assertEquals(12.0, estimator.snapshot().gainMeters, 0.01)
        assertEquals(0.0, estimator.snapshot().lossMeters, 0.01)
    }

    @Test
    fun climbAndReturnCountsGainAndLossSymmetrically() {
        val estimator = ElevationEstimator()
        feed(
            estimator,
            (100..112).map(Int::toDouble) + List(4) { 112.0 } +
                (111 downTo 100).map(Int::toDouble) + List(8) { 100.0 }
        )

        assertEquals(12.0, estimator.snapshot().gainMeters, 0.01)
        assertEquals(12.0, estimator.snapshot().lossMeters, 0.01)
    }

    @Test
    fun pauseDiscardsAltitudeJumpAndResumeKeepsConfirmedTotals() {
        val estimator = ElevationEstimator()
        feed(estimator, (100..112).map(Int::toDouble) + List(4) { 112.0 })
        val beforePause = estimator.snapshot()

        estimator.pause()
        feed(estimator, listOf(800.0, 1200.0, 200.0))
        assertEquals(beforePause.gainMeters, estimator.snapshot().gainMeters, 0.0)
        assertEquals(beforePause.lossMeters, estimator.snapshot().lossMeters, 0.0)

        estimator.resume()
        feed(estimator, listOf(500.0, 501.0, 502.0))

        assertEquals(beforePause.gainMeters, estimator.snapshot().gainMeters, 0.0)
        assertEquals(beforePause.lossMeters, estimator.snapshot().lossMeters, 0.0)
    }

    @Test
    fun duplicateAndOutOfOrderFixesAreIgnored() {
        val estimator = ElevationEstimator()
        estimator.accept(sample(100.0, 1_000_000_000L))
        estimator.accept(sample(110.0, 2_000_000_000L))
        val accepted = estimator.snapshot().acceptedSamples

        estimator.accept(sample(500.0, 2_000_000_000L))
        estimator.accept(sample(600.0, 1_500_000_000L))

        assertEquals(accepted, estimator.snapshot().acceptedSamples)
        assertNotEquals(500.0, estimator.snapshot().filteredAltitudeMeters)
    }

    @Test
    fun missingAltitudeAndNonFiniteAltitudeDoNotEnterTheFilter() {
        val estimator = ElevationEstimator()

        estimator.accept(sample(null, 1_000_000_000L))
        estimator.accept(sample(Double.NaN, 2_000_000_000L))

        assertEquals(0L, estimator.snapshot().acceptedSamples)
        assertNull(estimator.snapshot().filteredAltitudeMeters)
        assertEquals(ElevationQuality.WAITING, estimator.snapshot().quality)
    }

    @Test
    fun poorVerticalAccuracyFreezesTotalsUntilQualityRecovers() {
        val estimator = ElevationEstimator()
        feed(estimator, (100..112).map(Int::toDouble) + List(4) { 112.0 })
        val beforePoorFix = estimator.snapshot()

        estimator.accept(sample(300.0, nextTime(), verticalAccuracy = 26.0))

        assertEquals(ElevationQuality.LOW_CONFIDENCE, estimator.snapshot().quality)
        assertEquals(beforePoorFix.gainMeters, estimator.snapshot().gainMeters, 0.0)
        assertEquals(beforePoorFix.lossMeters, estimator.snapshot().lossMeters, 0.0)

        val recovered = estimator.accept(sample(112.0, nextTime(), verticalAccuracy = 4.0))
        assertEquals(ElevationQuality.ACTIVE, recovered.quality)
        assertEquals(beforePoorFix.gainMeters, recovered.gainMeters, 0.0)
    }

    @Test
    fun repeatedPoorVerticalAccuracyFixesDoNotBridgeAnUntrustedGap() {
        val estimator = ElevationEstimator()
        feed(estimator, (100..112).map(Int::toDouble) + List(4) { 112.0 })
        val beforePoorFixes = estimator.snapshot()
        val firstPoorFixTime = timeIndex * FIX_INTERVAL_NANOS + 10 * FIX_INTERVAL_NANOS

        listOf(10L, 20L, 30L, 40L).forEach { seconds ->
            estimator.accept(sample(300.0, firstPoorFixTime + seconds * FIX_INTERVAL_NANOS, verticalAccuracy = 26.0))
        }
        val recovered = estimator.accept(sample(300.0, firstPoorFixTime + 41 * FIX_INTERVAL_NANOS, verticalAccuracy = 4.0))

        assertEquals(beforePoorFixes.gainMeters, recovered.gainMeters, 0.0)
        assertEquals(beforePoorFixes.lossMeters, recovered.lossMeters, 0.0)

        timeIndex = (firstPoorFixTime / FIX_INTERVAL_NANOS) + 41
        feed(estimator, (301..312).map(Int::toDouble) + List(4) { 312.0 }, verticalAccuracy = 4.0)

        assertEquals(beforePoorFixes.gainMeters + 12.0, estimator.snapshot().gainMeters, 0.01)
    }

    @Test
    fun verticalAccuracySetsLocalThresholdWithConfiguredBounds() {
        val precise = ElevationEstimator()
        feed(precise, (100..106).map(Int::toDouble) + List(5) { 106.0 }, verticalAccuracy = 2.0)
        assertEquals(6.0, precise.snapshot().gainMeters, 0.01)

        val uncertain = ElevationEstimator()
        feed(uncertain, (100..112).map(Int::toDouble) + List(5) { 112.0 }, verticalAccuracy = 10.0)
        assertEquals(0.0, uncertain.snapshot().gainMeters, 0.01)
    }

    @Test
    fun gapOverThirtySecondsStartsANewElevationSegment() {
        val estimator = ElevationEstimator()
        estimator.accept(sample(100.0, 1_000_000_000L))

        val afterGap = estimator.accept(sample(500.0, 32_000_000_001L))

        assertEquals(ElevationQuality.STALE, afterGap.quality)
        assertEquals(0.0, afterGap.gainMeters, 0.0)
        timeIndex = 32L
        feed(estimator, (501..512).map(Int::toDouble) + List(4) { 512.0 })
        assertEquals(12.0, estimator.snapshot().gainMeters, 0.01)
    }

    @Test
    fun snapshotsAndArbitraryBatchBoundariesDoNotChangeTheEstimate() {
        val elevations = (100..112).map(Int::toDouble) + List(4) { 112.0 } +
            (111 downTo 100).map(Int::toDouble) + List(4) { 100.0 }
        val continuous = ElevationEstimator()
        val batched = ElevationEstimator()

        elevations.forEachIndexed { index, elevation ->
            continuous.accept(sample(elevation, (index + 1L) * FIX_INTERVAL_NANOS))
            if (index % 4 == 0) batched.snapshot()
            batched.accept(sample(elevation, (index + 1L) * FIX_INTERVAL_NANOS))
        }

        assertEquals(continuous.snapshot().gainMeters, batched.snapshot().gainMeters, 0.01)
        assertEquals(continuous.snapshot().lossMeters, batched.snapshot().lossMeters, 0.01)
    }

    @Test
    fun confirmedTotalsAreMonotonicAndFinishIsIdempotent() {
        val estimator = ElevationEstimator()
        val gains = mutableListOf<Double>()
        (100..112).map(Int::toDouble).forEach { altitude ->
            estimator.accept(sample(altitude, nextTime()))
            gains += estimator.snapshot().gainMeters
        }
        val finished = estimator.finish()

        assertTrue(gains.zipWithNext().all { (a, b) -> b >= a })
        assertEquals(finished, estimator.finish())
        assertEquals(finished, estimator.accept(sample(300.0, nextTime())))
    }

    private fun feed(
        estimator: ElevationEstimator,
        elevations: List<Double>,
        verticalAccuracy: Double? = null
    ) {
        elevations.forEach { estimator.accept(sample(it, nextTime(), verticalAccuracy)) }
    }

    private fun sample(altitude: Double?, time: Long, verticalAccuracy: Double? = null) =
        ElevationSample(altitude, verticalAccuracy, time)

    private fun nextTime() = FIX_INTERVAL_NANOS * (++timeIndex)

    private var timeIndex = 0L

    companion object { private const val FIX_INTERVAL_NANOS = 1_000_000_000L }
}

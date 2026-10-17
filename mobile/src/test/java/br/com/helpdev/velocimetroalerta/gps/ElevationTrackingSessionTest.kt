package br.com.helpdev.velocimetroalerta.gps

import org.junit.Assert.assertEquals
import org.junit.Test

class ElevationTrackingSessionTest {
    @Test
    fun lowSpeedSamplesCountWhenAutomaticPauseIsCancelled() {
        val session = ElevationTrackingSession()
        session.beginAutomaticPauseCandidate()
        feed(session, (100..112).map(Int::toDouble) + List(4) { 112.0 })

        assertEquals(0.0, session.snapshot().gainMeters, 0.0)
        session.cancelAutomaticPauseCandidate()

        assertEquals(12.0, session.snapshot().gainMeters, 0.01)
    }

    @Test
    fun confirmedAutomaticPauseDropsDebounceSamplesAndResumeDoesNotConnectJump() {
        val session = ElevationTrackingSession()
        session.beginAutomaticPauseCandidate()
        feed(session, listOf(100.0, 105.0, 110.0, 115.0))
        val paused = session.confirmAutomaticPause()

        assertEquals(0.0, paused.gainMeters, 0.0)
        session.accept(sample(900.0))
        session.resume()
        feed(session, (800..812).map(Int::toDouble) + List(4) { 812.0 })

        assertEquals(12.0, session.snapshot().gainMeters, 0.01)
        assertEquals(0.0, session.snapshot().lossMeters, 0.01)
    }

    @Test
    fun fixesAfterAutomaticPauseConfirmationAreIgnoredUntilResume() {
        val session = ElevationTrackingSession()
        session.beginAutomaticPauseCandidate()
        session.accept(sample(100.0))
        session.confirmAutomaticPause()

        session.beginAutomaticPauseCandidate()
        session.accept(sample(500.0))
        session.cancelAutomaticPauseCandidate()
        session.resume()
        feed(session, listOf(800.0, 801.0, 802.0))

        assertEquals(0.0, session.snapshot().gainMeters, 0.0)
        assertEquals(0.0, session.snapshot().lossMeters, 0.0)
    }

    @Test
    fun manualPauseClearsPendingSamplesAndPreservesConfirmedTotals() {
        val session = ElevationTrackingSession()
        feed(session, (100..112).map(Int::toDouble) + List(4) { 112.0 })
        session.beginAutomaticPauseCandidate()
        feed(session, listOf(112.0, 100.0, 90.0))

        session.pauseManually()
        session.resume()
        feed(session, listOf(700.0, 701.0, 702.0))

        assertEquals(12.0, session.snapshot().gainMeters, 0.01)
        assertEquals(0.0, session.snapshot().lossMeters, 0.01)
    }

    private fun feed(session: ElevationTrackingSession, elevations: List<Double>) {
        elevations.forEach { session.accept(sample(it)) }
    }

    private fun sample(altitude: Double) = ElevationSample(altitude, null, ++timeNanos)

    private var timeNanos = 0L
}

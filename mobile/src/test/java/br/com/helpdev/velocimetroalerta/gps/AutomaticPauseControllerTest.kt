package br.com.helpdev.velocimetroalerta.gps

import org.junit.Assert.assertEquals
import org.junit.Test

class AutomaticPauseControllerTest {
    private val controller = AutomaticPauseController()

    @Test
    fun waitsThreeSecondsBeforeConfirmingLowSpeedPause() {
        assertEquals(AutomaticPauseDecision.HOLD_FOR_PAUSE, controller.update(1_000L, 0.5, 5.0, false))
        assertEquals(AutomaticPauseDecision.HOLD_FOR_PAUSE, controller.update(3_999L, 0.5, 5.0, false))
        assertEquals(AutomaticPauseDecision.CONFIRM_PAUSE, controller.update(4_001L, 0.5, 5.0, false))
    }

    @Test
    fun shortLowSpeedIntervalIsCancelledWhenMovementReturns() {
        assertEquals(AutomaticPauseDecision.HOLD_FOR_PAUSE, controller.update(1_000L, 0.5, 5.0, false))

        assertEquals(AutomaticPauseDecision.TRACK, controller.update(2_000L, 4.0, 5.0, false))
        assertEquals(AutomaticPauseDecision.HOLD_FOR_PAUSE, controller.update(2_100L, 0.5, 5.0, false))
    }

    @Test
    fun poorAccuracyCannotResumeAnAutomaticallyPausedRide() {
        assertEquals(AutomaticPauseDecision.KEEP_PAUSED, controller.update(5_000L, 10.0, 30.0, true))
        assertEquals(AutomaticPauseDecision.KEEP_PAUSED, controller.update(6_000L, 10.0, 0.0, true))
        assertEquals(AutomaticPauseDecision.RESUME, controller.update(7_000L, 10.0, 5.0, true))
    }

    @Test
    fun gpsLossCanConfirmPauseButUnknownZeroAccuracyCannot() {
        assertEquals(AutomaticPauseDecision.HOLD_FOR_PAUSE, controller.update(1_000L, 0.0, -1.0, false))
        assertEquals(AutomaticPauseDecision.TRACK, controller.update(2_000L, 0.0, 0.0, false))
    }
}

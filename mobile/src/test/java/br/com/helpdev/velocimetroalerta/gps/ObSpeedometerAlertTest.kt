package br.com.helpdev.velocimetroalerta.gps

import org.junit.Assert.assertEquals
import org.junit.Test

class ObSpeedometerAlertTest {
    @Test
    fun cadenceAndBpmAveragesExcludePausedSamples() {
        val alert = ObSpeedometerAlert()
        alert.cadence = 20
        alert.bpm = 100

        alert.inPauseActivity = true
        alert.cadence = 40
        alert.bpm = 200

        alert.inPauseActivity = false
        alert.cadence = 30
        alert.bpm = 120

        assertEquals(25, alert.cadenceAvg)
        assertEquals(40, alert.cadenceMax)
        assertEquals(110, alert.bpmAvg)
        assertEquals(200, alert.bpmMax)
    }

    @Test
    fun addTimePausedAccumulatesMilliseconds() {
        val alert = ObSpeedometerAlert()

        alert.addTimePaused(1_000.0)
        alert.addTimePaused(250.0)

        assertEquals(1_250L, alert.timePaused)
    }
}

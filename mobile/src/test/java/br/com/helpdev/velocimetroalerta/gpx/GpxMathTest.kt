package br.com.helpdev.velocimetroalerta.gpx

import br.com.helpdev.velocimetroalerta.gpx.objects.TrkPt
import org.junit.Assert.assertEquals
import org.junit.Test

class GpxMathTest {
    @Test
    fun calculateGainCountsChangesAtLeastMeanAccuracy() {
        val points = listOf(
            point(elevation = 100.0, accuracy = 2.0),
            point(elevation = 102.0, accuracy = 2.0),
            point(elevation = 105.0, accuracy = 2.0),
            point(elevation = 103.0, accuracy = 2.0)
        )

        assertEquals(5.0 to 2.0, GpxMath.calculateGain(0, points))
    }

    @Test
    fun calculateGainReturnsZeroForEmptyTrack() {
        assertEquals(0.0 to 0.0, GpxMath.calculateGain(0, emptyList()))
    }

    private fun point(elevation: Double, accuracy: Double) = TrkPt().apply {
        ele = elevation
        this.accuracy = accuracy
    }
}

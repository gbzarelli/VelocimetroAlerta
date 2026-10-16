package br.com.helpdev.velocimetroalerta.activities

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RoutePreviewViewTest {
    @Test
    fun verticalRouteIsCenteredAndStaysInsideBounds() {
        val normalized = RoutePreviewView.normalize(listOf(point(1.0, 2.0), point(2.0, 2.0)), 100f, 60f, 10f)

        assertEquals(50f, normalized[0].x, 0f)
        assertEquals(50f, normalized[1].x, 0f)
        assertEquals(10f, normalized.minOf { it.y }, 0f)
        assertEquals(50f, normalized.maxOf { it.y }, 0f)
    }

    @Test
    fun horizontalRouteIsCenteredAndStaysInsideBounds() {
        val normalized = RoutePreviewView.normalize(listOf(point(1.0, 2.0), point(1.0, 4.0)), 100f, 60f, 10f)

        assertEquals(30f, normalized[0].y, 0f)
        assertEquals(30f, normalized[1].y, 0f)
        assertEquals(10f, normalized.minOf { it.x }, 0f)
        assertEquals(90f, normalized.maxOf { it.x }, 0f)
    }

    @Test
    fun repeatedCoordinatesStayAtTheCenter() {
        val normalized = RoutePreviewView.normalize(listOf(point(1.0, 2.0), point(1.0, 2.0)), 100f, 60f, 10f)

        assertEquals(listOf(PreviewCoordinate(50f, 30f), PreviewCoordinate(50f, 30f)), normalized)
        assertTrue(normalized.all { it.x in 10f..90f && it.y in 10f..50f })
    }

    private fun point(latitude: Double, longitude: Double) = ActivityPreviewPoint(latitude, longitude)
}

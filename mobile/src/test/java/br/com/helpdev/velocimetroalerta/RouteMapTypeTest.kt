package br.com.helpdev.velocimetroalerta

import org.junit.Assert.assertEquals
import org.junit.Test

class RouteMapTypeTest {
    @Test
    fun normalAndSatelliteMapTypesToggleBackAndForth() {
        assertEquals(RouteMapType.SATELLITE, RouteMapType.NORMAL.toggle())
        assertEquals(RouteMapType.NORMAL, RouteMapType.SATELLITE.toggle())
    }
}

package br.com.helpdev.velocimetroalerta

import br.com.helpdev.velocimetroalerta.gps.ActiveRoute
import br.com.helpdev.velocimetroalerta.gps.RoutePoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.io.FileInputStream
import javax.xml.stream.XMLInputFactory
import javax.xml.stream.XMLStreamConstants

/** Uses a caller-provided GPX locally; no source coordinates or test fixture are committed. */
class GpxRouteMapValidationTest {
    @Test
    fun downloadedGpxIsPreservedInOrderByTheRouteMapState() {
        val gpxPath = System.getenv("ROUTE_GPX_PATH")
        assumeTrue("Set ROUTE_GPX_PATH to validate a local GPX", !gpxPath.isNullOrBlank())
        val file = File(gpxPath!!)
        assertTrue("The local GPX input must be readable", file.isFile && file.canRead())

        val inputFactory = XMLInputFactory.newFactory().apply {
            setProperty(XMLInputFactory.SUPPORT_DTD, false)
            setProperty("javax.xml.stream.isSupportingExternalEntities", false)
        }
        val points = mutableListOf<RoutePoint>()
        FileInputStream(file).use { input ->
            val reader = inputFactory.createXMLStreamReader(input)
            while (reader.hasNext()) {
                if (reader.next() == XMLStreamConstants.START_ELEMENT && reader.localName == "trkpt") {
                    val latitude = reader.getAttributeValue(null, "lat").toDouble()
                    val longitude = reader.getAttributeValue(null, "lon").toDouble()
                    assertTrue(latitude in -90.0..90.0)
                    assertTrue(longitude in -180.0..180.0)
                    points += RoutePoint(latitude, longitude)
                }
            }
            reader.close()
        }
        assertTrue("The downloaded GPX must contain a track with multiple points", points.size > 1)

        val route = ActiveRoute()
        points.forEach(route::append)
        val snapshot = route.snapshot()
        val renderer = GpxRouteRendererFake()
        val state = RouteMapState(renderer)
        state.applySnapshot(snapshot)

        assertEquals(points.size.toLong(), snapshot.revision)
        assertEquals(points, snapshot.points)
        assertEquals(1, renderer.renderCount)
        assertEquals(points, renderer.renderedPoints)
        assertNotNull(renderer.finalPoint)
        assertEquals(points.last(), renderer.finalPoint)
    }

    private class GpxRouteRendererFake : RouteMapRenderer {
        var renderCount = 0
        var renderedPoints: List<RoutePoint> = emptyList()
        var finalPoint: RoutePoint? = null

        override fun showWaiting() = Unit
        override fun showUnavailable() = Unit
        override fun showRouteAvailable() = Unit
        override fun clearRoute() = Unit
        override fun renderRoute(points: List<RoutePoint>, follow: Boolean, frameRoute: Boolean) {
            renderCount++
            renderedPoints = points
            finalPoint = points.lastOrNull()
        }
        override fun frame(point: RoutePoint, zoom: Float) = Unit
        override fun focus(point: RoutePoint, zoom: Float) = Unit
        override fun setOnUserPanListener(listener: () -> Unit) = Unit
        override fun zoomIn() = Unit
        override fun zoomOut() = Unit
    }
}

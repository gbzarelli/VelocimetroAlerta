package br.com.helpdev.velocimetroalerta

import br.com.helpdev.velocimetroalerta.gps.RoutePoint
import br.com.helpdev.velocimetroalerta.gps.RouteSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteMapRenderingTest {
    @Test
    fun emptyRouteShowsWaitingState() {
        val renderer = FakeRenderer()
        val state = RouteMapState(renderer)

        state.applySnapshot(RouteSnapshot(0, emptyList()))

        assertTrue(renderer.waitingShown)
        assertTrue(renderer.renderedRoutes.isEmpty())
    }

    @Test
    fun initialSnapshotDrawsOnePolylineAndFinalMarker() {
        val renderer = FakeRenderer()
        val state = RouteMapState(renderer)
        val route = listOf(point(1), point(2), point(3))

        state.applySnapshot(RouteSnapshot(3, route))

        assertEquals(listOf(route), renderer.renderedRoutes)
        assertEquals(route.last(), renderer.lastPoint)
        assertEquals(listOf(true), renderer.followValues)
        assertEquals(listOf(true), renderer.frameValues)
        assertEquals(1, renderer.routeAvailableCount)
    }

    @Test
    fun laterPointUpdatesTheExistingPolylineAndMarker() {
        val renderer = FakeRenderer()
        val state = RouteMapState(renderer)
        state.applySnapshot(RouteSnapshot(1, listOf(point(1))))

        state.onPointAdded(2, point(2))

        assertEquals(listOf(listOf(point(1)), listOf(point(1), point(2))), renderer.renderedRoutes)
        assertEquals(point(2), renderer.lastPoint)
    }

    @Test
    fun staleRevisionIsIgnored() {
        val renderer = FakeRenderer()
        val state = RouteMapState(renderer)
        state.applySnapshot(RouteSnapshot(2, listOf(point(1), point(2))))

        state.onPointAdded(2, point(2))
        state.onPointAdded(1, point(99))

        assertEquals(1, renderer.renderedRoutes.size)
        assertEquals(listOf(point(1), point(2)), renderer.renderedRoutes.single())
    }

    @Test
    fun routeResetClearsPreviousActivityBeforeRenderingTheNextPoint() {
        val renderer = FakeRenderer()
        val state = RouteMapState(renderer)
        state.applySnapshot(RouteSnapshot(1, listOf(point(1), point(2))))

        state.onRouteReset(2)
        state.onPointAdded(3, point(3))

        assertEquals(listOf(listOf(point(1), point(2)), listOf(point(3))), renderer.renderedRoutes)
        assertEquals(1, renderer.clearedRouteCount)
        assertEquals(point(3), renderer.lastPoint)
    }

    @Test
    fun panStopsFollowAndRecenterRestoresIt() {
        val renderer = FakeRenderer()
        val state = RouteMapState(renderer)
        state.applySnapshot(RouteSnapshot(1, listOf(point(1))))

        state.onUserPan()
        state.onPointAdded(2, point(2))
        assertFalse(state.isFollowing)
        assertEquals(false, renderer.followValues.last())

        state.recenter()
        assertTrue(state.isFollowing)
        assertEquals(point(2), renderer.focusedPoint)
        assertEquals(RouteMapState.NEARBY_ZOOM, renderer.focusedZoom)

        state.onPointAdded(3, point(3))
        assertEquals(true, renderer.followValues.last())
    }

    @Test
    fun mapViewportReadyFramesAllRoutePointsWhenCameraHasNotBeenChangedByUser() {
        val renderer = FakeRenderer()
        val state = RouteMapState(renderer)
        state.applySnapshot(RouteSnapshot(1, listOf(point(1))))
        state.onPointAdded(2, point(2))

        state.onMapViewportReady()

        assertEquals(listOf(point(1), point(2)), renderer.framedRoute)
        assertEquals(RouteMapState.ROUTE_PADDING_PX, renderer.frameRoutePadding)
    }

    @Test
    fun mapViewportReadyFramesTheFullHistoricalRoute() {
        val renderer = FakeRenderer()
        val state = RouteMapState(renderer)
        val route = listOf(point(1), point(2), point(3))
        state.applySnapshot(RouteSnapshot(3, route))

        state.onMapViewportReady()

        assertEquals(route, renderer.framedRoute)
        assertEquals(RouteMapState.ROUTE_PADDING_PX, renderer.frameRoutePadding)
    }

    @Test
    fun mapViewportReadyDoesNotOverrideUserCameraChanges() {
        val renderer = FakeRenderer()
        val state = RouteMapState(renderer)
        state.applySnapshot(RouteSnapshot(1, listOf(point(1))))

        state.zoomIn()
        state.onMapViewportReady()
        assertEquals(null, renderer.framedPoint)

        state.recenter()
        state.onUserPan()
        renderer.framedPoint = null
        renderer.framedZoom = null

        state.onMapViewportReady()

        assertEquals(null, renderer.framedPoint)
    }

    @Test
    fun zoomControlsDelegateToTheMapRenderer() {
        val renderer = FakeRenderer()
        val state = RouteMapState(renderer)

        state.zoomIn()
        state.zoomOut()

        assertEquals(1, renderer.zoomInCount)
        assertEquals(1, renderer.zoomOutCount)
    }

    private fun point(index: Int) = RoutePoint(-23.5 - index / 1000.0, -46.6 - index / 1000.0)

    private class FakeRenderer : RouteMapRenderer {
        var waitingShown = false
        val renderedRoutes = mutableListOf<List<RoutePoint>>()
        val followValues = mutableListOf<Boolean>()
        val frameValues = mutableListOf<Boolean>()
        var lastPoint: RoutePoint? = null
        var focusedPoint: RoutePoint? = null
        var focusedZoom: Float? = null
        var framedPoint: RoutePoint? = null
        var framedZoom: Float? = null
        var framedRoute: List<RoutePoint>? = null
        var frameRoutePadding: Int? = null
        var clearedRouteCount = 0
        var zoomInCount = 0
        var zoomOutCount = 0
        var routeAvailableCount = 0

        override fun showWaiting() { waitingShown = true }
        override fun showUnavailable() = Unit
        override fun showRouteAvailable() { routeAvailableCount++ }
        override fun clearRoute() { clearedRouteCount++ }
        override fun renderRoute(points: List<RoutePoint>, follow: Boolean, frameRoute: Boolean) {
            renderedRoutes += points.toList()
            lastPoint = points.lastOrNull()
            followValues += follow
            frameValues += frameRoute
        }
        override fun focus(point: RoutePoint, zoom: Float) {
            focusedPoint = point
            focusedZoom = zoom
        }
        override fun frame(point: RoutePoint, zoom: Float) {
            framedPoint = point
            framedZoom = zoom
        }
        override fun frameRoute(points: List<RoutePoint>, padding: Int) {
            framedRoute = points
            frameRoutePadding = padding
        }
        override fun setOnUserPanListener(listener: () -> Unit) = Unit
        override fun zoomIn() { zoomInCount++ }
        override fun zoomOut() { zoomOutCount++ }
    }
}

package br.com.helpdev.velocimetroalerta

import android.graphics.Color
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.maps.model.Polyline
import com.google.android.gms.maps.model.PolylineOptions
import br.com.helpdev.velocimetroalerta.gps.RoutePoint
import br.com.helpdev.velocimetroalerta.gps.RouteSnapshot

interface RouteMapRenderer {
    fun showWaiting()
    fun showUnavailable()
    fun showRouteAvailable()
    fun clearRoute()
    fun renderRoute(points: List<RoutePoint>, follow: Boolean, frameRoute: Boolean)
    fun frame(point: RoutePoint, zoom: Float)
    fun frameRoute(points: List<RoutePoint>, padding: Int) {
        points.lastOrNull()?.let { frame(it, RouteMapState.NEARBY_ZOOM) }
    }
    fun focus(point: RoutePoint, zoom: Float)
    fun setOnUserPanListener(listener: () -> Unit)
    fun zoomIn()
    fun zoomOut()
}

/** Provider-neutral display state, deliberately kept simple enough for JVM tests. */
class RouteMapState(private val renderer: RouteMapRenderer) {
    private val points = mutableListOf<RoutePoint>()
    private var lastRevision = -1L
    private var hasFramedRoute = false
    private var cameraChangedByUser = false

    var isFollowing: Boolean = true
        private set
    val hasRoute: Boolean get() = points.isNotEmpty()

    fun applySnapshot(snapshot: RouteSnapshot) {
        if (snapshot.revision <= lastRevision) return
        lastRevision = snapshot.revision
        points.clear()
        points.addAll(snapshot.points)
        render(frameRoute = points.isNotEmpty() && !hasFramedRoute)
    }

    fun onPointAdded(revision: Long, point: RoutePoint) {
        if (revision <= lastRevision) return
        lastRevision = revision
        points += point
        render(frameRoute = points.size == 1 && !hasFramedRoute)
    }

    fun onRouteReset(revision: Long) {
        if (revision <= lastRevision) return
        lastRevision = revision
        points.clear()
        hasFramedRoute = false
        cameraChangedByUser = false
        renderer.clearRoute()
        renderer.showWaiting()
    }

    fun onUserPan() {
        isFollowing = false
        cameraChangedByUser = true
    }

    fun recenter() {
        isFollowing = true
        cameraChangedByUser = false
        points.lastOrNull()?.let { renderer.focus(it, NEARBY_ZOOM) }
    }

    fun onMapViewportReady() {
        if (!cameraChangedByUser) {
            if (points.size > 1) renderer.frameRoute(points.toList(), ROUTE_PADDING_PX)
            else points.lastOrNull()?.let { renderer.frame(it, NEARBY_ZOOM) }
        }
    }

    fun zoomIn() {
        cameraChangedByUser = true
        renderer.zoomIn()
    }

    fun zoomOut() {
        cameraChangedByUser = true
        renderer.zoomOut()
    }

    fun showUnavailable() {
        if (points.isEmpty()) renderer.showUnavailable()
    }

    private fun render(frameRoute: Boolean) {
        if (points.isEmpty()) {
            renderer.showWaiting()
            return
        }
        renderer.renderRoute(points.toList(), isFollowing, frameRoute)
        renderer.showRouteAvailable()
        if (frameRoute) hasFramedRoute = true
    }

    companion object {
        const val NEARBY_ZOOM = 17f
        const val ROUTE_PADDING_PX = 64
    }
}

/** Owns exactly one polyline and one marker for the lifetime of this map view. */
class GoogleRouteMapRenderer(
    private val map: GoogleMap,
    private val waiting: () -> Unit,
    private val unavailable: () -> Unit,
    private val routeAvailable: () -> Unit
) : RouteMapRenderer {
    private var polyline: Polyline? = null
    private var currentMarker: Marker? = null

    init {
        map.uiSettings.isMyLocationButtonEnabled = false
        map.setOnCameraMoveStartedListener { reason ->
            if (reason == GoogleMap.OnCameraMoveStartedListener.REASON_GESTURE) onUserPan()
        }
    }

    private var onUserPan: () -> Unit = {}

    override fun setOnUserPanListener(listener: () -> Unit) {
        onUserPan = listener
    }

    override fun showWaiting() = waiting()

    override fun showUnavailable() = unavailable()

    override fun showRouteAvailable() = routeAvailable()

    override fun clearRoute() {
        polyline?.remove()
        polyline = null
        currentMarker?.remove()
        currentMarker = null
    }

    override fun renderRoute(points: List<RoutePoint>, follow: Boolean, frameRoute: Boolean) {
        val coordinates = points.map { LatLng(it.latitude, it.longitude) }
        val line = polyline
        if (line == null) {
            polyline = map.addPolyline(
                PolylineOptions().addAll(coordinates).color(Color.rgb(0, 150, 136)).width(10f)
            )
        } else {
            line.points = coordinates
        }

        val final = coordinates.last()
        val marker = currentMarker
        if (marker == null) {
            currentMarker = map.addMarker(
                MarkerOptions().position(final).title("Posição atual")
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE))
            )
        } else {
            marker.position = final
        }

        when {
            frameRoute -> this.frameRoute(points, RouteMapState.ROUTE_PADDING_PX)
            follow -> map.animateCamera(CameraUpdateFactory.newLatLng(final))
        }
    }

    override fun frame(point: RoutePoint, zoom: Float) {
        map.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(point.latitude, point.longitude), zoom))
    }

    override fun frameRoute(points: List<RoutePoint>, padding: Int) {
        if (points.isEmpty()) return
        if (points.size == 1) {
            frame(points.single(), RouteMapState.NEARBY_ZOOM)
            return
        }
        val bounds = com.google.android.gms.maps.model.LatLngBounds.builder()
            .apply { points.forEach { include(LatLng(it.latitude, it.longitude)) } }
            .build()
        try {
            map.moveCamera(CameraUpdateFactory.newLatLngBounds(bounds, padding))
        } catch (_: IllegalStateException) {
            val last = points.last()
            map.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(last.latitude, last.longitude), RouteMapState.NEARBY_ZOOM))
        }
    }

    override fun focus(point: RoutePoint, zoom: Float) {
        map.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(point.latitude, point.longitude), zoom))
    }

    override fun zoomIn() = map.animateCamera(CameraUpdateFactory.zoomIn())

    override fun zoomOut() = map.animateCamera(CameraUpdateFactory.zoomOut())
}

package br.com.helpdev.velocimetroalerta

import android.graphics.Color
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.maps.model.Polyline
import com.google.android.gms.maps.model.PolylineOptions
import br.com.helpdev.velocimetroalerta.gps.RoutePoint
import br.com.helpdev.velocimetroalerta.gps.RouteSnapshot

interface RouteMapRenderer {
    fun showWaiting()
    fun showUnavailable()
    fun clearRoute()
    fun renderRoute(points: List<RoutePoint>, follow: Boolean, frameRoute: Boolean)
    fun focus(point: RoutePoint)
    fun setOnUserPanListener(listener: () -> Unit)
}

/** Provider-neutral display state, deliberately kept simple enough for JVM tests. */
class RouteMapState(private val renderer: RouteMapRenderer) {
    private val points = mutableListOf<RoutePoint>()
    private var lastRevision = -1L
    private var hasFramedRoute = false

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
        renderer.clearRoute()
        renderer.showWaiting()
    }

    fun onUserPan() {
        isFollowing = false
    }

    fun recenter() {
        isFollowing = true
        points.lastOrNull()?.let(renderer::focus)
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
        if (frameRoute) hasFramedRoute = true
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
                PolylineOptions().addAll(coordinates).color(Color.rgb(25, 118, 210)).width(10f)
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
            frameRoute && coordinates.size > 1 -> {
                val bounds = LatLngBounds.builder().apply { coordinates.forEach(::include) }.build()
                map.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, 72))
            }
            frameRoute -> map.animateCamera(CameraUpdateFactory.newLatLngZoom(final, 16f))
            follow -> map.animateCamera(CameraUpdateFactory.newLatLng(final))
        }
        routeAvailable()
    }

    override fun focus(point: RoutePoint) {
        map.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(point.latitude, point.longitude), 16f))
    }
}

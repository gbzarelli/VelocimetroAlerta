package br.com.helpdev.velocimetroalerta

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.doOnLayout
import androidx.fragment.app.Fragment
import com.google.android.gms.maps.SupportMapFragment
import br.com.helpdev.velocimetroalerta.databinding.FragmentRouteMapBinding
import br.com.helpdev.velocimetroalerta.gps.RouteObserver
import br.com.helpdev.velocimetroalerta.gps.RoutePoint
import br.com.helpdev.velocimetroalerta.gps.RouteSnapshot
import br.com.helpdev.velocimetroalerta.gps.SpeedometerService

class RouteMapFragment : Fragment() {
    private var _binding: FragmentRouteMapBinding? = null
    private val binding get() = _binding ?: error("FragmentRouteMapBinding is only available with its view")
    private val mainHandler = Handler(Looper.getMainLooper())
    private val mapLoadTimeout = Runnable {
        if (!mapTilesReady && _binding != null) showStatus(R.string.route_map_display_error)
    }
    private var service: SpeedometerService? = null
    private var staticRouteMode = false
    private var staticRoutePoints: List<RoutePoint>? = null
    private var renderer: RouteMapRenderer? = null
    private var routeState: RouteMapState? = null
    private var observer: RouteObserver? = null
    private var subscriptionGeneration = 0
    private var viewGeneration = 0
    private var mapReady = false
    private var mapTilesReady = false
    private var serviceUnavailable = true

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        viewGeneration++
        _binding = FragmentRouteMapBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.routeMapStatus.visibility = View.VISIBLE
        binding.routeMapStatus.setText(R.string.route_map_waiting)
        binding.routeMapRecenter.setOnClickListener { routeState?.recenter() }
        binding.routeMapZoomIn.setOnClickListener { routeState?.zoomIn() }
        binding.routeMapZoomOut.setOnClickListener { routeState?.zoomOut() }
        val mapFragment = childFragmentManager.findFragmentById(R.id.google_map) as? SupportMapFragment
        if (mapFragment == null) {
            showUnavailable()
        } else {
            val generation = viewGeneration
            mapFragment.getMapAsync { map ->
                if (generation == viewGeneration) onMapReady(map)
            }
        }
    }

    private fun onMapReady(map: com.google.android.gms.maps.GoogleMap) {
        if (_binding == null) return
        try {
            renderer = GoogleRouteMapRenderer(
                map,
                waiting = { showStatus(R.string.route_map_waiting) },
                unavailable = { showStatus(R.string.route_map_unavailable) },
                routeAvailable = { _binding?.routeMapStatus?.visibility = View.GONE }
            )
            routeState = RouteMapState(renderer!!).also { state ->
                renderer!!.setOnUserPanListener(state::onUserPan)
            }
            mapReady = true
            val generation = viewGeneration
            map.setOnMapLoadedCallback {
                if (generation != viewGeneration) return@setOnMapLoadedCallback
                mapTilesReady = true
                mainHandler.removeCallbacks(mapLoadTimeout)
                when {
                    routeState?.hasRoute == true -> _binding?.routeMapStatus?.visibility = View.GONE
                    serviceUnavailable -> routeState?.showUnavailable()
                    else -> routeState?.applySnapshot(RouteSnapshot(0, emptyList()))
                }
            }
            mainHandler.postDelayed(mapLoadTimeout, MAP_LOAD_TIMEOUT_MS)
            routeState?.applySnapshot(RouteSnapshot(0, emptyList()))
            staticRoutePoints?.let { points -> routeState?.applySnapshot(RouteSnapshot(1, points)) }
            if (serviceUnavailable) routeState?.showUnavailable()
            maybeSubscribe()
            (childFragmentManager.findFragmentById(R.id.google_map) as? SupportMapFragment)
                ?.view
                ?.doOnLayout {
                    if (generation == viewGeneration && _binding != null) {
                        routeState?.onMapViewportReady()
                    }
                }
        } catch (_: RuntimeException) {
            showUnavailable()
        }
    }

    fun setRouteService(routeService: SpeedometerService?) {
        if (staticRouteMode) return
        if (service === routeService) {
            maybeSubscribe()
            return
        }
        unsubscribe()
        service = routeService
        serviceUnavailable = routeService == null
        if (routeService == null) routeState?.showUnavailable() else maybeSubscribe()
    }

    fun onRouteServiceDisconnected() {
        if (staticRouteMode) return
        unsubscribe()
        service = null
        serviceUnavailable = true
        routeState?.showUnavailable()
    }

    fun showUnavailable() {
        routeState?.showUnavailable() ?: showStatus(R.string.route_map_unavailable)
    }

    /** Displays an immutable saved track without binding to or observing the live tracking service. */
    fun showStaticRoute(points: List<RoutePoint>) {
        unsubscribe()
        service = null
        staticRouteMode = true
        staticRoutePoints = points.toList()
        serviceUnavailable = points.isEmpty()
        if (mapReady) {
            routeState?.applySnapshot(RouteSnapshot(1, staticRoutePoints.orEmpty()))
            if (points.isEmpty()) routeState?.showUnavailable()
        }
    }

    override fun onStart() {
        super.onStart()
        maybeSubscribe()
    }

    override fun onStop() {
        unsubscribe()
        mainHandler.removeCallbacks(mapLoadTimeout)
        super.onStop()
    }

    override fun onDestroyView() {
        unsubscribe()
        viewGeneration++
        renderer = null
        routeState = null
        mapReady = false
        mapTilesReady = false
        _binding = null
        super.onDestroyView()
    }

    private fun maybeSubscribe() {
        if (staticRouteMode) return
        val currentService = service ?: return
        if (!mapReady || _binding == null || !lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED) || observer != null) return
        if (!currentService.isRunning) {
            routeState?.showUnavailable()
            return
        }
        val generation = ++subscriptionGeneration
        val routeObserver = object : RouteObserver {
            override fun onPointAdded(revision: Long, point: RoutePoint) {
                mainHandler.post {
                    if (generation == subscriptionGeneration && _binding != null && lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED)) {
                        routeState?.onPointAdded(revision, point)
                    }
                }
            }

            override fun onRouteReset(revision: Long) {
                mainHandler.post {
                    if (generation == subscriptionGeneration && _binding != null && lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED)) {
                        routeState?.onRouteReset(revision)
                    }
                }
            }
        }
        observer = routeObserver
        val snapshot = currentService.subscribeRoute(routeObserver)
        routeState?.applySnapshot(snapshot)
    }

    private fun unsubscribe() {
        subscriptionGeneration++
        val currentObserver = observer ?: return
        observer = null
        service?.unsubscribeRoute(currentObserver)
    }

    private fun showStatus(message: Int) {
        val currentBinding = _binding ?: return
        currentBinding.routeMapStatus.setText(message)
        currentBinding.routeMapStatus.visibility = View.VISIBLE
    }

    private companion object {
        const val MAP_LOAD_TIMEOUT_MS = 12_000L
    }
}

package br.com.helpdev.velocimetroalerta

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
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
                routeAvailable = { if (mapTilesReady) _binding?.routeMapStatus?.visibility = View.GONE }
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
            if (serviceUnavailable) routeState?.showUnavailable()
            maybeSubscribe()
        } catch (_: RuntimeException) {
            showUnavailable()
        }
    }

    fun setRouteService(routeService: SpeedometerService?) {
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
        unsubscribe()
        service = null
        serviceUnavailable = true
        routeState?.showUnavailable()
    }

    fun showUnavailable() {
        routeState?.showUnavailable() ?: showStatus(R.string.route_map_unavailable)
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

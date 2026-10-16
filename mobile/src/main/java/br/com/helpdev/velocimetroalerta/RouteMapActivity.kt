package br.com.helpdev.velocimetroalerta

import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import br.com.helpdev.velocimetroalerta.databinding.ActivityRouteMapBinding
import br.com.helpdev.velocimetroalerta.gps.SpeedometerService

class RouteMapActivity : AppCompatActivity() {
    private lateinit var binding: ActivityRouteMapBinding
    private var bindingRegistered = false
    private var bindingRequested = false

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            val service = (binder as? SpeedometerService.MyBinder)?.speedometerService
            routeFragment()?.setRouteService(service?.takeIf { it.isRunning })
        }

        override fun onServiceDisconnected(name: ComponentName) {
            routeFragment()?.onRouteServiceDisconnected()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        EdgeToEdge.enable(window)
        binding = ActivityRouteMapBinding.inflate(layoutInflater)
        setContentView(binding.root)
        EdgeToEdge.applySystemBarInsets(window, binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.apply {
            title = getString(R.string.route_map_title)
            setDisplayHomeAsUpEnabled(true)
        }
        binding.toolbar.setNavigationOnClickListener { finish() }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = finish()
        })

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.route_map_container, RouteMapFragment())
                .commitNow()
        }
    }

    override fun onStart() {
        super.onStart()
        if (bindingRequested) return
        bindingRequested = true
        val intent = Intent(this, SpeedometerService::class.java)
        val bound = try {
            bindService(intent, serviceConnection, 0)
        } catch (_: RuntimeException) {
            false
        }
        bindingRegistered = bound
        if (!bound) routeFragment()?.showUnavailable()
    }

    override fun onStop() {
        routeFragment()?.setRouteService(null)
        if (bindingRegistered) {
            unbindService(serviceConnection)
            bindingRegistered = false
        }
        bindingRequested = false
        super.onStop()
    }

    private fun routeFragment() = supportFragmentManager
        .findFragmentById(R.id.route_map_container) as? RouteMapFragment
}

package br.com.helpdev.velocimetroalerta

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import br.com.helpdev.velocimetroalerta.activities.ActivityRepository
import java.util.concurrent.Executors

class SavedRouteMapActivity : AppCompatActivity() {
    private val ioExecutor = Executors.newSingleThreadExecutor()
    private var routeFragment: RouteMapFragment? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        EdgeToEdge.enable(window)
        setContentView(R.layout.activity_saved_route_map)
        EdgeToEdge.applySystemBarInsets(window, findViewById(R.id.saved_route_map_root))
        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.apply {
            title = getString(R.string.activity_detail_map_title)
            setDisplayHomeAsUpEnabled(true)
        }
        toolbar.setNavigationOnClickListener { finish() }

        routeFragment = supportFragmentManager.findFragmentById(R.id.route_map_container) as? RouteMapFragment
        if (routeFragment == null && savedInstanceState == null) {
            routeFragment = RouteMapFragment()
            supportFragmentManager.beginTransaction()
                .replace(R.id.route_map_container, routeFragment!!)
                .commitNow()
        }
        loadRoute(intent.getStringExtra(EXTRA_ACTIVITY_ID))
    }

    private fun loadRoute(id: String?) {
        if (id.isNullOrBlank()) {
            routeFragment?.showStaticRoute(emptyList())
            return
        }
        ioExecutor.execute {
            val route = ActivityRepository(applicationContext.filesDir).loadRoute(id).orEmpty()
            runOnUiThread {
                if (!isFinishing) routeFragment?.showStaticRoute(route)
            }
        }
    }

    override fun onDestroy() {
        ioExecutor.shutdown()
        super.onDestroy()
    }

    companion object {
        const val EXTRA_ACTIVITY_ID = "saved_route_map.activity_id"
        fun newIntent(context: Context, id: String) = Intent(context, SavedRouteMapActivity::class.java)
            .putExtra(EXTRA_ACTIVITY_ID, id)
    }
}

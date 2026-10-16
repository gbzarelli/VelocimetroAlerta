package br.com.helpdev.velocimetroalerta

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import br.com.helpdev.velocimetroalerta.activities.ActivityRepository
import br.com.helpdev.velocimetroalerta.activities.ActivitySummary
import br.com.helpdev.velocimetroalerta.activities.RoutePreviewView
import java.io.IOException
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class ActivityDetailActivity : AppCompatActivity() {
    private val ioExecutor = Executors.newSingleThreadExecutor()
    private var activityId: String? = null
    private lateinit var detailContent: View
    private lateinit var loading: View
    private lateinit var error: View
    private lateinit var exportButton: View

    private val createGpxDocument = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/gpx+xml")
    ) { uri ->
        val id = activityId
        if (uri != null && id != null) exportGpx(uri, id)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        EdgeToEdge.enable(window)
        setContentView(R.layout.activity_activity_detail)
        EdgeToEdge.applySystemBarInsets(window, findViewById(R.id.activity_detail_root))
        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationOnClickListener { finish() }

        detailContent = findViewById(R.id.activity_detail_content)
        loading = findViewById(R.id.activity_detail_loading)
        error = findViewById(R.id.activity_detail_error)
        exportButton = findViewById(R.id.activity_export_gpx)
        activityId = intent.getStringExtra(EXTRA_ACTIVITY_ID)
        exportButton.setOnClickListener { activityId?.let(::launchExport) }
        loadActivity()
    }

    private fun loadActivity() {
        val id = activityId
        if (id.isNullOrBlank()) {
            showUnavailable()
            return
        }
        ioExecutor.execute {
            val repository = ActivityRepository(applicationContext.filesDir)
            val loaded = repository.loadSummary(id)
            runOnUiThread {
                if (isFinishing) return@runOnUiThread
                if (loaded == null) showUnavailable() else bindActivity(loaded)
            }
        }
    }

    private fun bindActivity(summary: ActivitySummary) {
        loading.visibility = View.GONE
        error.visibility = View.GONE
        detailContent.visibility = View.VISIBLE
        exportButton.visibility = View.VISIBLE
        supportActionBar?.title = summary.name

        findViewById<RoutePreviewView>(R.id.activity_route_preview).apply {
            submit(summary.routePreview)
            setOnClickListener { startActivity(SavedRouteMapActivity.newIntent(this@ActivityDetailActivity, summary.id)) }
        }
        findViewById<TextView>(R.id.activity_name).text = summary.name
        val started = SimpleDateFormat("EEE, d MMM yyyy, HH:mm", Locale.getDefault())
            .format(Date(summary.startedAtEpochMillis))
        findViewById<TextView>(R.id.activity_started).text = getString(R.string.activity_detail_started, started)

        val number = NumberFormat.getNumberInstance(Locale.getDefault()).apply {
            minimumFractionDigits = 1
            maximumFractionDigits = 1
        }
        findViewById<TextView>(R.id.activity_distance).text = "${number.format(summary.distanceKm)} km"
        findViewById<TextView>(R.id.activity_moving_duration).text = formatDuration(summary.movingDurationMillis)
        findViewById<TextView>(R.id.activity_average_speed).text = "${number.format(summary.averageSpeedKmh)} km/h"

        val metrics = findViewById<LinearLayout>(R.id.activity_detail_metrics)
        metrics.removeAllViews()
        addMetric(metrics, R.string.activity_metric_total_duration, formatDuration(summary.totalDurationMillis))
        addMetric(metrics, R.string.activity_metric_paused_duration, formatDuration(summary.pausedDurationMillis))
        addMetric(metrics, R.string.activity_metric_max_speed, "${number.format(summary.maxSpeedKmh)} km/h")
        addMetric(metrics, R.string.activity_metric_paused_distance, "${number.format(summary.distancePausedKm)} km")
        addMetric(metrics, R.string.activity_metric_elevation_gain, "${number.format(summary.elevationGainMeters)} m")
        addMetric(metrics, R.string.activity_metric_elevation_loss, "${number.format(summary.elevationLossMeters)} m")
        if (summary.hasCadenceData) {
            summary.averageCadenceRpm?.let { addMetric(metrics, R.string.activity_metric_avg_cadence, "$it rpm") }
            summary.maxCadenceRpm?.let { addMetric(metrics, R.string.activity_metric_max_cadence, "$it rpm") }
        }
        if (summary.hasHeartRateData) {
            summary.averageHeartRateBpm?.let { addMetric(metrics, R.string.activity_metric_avg_heart_rate, "$it bpm") }
            summary.maxHeartRateBpm?.let { addMetric(metrics, R.string.activity_metric_max_heart_rate, "$it bpm") }
        }
        if (summary.hasTemperatureData) summary.temperatureCelsius?.let {
            addMetric(metrics, R.string.activity_metric_temperature, "$it °C")
        }
        if (summary.hasHumidityData) summary.humidityPercent?.let {
            addMetric(metrics, R.string.activity_metric_humidity, "$it%")
        }
        if (summary.finalAltitudeMeters > 0) addMetric(
            metrics, R.string.activity_metric_altitude, "${number.format(summary.finalAltitudeMeters)} m"
        )
        if (summary.gpsAccuracyMeters > 0) addMetric(
            metrics, R.string.activity_metric_gps_accuracy, "${number.format(summary.gpsAccuracyMeters)} m"
        )
    }

    private fun addMetric(container: LinearLayout, label: Int, value: String) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(12), 0, dp(12))
        }
        row.addView(TextView(this).apply {
            text = getString(label)
            textSize = 15f
            setTextColor(0xFF607D8B.toInt())
        }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(TextView(this).apply {
            text = value
            textSize = 16f
            setTextColor(0xFF263238.toInt())
        })
        container.addView(row)
        val divider = View(this).apply { setBackgroundColor(0xFFECEFF1.toInt()) }
        container.addView(divider, LinearLayout.LayoutParams(-1, dp(1)))
    }

    private fun launchExport(id: String) {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        createGpxDocument.launch("VEL_ALERTA_${timestamp}_${id.take(8)}.gpx")
    }

    private fun exportGpx(uri: Uri, id: String) {
        exportButton.isEnabled = false
        ioExecutor.execute {
            val result = runCatching {
                val source = ActivityRepository(applicationContext.filesDir).gpxFile(id)
                    ?: throw IOException("Saved GPX is not available")
                val output = contentResolver.openOutputStream(uri, "w")
                    ?: throw IOException("The document provider did not open an output stream")
                output.use { destination -> source.inputStream().use { input -> input.copyTo(destination) } }
            }
            runOnUiThread {
                if (isFinishing) return@runOnUiThread
                exportButton.isEnabled = true
                result.onSuccess {
                    AlertDialog.Builder(this)
                        .setMessage(R.string.activity_exported)
                        .setPositiveButton(R.string.bt_ok, null)
                        .show()
                }.onFailure { failure ->
                    Log.e(LOG_TAG, "Unable to export saved GPX", failure)
                    AlertDialog.Builder(this)
                        .setMessage(getString(R.string.activity_export_error, failure.message ?: ""))
                        .setPositiveButton(R.string.bt_ok, null)
                        .show()
                }
            }
        }
    }

    private fun showUnavailable() {
        loading.visibility = View.GONE
        detailContent.visibility = View.GONE
        exportButton.visibility = View.GONE
        error.visibility = View.VISIBLE
    }

    private fun formatDuration(milliseconds: Long): String {
        val hours = TimeUnit.MILLISECONDS.toHours(milliseconds)
        val minutes = TimeUnit.MILLISECONDS.toMinutes(milliseconds) - TimeUnit.HOURS.toMinutes(hours)
        val seconds = TimeUnit.MILLISECONDS.toSeconds(milliseconds) - TimeUnit.MINUTES.toSeconds(
            TimeUnit.MILLISECONDS.toMinutes(milliseconds)
        )
        return if (hours > 0) String.format(Locale.getDefault(), "%d:%02d:%02d", hours, minutes, seconds)
        else String.format(Locale.getDefault(), "%d:%02d", minutes, seconds)
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    override fun onDestroy() {
        ioExecutor.shutdown()
        super.onDestroy()
    }

    companion object {
        const val EXTRA_ACTIVITY_ID = "activity_detail.id"
        private const val LOG_TAG = "ActivityDetail"
        fun newIntent(context: Context, id: String) = Intent(context, ActivityDetailActivity::class.java)
            .putExtra(EXTRA_ACTIVITY_ID, id)
    }
}

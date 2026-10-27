package br.com.helpdev.velocimetroalerta

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.inputmethod.InputMethodManager
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import br.com.helpdev.velocimetroalerta.activities.ActivityRepository
import br.com.helpdev.velocimetroalerta.activities.ActivitySummary
import br.com.helpdev.velocimetroalerta.activities.ActivityType
import br.com.helpdev.velocimetroalerta.activities.RoutePreviewView
import br.com.helpdev.velocimetroalerta.ads.BannerAdController
import br.com.helpdev.velocimetroalerta.strava.PendingManualActivityStore
import br.com.helpdev.velocimetroalerta.strava.StravaConnectionRepository
import br.com.helpdev.velocimetroalerta.strava.StravaSyncCoordinator
import br.com.helpdev.velocimetroalerta.strava.StravaSyncStore
import br.com.helpdev.velocimetroalerta.strava.StravaUploadOrigin
import br.com.helpdev.velocimetroalerta.strava.StravaUploadStatus
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
    private lateinit var stravaPanel: View
    private lateinit var stravaButton: View
    private lateinit var stravaStatus: TextView
    private lateinit var stravaProgress: ProgressBar
    private lateinit var stravaNeedsAction: View
    private lateinit var bannerAdController: BannerAdController
    private var loadedSummary: ActivitySummary? = null
    private var isMutatingActivity = false
    private val stravaStore by lazy { StravaSyncStore(applicationContext) }
    private val pendingManualStore by lazy { PendingManualActivityStore(applicationContext) }
    private val statusHandler = Handler(Looper.getMainLooper())
    private val statusRefresh = object : Runnable {
        override fun run() {
            renderStravaState()
            statusHandler.postDelayed(this, STATUS_REFRESH_MILLIS)
        }
    }

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
        stravaPanel = findViewById(R.id.activity_strava_sync_panel)
        stravaButton = findViewById(R.id.activity_export_strava)
        stravaStatus = findViewById(R.id.activity_strava_status)
        stravaProgress = findViewById(R.id.activity_strava_progress)
        stravaNeedsAction = findViewById(R.id.activity_strava_needs_action)
        bannerAdController = BannerAdController(
            this,
            findViewById(R.id.activity_detail_banner_container),
            getString(R.string.ad_unit_activity_details_banner),
            (application as VelocimetroApplication).adsConsentManager
        )
        activityId = intent.getStringExtra(EXTRA_ACTIVITY_ID)
        exportButton.setOnClickListener { activityId?.let(::launchExport) }
        stravaButton.setOnClickListener { startManualStravaSync() }
        findViewById<View>(R.id.activity_strava_already_there).setOnClickListener { confirmManualPresence() }
        findViewById<View>(R.id.activity_strava_retry).setOnClickListener { confirmAmbiguousRetry() }
        loadActivity()
    }

    override fun onResume() {
        super.onResume()
        bannerAdController.onResume()
        statusHandler.removeCallbacks(statusRefresh)
        statusHandler.post(statusRefresh)
    }

    override fun onPause() {
        statusHandler.removeCallbacks(statusRefresh)
        bannerAdController.onPause()
        super.onPause()
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
        loadedSummary = summary
        bannerAdController.requestWhenAdsAreReady()
        invalidateOptionsMenu()
        loading.visibility = View.GONE
        error.visibility = View.GONE
        detailContent.visibility = View.VISIBLE
        exportButton.visibility = View.VISIBLE
        stravaPanel.visibility = View.VISIBLE
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
        summary.activityType?.let { addMetric(metrics, R.string.activity_type_label, it.label(this)) }
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
        summary.finalAltitudeMeters?.takeIf { it > 0 }?.let {
            addMetric(metrics, R.string.activity_metric_altitude, "${number.format(it)} m")
        }
        if (summary.gpsAccuracyMeters > 0) addMetric(
            metrics, R.string.activity_metric_gps_accuracy, "${number.format(summary.gpsAccuracyMeters)} m"
        )
        renderStravaState()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_activity_detail, menu)
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        val canEdit = loadedSummary != null && !isMutatingActivity && !isStravaUploadActive()
        menu.findItem(R.id.action_rename_activity)?.isEnabled = canEdit
        menu.findItem(R.id.action_delete_activity)?.isEnabled = canEdit
        return super.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        R.id.action_rename_activity -> {
            loadedSummary?.let(::showRenameDialog)
            true
        }
        R.id.action_delete_activity -> {
            loadedSummary?.let(::confirmDelete)
            true
        }
        else -> super.onOptionsItemSelected(item)
    }

    private fun showRenameDialog(summary: ActivitySummary) {
        val input = EditText(this).apply {
            id = R.id.activity_name_input
            hint = getString(R.string.activity_rename_hint)
            setSingleLine(true)
            setText(summary.name)
            setSelection(text.length)
        }
        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.activity_rename_title)
            .setView(input)
            .setNegativeButton(R.string.bt_cancel, null)
            .setPositiveButton(R.string.activity_rename_save, null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val newName = input.text.toString().trim()
                if (newName.isEmpty()) {
                    input.error = getString(R.string.activity_name_required)
                    return@setOnClickListener
                }
                (getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager)
                    ?.hideSoftInputFromWindow(input.windowToken, 0)
                dialog.dismiss()
                renameActivity(summary.id, newName)
            }
        }
        dialog.show()
        input.requestFocus()
    }

    private fun renameActivity(id: String, name: String) {
        setMutationInProgress(true)
        ioExecutor.execute {
            val result = runCatching {
                ActivityRepository(applicationContext.filesDir).rename(id, name)
                    ?: throw IOException("Saved activity is unavailable")
            }
            runOnUiThread {
                if (isFinishing) return@runOnUiThread
                setMutationInProgress(false)
                result.onSuccess(::bindActivity).onFailure { failure ->
                    Log.e(LOG_TAG, "Unable to rename saved activity", failure)
                    Toast.makeText(this, R.string.activity_rename_error, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun confirmDelete(summary: ActivitySummary) {
        AlertDialog.Builder(this)
            .setTitle(R.string.activity_delete_confirm_title)
            .setMessage(R.string.activity_delete_confirm_message)
            .setNegativeButton(R.string.bt_cancel, null)
            .setPositiveButton(R.string.activity_delete) { _, _ -> deleteActivity(summary.id) }
            .show()
    }

    private fun deleteActivity(id: String) {
        setMutationInProgress(true)
        ioExecutor.execute {
            val result = runCatching { ActivityRepository(applicationContext.filesDir).delete(id) }
            runOnUiThread {
                if (isFinishing) return@runOnUiThread
                setMutationInProgress(false)
                result.onSuccess { deleted ->
                    if (deleted) {
                        StravaSyncCoordinator.cancelActivity(this, id)
                        setResult(RESULT_OK)
                        finish()
                    } else {
                        Toast.makeText(this, R.string.activity_delete_error, Toast.LENGTH_LONG).show()
                    }
                }.onFailure { failure ->
                    Log.e(LOG_TAG, "Unable to delete saved activity", failure)
                    Toast.makeText(this, R.string.activity_delete_error, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun setMutationInProgress(inProgress: Boolean) {
        isMutatingActivity = inProgress
        exportButton.isEnabled = !inProgress
        renderStravaState()
        invalidateOptionsMenu()
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
        stravaPanel.visibility = View.GONE
        error.visibility = View.VISIBLE
    }

    private fun renderStravaState() {
        if (!::stravaPanel.isInitialized) return
        val summary = loadedSummary ?: return
        val uid = FirebaseServices.auth(this)?.currentUser?.uid
        val record = uid?.let { stravaStore.get(it, summary.id) }
        val status = record?.status ?: StravaUploadStatus.NOT_UPLOADED
        val active = status == StravaUploadStatus.QUEUED || status == StravaUploadStatus.UPLOADING ||
            status == StravaUploadStatus.PROCESSING
        val needsAction = status == StravaUploadStatus.NEEDS_ACTION
        val localOnly = summary.activityType != null && summary.activityType.stravaSportType == null

        stravaProgress.visibility = if (active) View.VISIBLE else View.GONE
        stravaNeedsAction.visibility = if (needsAction) View.VISIBLE else View.GONE
        stravaStatus.visibility = View.VISIBLE
        when (status) {
            StravaUploadStatus.SYNCED -> {
                stravaButton.visibility = View.VISIBLE
                stravaButton.isEnabled = false
                (stravaButton as TextView).setText(R.string.strava_activity_synced)
                stravaStatus.visibility = View.GONE
            }
            StravaUploadStatus.MANUALLY_CONFIRMED -> {
                stravaButton.visibility = View.VISIBLE
                stravaButton.isEnabled = false
                (stravaButton as TextView).setText(R.string.strava_manual_confirmation)
                stravaStatus.visibility = View.GONE
            }
            StravaUploadStatus.QUEUED, StravaUploadStatus.UPLOADING, StravaUploadStatus.PROCESSING -> {
                stravaButton.visibility = View.GONE
                stravaStatus.setText(R.string.strava_syncing)
            }
            StravaUploadStatus.NEEDS_ACTION -> {
                stravaButton.visibility = View.GONE
                stravaStatus.setText(R.string.strava_needs_action)
            }
            StravaUploadStatus.RETRYABLE_ERROR -> {
                stravaButton.visibility = View.VISIBLE
                stravaButton.isEnabled = !isMutatingActivity && !localOnly
                (stravaButton as TextView).setText(R.string.strava_retry_type)
                stravaStatus.setText(R.string.strava_sync_failed)
            }
            StravaUploadStatus.REJECTED -> {
                stravaButton.visibility = View.VISIBLE
                stravaButton.isEnabled = !isMutatingActivity && !localOnly
                (stravaButton as TextView).setText(R.string.strava_retry_type)
                val statusCode = record?.failureHttpStatus
                val message = record?.failureMessage ?: getString(R.string.strava_rejection_no_details)
                stravaStatus.text = if (statusCode == null) {
                    getString(R.string.strava_sync_failed)
                } else {
                    getString(R.string.strava_upload_rejected, statusCode, message)
                }
            }
            else -> {
                stravaButton.visibility = View.VISIBLE
                stravaButton.isEnabled = !isMutatingActivity && !localOnly
                (stravaButton as TextView).setText(if (localOnly) R.string.strava_activity_local_only else R.string.strava_send_activity)
                stravaStatus.visibility = View.GONE
            }
        }
        invalidateOptionsMenu()
    }

    private fun isStravaUploadActive(): Boolean {
        val id = activityId ?: return false
        val uid = FirebaseServices.auth(this)?.currentUser?.uid ?: return false
        return stravaStore.get(uid, id)?.status in setOf(
            StravaUploadStatus.QUEUED,
            StravaUploadStatus.UPLOADING,
            StravaUploadStatus.PROCESSING
        )
    }

    private fun startManualStravaSync() {
        val summary = loadedSummary ?: return
        val type = summary.activityType
        if (type == null) {
            showLegacyActivityTypePicker(summary)
            return
        }
        if (type.stravaSportType == null) {
            AlertDialog.Builder(this)
                .setMessage(R.string.strava_activity_local_only)
                .setPositiveButton(R.string.bt_ok, null)
                .show()
            return
        }
        val uid = FirebaseServices.auth(this)?.currentUser?.uid
        if (uid == null || !StravaConnectionRepository(this).isConnectedLocally(uid)) {
            pendingManualStore.save(summary.id, uid)
            startActivity(Intent(this, AccountConnectionsActivity::class.java)
                .putExtra(AccountConnectionsActivity.EXTRA_PENDING_ACTIVITY_ID, summary.id))
            return
        }
        StravaSyncCoordinator.enqueueManual(this, uid, summary.id)
        renderStravaState()
    }

    private fun showLegacyActivityTypePicker(summary: ActivitySummary) {
        val types = ActivityType.stravaSupported
        AlertDialog.Builder(this)
            .setTitle(R.string.strava_choose_legacy_type)
            .setItems(types.map { it.label(this) }.toTypedArray()) { _, index ->
                val chosen = types[index]
                ioExecutor.execute {
                    val updated = runCatching {
                        ActivityRepository(applicationContext.filesDir).updateActivityType(summary.id, chosen)
                    }.getOrNull()
                    runOnUiThread {
                        if (updated == null) {
                            Toast.makeText(this, R.string.activity_detail_unavailable, Toast.LENGTH_LONG).show()
                        } else {
                            bindActivity(updated)
                            startManualStravaSync()
                        }
                    }
                }
            }
            .setNegativeButton(R.string.bt_cancel, null)
            .show()
    }

    private fun confirmManualPresence() {
        val id = activityId ?: return
        val uid = FirebaseServices.auth(this)?.currentUser?.uid ?: return
        val previous = stravaStore.getOrCreate(uid, id, StravaUploadOrigin.MANUAL)
        stravaStore.save(previous.copy(status = StravaUploadStatus.MANUALLY_CONFIRMED))
        renderStravaState()
    }

    private fun confirmAmbiguousRetry() {
        val id = activityId ?: return
        val uid = FirebaseServices.auth(this)?.currentUser?.uid ?: return
        AlertDialog.Builder(this)
            .setMessage(R.string.strava_retry_duplicate_warning)
            .setNegativeButton(R.string.bt_cancel, null)
            .setPositiveButton(R.string.retry) { _, _ ->
                StravaSyncCoordinator.retryAfterAmbiguousResult(this, uid, id)
                renderStravaState()
            }
            .show()
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
        statusHandler.removeCallbacks(statusRefresh)
        bannerAdController.onDestroy()
        ioExecutor.shutdown()
        super.onDestroy()
    }

    companion object {
        const val EXTRA_ACTIVITY_ID = "activity_detail.id"
        private const val LOG_TAG = "ActivityDetail"
        private const val STATUS_REFRESH_MILLIS = 1_000L
        fun newIntent(context: Context, id: String) = Intent(context, ActivityDetailActivity::class.java)
            .putExtra(EXTRA_ACTIVITY_ID, id)
    }
}

package br.com.helpdev.velocimetroalerta

import android.Manifest
import android.content.ComponentName
import android.content.DialogInterface
import android.content.Intent
import android.content.ServiceConnection
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.provider.Settings
import com.google.android.material.navigation.NavigationView
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.WindowManager
import android.widget.Toast

import br.com.helpdev.velocimetroalerta.dialogs.ConfirmDialogFrag
import br.com.helpdev.velocimetroalerta.gps.SpeedometerService
import br.com.helpdev.velocimetroalerta.permissions.RuntimePermissionPolicy
import br.com.helpdev.velocimetroalerta.sensors.SensorConnectionState
import br.com.helpdev.velocimetroalerta.sensors.SensorKind

class MainActivity : AppCompatActivity(), NavigationView.OnNavigationItemSelectedListener, ServiceConnection {

    private var callbackNotify: CallbackNotify? = null
    private var isServiceBound = false
    var speedometerService: SpeedometerService? = null
        private set

    private val configActivityLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { reloadPreferences() }
    private val activitiesLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { }

    private val trackingPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        if (hasPreciseLocationPermission()) {
            if (Build.VERSION.SDK_INT >= 33 && results[Manifest.permission.POST_NOTIFICATIONS] == false) {
                Toast.makeText(this, R.string.notification_permission_limited, Toast.LENGTH_LONG).show()
            }
            if (hasConfiguredBluetoothSensors() && Build.VERSION.SDK_INT >= 31 &&
                results[Manifest.permission.BLUETOOTH_CONNECT] == false) {
                Toast.makeText(this, R.string.bluetooth_permission_limited, Toast.LENGTH_LONG).show()
                val preferences = androidx.preference.PreferenceManager.getDefaultSharedPreferences(this)
                speedometerService?.let { service ->
                    if (preferences.getBoolean(getString(R.string.pref_hr_sensor), false)) {
                        service.reportSensorStatus(SensorKind.HEART_RATE, SensorConnectionState.PERMISSION_DENIED)
                    }
                    if (preferences.getBoolean(getString(R.string.pref_module_vel_alert), false)) {
                        service.reportSensorStatus(SensorKind.VAM, SensorConnectionState.PERMISSION_DENIED)
                    }
                }
            }
            startTrackingService()
        } else {
            showPreciseLocationRequired()
        }
    }

    private val sharePref: SharedPreferences
        get() = getSharedPreferences("sp_main_activity", MODE_PRIVATE)

    interface CallbackNotify {
        fun onServiceConnected(speedometerService: SpeedometerService?)

        fun onBeforeDisconnect(speedometerService: SpeedometerService)

        fun onCloseProgram()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        EdgeToEdge.enable(window)
        setContentView(R.layout.activity_main)
        val drawer = findViewById<DrawerLayout>(R.id.drawer_layout)
        EdgeToEdge.applySystemBarInsets(window, findViewById(R.id.app_bar_main))
        EdgeToEdge.applySystemBarInsets(window, findViewById(R.id.nav_view))
        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)

        val toggle = ActionBarDrawerToggle(
                this, drawer, toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close)
        drawer.addDrawerListener(toggle)
        toggle.syncState()

        val navigationView = findViewById<NavigationView>(R.id.nav_view)
        navigationView.setNavigationItemSelectedListener(this)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (drawer.isDrawerVisible(GravityCompat.START)) {
                    drawer.closeDrawer(GravityCompat.START)
                } else if (speedometerService?.isRunning == true) {
                    if (supportFragmentManager.findFragmentByTag(BACK_CONFIRMATION_TAG) == null) {
                        ConfirmDialogFrag.getInstance(
                            getString(R.string.confirme_sair),
                            true,
                            DialogInterface.OnClickListener { _, which ->
                                if (which == DialogInterface.BUTTON_POSITIVE) {
                                    stopService()
                                    finish()
                                }
                            }
                        ).show(supportFragmentManager, BACK_CONFIRMATION_TAG)
                    }
                } else {
                    stopService()
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })
    }

    fun requestTrackingStart() {
        val preferences = androidx.preference.PreferenceManager.getDefaultSharedPreferences(this)
        if (!preferences.getBoolean(LOCATION_BACKGROUND_DISCLOSURE_ACCEPTED, false)) {
            AlertDialog.Builder(this)
                .setTitle(R.string.location_background_disclosure_title)
                .setMessage(R.string.location_background_disclosure_message)
                .setPositiveButton(R.string.location_background_disclosure_continue) { _, _ ->
                    preferences.edit().putBoolean(LOCATION_BACKGROUND_DISCLOSURE_ACCEPTED, true).apply()
                    requestTrackingStart()
                }
                .setNegativeButton(R.string.location_background_disclosure_cancel, null)
                .show()
            return
        }

        val locationPermissions = RuntimePermissionPolicy.permissionsFor(
            RuntimePermissionPolicy.Feature.LOCATION,
            Build.VERSION.SDK_INT
        )
        val requestedPermissions = mutableSetOf<String>()
        val grantedLocationPermissions = locationPermissions.filterTo(mutableSetOf()) {
            checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED
        }
        requestedPermissions += RuntimePermissionPolicy.permissionsToRequest(
            RuntimePermissionPolicy.Feature.LOCATION,
            Build.VERSION.SDK_INT,
            grantedLocationPermissions
        )
        val notificationPermissions = RuntimePermissionPolicy.permissionsFor(
            RuntimePermissionPolicy.Feature.POST_NOTIFICATIONS,
            Build.VERSION.SDK_INT
        )
        requestedPermissions += notificationPermissions.filter {
            checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED
        }
        if (hasConfiguredBluetoothSensors()) {
            val bluetoothPermissions = RuntimePermissionPolicy.permissionsFor(
                RuntimePermissionPolicy.Feature.BLUETOOTH_CONNECT,
                Build.VERSION.SDK_INT
            )
            requestedPermissions += bluetoothPermissions.filter {
                checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED
            }
        }

        if (requestedPermissions.isEmpty()) {
            startTrackingService()
        } else {
            trackingPermissionLauncher.launch(requestedPermissions.toTypedArray())
        }
    }

    private fun hasPreciseLocationPermission(): Boolean =
        checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    internal fun stopTrackingIfLocationPermissionWasRevoked() {
        if (speedometerService?.isTracking == true && !hasPreciseLocationPermission()) {
            speedometerService?.stop()
        }
    }

    private fun hasConfiguredBluetoothSensors(): Boolean {
        val preferences = androidx.preference.PreferenceManager.getDefaultSharedPreferences(this)
        return preferences.getBoolean(getString(R.string.pref_hr_sensor), false) ||
            preferences.getBoolean(getString(R.string.pref_module_vel_alert), false)
    }

    private fun startTrackingService() {
        val intent = Intent(this, SpeedometerService::class.java).setAction(SpeedometerService.ACTION_START_TRACKING)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    private fun showPreciseLocationRequired() {
        AlertDialog.Builder(this)
            .setTitle(R.string.app_name)
            .setMessage(R.string.precise_location_required)
            .setPositiveButton(R.string.retry) { _, _ -> requestTrackingStart() }
            .setNegativeButton(R.string.open_settings) { _, _ ->
                startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
            }
            .setNeutralButton(R.string.bt_cancel, null)
            .show()
    }

    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        // Handle navigation view item clicks here.
        val id = item.itemId

        when (id) {
            R.id.nav_star -> openAppInGooglePlay()
            R.id.nav_privacy_policy -> openPrivacyPolicy()
            R.id.nav_atividades -> activitiesLauncher.launch(Intent(this, MyActivities::class.java))
            R.id.nav_config -> openConfigIfAllowed()
            R.id.nav_share -> shareApp()
        }

        val drawer = findViewById<DrawerLayout>(R.id.drawer_layout)
        drawer.closeDrawer(GravityCompat.START)
        return false
    }

    private fun openPrivacyPolicy() {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(getString(R.string.privacy_policy_url)))
        try {
            startActivity(intent)
        } catch (_: android.content.ActivityNotFoundException) {
            Toast.makeText(this, R.string.privacy_policy_unavailable, Toast.LENGTH_LONG).show()
        }
    }

    private fun openAppInGooglePlay() {
        val appPackageName = packageName
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$appPackageName")))
        } catch (e: android.content.ActivityNotFoundException) { // if there is no Google Play on device
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$appPackageName")))
        }

    }

    private fun shareApp() {
        val sendIntent = Intent()
        sendIntent.action = Intent.ACTION_SEND
        sendIntent.putExtra(Intent.EXTRA_TEXT, getString(R.string.app_name) + " https://play.google.com/store/apps/details?id=" + packageName)
        sendIntent.type = "text/plain"
        startActivity(sendIntent)
    }

    private fun openConfigIfAllowed() {
        val service = speedometerService
        if (service == null || !service.isRunning || service.isPause) {
            configActivityLauncher.launch(Intent(this, ConfigActivity::class.java))
            return
        }

        AlertDialog.Builder(this)
            .setTitle(R.string.app_name)
            .setMessage(R.string.info_config_in_running)
            .setPositiveButton(R.string.bt_ok, null)
            .show()
    }

    private fun stopService() {
        if (callbackNotify != null) callbackNotify!!.onCloseProgram()
        stopService(Intent(this, SpeedometerService::class.java))
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        loadIconKeepAlive(menu.getItem(0))
        return true
    }

    private fun loadIconKeepAlive(item: MenuItem) {
        if (sharePref.getBoolean(SP_KEEP_ALIVE, false)) {
            item.setIcon(R.drawable.ic_settings_brightness_black_48dp)
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            item.setIcon(R.drawable.ic_settings_brightness_black_48dp_off)
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        val id = item.itemId
        if (id == R.id.action_settings) {
            openConfigIfAllowed()
        } else if (id == R.id.action_keep_alive) {
            val msg = if (sharePref.getBoolean(SP_KEEP_ALIVE, false)) {
                sharePref.edit().putBoolean(SP_KEEP_ALIVE, false).apply()
                getString(R.string.bloqueio_ativado)
            } else {
                sharePref.edit().putBoolean(SP_KEEP_ALIVE, true).apply()
                getString(R.string.bloqueio_desativado)
            }
            Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
            loadIconKeepAlive(item)
        }

        return super.onOptionsItemSelected(item)
    }

    override fun onResume() {
        super.onResume()
        isServiceBound = bindService(Intent(this, SpeedometerService::class.java), this, BIND_AUTO_CREATE)
    }

    override fun onPause() {
        super.onPause()
        if (isServiceBound) {
            try {
                speedometerService?.let { service -> callbackNotify?.onBeforeDisconnect(service) }
            } catch (t: Throwable) {
                Log.e(LOG, "onBeforeDisconnect")
            }

            try {
                unbindService(this)
                isServiceBound = false
            } catch (t: Throwable) {
                Log.e(LOG, "unbindService")
            }

        }
    }

    private fun reloadPreferences() {
        if (speedometerService != null) {
            speedometerService!!.reloadPreferences()
        }
    }


    fun setCallbackNotify(callbackNotify: CallbackNotify) {
        this.callbackNotify = callbackNotify
    }

    override fun onServiceConnected(componentName: ComponentName, iBinder: IBinder) {
        if (iBinder is SpeedometerService.MyBinder) {
            speedometerService = iBinder.speedometerService
            stopTrackingIfLocationPermissionWasRevoked()
            reloadPreferences()
            if (callbackNotify != null) callbackNotify!!.onServiceConnected(speedometerService)
            speedometerService?.retryConfiguredSensorsAfterPermissionGrant()
        }
    }

    override fun onServiceDisconnected(componentName: ComponentName) {
        speedometerService = null
    }

    companion object {
        private const val BACK_CONFIRMATION_TAG = "back-confirmation"
        private const val LOCATION_BACKGROUND_DISCLOSURE_ACCEPTED = "location_background_disclosure_accepted"
        private const val SP_KEEP_ALIVE = "keep_alive"
        private const val LOG = "MainActivity"
    }
}

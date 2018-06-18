package br.com.helpdev.velocimetroalerta

import android.Manifest
import android.content.ComponentName
import android.content.DialogInterface
import android.content.Intent
import android.content.ServiceConnection
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.IBinder
import android.support.design.widget.NavigationView
import android.support.v4.app.ActivityCompat
import android.support.v4.view.GravityCompat
import android.support.v4.widget.DrawerLayout
import android.support.v7.app.ActionBarDrawerToggle
import android.support.v7.app.AlertDialog
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.Toolbar
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.WindowManager
import android.widget.Toast

import br.com.helpdev.velocimetroalerta.dialogs.ConfirmDialogFrag
import br.com.helpdev.velocimetroalerta.gps.SpeedometerService

class MainActivity : AppCompatActivity(), NavigationView.OnNavigationItemSelectedListener, ServiceConnection {

    private var callbackNotify: CallbackNotify? = null
    var speedometerService: SpeedometerService? = null
        private set

    private val isPermissionsGranted: Boolean
        get() = ActivityCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    private val sharePref: SharedPreferences
        get() = getSharedPreferences("sp_main_activity", MODE_PRIVATE)

    interface CallbackNotify {
        fun onServiceConnected(speedometerService: SpeedometerService?)

        fun onBeforeDisconnect(speedometerService: SpeedometerService)

        fun onCloseProgram()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)

        if (savedInstanceState == null) {
            loadPermissions()
        }

        val drawer = findViewById<DrawerLayout>(R.id.drawer_layout)
        val toggle = ActionBarDrawerToggle(
                this, drawer, toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close)
        drawer.addDrawerListener(toggle)
        toggle.syncState()

        val navigationView = findViewById<NavigationView>(R.id.nav_view)
        navigationView.setNavigationItemSelectedListener(this)
    }

    private fun loadPermissions() {
        if (isPermissionsGranted) {
            val service = Intent(this, SpeedometerService::class.java)
            startService(service)
            bindService(service, this, BIND_AUTO_CREATE)
        } else {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.ACCESS_FINE_LOCATION), 12)
        }
    }

    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        // Handle navigation view item clicks here.
        val id = item.itemId

        when (id) {
            R.id.nav_star -> openAppInGooglePlay()
            R.id.nav_atividades -> startActivityForResult(Intent(this, MyActivities::class.java), REQUEST_MY_ACTIVITIES)
            R.id.nav_config -> startActivityForResult(Intent(this, ConfigActivity::class.java), REQUEST_CONFIG_AUDIO)
            R.id.nav_share -> shareApp()
        }

        val drawer = findViewById<DrawerLayout>(R.id.drawer_layout)
        drawer.closeDrawer(GravityCompat.START)
        return false
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

    override fun onBackPressed() {
        val drawer = findViewById<DrawerLayout>(R.id.drawer_layout)
        if (drawer.isDrawerOpen(GravityCompat.START)) {
            drawer.closeDrawer(GravityCompat.START)
            return
        }

        if (speedometerService != null && speedometerService!!.isRunning) {
            if (supportFragmentManager.findFragmentByTag("DIALOG") == null) {
                ConfirmDialogFrag.getInstance(getString(R.string.confirme_sair), true, DialogInterface.OnClickListener { dialog, which ->
                    if (which == DialogInterface.BUTTON_POSITIVE) {
                        stopService()
                        this@MainActivity.finish()
                    }
                }).show(supportFragmentManager, "DIALOG")
            }
            return
        } else {
            stopService()
        }

        super.onBackPressed()
    }

    private fun stopService() {
        if (callbackNotify != null) callbackNotify!!.onCloseProgram()
        stopService(Intent(this, SpeedometerService::class.java))
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        for (i in grantResults) {
            if (i != PackageManager.PERMISSION_GRANTED) {
                loadPermissions()
                break
            }
        }
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
            if (speedometerService == null || !speedometerService!!.isRunning || speedometerService!!.isPause) {
                startActivityForResult(Intent(this, ConfigActivity::class.java), REQUEST_CONFIG_AUDIO)
            }else{
                val dialog = AlertDialog.Builder(this)
                dialog.setTitle(R.string.app_name)
                dialog.setMessage(R.string.info_config_in_running)
                dialog.setPositiveButton(R.string.bt_ok, null)
                dialog.create().show()
            }
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
        if (isPermissionsGranted) {
            bindService(Intent(this, SpeedometerService::class.java), this, BIND_AUTO_CREATE)
        }
    }

    override fun onPause() {
        super.onPause()
        if (speedometerService != null) {
            try {
                if (callbackNotify != null) callbackNotify!!.onBeforeDisconnect(speedometerService!!)
            } catch (t: Throwable) {
                Log.e(LOG, "onBeeforeDisconnect")
            }

            try {
                unbindService(this)
            } catch (t: Throwable) {
                Log.e(LOG, "unbindService")
            }

        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_CONFIG_AUDIO) {
            reloadPreferences()
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
            reloadPreferences()
            if (callbackNotify != null) callbackNotify!!.onServiceConnected(speedometerService)
        }
    }

    override fun onServiceDisconnected(componentName: ComponentName) {
        speedometerService = null
    }

    companion object {
        private const val REQUEST_CONFIG_AUDIO = 2
        private const val REQUEST_MY_ACTIVITIES = 29
        private const val SP_KEEP_ALIVE = "keep_alive"
        private const val LOG = "MainActivity"
    }
}

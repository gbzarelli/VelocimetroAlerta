package br.com.helpdev.velocimetroalerta

import android.content.pm.PackageManager
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar

class ConfigActivity : AppCompatActivity() {

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == br.com.helpdev.velocimetroalerta.dialogs.DialogHR.REQUEST_BLUETOOTH_PERMISSIONS ||
            requestCode == br.com.helpdev.velocimetroalerta.dialogs.DialogPrefVelAlertBLModule.REQUEST_BLUETOOTH_PERMISSIONS) {
            val granted = grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }
            supportFragmentManager.fragments.filterIsInstance<ConfigActivityFragment>()
                .forEach { it.onBluetoothPermissionResult(requestCode, granted) }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_config)
        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        if (supportActionBar != null) supportActionBar!!.setDisplayHomeAsUpEnabled(true)
    }

}

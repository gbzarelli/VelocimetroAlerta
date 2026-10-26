package br.com.helpdev.velocimetroalerta

import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import br.com.helpdev.velocimetroalerta.ads.BannerAdController
import br.com.helpdev.velocimetroalerta.ads.AdsConsentManager

class ConfigActivity : AppCompatActivity() {
    private lateinit var adsConsentManager: AdsConsentManager
    private lateinit var privacyOptionsButton: View
    private lateinit var bannerAdController: BannerAdController

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
        adsConsentManager = (application as VelocimetroApplication).adsConsentManager
        EdgeToEdge.enable(window)
        setContentView(R.layout.activity_config)
        EdgeToEdge.applySystemBarInsets(window, findViewById(R.id.config_root))
        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        if (supportActionBar != null) supportActionBar!!.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationOnClickListener { finish() }
        privacyOptionsButton = findViewById(R.id.privacy_options_button)
        privacyOptionsButton.setOnClickListener { adsConsentManager.showPrivacyOptions(this) }
        bannerAdController = BannerAdController(
            this,
            findViewById(R.id.settings_banner_container),
            getString(R.string.ad_unit_banner),
            adsConsentManager
        )
        bannerAdController.requestWhenAdsAreReady()
    }

    override fun onResume() {
        super.onResume()
        refreshPrivacyOptionsVisibility()
        bannerAdController.onResume()
    }

    override fun onPause() {
        bannerAdController.onPause()
        super.onPause()
    }

    override fun onDestroy() {
        bannerAdController.onDestroy()
        super.onDestroy()
    }

    private fun refreshPrivacyOptionsVisibility() {
        if (::privacyOptionsButton.isInitialized) {
            privacyOptionsButton.visibility = if (adsConsentManager.isPrivacyOptionsRequired) View.VISIBLE else View.GONE
        }
    }
}

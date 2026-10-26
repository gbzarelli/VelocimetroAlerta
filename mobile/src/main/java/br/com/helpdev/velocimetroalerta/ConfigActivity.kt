package br.com.helpdev.velocimetroalerta

import android.content.pm.PackageManager
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import br.com.helpdev.velocimetroalerta.ads.AdsConsentManager
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView

class ConfigActivity : AppCompatActivity() {
    private lateinit var adsConsentManager: AdsConsentManager
    private lateinit var privacyOptionsButton: View
    private var bannerAdView: AdView? = null
    private var cancelAdsReadyRegistration: (() -> Unit)? = null

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
        requestSettingsBannerWhenAdsAreReady()
    }

    override fun onResume() {
        super.onResume()
        refreshPrivacyOptionsVisibility()
        bannerAdView?.resume()
    }

    override fun onPause() {
        bannerAdView?.pause()
        super.onPause()
    }

    override fun onDestroy() {
        cancelAdsReadyRegistration?.invoke()
        cancelAdsReadyRegistration = null
        bannerAdView?.destroy()
        bannerAdView = null
        super.onDestroy()
    }

    private fun refreshPrivacyOptionsVisibility() {
        if (::privacyOptionsButton.isInitialized) {
            privacyOptionsButton.visibility = if (adsConsentManager.isPrivacyOptionsRequired) View.VISIBLE else View.GONE
        }
    }

    private fun requestSettingsBannerWhenAdsAreReady() {
        cancelAdsReadyRegistration = adsConsentManager.whenAdsReady {
            cancelAdsReadyRegistration = null
            val container = findViewById<FrameLayout>(R.id.settings_banner_container)
            container.post {
                if (isFinishing || isDestroyed || bannerAdView != null) return@post
                val density = resources.displayMetrics.density
                val widthPixels = container.width.takeIf { it > 0 } ?: resources.displayMetrics.widthPixels
                val widthDp = (widthPixels / density).toInt().coerceAtLeast(1)
                val adSize = AdSize.getLargeAnchoredAdaptiveBannerAdSize(this, widthDp)
                val adView = AdView(this).apply {
                    adUnitId = getString(R.string.ad_unit_banner)
                    setAdSize(adSize)
                }
                bannerAdView = adView
                container.addView(
                    adView,
                    FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.CENTER)
                )
                if (lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)) adView.resume()
                adView.loadAd(AdRequest.Builder().build())
            }
        }
    }

}

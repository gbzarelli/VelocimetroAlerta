package br.com.helpdev.velocimetroalerta.ads

import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView

/** Loads a consent-gated banner and ties its lifetime to the host screen. */
class BannerAdController(
    private val activity: AppCompatActivity,
    private val container: FrameLayout,
    private val adUnitId: String,
    private val adsConsentManager: AdsConsentManager
) {
    private var adView: AdView? = null
    private var cancelAdsReadyRegistration: (() -> Unit)? = null
    private var requestStarted = false

    fun requestWhenAdsAreReady() {
        if (requestStarted) return
        requestStarted = true
        cancelAdsReadyRegistration = adsConsentManager.whenAdsReady {
            cancelAdsReadyRegistration = null
            if (activity.isFinishing || activity.isDestroyed) return@whenAdsReady
            container.post {
                if (activity.isFinishing || activity.isDestroyed || adView != null) return@post
                val banner = AdView(activity).apply {
                    adUnitId = this@BannerAdController.adUnitId
                    setAdSize(AdSize.BANNER)
                }
                adView = banner
                container.addView(
                    banner,
                    FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT)
                )
                if (activity.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) banner.resume()
                banner.loadAd(AdRequest.Builder().build())
            }
        }
    }

    fun onResume() {
        adView?.resume()
    }

    fun onPause() {
        adView?.pause()
    }

    fun onDestroy() {
        cancelAdsReadyRegistration?.invoke()
        cancelAdsReadyRegistration = null
        adView?.let { banner ->
            container.removeView(banner)
            banner.destroy()
        }
        adView = null
    }
}

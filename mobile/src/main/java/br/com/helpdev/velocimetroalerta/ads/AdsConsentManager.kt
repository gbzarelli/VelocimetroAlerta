package br.com.helpdev.velocimetroalerta.ads

import android.app.Activity
import android.content.Context
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.RequestConfiguration
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform

/** Updates privacy choices at launch and only exposes ads when UMP allows requests. */
class AdsConsentManager(context: Context) {
    private val applicationContext = context.applicationContext
    private val consentInformation = UserMessagingPlatform.getConsentInformation(applicationContext)
    private val adsReadyCallbacks = mutableSetOf<() -> Unit>()
    private var consentRequestStarted = false
    private var adsInitializationStarted = false
    private var adsInitialized = false

    val isPrivacyOptionsRequired: Boolean
        get() = consentInformation.privacyOptionsRequirementStatus ==
            ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED

    fun requestConsent(activity: Activity) {
        if (consentRequestStarted) return
        consentRequestStarted = true
        consentInformation.requestConsentInfoUpdate(
            activity,
            ConsentRequestParameters.Builder().build(),
            {
                initializeAdsIfAllowed()
                UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) {
                    initializeAdsIfAllowed()
                }
            },
            {
                initializeAdsIfAllowed()
            }
        )
        // UMP can reuse a valid decision from the previous session while refreshing it.
        initializeAdsIfAllowed()
    }

    fun whenAdsReady(callback: () -> Unit): () -> Unit {
        if (adsInitialized) {
            callback()
            return {}
        }
        adsReadyCallbacks += callback
        return { adsReadyCallbacks -= callback }
    }

    fun showPrivacyOptions(activity: Activity) {
        if (!isPrivacyOptionsRequired) return
        UserMessagingPlatform.showPrivacyOptionsForm(activity) {
            initializeAdsIfAllowed()
        }
    }

    private fun initializeAdsIfAllowed() {
        if (!consentInformation.canRequestAds() || adsInitializationStarted) return
        adsInitializationStarted = true
        MobileAds.setRequestConfiguration(
            RequestConfiguration.Builder()
                .setMaxAdContentRating(RequestConfiguration.MAX_AD_CONTENT_RATING_T)
                .build()
        )
        MobileAds.initialize(applicationContext) {
            adsInitialized = true
            adsReadyCallbacks.toList().forEach { callback -> callback() }
            adsReadyCallbacks.clear()
        }
    }
}

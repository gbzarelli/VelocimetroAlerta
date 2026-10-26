package br.com.helpdev.velocimetroalerta

import android.app.Application
import br.com.helpdev.velocimetroalerta.ads.AdsConsentManager

class VelocimetroApplication : Application() {
    lateinit var adsConsentManager: AdsConsentManager
        private set

    override fun onCreate() {
        super.onCreate()
        adsConsentManager = AdsConsentManager(this)
    }
}

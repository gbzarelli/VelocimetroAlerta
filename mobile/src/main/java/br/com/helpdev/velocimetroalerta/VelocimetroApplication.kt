package br.com.helpdev.velocimetroalerta

import android.app.Application
import android.content.pm.ApplicationInfo
import android.util.Log
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory
import br.com.helpdev.velocimetroalerta.ads.AdsConsentManager
import br.com.helpdev.velocimetroalerta.settings.SettingsSyncRepository

class VelocimetroApplication : Application() {
    val settingsSyncRepository: SettingsSyncRepository by lazy { SettingsSyncRepository(this) }

    lateinit var adsConsentManager: AdsConsentManager
        private set

    override fun onCreate() {
        super.onCreate()
        FirebaseServices.app(this)?.let { firebaseApp ->
            val appCheck = FirebaseAppCheck.getInstance(firebaseApp)
            val isDebuggable = applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
            val debugFactory = if (isDebuggable) runCatching {
                Class.forName("com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory")
                    .getMethod("getInstance")
                    .invoke(null) as com.google.firebase.appcheck.AppCheckProviderFactory
            }.getOrNull() else null
            appCheck.installAppCheckProviderFactory(debugFactory ?: PlayIntegrityAppCheckProviderFactory.getInstance())
        }
        FirebaseServices.auth(this)?.currentUser?.uid?.let(settingsSyncRepository::start)
        adsConsentManager = AdsConsentManager(this)
    }
}

package br.com.helpdev.velocimetroalerta

import android.view.View
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AdMobConfigurationTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun debugBuildUsesGoogleSampleAdUnitsAndSettingsProvidesAdAndPrivacyEntryPoints() {
        assertEquals("ca-app-pub-3940256099942544~3347511713", context.getString(R.string.admob_app_id))
        assertEquals("ca-app-pub-3940256099942544/6300978111", context.getString(R.string.ad_unit_banner))
        assertEquals("ca-app-pub-3940256099942544/1033173712", context.getString(R.string.ad_unit_activities_interstitial))

        ActivityScenario.launch(ConfigActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                assertNotNull(activity.findViewById<View>(R.id.settings_banner_container))
                assertNotNull(activity.findViewById<View>(R.id.privacy_options_button))
            }
        }
    }
}

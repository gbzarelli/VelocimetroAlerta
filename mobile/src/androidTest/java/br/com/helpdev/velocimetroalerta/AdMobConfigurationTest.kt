package br.com.helpdev.velocimetroalerta

import android.view.View
import android.view.LayoutInflater
import androidx.appcompat.view.ContextThemeWrapper
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
    fun debugBuildUsesSampleBannerUnitsAndEveryAdScreenHasABottomContainer() {
        assertEquals("ca-app-pub-3940256099942544~3347511713", context.getString(R.string.admob_app_id))
        assertEquals("ca-app-pub-3940256099942544/6300978111", context.getString(R.string.ad_unit_banner))
        assertEquals("ca-app-pub-3940256099942544/6300978111", stringResource("ad_unit_activities_banner"))
        assertEquals("ca-app-pub-3940256099942544/6300978111", stringResource("ad_unit_activity_details_banner"))
        assertEquals(0, context.resources.getIdentifier("ad_unit_activities_interstitial", "string", context.packageName))

        val inflater = LayoutInflater.from(context)
        val activities = inflater.inflate(R.layout.content_my_activities, null)
        val details = LayoutInflater.from(ContextThemeWrapper(context, R.style.AppTheme_NoActionBar))
            .inflate(R.layout.activity_activity_detail, null)
        assertNotNull(findViewByResourceName(activities, "activities_banner_container"))
        assertNotNull(findViewByResourceName(details, "activity_detail_banner_container"))

        ActivityScenario.launch(ConfigActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                assertNotNull(activity.findViewById<View>(R.id.settings_banner_container))
                assertNotNull(activity.findViewById<View>(R.id.privacy_options_button))
            }
        }
    }

    private fun findViewByResourceName(root: View, name: String): View? {
        val id = context.resources.getIdentifier(name, "id", context.packageName)
        return if (id == 0) null else root.findViewById<View>(id)
    }

    private fun stringResource(name: String): String? {
        val id = context.resources.getIdentifier(name, "string", context.packageName)
        return if (id == 0) null else context.getString(id)
    }
}

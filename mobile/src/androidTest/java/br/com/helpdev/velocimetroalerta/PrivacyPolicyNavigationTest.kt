package br.com.helpdev.velocimetroalerta

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.view.View
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.Intents.intended
import androidx.test.espresso.intent.Intents.intending
import androidx.test.espresso.intent.matcher.IntentMatchers.hasAction
import androidx.test.espresso.intent.matcher.IntentMatchers.hasData
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import br.com.helpdev.velocimetroalerta.test.RuntimePermissionStateRule
import org.hamcrest.Matchers.allOf
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class PrivacyPolicyNavigationTest {
    @get:Rule
    val permissionState = RuntimePermissionStateRule()

    @Test
    fun navigationDrawerOpensThePrivacyPolicyUrl() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val policyUrl = context.getString(R.string.privacy_policy_url)
        val expectedIntent = allOf(
            hasAction(Intent.ACTION_VIEW),
            hasData(policyUrl)
        )
        Intents.init()

        try {
            intending(expectedIntent).respondWith(
                Instrumentation.ActivityResult(Activity.RESULT_OK, null)
            )
            var privacyMenuItemId = 0
            val drawerOpened = CountDownLatch(1)
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                scenario.onActivity { activity ->
                    privacyMenuItemId = activity.resources.getIdentifier(
                        "nav_privacy_policy",
                        "id",
                        activity.packageName
                    )
                    assertNotEquals("The drawer must contain a privacy policy item", 0, privacyMenuItemId)
                    val drawer = activity.findViewById<DrawerLayout>(R.id.drawer_layout)
                    val listener = object : DrawerLayout.SimpleDrawerListener() {
                        override fun onDrawerOpened(drawerView: View) {
                            drawer.removeDrawerListener(this)
                            drawerOpened.countDown()
                        }
                    }
                    drawer.addDrawerListener(listener)
                    drawer.openDrawer(GravityCompat.START)
                }

                assertTrue("The navigation drawer should finish opening", drawerOpened.await(5, TimeUnit.SECONDS))

                onView(withId(privacyMenuItemId)).perform(click())
            }

            intended(expectedIntent)
        } finally {
            Intents.release()
        }
    }
}

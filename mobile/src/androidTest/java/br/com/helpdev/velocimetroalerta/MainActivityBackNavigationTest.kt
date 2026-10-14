package br.com.helpdev.velocimetroalerta

import android.content.Intent
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.Espresso.pressBack
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import br.com.helpdev.velocimetroalerta.gps.SpeedometerService
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainActivityBackNavigationTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @After
    fun stopTrackingService() {
        context.stopService(Intent(context, SpeedometerService::class.java))
    }

    @Test
    fun backClosesDrawerBeforeActivity() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.findViewById<DrawerLayout>(R.id.drawer_layout).openDrawer(GravityCompat.START)
            }

            pressBack()

            scenario.onActivity { activity ->
                assertFalse(activity.findViewById<DrawerLayout>(R.id.drawer_layout).isDrawerOpen(GravityCompat.START))
                assertFalse(activity.isFinishing)
            }
        }
    }

    @Test
    fun backDuringRideRequiresConfirmation() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity -> activity.requestTrackingStart() }
            awaitTracking(scenario)

            pressBack()

            onView(withText(R.string.confirme_sair)).check(matches(isDisplayed()))
            onView(withText(R.string.bt_cancel)).perform(click())
            scenario.onActivity { activity ->
                assertFalse(activity.isFinishing)
                assertTrue(activity.speedometerService?.isRunning == true)
            }
        }
    }

    private fun awaitTracking(scenario: ActivityScenario<MainActivity>) {
        val deadline = System.currentTimeMillis() + 8_000
        var isRunning = false
        while (!isRunning && System.currentTimeMillis() < deadline) {
            scenario.onActivity { activity -> isRunning = activity.speedometerService?.isRunning == true }
            if (!isRunning) Thread.sleep(100)
        }
        assertTrue("The active-ride Back scenario must start a tracking service", isRunning)
    }
}

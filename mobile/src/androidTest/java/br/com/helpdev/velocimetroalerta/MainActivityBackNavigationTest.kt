package br.com.helpdev.velocimetroalerta

import android.Manifest
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
import br.com.helpdev.velocimetroalerta.test.RuntimePermissionState
import br.com.helpdev.velocimetroalerta.test.RuntimePermissionStateRule
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainActivityBackNavigationTest {
    @get:Rule
    val permissionState = RuntimePermissionStateRule()

    @Test
    fun backClosesDrawerBeforeActivity() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.findViewById<DrawerLayout>(R.id.drawer_layout).openDrawer(GravityCompat.START)
            }
            awaitDrawerOpen(scenario)

            pressBack()
            awaitDrawerClosed(scenario)

            scenario.onActivity { activity ->
                assertFalse(activity.isFinishing)
            }
        }
    }

    @RuntimePermissionState(
        granted = [
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.POST_NOTIFICATIONS
        ]
    )
    @Test
    fun backDuringRideRequiresConfirmation() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            onView(withId(R.id.btStartStop)).perform(click())
            onView(withText(R.string.location_background_disclosure_message)).check(matches(isDisplayed()))
            onView(withText(R.string.location_background_disclosure_continue)).perform(click())
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

    private fun awaitDrawerOpen(scenario: ActivityScenario<MainActivity>) {
        val deadline = System.currentTimeMillis() + 5_000
        var isOpen = false
        while (!isOpen && System.currentTimeMillis() < deadline) {
            scenario.onActivity { activity ->
                isOpen = activity.findViewById<DrawerLayout>(R.id.drawer_layout)
                    .isDrawerOpen(GravityCompat.START)
            }
            if (!isOpen) Thread.sleep(50)
        }
        assertTrue("The navigation drawer should finish opening before Back is pressed", isOpen)
    }

    private fun awaitDrawerClosed(scenario: ActivityScenario<MainActivity>) {
        val deadline = System.currentTimeMillis() + 5_000
        var isClosed = false
        while (!isClosed && System.currentTimeMillis() < deadline) {
            scenario.onActivity { activity ->
                isClosed = !activity.findViewById<DrawerLayout>(R.id.drawer_layout)
                    .isDrawerVisible(GravityCompat.START)
            }
            if (!isClosed) Thread.sleep(50)
        }
        assertTrue("Back should finish closing the navigation drawer", isClosed)
    }
}

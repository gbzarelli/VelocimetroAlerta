package br.com.helpdev.velocimetroalerta

import android.Manifest
import androidx.preference.PreferenceManager
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.platform.app.InstrumentationRegistry
import br.com.helpdev.velocimetroalerta.test.RuntimePermissionState
import br.com.helpdev.velocimetroalerta.test.RuntimePermissionStateRule
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4

@RunWith(AndroidJUnit4::class)
class PausedRideRecreationTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val preferences by lazy { PreferenceManager.getDefaultSharedPreferences(context) }
    private val locationDisclosureKey = "location_background_disclosure_accepted"

    @get:Rule
    val permissionState = RuntimePermissionStateRule()

    @Before
    fun acceptDisclosureForTrackingScenario() {
        preferences.edit().putBoolean(locationDisclosureKey, true).commit()
    }

    @RuntimePermissionState(
        granted = [
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.POST_NOTIFICATIONS
        ]
    )
    @Test
    fun pausedRideControlsSurviveActivityRecreation() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            onView(withId(R.id.btStartStop)).perform(click())
            waitForTrackingState(scenario, expectedPaused = false)

            onView(withId(R.id.btStartStop)).perform(click())
            waitForTrackingState(scenario, expectedPaused = true)

            scenario.recreate()
            waitForTrackingState(scenario, expectedPaused = true)
            onView(withId(R.id.btSave)).check(matches(isDisplayed()))
            onView(withId(R.id.btRefresh)).check(matches(isDisplayed()))
        }
    }

    private fun waitForTrackingState(scenario: ActivityScenario<MainActivity>, expectedPaused: Boolean) {
        val deadline = System.currentTimeMillis() + 10_000
        var stateReached = false
        while (System.currentTimeMillis() < deadline) {
            scenario.onActivity { activity ->
                val service = activity.speedometerService
                stateReached = if (expectedPaused) service?.isRunning == true && service.isPause
                else service?.isRunning == true && !service.isPause
            }
            if (stateReached) return
            Thread.sleep(100)
        }
        assertTrue("Expected tracking paused=$expectedPaused", stateReached)
    }
}

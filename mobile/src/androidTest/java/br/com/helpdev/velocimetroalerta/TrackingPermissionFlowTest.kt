package br.com.helpdev.velocimetroalerta

import android.Manifest
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.PackageManager
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import br.com.helpdev.velocimetroalerta.gps.SpeedometerService
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.preference.PreferenceManager

@RunWith(AndroidJUnit4::class)
class TrackingPermissionFlowTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val device = UiDevice.getInstance(instrumentation)

    @Before
    fun stopPreviousService() {
        context.stopService(Intent(context, SpeedometerService::class.java))
        PreferenceManager.getDefaultSharedPreferences(context)
            .edit()
            .remove("location_background_disclosure_accepted")
            .commit()
    }

    @After
    fun stopService() {
        context.stopService(Intent(context, SpeedometerService::class.java))
    }

    @Test
    fun locationDeniedDoesNotStartTracking() {
        assertTrue("Prepare the emulator with coarse location denied", context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED)
        assertTrue("Prepare the emulator with precise location denied", context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED)
        assertTrue("Prepare the emulator with notifications enabled", context.getSystemService(NotificationManager::class.java)!!.areNotificationsEnabled())
        ActivityScenario.launch(MainActivity::class.java).use {
            onView(withId(R.id.btStartStop)).perform(click())
            confirmLocationDisclosure()
            assertNotNull("The location permission prompt should be shown only after Start", findPermissionButton("permission_deny_button"))
            clickPermissionButton("permission_deny_button")
            waitForTrackingState(it, expected = false)
        }
    }

    @Test
    fun locationDisclosureCanBeCancelledBeforePermissionPrompt() {
        assertTrue("Prepare the emulator with coarse location denied", context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED)
        assertTrue("Prepare the emulator with precise location denied", context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED)
        val disclosure = context.getString(R.string.location_background_disclosure_message).lowercase()
        val language = context.resources.configuration.locales[0].language
        val locationTerm = if (language == "pt") "localização" else "location"
        val backgroundTerm = if (language == "pt") "segundo plano" else "background"
        assertTrue("The disclosure must identify location use", disclosure.contains(locationTerm))
        assertTrue("The disclosure must identify background location use", disclosure.contains(backgroundTerm))
        assertTrue("The disclosure must list speed tracking", disclosure.contains("velocidade") || disclosure.contains("speed"))
        assertTrue("The disclosure must list route recording", disclosure.contains("percurso") || disclosure.contains("route"))
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            onView(withId(R.id.btStartStop)).perform(click())
            onView(withText(R.string.location_background_disclosure_message)).check(matches(isDisplayed()))
            onView(withText(R.string.location_background_disclosure_cancel)).perform(click())

            assertNull("Cancel must not open the Android permission prompt", findPermissionButton("permission_deny_button"))
            assertTrue("Cancel must leave coarse location denied", context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED)
            assertTrue("Cancel must leave precise location denied", context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED)
            scenario.onActivity { activity ->
                assertFalse("Cancel must not start tracking", activity.speedometerService?.isTracking == true)
            }
        }
    }

    @Test
    fun preciseGrantStartsLocationForegroundService() {
        assertTrue("Prepare the emulator with coarse location granted", context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED)
        assertTrue("Prepare the emulator with precise location granted", context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED)
        assertTrue("Prepare the emulator with notifications enabled", context.getSystemService(NotificationManager::class.java)!!.areNotificationsEnabled())
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            onView(withId(R.id.btStartStop)).perform(click())
            confirmLocationDisclosure()
            waitForTrackingState(scenario, expected = true)
            waitForLocationFix(scenario)
            scenario.onActivity { activity ->
                assertTrue("The location service must be foreground before GPS updates start", activity.speedometerService?.isForegroundTracking == true)
            }
            scenario.onActivity { it.speedometerService?.stop() }
            waitForTrackingState(scenario, expected = false)
            scenario.onActivity { activity ->
                assertFalse("Stopping tracking must remove foreground state", activity.speedometerService?.isForegroundTracking == true)
                assertFalse("Stopping tracking must clear the last GPS fix", activity.speedometerService?.hasLocationFix == true)
            }
        }
    }

    @Test
    fun approximateOnlyDoesNotStartPrecisionTracking() {
        assertTrue("Prepare the emulator with coarse location granted", context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED)
        assertTrue("Prepare the emulator with precise location denied", context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED)
        assertTrue(context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED)
        assertTrue(context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED)

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            onView(withId(R.id.btStartStop)).perform(click())
            confirmLocationDisclosure()
            assertNotNull("The app should request precise location before tracking", findPermissionButton("permission_deny_button"))
            clickPermissionButton("permission_deny_button")
            waitForTrackingState(scenario, expected = false)
        }
    }

    @Test
    fun notificationDeniedDoesNotCrashTracking() {
        assertTrue("Prepare the emulator with coarse location granted", context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED)
        assertTrue("Prepare the emulator with precise location granted", context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED)
        assertFalse("Prepare the emulator with notifications denied", context.getSystemService(NotificationManager::class.java)!!.areNotificationsEnabled())
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            onView(withId(R.id.btStartStop)).perform(click())
            confirmLocationDisclosure()
            clickPermissionButton("permission_deny_button")
            waitForTrackingState(scenario, expected = true)
            scenario.onActivity { activity ->
                assertTrue("Notification denial must not prevent the location foreground service", activity.speedometerService?.isForegroundTracking == true)
            }
            val manager = context.getSystemService(NotificationManager::class.java)!!
            assertFalse(manager.areNotificationsEnabled())
        }
    }

    private fun clickPermissionButton(resourceName: String) {
        val button = findPermissionButton(resourceName)
        assertNotNull("Permission dialog button '$resourceName' should appear", button)
        button!!.click()
        device.waitForIdle()
    }

    private fun confirmLocationDisclosure() {
        onView(withText(R.string.location_background_disclosure_message)).check(matches(isDisplayed()))
        onView(withText(R.string.location_background_disclosure_continue)).perform(click())
    }

    private fun findPermissionButton(resourceName: String) = device.wait(
            Until.findObject(By.res("com.android.permissioncontroller", resourceName)),
            5_000
        )

    private fun waitForTrackingState(scenario: ActivityScenario<MainActivity>, expected: Boolean) {
        val deadline = System.currentTimeMillis() + 8_000
        var actual: Boolean? = null
        while (System.currentTimeMillis() < deadline) {
            scenario.onActivity { actual = it.speedometerService?.isTracking ?: false }
            if (actual == expected) return
            Thread.sleep(100)
        }
        assertTrue("Expected tracking state $expected but was $actual", actual == expected)
    }

    private fun waitForLocationFix(scenario: ActivityScenario<MainActivity>) {
        val deadline = System.currentTimeMillis() + 15_000
        var hasLocationFix = false
        while (System.currentTimeMillis() < deadline) {
            scenario.onActivity { hasLocationFix = it.speedometerService?.hasLocationFix == true }
            if (hasLocationFix) return
            Thread.sleep(250)
        }
        assertTrue("The foreground location service should receive emulator GPS updates", hasLocationFix)
    }
}

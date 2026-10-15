package br.com.helpdev.velocimetroalerta

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import androidx.lifecycle.Lifecycle
import androidx.preference.PreferenceManager
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.platform.app.InstrumentationRegistry
import br.com.helpdev.velocimetroalerta.gps.SpeedometerService
import br.com.helpdev.velocimetroalerta.test.RuntimePermissionState
import br.com.helpdev.velocimetroalerta.test.RuntimePermissionStateRule
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress

@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 31)
class BluetoothPermissionRecoveryTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val preferences by lazy { PreferenceManager.getDefaultSharedPreferences(context) }
    private val heartRateEnabledKey by lazy { context.getString(R.string.pref_hr_sensor) }
    private val heartRateAddressKey by lazy { context.getString(R.string.pref_hr_sensor_address) }
    private val savedPreferences = mutableMapOf<String, Any?>()

    @get:Rule
    val permissionState = RuntimePermissionStateRule()

    @Before
    fun prepareDeniedBluetoothWithConfiguredSensor() {
        savePreference(heartRateEnabledKey)
        savePreference(heartRateAddressKey)
        preferences.edit()
            .putBoolean(heartRateEnabledKey, true)
            .putString(heartRateAddressKey, "not-a-bluetooth-address")
            .putBoolean("location_background_disclosure_accepted", true)
            .commit()
        preferences.edit().putBoolean(heartRateEnabledKey, false).commit()
        context.stopService(Intent(context, SpeedometerService::class.java))
    }

    @After
    fun restoreEnvironment() {
        context.stopService(Intent(context, SpeedometerService::class.java))
        val editor = preferences.edit()
        savedPreferences.forEach { (key, value) ->
            when (value) {
                is Boolean -> editor.putBoolean(key, value)
                is String -> editor.putString(key, value)
                null -> editor.remove(key)
            }
        }
        editor.commit()
    }

    @RuntimePermissionState(
        granted = [
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.POST_NOTIFICATIONS
        ],
        denied = [Manifest.permission.BLUETOOTH_CONNECT]
    )
    @Test
    fun grantingBluetoothInSettingsReconnectsConfiguredSensorDuringRide() {
        assumeTrue(
            "Prepare the emulator with Bluetooth connect denied before this instrumentation run",
            context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED
        )
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            onView(withId(R.id.btStartStop)).perform(click())
            waitForTracking(scenario)

            scenario.onActivity { activity ->
                PreferenceManager.getDefaultSharedPreferences(activity).edit()
                    .putBoolean(heartRateEnabledKey, true)
                    .commit()
            }
            onView(withId(R.id.search_sensor_hr))
                .check(matches(withText(context.getString(R.string.bluetooth_sensor_permission_denied))))

            scenario.moveToState(Lifecycle.State.CREATED)
            setPermission(Manifest.permission.BLUETOOTH_CONNECT, granted = true)
            scenario.moveToState(Lifecycle.State.RESUMED)

            val recoveredStatus = waitForSensorRecovery(scenario)
            onView(withId(R.id.search_sensor_hr)).check(matches(withText(recoveredStatus)))
            scenario.onActivity { activity ->
                assertTrue("Bluetooth recovery must not stop the GPS ride", activity.speedometerService?.isTracking == true)
            }
        }
    }

    private fun waitForTracking(scenario: ActivityScenario<MainActivity>) {
        val deadline = System.currentTimeMillis() + 8_000
        var tracking = false
        while (System.currentTimeMillis() < deadline) {
            scenario.onActivity { tracking = it.speedometerService?.isTracking == true }
            if (tracking) return
            Thread.sleep(100)
        }
        assertTrue("Bluetooth denial must leave location tracking active", tracking)
    }

    private fun waitForSensorRecovery(scenario: ActivityScenario<MainActivity>): String {
        val deadline = System.currentTimeMillis() + 10_000
        var recoveredStatus = ""
        val deniedStatus = context.getString(R.string.bluetooth_sensor_permission_denied)
        val retryStates = setOf(
            context.getString(R.string.bluetooth_sensor_disabled),
            context.getString(R.string.bluetooth_sensor_connecting),
            context.getString(R.string.bluetooth_sensor_connection_failed),
            context.getString(R.string.bluetooth_sensor_connected)
        )
        while (System.currentTimeMillis() < deadline) {
            scenario.onActivity { activity ->
                val state = activity.findViewById<android.widget.TextView>(R.id.search_sensor_hr).text.toString()
                if (state != deniedStatus && state in retryStates) recoveredStatus = state
            }
            if (recoveredStatus.isNotEmpty()) return recoveredStatus
            Thread.sleep(100)
        }
        assertTrue("Returning with Bluetooth access must retry the configured sensor", recoveredStatus.isNotEmpty())
        return recoveredStatus
    }

    private fun savePreference(key: String) {
        savedPreferences[key] = when (val value = preferences.all[key]) {
            is Boolean, is String -> value
            else -> null
        }
    }

    private fun setPermission(permission: String, granted: Boolean) {
        val wasGranted = context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
        if (granted && !wasGranted) instrumentation.uiAutomation.grantRuntimePermission(context.packageName, permission)
        if (!granted && wasGranted) instrumentation.uiAutomation.revokeRuntimePermission(context.packageName, permission)
    }
}

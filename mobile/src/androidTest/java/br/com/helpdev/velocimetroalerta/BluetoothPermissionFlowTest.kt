package br.com.helpdev.velocimetroalerta

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import androidx.preference.PreferenceManager
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import androidx.test.filters.SdkSuppress
import br.com.helpdev.velocimetroalerta.gps.SpeedometerService
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4

@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 31)
class BluetoothPermissionFlowTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val device = UiDevice.getInstance(instrumentation)

    @Test
    fun deniedBluetoothPermissionLeavesGpsAvailable() {
        val preferences = PreferenceManager.getDefaultSharedPreferences(context)
        val sensorPreference = context.getString(R.string.pref_hr_sensor)
        val previousSensorEnabled = preferences.getBoolean(sensorPreference, false)
        val locationGranted = context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED &&
            context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val bluetoothDenied = context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED
        val notificationsGranted = android.os.Build.VERSION.SDK_INT < 33 ||
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        assertTrue("Prepare the emulator with precise location granted", locationGranted)
        assertTrue("Prepare the emulator with Bluetooth connect denied", bluetoothDenied)
        assertTrue("Prepare the emulator with notifications granted", notificationsGranted)
        PreferenceManager.getDefaultSharedPreferences(context)
            .edit()
            .remove("location_background_disclosure_accepted")
            .commit()

        preferences.edit().putBoolean(sensorPreference, true).apply()
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                onView(androidx.test.espresso.matcher.ViewMatchers.withId(R.id.btStartStop)).perform(click())
                onView(withText(R.string.location_background_disclosure_message)).check(matches(isDisplayed()))
                onView(withText(R.string.location_background_disclosure_continue)).perform(click())

                val denyButton = device.wait(
                    Until.findObject(By.res("com.android.permissioncontroller", "permission_deny_button")),
                    2_000
                )
                if (denyButton != null) {
                    denyButton.click()
                    device.waitForIdle()
                } else {
                    assertTrue(
                        "Bluetooth connect should remain denied when Android remembers an earlier denial",
                        context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED
                    )
                }

                val deadline = System.currentTimeMillis() + 8_000
                var tracking = false
                while (System.currentTimeMillis() < deadline) {
                    scenario.onActivity { tracking = it.speedometerService?.isTracking == true }
                    if (tracking) break
                    Thread.sleep(100)
                }
                assertTrue("Bluetooth denial must leave GPS tracking active", tracking)
                onView(androidx.test.espresso.matcher.ViewMatchers.withId(R.id.search_sensor_hr))
                    .check(matches(withText(context.getString(R.string.bluetooth_sensor_permission_denied))))
            }
        } finally {
            preferences.edit().putBoolean(sensorPreference, previousSensorEnabled).apply()
            context.stopService(Intent(context, SpeedometerService::class.java))
        }
    }
}

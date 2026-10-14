package br.com.helpdev.velocimetroalerta

import android.Manifest
import android.content.pm.PackageManager
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainActivitySmokeTest {
    @Test
    fun mainScreenShowsSpeedAndStartControls() {
        ActivityScenario.launch(MainActivity::class.java).use {
            onView(withId(R.id.velocidadeAtual)).check(matches(isDisplayed()))
            onView(withId(R.id.btStartStop)).check(matches(isDisplayed()))
            it.onActivity { activity -> assertFalse(activity.speedometerService?.isTracking == true) }
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            if (context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
                assertNull(
                    "Location permission must not be requested at launch",
                    device.wait(Until.findObject(By.res("com.android.permissioncontroller", "permission_deny_button")), 1_000)
                )
            }
        }
    }
}

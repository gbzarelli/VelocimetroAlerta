package br.com.helpdev.velocimetroalerta

import android.Manifest
import android.content.pm.PackageManager
import androidx.test.core.app.ActivityScenario
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import br.com.helpdev.velocimetroalerta.test.RuntimePermissionStateRule

@RunWith(AndroidJUnit4::class)
class MainActivitySmokeTest {
    @get:Rule
    val permissionState = RuntimePermissionStateRule()

    @Test
    fun mainScreenShowsSpeedAndStartControls() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            onView(withId(R.id.velocidadeAtual)).check(matches(isDisplayed()))
            onView(withId(R.id.btStartStop)).check(matches(isDisplayed()))
            scenario.onActivity { activity ->
                assertFalse(activity.speedometerService?.isTracking == true)

                val content = activity.findViewById<android.view.View>(R.id.app_bar_main)
                val insets = ViewCompat.getRootWindowInsets(content)
                assertNotNull("The main layout should receive system-bar insets", insets)
                val safeInsets = insets!!.getInsets(
                    WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
                )

                val toolbar = activity.findViewById<android.view.View>(R.id.toolbar)
                val toolbarPosition = IntArray(2)
                toolbar.getLocationOnScreen(toolbarPosition)
                assertTrue("The toolbar must remain below the status bar", toolbarPosition[1] >= safeInsets.top)

                val startButton = activity.findViewById<android.view.View>(R.id.btStartStop)
                val buttonPosition = IntArray(2)
                startButton.getLocationOnScreen(buttonPosition)
                val buttonBottom = buttonPosition[1] + startButton.height
                val windowHeight = activity.window.decorView.height
                assertTrue(
                    "The start/stop control must remain above the navigation bar",
                    buttonBottom <= windowHeight - safeInsets.bottom
                )
            }
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            if (context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
                assertNull(
                    "A launch prompt marked handled by the test fixture must not reappear",
                    device.wait(Until.findObject(By.res("com.android.permissioncontroller", "permission_deny_button")), 1_000)
                )
            }
        }
    }
}

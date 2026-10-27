package br.com.helpdev.velocimetroalerta

import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import android.view.View
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AccountConnectionsSmokeTest {
    @Test
    fun accountConnectionsScreenShowsGoogleSignIn() {
        ActivityScenario.launch(AccountConnectionsActivity::class.java).use {
            onView(withId(R.id.account_google_button)).check(matches(isDisplayed()))
            onView(withId(R.id.account_strava_button)).check(matches(isDisplayed()))
            it.onActivity { activity ->
                val toolbar = activity.findViewById<View>(R.id.toolbar)
                val insets = ViewCompat.getRootWindowInsets(toolbar)
                assertNotNull("The account screen should receive system bar insets", insets)
                val statusBarHeight = insets!!.getInsets(
                    WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
                ).top
                val toolbarPosition = IntArray(2)
                toolbar.getLocationOnScreen(toolbarPosition)
                assertTrue(
                    "The account screen toolbar must remain below the status bar",
                    toolbarPosition[1] >= statusBarHeight
                )
            }
        }
    }
}

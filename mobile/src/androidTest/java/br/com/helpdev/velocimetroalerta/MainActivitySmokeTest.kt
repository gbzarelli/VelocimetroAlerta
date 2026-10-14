package br.com.helpdev.velocimetroalerta

import android.Manifest
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.rule.GrantPermissionRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainActivitySmokeTest {
    @get:Rule
    val permissions: GrantPermissionRule = GrantPermissionRule.grant(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.WRITE_EXTERNAL_STORAGE
    )

    @Test
    fun mainScreenShowsSpeedAndStartControls() {
        ActivityScenario.launch(MainActivity::class.java).use {
            onView(withId(R.id.velocidadeAtual)).check(matches(isDisplayed()))
            onView(withId(R.id.btStartStop)).check(matches(isDisplayed()))
        }
    }
}

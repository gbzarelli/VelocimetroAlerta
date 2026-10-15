package br.com.helpdev.velocimetroalerta

import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ConfigScreensSmokeTest {
    @Test
    fun settingsAndHeartRateScreensRender() {
        val targetPackage = InstrumentationRegistry.getInstrumentation().targetContext.packageName
        assertEquals("The successor app must install under its new package", "br.com.helpdev.velocimetroalerta.v2", targetPackage)

        ActivityScenario.launch(ConfigActivity::class.java).use {
            onView(withId(R.id.toolbar)).check(matches(isDisplayed()))
            onView(withText(R.string.edit_heartrate_zones))
                .check(matches(isDisplayed()))
                .perform(click())
            onView(withId(R.id.toolbar)).check(matches(isDisplayed()))
            onView(withId(R.id.layout_max_hr)).check(matches(isDisplayed()))
        }
    }
}

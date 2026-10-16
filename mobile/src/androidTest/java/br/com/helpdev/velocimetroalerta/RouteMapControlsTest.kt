package br.com.helpdev.velocimetroalerta

import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RouteMapControlsTest {
    @Test
    fun mapTypeButtonSwitchesBetweenNormalAndSatellite() {
        ActivityScenario.launch(RouteMapActivity::class.java).use {
            onView(withId(R.id.route_map_type))
                .check(matches(isDisplayed()))
                .check(matches(withText(R.string.route_map_type_satellite)))
                .perform(click())
                .check(matches(withText(R.string.route_map_type_normal)))
                .perform(click())
                .check(matches(withText(R.string.route_map_type_satellite)))
        }
    }
}

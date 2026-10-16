package br.com.helpdev.velocimetroalerta

import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.closeSoftKeyboard
import androidx.test.espresso.action.ViewActions.replaceText
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ActivitySaveFlowTest {
    @Test
    fun suggestsActivityNameAndRequiresNonBlankReplacement() {
        ActivityScenario.launch<ActivitySaveActivity>(
            ActivitySaveActivity.newIntent(
                androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext,
                "Atividade 16 out, 18:30"
            )
        ).use {
            onView(withId(R.id.activity_name)).check(matches(androidx.test.espresso.matcher.ViewMatchers.withText("Atividade 16 out, 18:30")))
            onView(withId(R.id.activity_name)).perform(replaceText("   "), closeSoftKeyboard())
            onView(withId(R.id.complete_activity)).perform(click())
            onView(withId(R.id.activity_name_error)).check(matches(isDisplayed()))
        }
    }

    @Test
    fun acceptsEditedNameAndCanDiscard() {
        ActivityScenario.launch<ActivitySaveActivity>(
            ActivitySaveActivity.newIntent(
                androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext,
                "Atividade 16 out, 18:30"
            )
        ).use {
            onView(withId(R.id.activity_name)).perform(replaceText("Pedalada da tarde"), closeSoftKeyboard())
            onView(withId(R.id.complete_activity)).perform(click())
        }

        ActivityScenario.launch<ActivitySaveActivity>(
            ActivitySaveActivity.newIntent(
                androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext,
                "Atividade 16 out, 18:30"
            )
        ).use {
            onView(withId(R.id.discard_activity)).perform(click())
        }
    }
}

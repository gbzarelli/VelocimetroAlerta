package br.com.helpdev.velocimetroalerta

import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import org.hamcrest.Matchers.containsString
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import br.com.helpdev.velocimetroalerta.activities.ActivityDraft
import br.com.helpdev.velocimetroalerta.activities.ActivityDraftFactory
import br.com.helpdev.velocimetroalerta.activities.ActivityRepository
import br.com.helpdev.velocimetroalerta.gps.ObSpeedometerAlert
import br.com.helpdev.velocimetroalerta.gpx.objects.Gpx
import br.com.helpdev.velocimetroalerta.gpx.objects.TrkPt
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Date
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Locale

@RunWith(AndroidJUnit4::class)
class MyActivitiesTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val activitiesDirectory get() = java.io.File(context.filesDir, "activities")

    @Before
    fun clearActivities() {
        activitiesDirectory.deleteRecursively()
    }

    @Test
    fun emptyRepositoryShowsHelpfulEmptyState() {
        ActivityScenario.launch(MyActivities::class.java).use {
            awaitDisplayed(R.id.activity_empty_state)
            onView(withText(R.string.activity_feed_empty_title)).check(matches(isDisplayed()))
            onView(withId(R.id.activity_list)).check(matches(androidx.test.espresso.matcher.ViewMatchers.withEffectiveVisibility(androidx.test.espresso.matcher.ViewMatchers.Visibility.GONE)))
        }
    }

    @Test
    fun showsSavedCardAndOpensItsDetail() {
        val repository = ActivityRepository(context.filesDir)
        repository.save(draft("first", "Pedalada da tarde", 1_800_000_000_000L), "Pedalada da tarde")
        repository.save(draft("second", "Pedalada anterior", 1_797_000_000_000L), "Pedalada anterior")

        ActivityScenario.launch(MyActivities::class.java).use {
            awaitDisplayed(R.id.activity_list)
            onView(withText("Pedalada da tarde")).check(matches(isDisplayed()))
            val number = NumberFormat.getNumberInstance(Locale.getDefault()).apply {
                minimumFractionDigits = 1
                maximumFractionDigits = 1
            }
            val expectedMetrics = "${number.format(24.6)} km · 1h18 · ${number.format(18.9)} km/h"
            onView(withText(containsString(expectedMetrics))).check(matches(isDisplayed()))
            val expectedMonth = SimpleDateFormat("MMMM yyyy", Locale.getDefault())
                .format(Date(1_800_000_000_000L)).replaceFirstChar { it.titlecase(Locale.getDefault()) }
            onView(withText(expectedMonth)).check(matches(isDisplayed()))
            onView(withText("Pedalada da tarde")).perform(click())
            awaitDisplayed(R.id.activity_detail_content)
            onView(withId(R.id.activity_name)).check(matches(withText("Pedalada da tarde")))
        }
    }

    private fun draft(id: String, name: String, startedAt: Long): ActivityDraft {
        val alert = ObSpeedometerAlert().apply {
            dateTimeStart = Date(startedAt)
            time = 78 * 60 * 1000L
            distance = if (id == "first") 24.6 else 2.0
            speedAvg = if (id == "first") 18.9 else 9.1
        }
        val gpx = Gpx("Velocimetro Alerta").apply {
            trk.name = name
            trk.trkseg.trkPts.add(point(-23.55, -46.63))
            trk.trkseg.trkPts.add(point(-23.56, -46.64))
        }
        return ActivityDraftFactory.from(gpx, alert, name, id)
    }

    private fun point(latitude: Double, longitude: Double) = TrkPt().apply {
        setLat(latitude)
        setLon(longitude)
    }

    private fun awaitDisplayed(id: Int) {
        val deadline = System.currentTimeMillis() + 10_000
        var displayed = false
        while (!displayed && System.currentTimeMillis() < deadline) {
            try {
                onView(withId(id)).check(matches(isDisplayed()))
                displayed = true
            } catch (_: AssertionError) {
                Thread.sleep(100)
            }
        }
        assertTrue("Expected view $id to become visible", displayed)
    }
}

package br.com.helpdev.velocimetroalerta

import android.app.Activity
import android.app.ActivityManager
import android.app.Instrumentation
import android.content.Intent
import android.net.Uri
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.scrollTo
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.assertion.ViewAssertions.doesNotExist
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.Intents.intending
import androidx.test.espresso.intent.matcher.IntentMatchers.hasAction
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import br.com.helpdev.velocimetroalerta.activities.ActivityDraftFactory
import br.com.helpdev.velocimetroalerta.activities.ActivityRepository
import br.com.helpdev.velocimetroalerta.gps.ObSpeedometerAlert
import br.com.helpdev.velocimetroalerta.gpx.objects.Extensions
import br.com.helpdev.velocimetroalerta.gpx.objects.Gpx
import br.com.helpdev.velocimetroalerta.gpx.objects.TrackPointExtension
import br.com.helpdev.velocimetroalerta.gpx.objects.TrkPt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertArrayEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.Date

@RunWith(AndroidJUnit4::class)
class ActivityDetailTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val repository get() = ActivityRepository(context.filesDir)
    private val activitiesDirectory get() = File(context.filesDir, "activities")

    @Before
    fun clearActivities() {
        activitiesDirectory.deleteRecursively()
    }

    @Test
    fun showsRecordedMetricsAndHidesUnavailableSensors() {
        val summary = repository.save(draft("with-sensors", sensors = true), "Sensor ride")

        ActivityScenario.launch<ActivityDetailActivity>(ActivityDetailActivity.newIntent(context, summary.id)).use {
            awaitDisplayed(R.id.activity_detail_content)
            onView(withId(R.id.activity_name)).check(matches(withText("Sensor ride")))
            onView(withText("82 rpm")).perform(scrollTo()).check(matches(isDisplayed()))
            onView(withText("142 bpm")).perform(scrollTo()).check(matches(isDisplayed()))
            onView(withText("26 °C")).perform(scrollTo()).check(matches(isDisplayed()))
            onView(withText("61%")).perform(scrollTo()).check(matches(isDisplayed()))
            onView(withId(R.id.activity_export_gpx)).check(matches(isDisplayed()))
        }

        val noSensors = repository.save(draft("without-sensors", sensors = false), "No sensors")
        ActivityScenario.launch<ActivityDetailActivity>(ActivityDetailActivity.newIntent(context, noSensors.id)).use {
            awaitDisplayed(R.id.activity_detail_content)
            onView(withText("82 rpm")).check(doesNotExist())
            onView(withText("142 bpm")).check(doesNotExist())
        }
    }

    @Test
    fun routePreviewOpensStaticMapWithoutStartingTrackingService() {
        val summary = repository.save(draft("static-map", sensors = false), "Static route")
        val serviceWasRunning = isTrackingServiceRunning()

        ActivityScenario.launch<ActivityDetailActivity>(ActivityDetailActivity.newIntent(context, summary.id)).use {
            awaitDisplayed(R.id.activity_detail_content)
            onView(withId(R.id.activity_route_preview)).perform(click())
            awaitDisplayed(R.id.saved_route_map_root)
        }

        assertEquals("Viewing a saved route must not change live tracking", serviceWasRunning, isTrackingServiceRunning())
    }

    @Test
    fun exportCopiesCanonicalGpxByteForByte() {
        val summary = repository.save(draft("export-route", sensors = true), "Export route")
        val canonicalFile = requireNotNull(repository.gpxFile(summary.id))
        val canonicalBytes = canonicalFile.readBytes()
        val exportUri = Uri.parse("content://br.com.helpdev.velocimetroalerta.test.documents/export.gpx")
        Intents.init()
        try {
            intending(hasAction(Intent.ACTION_CREATE_DOCUMENT)).respondWith(
                Instrumentation.ActivityResult(Activity.RESULT_OK, Intent().setData(exportUri))
            )
            ActivityScenario.launch<ActivityDetailActivity>(ActivityDetailActivity.newIntent(context, summary.id)).use {
                awaitDisplayed(R.id.activity_detail_content)
                onView(withId(R.id.activity_export_gpx)).perform(click())
                assertArrayEquals(canonicalBytes, awaitExport(exportUri))
                onView(withText(R.string.activity_exported)).check(matches(isDisplayed()))
                onView(withText(R.string.bt_ok)).perform(click())
                assertArrayEquals(canonicalBytes, canonicalFile.readBytes())
            }
        } finally {
            Intents.release()
        }
    }

    @Test
    fun cancellingExportDoesNotChangeInternalGpx() {
        val summary = repository.save(draft("cancel-export", sensors = true), "Cancel export")
        val canonicalFile = requireNotNull(repository.gpxFile(summary.id))
        val canonicalBytes = canonicalFile.readBytes()
        Intents.init()
        try {
            intending(hasAction(Intent.ACTION_CREATE_DOCUMENT)).respondWith(
                Instrumentation.ActivityResult(Activity.RESULT_CANCELED, null)
            )
            ActivityScenario.launch<ActivityDetailActivity>(ActivityDetailActivity.newIntent(context, summary.id)).use {
                awaitDisplayed(R.id.activity_detail_content)
                onView(withId(R.id.activity_export_gpx)).perform(click())
                onView(withId(R.id.activity_detail_content)).check(matches(isDisplayed()))
                assertArrayEquals(canonicalBytes, canonicalFile.readBytes())
            }
        } finally {
            Intents.release()
        }
    }

    private fun draft(id: String, sensors: Boolean) = run {
        val alert = ObSpeedometerAlert().apply {
            dateTimeStart = Date(1_800_000_000_000L)
            time = 4_000_000
            timePaused = 500_000
            distance = 24.6
            distancePaused = 0.3
            speedAvg = 18.9
            speedMax = 42.3
            altitude = 760.0
            hasAltitude = true
            accuracyGPS = 4.5
            gainAlt = 210.0
            lostAlt = 180.0
            cadenceAvg = 82
            cadenceMax = 99
            bpmAvg = 142
            bpmMax = 175
            temperature = 26
            humidity = 61
        }
        val first = TrkPt().apply {
            setLat(-23.55)
            setLon(-46.63)
            if (sensors) extensions = Extensions(TrackPointExtension().apply {
                atemp = "26"
                rhu = "61"
                cad = "82"
                hr = "142"
            })
        }
        val second = TrkPt().apply { setLat(-23.56); setLon(-46.64) }
        val gpx = Gpx("Velocimetro Alerta").apply { trk.trkseg.trkPts.addAll(listOf(first, second)) }
        ActivityDraftFactory.from(gpx, alert, "Suggested", id)
    }

    private fun isTrackingServiceRunning(): Boolean =
        (context.getSystemService(android.content.Context.ACTIVITY_SERVICE) as ActivityManager)
            .getRunningServices(Int.MAX_VALUE)
            .any { it.service.className == "br.com.helpdev.velocimetroalerta.gps.SpeedometerService" }

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
        assertEquals("Expected view $id to become visible", true, displayed)
    }

    private fun awaitExport(uri: Uri): ByteArray {
        val deadline = System.currentTimeMillis() + 10_000
        var bytes: ByteArray? = null
        while (bytes == null && System.currentTimeMillis() < deadline) {
            bytes = runCatching {
                InstrumentationRegistry.getInstrumentation().context.contentResolver
                    .openInputStream(uri)?.use { it.readBytes() }
            }.getOrNull()?.takeIf { it.isNotEmpty() }
            if (bytes == null) Thread.sleep(100)
        }
        return requireNotNull(bytes) { "Expected exported GPX to be written" }
    }
}

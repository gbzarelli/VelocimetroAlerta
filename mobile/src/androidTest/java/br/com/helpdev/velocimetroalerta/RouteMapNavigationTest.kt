package br.com.helpdev.velocimetroalerta

import android.Manifest
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.preference.PreferenceManager
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.Espresso.pressBack
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.espresso.matcher.ViewMatchers.withEffectiveVisibility
import androidx.test.espresso.matcher.ViewMatchers.Visibility
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import br.com.helpdev.velocimetroalerta.gps.SpeedometerService
import br.com.helpdev.velocimetroalerta.test.RuntimePermissionState
import br.com.helpdev.velocimetroalerta.test.RuntimePermissionStateRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RouteMapNavigationTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val device by lazy { UiDevice.getInstance(instrumentation) }

    @get:Rule
    val permissionState = RuntimePermissionStateRule()

    @Before
    fun acceptLocationDisclosureForTrackingScenarios() {
        PreferenceManager.getDefaultSharedPreferences(context)
            .edit()
            .putBoolean("location_background_disclosure_accepted", true)
            .putBoolean(context.getString(R.string.pref_pause_automatico), false)
            .commit()
    }

    @Test
    fun mapButtonIsHiddenBeforeStarting() {
        ActivityScenario.launch(MainActivity::class.java).use {
            onView(withId(R.id.btMap)).check(matches(withEffectiveVisibility(Visibility.GONE)))
        }
    }

    @RuntimePermissionState(
        granted = [Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.POST_NOTIFICATIONS]
    )
    @Test
    fun mapButtonIsVisibleDuringRunningAndPausedRide() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            startRide()
            awaitRide(scenario, paused = false)
            onView(withId(R.id.btMap)).check(matches(isDisplayed()))

            onView(withId(R.id.btStartStop)).perform(click())
            awaitRide(scenario, paused = true)
            onView(withId(R.id.btMap)).check(matches(isDisplayed()))
        }
    }

    @RuntimePermissionState(
        granted = [Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.POST_NOTIFICATIONS]
    )
    @Test
    fun drawerConfigEntryRequiresPausedRide() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            startRide()
            awaitRide(scenario, paused = false)

            var expectedMessage: String? = null
            scenario.onActivity { activity ->
                expectedMessage = activity.getString(R.string.info_config_in_running)
                activity.findViewById<DrawerLayout>(R.id.drawer_layout).openDrawer(GravityCompat.START)
            }
            awaitDrawerOpen(scenario)
            onView(withId(R.id.nav_config)).perform(click())

            onView(withText(requireNotNull(expectedMessage))).check(matches(isDisplayed()))
            onView(withText(R.string.bt_ok)).perform(click())
            scenario.onActivity { activity ->
                assertTrue("The active ride must keep running after the drawer block", activity.speedometerService?.isRunning == true)
                assertTrue("The drawer block must not pause the active ride", activity.speedometerService?.isPause == false)
            }
        }
    }

    @RuntimePermissionState(
        granted = [Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.POST_NOTIFICATIONS]
    )
    @Test
    fun openingAndReturningPreservesRunningRide() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            startRide()
            awaitRide(scenario, paused = false)
            val before = captureRide(scenario)

            openMapAndReturn()

            val after = captureRide(scenario)
            assertRidePreserved(before, after, paused = false)

            pressBack()
            onView(withText(R.string.confirme_sair)).check(matches(isDisplayed()))
            onView(withText(R.string.bt_cancel)).perform(click())
            assertTrue("Main-screen Back must leave the active ride running", captureRide(scenario).service.isRunning)
        }
    }

    @RuntimePermissionState(
        granted = [Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.POST_NOTIFICATIONS]
    )
    @Test
    fun openingAndReturningPreservesPausedRide() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            startRide()
            awaitRide(scenario, paused = false)
            onView(withId(R.id.btStartStop)).perform(click())
            awaitRide(scenario, paused = true)
            val before = captureRide(scenario)

            openMapAndReturn()
            awaitRide(scenario, paused = true)
            onView(withId(R.id.btSave)).check(matches(isDisplayed()))
            onView(withId(R.id.btRefresh)).check(matches(isDisplayed()))

            val after = captureRide(scenario)
            assertRidePreserved(before, after, paused = true)
        }
    }

    private fun startRide() {
        onView(withId(R.id.btStartStop)).perform(click())
    }

    private fun awaitDrawerOpen(scenario: ActivityScenario<MainActivity>) {
        val deadline = System.currentTimeMillis() + 5_000
        var isOpen = false
        while (!isOpen && System.currentTimeMillis() < deadline) {
            scenario.onActivity { activity ->
                isOpen = activity.findViewById<DrawerLayout>(R.id.drawer_layout)
                    .isDrawerOpen(GravityCompat.START)
            }
            if (!isOpen) Thread.sleep(50)
        }
        assertTrue("The navigation drawer should finish opening before selecting Settings", isOpen)
    }

    private fun openMapAndReturn() {
        onView(withId(R.id.btMap)).check(matches(isDisplayed()))
        val mapMonitor = instrumentation.addMonitor(RouteMapActivity::class.java.name, null, false)
        val mapActivity = try {
            onView(withId(R.id.btMap)).perform(click())
            instrumentation.waitForMonitorWithTimeout(mapMonitor, 8_000)
        } finally {
            instrumentation.removeMonitor(mapMonitor)
        }
        assertNotNull("The map activity should open", mapActivity)
        assertTrue(
            "The map activity should render its root view",
            device.wait(Until.hasObject(By.res(context.packageName, "route_map_root")), 8_000)
        )
        assertEquals(context.getString(R.string.route_map_title), (mapActivity as RouteMapActivity).supportActionBar?.title)
        device.pressBack()
        assertTrue(
            "Back from the map should return to the speed screen",
            device.wait(Until.hasObject(By.res(context.packageName, "btStartStop")), 8_000)
        )
    }

    private fun awaitRide(scenario: ActivityScenario<MainActivity>, paused: Boolean) {
        val deadline = System.currentTimeMillis() + 10_000
        var previousBase: Long? = null
        var baseStableSince = 0L
        while (System.currentTimeMillis() < deadline) {
            var stateReached = false
            var base = 0L
            scenario.onActivity { activity ->
                val service = activity.speedometerService
                stateReached = service?.isRunning == true && service.isPause == paused
                base = activity.findViewById<android.widget.Chronometer>(R.id.chronometer).base
            }
            val now = System.currentTimeMillis()
            if (stateReached && previousBase == base) {
                if (baseStableSince == 0L) baseStableSince = now
                if (now - baseStableSince >= 1_200) return
            } else {
                baseStableSince = 0L
            }
            previousBase = base.takeIf { stateReached }
            Thread.sleep(100)
        }
        assertTrue("Expected a stable running ride with paused=$paused", false)
    }

    private fun captureRide(scenario: ActivityScenario<MainActivity>): RideState {
        var captured: RideState? = null
        scenario.onActivity { activity ->
            val service = requireNotNull(activity.speedometerService)
            captured = RideState(
                service = service,
                paused = service.isPause,
                chronometerBase = activity.findViewById<android.widget.Chronometer>(R.id.chronometer).base,
                acceptedPoints = service.gpx.trk.trkseg.trkPts.size
            )
        }
        return requireNotNull(captured)
    }

    private fun assertRidePreserved(
        before: RideState,
        after: RideState,
        paused: Boolean
    ) {
        assertSame("Map navigation must keep the same service instance", before.service, after.service)
        assertTrue("The ride must remain running", after.service.isRunning)
        assertEquals(paused, after.paused)
        if (!paused) {
            assertEquals("The running chronometer base must not be reset", before.chronometerBase, after.chronometerBase)
        }
        assertTrue("Accepted GPX points must not be lost", after.acceptedPoints >= before.acceptedPoints)
    }

    private data class RideState(
        val service: SpeedometerService,
        val paused: Boolean,
        val chronometerBase: Long,
        val acceptedPoints: Int
    )
}

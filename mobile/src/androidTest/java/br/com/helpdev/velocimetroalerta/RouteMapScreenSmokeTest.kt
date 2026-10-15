package br.com.helpdev.velocimetroalerta

import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import br.com.helpdev.velocimetroalerta.test.RuntimePermissionStateRule
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RouteMapScreenSmokeTest {
    @get:Rule
    val permissionState = RuntimePermissionStateRule()

    @Test
    fun mapOpensWithoutStartingTrackingAndBackFinishesOnlyMapActivity() {
        ActivityScenario.launch(RouteMapActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                assertEquals(android.view.View.VISIBLE, activity.findViewById<android.view.View>(R.id.toolbar).visibility)
                assertEquals(android.view.View.VISIBLE, activity.findViewById<android.view.View>(R.id.route_map_container).visibility)
                assertEquals(android.view.View.VISIBLE, activity.findViewById<android.view.View>(R.id.route_map_recenter).visibility)
                assertTrue(
                    "Opening the map must not start a tracking service: ${activity.speedometerServiceEntries().map { "started=${it.started}, clients=${it.clientCount}, pid=${it.pid}, process=${it.process}" }}",
                    activity.speedometerServiceIsNotOwned()
                )
                activity.onBackPressedDispatcher.onBackPressed()
                assertTrue("Back should finish only the map host", activity.isFinishing)
            }
        }
    }

    @Test
    fun recreatingMapDoesNotCreateOrStopTracking() {
        ActivityScenario.launch(RouteMapActivity::class.java).use { scenario ->
            scenario.recreate()
            scenario.onActivity { activity ->
                assertEquals(android.view.View.VISIBLE, activity.findViewById<android.view.View>(R.id.toolbar).visibility)
                assertTrue(
                    "Recreating the map must not start a tracking service: ${activity.speedometerServiceEntries().map { "started=${it.started}, clients=${it.clientCount}, pid=${it.pid}, process=${it.process}" }}",
                    activity.speedometerServiceIsNotOwned()
                )
            }
        }
    }

    private fun RouteMapActivity.speedometerServiceIsNotOwned(): Boolean =
        speedometerServiceEntries().none { it.started || it.pid != 0 }

    private fun RouteMapActivity.speedometerServiceEntries(): List<android.app.ActivityManager.RunningServiceInfo> =
        br.com.helpdev.velocimetroalerta.gps.SpeedometerService::class.java.let { serviceClass ->
            (getSystemService(android.content.Context.ACTIVITY_SERVICE) as android.app.ActivityManager)
                .getRunningServices(Int.MAX_VALUE)
                .filter { it.service.className == serviceClass.name }
        }
}

package br.com.helpdev.velocimetroalerta

import android.app.Activity
import android.content.Context
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.android.gms.tasks.Task
import com.google.android.play.core.review.ReviewInfo
import com.google.android.play.core.review.ReviewManager
import com.google.android.play.core.review.testing.FakeReviewManager
import com.google.android.gms.tasks.Tasks
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class InAppReviewLauncherTest {

    @Test
    fun launchesThePlayReviewFlowWhenAReviewInfoIsAvailable() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            lateinit var reviewManager: RecordingReviewManager
            scenario.onActivity { activity ->
                reviewManager = RecordingReviewManager(activity)

                InAppReviewLauncher(reviewManager) {}.request(activity)
            }

            assertTrue(
                "The review flow should be launched after the Play review info is requested",
                reviewManager.reviewFlowLaunched.await(5, TimeUnit.SECONDS)
            )
            assertEquals(1, reviewManager.launchRequests)
        }
    }

    @Test
    fun ratingMenuItemRequestsTheInAppReviewFlow() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            val requests = AtomicInteger()

            scenario.onActivity { activity ->
                activity.inAppReviewRequester = InAppReviewRequester { requests.incrementAndGet() }
                activity.onNavigationItemSelected(
                    activity.findViewById<com.google.android.material.navigation.NavigationView>(R.id.nav_view)
                        .menu
                        .findItem(R.id.nav_star)
                )
            }

            assertEquals(1, requests.get())
        }
    }

    @Test
    fun opensThePlayStoreListingWhenReviewRequestFails() {
        val fallbackRequests = AtomicInteger()
        val manager = FailedReviewManager()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                InAppReviewLauncher(manager) { fallbackRequests.incrementAndGet() }.request(activity)
            }

            assertTrue("The fallback should run after the failed review request", manager.requestFinished.await(5, TimeUnit.SECONDS))
            assertEquals(1, fallbackRequests.get())
        }
    }

    private class RecordingReviewManager(context: Context) : ReviewManager {
        private val delegate = FakeReviewManager(context)
        val reviewFlowLaunched = CountDownLatch(1)
        var launchRequests = 0
            private set

        override fun requestReviewFlow(): Task<ReviewInfo> = delegate.requestReviewFlow()

        override fun launchReviewFlow(activity: Activity, reviewInfo: ReviewInfo): Task<Void> {
            launchRequests += 1
            reviewFlowLaunched.countDown()
            return delegate.launchReviewFlow(activity, reviewInfo)
        }
    }

    private class FailedReviewManager : ReviewManager {
        val requestFinished = CountDownLatch(1)

        override fun requestReviewFlow(): Task<ReviewInfo> =
            Tasks.forException<ReviewInfo>(IllegalStateException("Play review unavailable")).also {
                it.addOnCompleteListener { requestFinished.countDown() }
            }

        override fun launchReviewFlow(activity: Activity, reviewInfo: ReviewInfo): Task<Void> =
            error("Review flow cannot launch without review info")
    }
}

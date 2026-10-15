package br.com.helpdev.velocimetroalerta.gps

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class ActiveRouteTest {
    @Test
    fun snapshotContainsPreexistingPointsInOrder() {
        val route = ActiveRoute()
        route.append(RoutePoint(-23.561414, -46.655881))
        route.append(RoutePoint(-23.562010, -46.656300))

        val snapshot = route.snapshot()

        assertEquals(
            listOf(
                RoutePoint(-23.561414, -46.655881),
                RoutePoint(-23.562010, -46.656300)
            ),
            snapshot.points
        )
    }

    @Test
    fun subscribeReturnsSnapshotAndReceivesOnlyLaterPoints() {
        val route = ActiveRoute()
        val first = RoutePoint(-23.561414, -46.655881)
        val second = RoutePoint(-23.562010, -46.656300)
        route.append(first)
        val updates = mutableListOf<Pair<Long, RoutePoint>>()
        val observer = RouteObserver { revision, point -> updates += revision to point }

        val snapshot = route.subscribe(observer)
        route.append(second)

        assertEquals(listOf(first), snapshot.points)
        assertEquals(listOf(2L to second), updates)
        assertEquals(listOf(first, second), route.snapshot().points)
    }

    @Test
    fun returnedSnapshotsCannotMutateTheRoute() {
        val route = ActiveRoute()
        val first = RoutePoint(-23.561414, -46.655881)
        val second = RoutePoint(-23.562010, -46.656300)
        route.append(first)
        route.append(second)

        val snapshot = route.snapshot()
        val mutationRejected = try {
            @Suppress("UNCHECKED_CAST")
            (snapshot.points as MutableList<RoutePoint>).clear()
            false
        } catch (_: UnsupportedOperationException) {
            true
        }

        assertTrue(mutationRejected)
        assertEquals(listOf(first, second), route.snapshot().points)
    }

    @Test
    fun notificationsIncreaseRevisionOncePerAppend() {
        val route = ActiveRoute()
        val revisions = mutableListOf<Long>()
        route.subscribe(RouteObserver { revision, _ -> revisions += revision })

        route.append(RoutePoint(-23.561414, -46.655881))
        route.append(RoutePoint(-23.562010, -46.656300))

        assertEquals(listOf(1L, 2L), revisions)
        assertEquals(2L, route.snapshot().revision)
    }

    @Test
    fun unsubscribeIsIdempotentAndStopsFutureNotifications() {
        val route = ActiveRoute()
        var notifications = 0
        val observer = RouteObserver { _, _ -> notifications++ }
        route.subscribe(observer)

        route.unsubscribe(observer)
        route.unsubscribe(observer)
        route.append(RoutePoint(-23.561414, -46.655881))

        assertEquals(0, notifications)
    }

    @Test
    fun resetClearsTheNextSnapshotWithoutReusingRevision() {
        val route = ActiveRoute()
        route.append(RoutePoint(-23.561414, -46.655881))

        route.reset()
        val snapshot = route.snapshot()

        assertTrue(snapshot.points.isEmpty())
        assertEquals(2L, snapshot.revision)
    }

    @Test
    fun resetNotifiesCurrentSubscribersBeforePublishingTheNextActivityPoint() {
        val route = ActiveRoute()
        val oldPoint = RoutePoint(-23.561414, -46.655881)
        val newPoint = RoutePoint(-23.562010, -46.656300)
        val events = mutableListOf<String>()
        val observer = object : RouteObserver {
            override fun onPointAdded(revision: Long, point: RoutePoint) {
                events += "point:$revision:$point"
            }

            override fun onRouteReset(revision: Long) {
                events += "reset:$revision"
            }
        }
        route.subscribe(observer)

        route.append(oldPoint)
        route.reset()
        route.append(newPoint)

        assertEquals(
            listOf("point:1:$oldPoint", "reset:2", "point:3:$newPoint"),
            events
        )
        assertEquals(listOf(newPoint), route.snapshot().points)
    }

    @Test
    fun rendererCanDiscardSnapshotWhenBackgroundAppendArrivesFirst() {
        val route = ActiveRoute()
        route.append(RoutePoint(-23.561414, -46.655881))
        val subscribed = CountDownLatch(1)
        val consumeSnapshot = CountDownLatch(1)
        val deliveredRevisions = mutableListOf<Long>()
        var initialSnapshot: RouteSnapshot? = null
        val observer = RouteObserver { revision, _ -> synchronized(deliveredRevisions) { deliveredRevisions += revision } }

        val subscriber = Thread {
            initialSnapshot = route.subscribe(observer)
            subscribed.countDown()
            consumeSnapshot.await(2, TimeUnit.SECONDS)
        }
        subscriber.start()
        assertTrue(subscribed.await(2, TimeUnit.SECONDS))

        route.append(RoutePoint(-23.562010, -46.656300))
        consumeSnapshot.countDown()
        subscriber.join(2_000)

        assertEquals(1L, initialSnapshot?.revision)
        assertEquals(listOf(2L), synchronized(deliveredRevisions) { deliveredRevisions.toList() })
        assertTrue("A renderer applying revision 1 after revision 2 must keep revision 2", 2L > initialSnapshot!!.revision)
    }
}

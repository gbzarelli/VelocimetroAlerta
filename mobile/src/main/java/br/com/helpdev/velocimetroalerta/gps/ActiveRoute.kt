package br.com.helpdev.velocimetroalerta.gps

import java.util.ArrayDeque
import java.util.Collections

/** Publishes the points recorded by the active service without coupling it to a map provider. */
class ActiveRoute {
    private val lock = Any()
    private val points = mutableListOf<RoutePoint>()
    private val observers = linkedSetOf<RouteObserver>()
    private val pendingNotifications = ArrayDeque<Notification>()
    private var revision = 0L
    private var dispatching = false

    fun snapshot(): RouteSnapshot = synchronized(lock) { snapshotLocked() }

    /** Atomically registers [observer] and returns the route state at registration time. */
    fun subscribe(observer: RouteObserver): RouteSnapshot = synchronized(lock) {
        observers += observer
        snapshotLocked()
    }

    fun unsubscribe(observer: RouteObserver) {
        synchronized(lock) { observers -= observer }
    }

    fun append(point: RoutePoint) {
        val shouldDrain = synchronized(lock) {
            points += point
            revision++
            pendingNotifications.addLast(Notification(revision, point, observers.toList()))
            if (dispatching) false else {
                dispatching = true
                true
            }
        }
        if (shouldDrain) drainNotifications()
    }

    /** Clears route geometry but keeps revisions monotonic for observers with queued updates. */
    fun reset() {
        val shouldDrain = synchronized(lock) {
            points.clear()
            revision++
            pendingNotifications.addLast(Notification(revision, null, observers.toList()))
            if (dispatching) false else {
                dispatching = true
                true
            }
        }
        if (shouldDrain) drainNotifications()
    }

    private fun snapshotLocked() = RouteSnapshot(
        revision = revision,
        points = Collections.unmodifiableList(ArrayList(points))
    )

    private fun drainNotifications() {
        while (true) {
            val notification = synchronized(lock) {
                if (pendingNotifications.isEmpty()) {
                    dispatching = false
                    return
                }
                pendingNotifications.removeFirst()
            }
            notification.observers.forEach { observer ->
                try {
                    val point = notification.point
                    if (point == null) observer.onRouteReset(notification.revision)
                    else observer.onPointAdded(notification.revision, point)
                } catch (_: Exception) {
                    // A map/UI consumer must never interrupt GPS recording or other consumers.
                }
            }
        }
    }

    private data class Notification(
        val revision: Long,
        val point: RoutePoint?,
        val observers: List<RouteObserver>
    )
}

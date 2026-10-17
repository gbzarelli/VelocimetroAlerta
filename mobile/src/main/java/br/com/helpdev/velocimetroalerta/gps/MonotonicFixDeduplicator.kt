package br.com.helpdev.velocimetroalerta.gps

/** Tracks each monotonic GPS fix once for route storage and real-time metrics. */
class MonotonicFixDeduplicator {
    private var lastFixElapsedRealtimeNanos = 0L

    @Synchronized
    fun shouldAccept(fixElapsedRealtimeNanos: Long): Boolean {
        if (fixElapsedRealtimeNanos <= lastFixElapsedRealtimeNanos) return false
        lastFixElapsedRealtimeNanos = fixElapsedRealtimeNanos
        return true
    }

    @Synchronized
    fun reset() {
        lastFixElapsedRealtimeNanos = 0L
    }
}

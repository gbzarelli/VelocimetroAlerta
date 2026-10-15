package br.com.helpdev.velocimetroalerta.gps

data class RoutePoint(val latitude: Double, val longitude: Double)

data class RouteSnapshot(val revision: Long, val points: List<RoutePoint>)

fun interface RouteObserver {
    fun onPointAdded(revision: Long, point: RoutePoint)
}

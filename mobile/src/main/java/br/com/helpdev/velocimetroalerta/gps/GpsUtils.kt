package br.com.helpdev.velocimetroalerta.gps

object GpsUtils {

    fun distanceCalculate(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        //double earthRadius = 3958.75;//miles
        val earthRadius = 6371.0//kilometers
        val dLat = Math.toRadians(lat2 - lat1)
        val dLng = Math.toRadians(lng2 - lng1)
        val sindLat = Math.sin(dLat / 2)
        val sindLng = Math.sin(dLng / 2)
        val a = Math.pow(sindLat, 2.0) + (Math.pow(sindLng, 2.0)
                * Math.cos(Math.toRadians(lat1))
                * Math.cos(Math.toRadians(lat2)))
        val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
        return earthRadius * c
    }
}

package br.com.helpdev.velocimetroalerta.gps

import android.location.Location
import android.os.Build

/** Converts an Android fix without conflating horizontal and vertical uncertainty. */
fun Location.toElevationSample(): ElevationSample {
    val altitude = altitude.takeIf { hasAltitude() && it.isFinite() }
    val verticalAccuracy = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && hasVerticalAccuracy()) {
        verticalAccuracyMeters.toDouble().takeIf { it.isFinite() && it > 0.0 }
    } else {
        null
    }
    return ElevationSample(
        altitudeMeters = altitude,
        verticalAccuracyMeters = verticalAccuracy,
        fixElapsedRealtimeNanos = elapsedRealtimeNanos
    )
}

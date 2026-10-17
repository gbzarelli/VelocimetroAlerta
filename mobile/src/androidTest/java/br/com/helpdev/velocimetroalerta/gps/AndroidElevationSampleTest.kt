package br.com.helpdev.velocimetroalerta.gps

import android.location.Location
import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AndroidElevationSampleTest {
    @Test
    fun mapsAltitudeAndMonotonicFixTime() {
        val location = Location("gps").apply {
            altitude = 742.5
            elapsedRealtimeNanos = 42_000_000_000L
        }

        val sample = location.toElevationSample()

        assertEquals(742.5, sample.altitudeMeters!!, 0.0)
        assertEquals(42_000_000_000L, sample.fixElapsedRealtimeNanos)
    }

    @Test
    fun missingAndNonFiniteAltitudeAreRepresentedAsMissing() {
        val withoutAltitude = Location("gps").apply { elapsedRealtimeNanos = 1_000_000_000L }
        val nonFiniteAltitude = Location("gps").apply {
            altitude = Double.NaN
            elapsedRealtimeNanos = 2_000_000_000L
        }

        assertNull(withoutAltitude.toElevationSample().altitudeMeters)
        assertNull(nonFiniteAltitude.toElevationSample().altitudeMeters)
    }

    @Test
    fun horizontalAccuracyIsNeverCopiedIntoVerticalAccuracy() {
        val location = Location("gps").apply {
            altitude = 742.5
            accuracy = 3.0f
            elapsedRealtimeNanos = 1_000_000_000L
        }

        assertNull(location.toElevationSample().verticalAccuracyMeters)
    }

    @Test
    fun api24DoesNotReadApi26VerticalAccuracyMethods() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) return
        val location = Location("gps").apply {
            altitude = 742.5
            elapsedRealtimeNanos = 1_000_000_000L
        }

        assertNull(location.toElevationSample().verticalAccuracyMeters)
        assertTrue(Build.VERSION.SDK_INT < Build.VERSION_CODES.O)
    }

    @Test
    fun mapsVerticalAccuracyOnlyWhenPlatformReportsIt() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val location = Location("gps").apply {
            altitude = 742.5
            verticalAccuracyMeters = 4.0f
            elapsedRealtimeNanos = 1_000_000_000L
        }

        assertEquals(4.0, location.toElevationSample().verticalAccuracyMeters!!, 0.0)
    }
}

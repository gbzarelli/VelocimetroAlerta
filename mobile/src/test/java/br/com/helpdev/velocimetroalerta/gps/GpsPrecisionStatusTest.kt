package br.com.helpdev.velocimetroalerta.gps

import org.junit.Assert.assertEquals
import org.junit.Test

class GpsPrecisionStatusTest {
    @Test
    fun missingOrInvalidAccuracyIsUnknown() {
        assertEquals(GpsPrecisionStatus.UNKNOWN, GpsPrecisionStatus.fromAccuracy(null))
        assertEquals(GpsPrecisionStatus.UNKNOWN, GpsPrecisionStatus.fromAccuracy(-1.0))
        assertEquals(GpsPrecisionStatus.UNKNOWN, GpsPrecisionStatus.fromAccuracy(Double.NaN))
        assertEquals(GpsPrecisionStatus.UNKNOWN, GpsPrecisionStatus.fromAccuracy(Double.POSITIVE_INFINITY))
    }

    @Test
    fun zeroAndThresholdAccuracyAreGood() {
        assertEquals(GpsPrecisionStatus.GOOD, GpsPrecisionStatus.fromAccuracy(0.0))
        assertEquals(GpsPrecisionStatus.GOOD, GpsPrecisionStatus.fromAccuracy(15.0))
    }

    @Test
    fun accuracyAboveThresholdIsPoor() {
        assertEquals(GpsPrecisionStatus.POOR, GpsPrecisionStatus.fromAccuracy(15.1))
    }
}

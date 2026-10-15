package br.com.helpdev.velocimetroalerta.sensors

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SensorActivationPolicyTest {
    @Test
    fun enabledSensorDoesNotActivateBeforeTrackingStarts() {
        assertFalse(SensorActivationPolicy.shouldActivate(false, true, true))
    }

    @Test
    fun enabledSensorActivatesDuringTrackingWhenPermissionIsGranted() {
        assertTrue(SensorActivationPolicy.shouldActivate(true, true, true))
    }

    @Test
    fun missingPreferenceOrPermissionKeepsSensorInactive() {
        assertFalse(SensorActivationPolicy.shouldActivate(true, false, true))
        assertFalse(SensorActivationPolicy.shouldActivate(true, true, false))
    }
}

package br.com.helpdev.velocimetroalerta.sensors

object SensorActivationPolicy {
    fun shouldActivate(isTracking: Boolean, isConfigured: Boolean, hasPermission: Boolean): Boolean =
        isTracking && isConfigured && hasPermission
}

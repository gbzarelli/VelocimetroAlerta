package br.com.helpdev.velocimetroalerta.sensors

enum class SensorKind {
    HEART_RATE,
    VAM
}

enum class SensorConnectionState {
    UNSUPPORTED,
    PERMISSION_DENIED,
    BLUETOOTH_DISABLED,
    CONNECTING,
    CONNECTED,
    RECONNECTING,
    CONNECTION_FAILED
}

fun interface SensorStatusListener {
    fun onSensorStatusChanged(sensor: SensorKind, state: SensorConnectionState)
}

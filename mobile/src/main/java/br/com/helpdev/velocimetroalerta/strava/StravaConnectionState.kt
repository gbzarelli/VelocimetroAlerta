package br.com.helpdev.velocimetroalerta.strava

data class StravaConnectionState(
    val connected: Boolean,
    val athleteName: String? = null,
    val reconnectRequired: Boolean = false
)

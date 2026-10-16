package br.com.helpdev.velocimetroalerta

/** Map layer selected by the user in the route map screen. */
internal enum class RouteMapType {
    NORMAL,
    SATELLITE;

    fun toggle(): RouteMapType = when (this) {
        NORMAL -> SATELLITE
        SATELLITE -> NORMAL
    }

    companion object {
        fun fromSavedValue(value: String?): RouteMapType =
            values().firstOrNull { it.name == value } ?: NORMAL
    }
}

package br.com.helpdev.velocimetroalerta.gps

/** Describes whether the current GPS accuracy is reliable enough for activity tracking. */
enum class GpsPrecisionStatus {
    UNKNOWN,
    GOOD,
    POOR;

    companion object {
        private const val MAX_ACCEPTABLE_ACCURACY_METERS = 15.0

        fun fromAccuracy(accuracyMeters: Double?): GpsPrecisionStatus {
            if (accuracyMeters == null || !accuracyMeters.isFinite() || accuracyMeters < 0.0) {
                return UNKNOWN
            }
            return if (accuracyMeters <= MAX_ACCEPTABLE_ACCURACY_METERS) GOOD else POOR
        }
    }
}

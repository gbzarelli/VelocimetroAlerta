package br.com.helpdev.velocimetroalerta.gps

enum class AutomaticPauseDecision { TRACK, HOLD_FOR_PAUSE, CONFIRM_PAUSE, KEEP_PAUSED, RESUME }

/** Monotonic-time state machine for the service's existing low-speed automatic pause policy. */
class AutomaticPauseController(
    private val speedThresholdKmh: Double = 1.0,
    private val maximumHorizontalAccuracyMeters: Double = 15.0,
    private val confirmationDelayMillis: Long = 3_000L
) {
    private var lowSpeedStartedAtElapsedRealtime: Long? = null

    init {
        require(speedThresholdKmh >= 0.0)
        require(maximumHorizontalAccuracyMeters > 0.0)
        require(confirmationDelayMillis > 0L)
    }

    @Synchronized
    fun update(
        nowElapsedRealtime: Long,
        speedKmh: Double,
        horizontalAccuracyMeters: Double,
        automaticallyPaused: Boolean
    ): AutomaticPauseDecision {
        val reliablePosition = horizontalAccuracyMeters.isFinite() &&
            horizontalAccuracyMeters > 0.0 && horizontalAccuracyMeters <= maximumHorizontalAccuracyMeters

        if (automaticallyPaused) {
            lowSpeedStartedAtElapsedRealtime = null
            return if (speedKmh.isFinite() && speedKmh > speedThresholdKmh && reliablePosition) {
                AutomaticPauseDecision.RESUME
            } else {
                AutomaticPauseDecision.KEEP_PAUSED
            }
        }

        val mayConfirmPause = horizontalAccuracyMeters < 0.0 || reliablePosition
        if (!speedKmh.isFinite() || speedKmh > speedThresholdKmh || !mayConfirmPause) {
            lowSpeedStartedAtElapsedRealtime = null
            return AutomaticPauseDecision.TRACK
        }

        val start = lowSpeedStartedAtElapsedRealtime
        if (start == null || nowElapsedRealtime < start) {
            lowSpeedStartedAtElapsedRealtime = nowElapsedRealtime
            return AutomaticPauseDecision.HOLD_FOR_PAUSE
        }
        if (nowElapsedRealtime - start >= confirmationDelayMillis) {
            lowSpeedStartedAtElapsedRealtime = null
            return AutomaticPauseDecision.CONFIRM_PAUSE
        }
        return AutomaticPauseDecision.HOLD_FOR_PAUSE
    }

    @Synchronized
    fun reset() {
        lowSpeedStartedAtElapsedRealtime = null
    }
}

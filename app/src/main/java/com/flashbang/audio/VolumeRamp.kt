package com.flashbang.audio

import com.flashbang.config.AlarmConfig
import kotlin.time.Duration

/**
 * Linear player-volume ramp (FR-008). Pure interpolation, unit-tested; the
 * engine drives it on a coroutine ticker. Never touches system stream volume.
 */
object VolumeRamp {

    fun volumeAt(
        elapsed: Duration,
        rampDuration: Duration = AlarmConfig.VOLUME_RAMP_DURATION,
        initialFraction: Float = AlarmConfig.INITIAL_VOLUME_FRACTION,
    ): Float {
        if (elapsed <= Duration.ZERO) return initialFraction
        if (elapsed >= rampDuration) return 1.0f
        val progress = (elapsed.inWholeMilliseconds.toFloat() / rampDuration.inWholeMilliseconds)
        return initialFraction + (1.0f - initialFraction) * progress
    }
}

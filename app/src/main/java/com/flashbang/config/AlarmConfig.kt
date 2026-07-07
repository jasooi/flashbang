package com.flashbang.config

import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Every alarm-behavior tunable lives here (FR-008 / RD-005): no magic numbers
 * at call sites.
 */
object AlarmConfig {
    /** Total time for player volume to ramp from [INITIAL_VOLUME_FRACTION] to 1.0. */
    val VOLUME_RAMP_DURATION: Duration = 20.seconds

    /** Player volume at the first audible moment of the alarm. */
    const val INITIAL_VOLUME_FRACTION = 0.05f

    /** If TTS has not initialized within this window, fall back to the bundled tone (FR-017). */
    val TTS_INIT_TIMEOUT: Duration = 3.seconds

    /** Pause between repeated TTS reads of the card front. */
    val TTS_REPEAT_GAP: Duration = 2.seconds

    /** Minimum interval between overlay re-launches of the challenge (FR-011 debounce). */
    val OVERLAY_RELAUNCH_DEBOUNCE: Duration = 1000.milliseconds

    const val RINGING_CHANNEL_ID = "alarm_ringing"
    const val RINGING_NOTIFICATION_ID = 1001
}

package com.flashbang.alarm.ring

import com.flashbang.config.AlarmConfig

/**
 * Decides whether the service should re-launch the challenge when it leaves the
 * foreground unresolved (FR-011/FR-012). Pure logic, unit-tested as a matrix.
 * Tier 3 (not opted in / permission denied) is a normal state: never redisplay,
 * never error — audio and the notification are the pressure.
 */
class OverlayRedisplayPolicy(
    private val debounceMs: Long = AlarmConfig.OVERLAY_RELAUNCH_DEBOUNCE.inWholeMilliseconds,
) {
    private var lastRelaunchAtMs: Long? = null

    fun shouldRedisplay(
        optedIn: Boolean,
        overlayPermissionGranted: Boolean,
        alarmResolved: Boolean,
        nowMs: Long,
    ): Boolean {
        if (!optedIn || !overlayPermissionGranted || alarmResolved) return false
        val last = lastRelaunchAtMs
        if (last != null && nowMs - last < debounceMs) return false
        lastRelaunchAtMs = nowMs
        return true
    }
}

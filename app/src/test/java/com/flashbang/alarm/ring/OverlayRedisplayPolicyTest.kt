package com.flashbang.alarm.ring

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OverlayRedisplayPolicyTest {

    private fun policy() = OverlayRedisplayPolicy(debounceMs = 1000)

    // Decision matrix (FR-011/FR-012): redisplay only when opted-in AND granted AND unresolved.

    @Test
    fun `redisplays when opted in, granted, unresolved`() {
        assertTrue(policy().shouldRedisplay(optedIn = true, overlayPermissionGranted = true, alarmResolved = false, nowMs = 0))
    }

    @Test
    fun `never redisplays when not opted in`() {
        assertFalse(policy().shouldRedisplay(optedIn = false, overlayPermissionGranted = true, alarmResolved = false, nowMs = 0))
    }

    @Test
    fun `never redisplays when permission denied - tier 3 is a normal state`() {
        assertFalse(policy().shouldRedisplay(optedIn = true, overlayPermissionGranted = false, alarmResolved = false, nowMs = 0))
    }

    @Test
    fun `never redisplays after alarm resolved`() {
        assertFalse(policy().shouldRedisplay(optedIn = true, overlayPermissionGranted = true, alarmResolved = true, nowMs = 0))
    }

    // Debounce

    @Test
    fun `debounces relaunches within the window`() {
        val p = policy()
        assertTrue(p.shouldRedisplay(true, true, false, nowMs = 0))
        assertFalse(p.shouldRedisplay(true, true, false, nowMs = 500))
        assertTrue(p.shouldRedisplay(true, true, false, nowMs = 1000))
    }

    @Test
    fun `denied attempts do not consume the debounce window`() {
        val p = policy()
        assertFalse(p.shouldRedisplay(optedIn = false, overlayPermissionGranted = true, alarmResolved = false, nowMs = 0))
        // First eligible attempt should still fire immediately.
        assertTrue(p.shouldRedisplay(optedIn = true, overlayPermissionGranted = true, alarmResolved = false, nowMs = 10))
    }
}

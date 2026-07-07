package com.flashbang.audio

import kotlin.time.Duration.Companion.seconds
import org.junit.Assert.assertEquals
import org.junit.Test

class VolumeRampTest {

    private val ramp = 20.seconds
    private val initial = 0.05f

    @Test
    fun `starts at initial fraction`() {
        assertEquals(initial, VolumeRamp.volumeAt(0.seconds, ramp, initial))
    }

    @Test
    fun `negative elapsed clamps to initial`() {
        assertEquals(initial, VolumeRamp.volumeAt((-5).seconds, ramp, initial))
    }

    @Test
    fun `midpoint is halfway between initial and full`() {
        val expected = initial + (1.0f - initial) * 0.5f
        assertEquals(expected, VolumeRamp.volumeAt(10.seconds, ramp, initial), 0.001f)
    }

    @Test
    fun `reaches full volume at ramp end`() {
        assertEquals(1.0f, VolumeRamp.volumeAt(20.seconds, ramp, initial))
    }

    @Test
    fun `stays at full volume past ramp end`() {
        assertEquals(1.0f, VolumeRamp.volumeAt(120.seconds, ramp, initial))
    }

    @Test
    fun `monotonically non-decreasing across the ramp`() {
        var last = -1f
        for (ms in 0..21_000 step 250) {
            val v = VolumeRamp.volumeAt(kotlin.time.Duration.parse("${ms}ms"), ramp, initial)
            check(v >= last) { "ramp decreased at ${ms}ms: $v < $last" }
            last = v
        }
    }
}

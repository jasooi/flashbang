package com.flashbang.alarm

import com.flashbang.data.Alarm
import java.time.DayOfWeek
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NextFireTimeCalculatorTest {

    private val zone = ZoneId.of("Asia/Tokyo")

    private fun alarm(timeMinutes: Int, mask: Int = 0) =
        Alarm(id = 1, deckId = 1, timeMinutes = timeMinutes, daysOfWeekMask = mask)

    private fun at(year: Int, month: Int, day: Int, hour: Int, minute: Int, z: ZoneId = zone): ZonedDateTime =
        ZonedDateTime.of(year, month, day, hour, minute, 0, 0, z)

    // --- mask helpers ---

    @Test
    fun `mask bit layout is monday-first`() {
        assertEquals(0b1, NextFireTimeCalculator.bitFor(DayOfWeek.MONDAY))
        assertEquals(0b1000000, NextFireTimeCalculator.bitFor(DayOfWeek.SUNDAY))
        assertEquals(
            setOf(DayOfWeek.MONDAY, DayOfWeek.SUNDAY),
            NextFireTimeCalculator.daysFromMask(0b1000001),
        )
    }

    // --- daily (empty mask) ---

    @Test
    fun `same day when alarm time is still ahead`() {
        // 2026-07-08 is a Wednesday
        val now = at(2026, 7, 8, 6, 0)
        val next = NextFireTimeCalculator.nextFire(alarm(6 * 60 + 45), now)
        assertEquals(at(2026, 7, 8, 6, 45), next)
    }

    @Test
    fun `next day when alarm time already passed`() {
        val now = at(2026, 7, 8, 7, 0)
        val next = NextFireTimeCalculator.nextFire(alarm(6 * 60 + 45), now)
        assertEquals(at(2026, 7, 9, 6, 45), next)
    }

    @Test
    fun `exact tie rolls to next day`() {
        val now = at(2026, 7, 8, 6, 45)
        val next = NextFireTimeCalculator.nextFire(alarm(6 * 60 + 45), now)
        assertEquals(at(2026, 7, 9, 6, 45), next)
    }

    // --- day-of-week masks ---

    @Test
    fun `skips to next enabled weekday`() {
        // Wednesday now; alarm only on Friday
        val now = at(2026, 7, 8, 12, 0)
        val mask = NextFireTimeCalculator.bitFor(DayOfWeek.FRIDAY)
        val next = NextFireTimeCalculator.nextFire(alarm(6 * 60 + 45, mask), now)
        assertEquals(at(2026, 7, 10, 6, 45), next)
        assertEquals(DayOfWeek.FRIDAY, next.dayOfWeek)
    }

    @Test
    fun `week wraparound when only enabled day already passed this week`() {
        // Wednesday 12:00; alarm Mondays at 06:45 -> next Monday (2026-07-13)
        val now = at(2026, 7, 8, 12, 0)
        val mask = NextFireTimeCalculator.bitFor(DayOfWeek.MONDAY)
        val next = NextFireTimeCalculator.nextFire(alarm(6 * 60 + 45, mask), now)
        assertEquals(at(2026, 7, 13, 6, 45), next)
    }

    @Test
    fun `single day mask fires today if time ahead`() {
        val now = at(2026, 7, 8, 5, 0)
        val mask = NextFireTimeCalculator.bitFor(DayOfWeek.WEDNESDAY)
        val next = NextFireTimeCalculator.nextFire(alarm(6 * 60 + 45, mask), now)
        assertEquals(at(2026, 7, 8, 6, 45), next)
    }

    @Test
    fun `same enabled day but time passed wraps a full week`() {
        val now = at(2026, 7, 8, 7, 0)
        val mask = NextFireTimeCalculator.bitFor(DayOfWeek.WEDNESDAY)
        val next = NextFireTimeCalculator.nextFire(alarm(6 * 60 + 45, mask), now)
        assertEquals(at(2026, 7, 15, 6, 45), next)
    }

    // --- DST (US zone; Tokyo has no DST) ---

    @Test
    fun `spring forward gap resolves to shifted instant`() {
        val ny = ZoneId.of("America/New_York")
        // 2026-03-08: 02:00-03:00 does not exist in New York.
        val now = at(2026, 3, 7, 12, 0, ny)
        val next = NextFireTimeCalculator.nextFire(alarm(2 * 60 + 30), now)
        // java.time resolves 02:30 in the gap by shifting forward one hour to 03:30.
        assertEquals(at(2026, 3, 8, 3, 30, ny), next)
    }

    @Test
    fun `fall back overlap picks earlier offset and stays after now`() {
        val ny = ZoneId.of("America/New_York")
        // 2026-11-01: 01:00-02:00 happens twice in New York.
        val now = at(2026, 10, 31, 12, 0, ny)
        val next = NextFireTimeCalculator.nextFire(alarm(1 * 60 + 30), now)
        assertEquals(1, next.hour)
        assertEquals(30, next.minute)
        assertTrue(next.isAfter(now))
    }

    // --- validation ---

    @Test(expected = IllegalArgumentException::class)
    fun `rejects out of range minutes`() {
        NextFireTimeCalculator.nextFire(alarm(1440), at(2026, 7, 8, 6, 0))
    }
}

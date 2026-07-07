package com.flashbang.alarm

import com.flashbang.data.Alarm
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.ZonedDateTime

/**
 * Pure next-occurrence math for an alarm. Kept free of Android types so the
 * DST/wraparound edge cases are exhaustively unit-testable on the JVM.
 */
object NextFireTimeCalculator {

    /** Bit for a day in [Alarm.daysOfWeekMask]: bit 0 = Monday .. bit 6 = Sunday. */
    fun bitFor(day: DayOfWeek): Int = 1 shl (day.value - 1)

    fun daysFromMask(mask: Int): Set<DayOfWeek> =
        DayOfWeek.entries.filter { mask and bitFor(it) != 0 }.toSet()

    /**
     * Next fire instant strictly after [now]. A mask of 0 means every day.
     * An alarm scheduled for exactly `now` is considered missed and rolls to
     * the next eligible day (an exact tie can't ring "now" — it's already passed
     * by the time we compute).
     *
     * DST notes: the candidate is built with [ZonedDateTime.of]-style resolution
     * via `atZone`, so a wall time inside a spring-forward gap resolves to the
     * shifted (post-gap) instant, and a fall-back overlap resolves to the earlier
     * offset — java.time defaults, asserted by tests rather than re-implemented.
     */
    fun nextFire(alarm: Alarm, now: ZonedDateTime): ZonedDateTime {
        require(alarm.timeMinutes in 0..1439) { "timeMinutes out of range: ${alarm.timeMinutes}" }
        val time = LocalTime.of(alarm.timeMinutes / 60, alarm.timeMinutes % 60)
        val days = daysFromMask(alarm.daysOfWeekMask).ifEmpty { DayOfWeek.entries.toSet() }

        for (offset in 0..7L) {
            val date = now.toLocalDate().plusDays(offset)
            if (date.dayOfWeek !in days) continue
            val candidate = date.atTime(time).atZone(now.zone)
            if (candidate.isAfter(now)) return candidate
        }
        // Unreachable: with at least one eligible day, a candidate within 8 days always exists.
        error("No next fire time found for alarm ${alarm.id}")
    }
}

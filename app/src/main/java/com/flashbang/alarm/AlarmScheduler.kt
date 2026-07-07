package com.flashbang.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import com.flashbang.data.Alarm
import com.flashbang.data.FlashbangDatabase
import com.flashbang.ui.MainActivity
import java.time.ZonedDateTime

/**
 * Wraps [AlarmManager.setAlarmClock] (FR-001): Doze-exempt, status-bar alarm icon.
 * One-shot semantics — the service re-arms the next occurrence after each fire;
 * repetition is derived from days_of_week, never from setRepeating (inexact).
 */
class AlarmScheduler(private val context: Context) {

    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    fun schedule(alarm: Alarm, now: ZonedDateTime = ZonedDateTime.now()) {
        if (!alarm.enabled) return
        val fireAt = NextFireTimeCalculator.nextFire(alarm, now)
        val info = AlarmManager.AlarmClockInfo(fireAt.toInstant().toEpochMilli(), showIntent())
        alarmManager.setAlarmClock(info, fireOperation(alarm.id))
        Log.i(TAG, "Alarm ${alarm.id} scheduled for $fireAt")
    }

    fun cancel(alarmId: Long) {
        alarmManager.cancel(fireOperation(alarmId))
    }

    /** Re-registers everything enabled — used after boot, time/timezone change, or alarm edits. */
    suspend fun rescheduleAll() {
        val alarms = FlashbangDatabase.get(context).alarmDao().enabled()
        val now = ZonedDateTime.now()
        alarms.forEach { schedule(it, now) }
        Log.i(TAG, "Rescheduled ${alarms.size} alarm(s)")
    }

    private fun fireOperation(alarmId: Long): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            alarmId.toInt(), // per-alarm request code so intents don't collide
            Intent(context, AlarmReceiver::class.java).putExtra(AlarmReceiver.EXTRA_ALARM_ID, alarmId),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    private fun showIntent(): PendingIntent =
        PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    private companion object {
        const val TAG = "AlarmScheduler"
    }
}

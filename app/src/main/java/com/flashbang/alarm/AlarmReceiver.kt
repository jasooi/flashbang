package com.flashbang.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.flashbang.alarm.ring.AlarmRingingService

/**
 * Fire path entry point (FR-004). Does exactly one thing: hands off to the
 * foreground service. Receivers get ~10s; the service owns everything after this.
 */
class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val alarmId = intent.getLongExtra(EXTRA_ALARM_ID, -1L)
        if (alarmId == -1L) return
        val serviceIntent = Intent(context, AlarmRingingService::class.java)
            .setAction(AlarmRingingService.ACTION_RING)
            .putExtra(EXTRA_ALARM_ID, alarmId)
        ContextCompat.startForegroundService(context, serviceIntent)
    }

    companion object {
        const val EXTRA_ALARM_ID = "alarm_id"
    }
}

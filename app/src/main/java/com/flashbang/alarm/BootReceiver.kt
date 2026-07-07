package com.flashbang.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Re-registers all enabled alarms after reboot (FR-003) and after clock or
 * timezone changes (FR-003b) — AlarmManager fire times are wall-clock-relative
 * and go stale on both.
 *
 * Direct-boot (pre-first-unlock) is a documented MVP limitation: BOOT_COMPLETED
 * only arrives after the user unlocks once (see requirements.md, Out of scope).
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in HANDLED_ACTIONS) return
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                AlarmScheduler(context).rescheduleAll()
            } finally {
                pendingResult.finish()
            }
        }
    }

    private companion object {
        val HANDLED_ACTIONS = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
        )
    }
}

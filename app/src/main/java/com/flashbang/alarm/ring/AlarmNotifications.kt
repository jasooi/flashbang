package com.flashbang.alarm.ring

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.flashbang.R
import com.flashbang.config.AlarmConfig
import com.flashbang.ui.challenge.ChallengeActivity

object AlarmNotifications {

    /** Idempotent; called at app start. Channel itself is silent — the service owns all sound. */
    fun registerChannel(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            AlarmConfig.RINGING_CHANNEL_ID,
            context.getString(R.string.ringing_channel_name),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            setSound(null, null)
            enableVibration(false)
            description = context.getString(R.string.ringing_channel_description)
        }
        manager.createNotificationChannel(channel)
    }

    /**
     * The ringing notification (FR-005, FR-010): full-screen intent launches the
     * challenge over the lock screen; content tap re-opens it; ongoing so it
     * can't be swiped while the alarm rings.
     */
    fun ringingNotification(context: Context): Notification {
        val challengeIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, ChallengeActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(context, AlarmConfig.RINGING_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_alarm)
            .setContentTitle(context.getString(R.string.ringing_notification_title))
            .setContentText(context.getString(R.string.ringing_notification_text))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setOngoing(true)
            .setContentIntent(challengeIntent)
            .setFullScreenIntent(challengeIntent, /* highPriority = */ true)
            .build()
    }
}

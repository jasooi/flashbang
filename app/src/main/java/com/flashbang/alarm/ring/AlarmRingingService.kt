package com.flashbang.alarm.ring

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.util.Log
import androidx.core.app.ServiceCompat
import com.flashbang.alarm.AlarmReceiver
import com.flashbang.alarm.AlarmScheduler
import com.flashbang.config.AlarmConfig
import com.flashbang.data.FlashbangDatabase
import com.flashbang.data.Language
import com.flashbang.data.SettingsRepository
import com.flashbang.audio.AlarmAudioEngine
import com.flashbang.ui.challenge.ChallengeActivity
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * The single owner of a ringing alarm (FR-004, FR-007, FR-009): sound, vibration,
 * and ringing state live here, never in the activity. The alarm stops only via
 * ACTION_RESOLVE — activity lifecycle, task removal, and UI crashes do not stop it.
 */
class AlarmRingingService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var audioEngine: AlarmAudioEngine? = null
    private val redisplayPolicy = OverlayRedisplayPolicy()
    private var activeAlarmId: Long = -1L

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_RING -> onRing(intent.getLongExtra(AlarmReceiver.EXTRA_ALARM_ID, -1L))
            ACTION_RESOLVE -> onResolve()
            ACTION_CHALLENGE_HIDDEN -> onChallengeHidden()
        }
        return START_NOT_STICKY
    }

    private fun onRing(alarmId: Long) {
        if (activeAlarmId != -1L) {
            Log.w(TAG, "Already ringing $activeAlarmId; ignoring ring for $alarmId")
            return
        }
        activeAlarmId = alarmId

        // Foreground FIRST — before any I/O — or the OS kills the service (design.md step 1).
        ServiceCompat.startForeground(
            this,
            AlarmConfig.RINGING_NOTIFICATION_ID,
            AlarmNotifications.ringingNotification(this),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SYSTEM_EXEMPTED
            } else {
                0
            },
        )

        scope.launch {
            val db = FlashbangDatabase.get(this@AlarmRingingService)
            val settings = SettingsRepository(this@AlarmRingingService)
            val vibrationEnabled = settings.vibrationEnabledNow()

            // Any failure below still rings — fallback tone + error card (NFR-003).
            val result = runCatching {
                val alarm = requireNotNull(db.alarmDao().byId(alarmId)) { "alarm $alarmId missing" }
                val deck = requireNotNull(db.deckDao().byId(alarm.deckId)) { "deck ${alarm.deckId} missing" }
                val card = requireNotNull(db.cardDao().firstCardOfDeck(deck.id)) { "deck ${deck.id} empty" }
                Triple(alarm, deck, card)
            }

            val engine = AlarmAudioEngine(this@AlarmRingingService, scope)
            audioEngine = engine
            result.fold(
                onSuccess = { (_, deck, card) ->
                    RingingStateHolder.publish(
                        RingingState(alarmId, card.front, card.reading),
                    )
                    engine.start(card, deck.language.toLocale(), vibrationEnabled)
                },
                onFailure = { e ->
                    Log.e(TAG, "Card load failed; ringing with fallback tone", e)
                    RingingStateHolder.publish(
                        RingingState(
                            alarmId,
                            cardFront = getString(com.flashbang.R.string.challenge_load_error),
                            cardReading = null,
                            loadFailed = true,
                        ),
                    )
                    engine.start(card = null, locale = Locale.getDefault(), vibrationEnabled = vibrationEnabled)
                },
            )
        }
    }

    private fun onResolve() {
        if (activeAlarmId == -1L) {
            stopSelf()
            return
        }
        val resolvedAlarmId = activeAlarmId
        activeAlarmId = -1L
        audioEngine?.stop()
        audioEngine = null
        RingingStateHolder.publish(null)

        // Re-arm the next occurrence, then fully stop (NFR-006).
        scope.launch {
            runCatching {
                val alarm = FlashbangDatabase.get(this@AlarmRingingService).alarmDao().byId(resolvedAlarmId)
                if (alarm != null) AlarmScheduler(this@AlarmRingingService).schedule(alarm)
            }.onFailure { Log.e(TAG, "Failed to re-arm alarm $resolvedAlarmId", it) }
            ServiceCompat.stopForeground(this@AlarmRingingService, ServiceCompat.STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    /** Activity reports onStop-while-unresolved; Tier 2 redisplay if opted in + granted (FR-011). */
    private fun onChallengeHidden() {
        scope.launch {
            val optedIn = SettingsRepository(this@AlarmRingingService).overlayBlockingOptedInNow()
            val granted = Settings.canDrawOverlays(this@AlarmRingingService)
            val resolved = activeAlarmId == -1L
            if (redisplayPolicy.shouldRedisplay(optedIn, granted, resolved, System.currentTimeMillis())) {
                startActivity(
                    Intent(this@AlarmRingingService, ChallengeActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
                )
            }
        }
    }

    /** Swiping the app from recents must not stop the alarm (FR-009). */
    override fun onTaskRemoved(rootIntent: Intent?) = Unit

    override fun onDestroy() {
        // Defensive teardown only; the normal path is ACTION_RESOLVE.
        audioEngine?.stop()
        RingingStateHolder.publish(null)
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        const val ACTION_RING = "com.flashbang.action.RING"
        const val ACTION_RESOLVE = "com.flashbang.action.RESOLVE"
        const val ACTION_CHALLENGE_HIDDEN = "com.flashbang.action.CHALLENGE_HIDDEN"
        private const val TAG = "AlarmRingingService"

        fun resolveIntent(context: Context): Intent =
            Intent(context, AlarmRingingService::class.java).setAction(ACTION_RESOLVE)

        fun challengeHiddenIntent(context: Context): Intent =
            Intent(context, AlarmRingingService::class.java).setAction(ACTION_CHALLENGE_HIDDEN)
    }
}

private fun Language.toLocale(): Locale = when (this) {
    Language.JA -> Locale.JAPANESE
    Language.EN -> Locale.ENGLISH
    Language.ZH -> Locale.CHINESE
}

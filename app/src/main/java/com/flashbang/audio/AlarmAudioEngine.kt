package com.flashbang.audio

import android.content.Context
import android.media.AudioAttributes
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.flashbang.config.AlarmConfig
import com.flashbang.data.Card
import com.flashbang.tts.TtsEngine
import com.flashbang.tts.ttsTextFor
import java.util.Locale
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Owns everything audible/haptic for one ringing alarm (FR-006 — FR-009).
 * Created and destroyed only by AlarmRingingService; activity lifecycle never
 * touches this. Sources: TTS loop (primary) or fallback tone (TTS failure or
 * no card). The volume ramp and vibration run independently of source swaps.
 */
class AlarmAudioEngine(
    private val context: Context,
    private val scope: CoroutineScope,
) {
    private val fallbackTone = FallbackTonePlayer(context)
    private var ttsEngine: TtsEngine? = null
    private var rampJob: Job? = null
    private var vibrator: Vibrator? = null

    @Volatile private var currentVolume = AlarmConfig.INITIAL_VOLUME_FRACTION

    /** [card] null means "ring anyway with the fallback tone" (card load failed, NFR-003). */
    fun start(card: Card?, locale: Locale, vibrationEnabled: Boolean) {
        startRamp()
        if (vibrationEnabled) startVibration()
        if (card == null) {
            fallbackTone.start(currentVolume)
        } else {
            ttsEngine = TtsEngine(context, locale, onUnavailable = {
                // Swap source; ramp and vibration keep running (FR-017).
                fallbackTone.start(currentVolume)
            }).also { it.startLoop(ttsTextFor(card)) }
        }
    }

    fun stop() {
        rampJob?.cancel()
        rampJob = null
        ttsEngine?.stop()
        ttsEngine = null
        fallbackTone.stop()
        vibrator?.cancel()
        vibrator = null
    }

    private fun startRamp() {
        rampJob = scope.launch {
            val start = System.currentTimeMillis()
            while (isActive) {
                val elapsed = (System.currentTimeMillis() - start).milliseconds
                currentVolume = VolumeRamp.volumeAt(elapsed)
                ttsEngine?.setVolume(currentVolume)
                fallbackTone.setVolume(currentVolume)
                if (elapsed >= AlarmConfig.VOLUME_RAMP_DURATION) break
                delay(RAMP_TICK_MS)
            }
        }
    }

    private fun startVibration() {
        val v = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
        val effect = VibrationEffect.createWaveform(VIBRATION_PATTERN_MS, /* repeat = */ 0)
        val attrs = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build()
        @Suppress("DEPRECATION") // AudioAttributes overload: simplest API spanning 26..35
        v.vibrate(effect, attrs)
        vibrator = v
    }

    private companion object {
        const val RAMP_TICK_MS = 250L
        val VIBRATION_PATTERN_MS = longArrayOf(0, 800, 400)
    }
}

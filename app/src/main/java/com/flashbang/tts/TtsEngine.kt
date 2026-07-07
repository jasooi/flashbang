package com.flashbang.tts

import android.content.Context
import android.media.AudioAttributes
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import com.flashbang.config.AlarmConfig
import com.flashbang.data.Card
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

/** What the alarm speaks: the reading when present, else the front (FR-016). Never the back. */
fun ttsTextFor(card: Card): String = card.reading?.takeIf { it.isNotBlank() } ?: card.front

/**
 * Platform TTS wrapper for the ring path (FR-014): on-device voices only — no
 * network calls are reachable from here. Loops the card text with a gap; any
 * init timeout or error reports through [onUnavailable] so the audio engine can
 * swap to the fallback tone without interrupting ramp or vibration (FR-017).
 */
class TtsEngine(
    private val context: Context,
    private val locale: Locale,
    private val onUnavailable: () -> Unit,
) {
    private val handler = Handler(Looper.getMainLooper())
    private var tts: TextToSpeech? = null
    private var currentVolume = AlarmConfig.INITIAL_VOLUME_FRACTION
    private val failed = AtomicBoolean(false)
    private val initialized = AtomicBoolean(false)
    private var stopped = false
    private var loopText: String? = null

    fun startLoop(text: String) {
        loopText = text
        handler.postDelayed(
            { if (!initialized.get()) fail("TTS init timeout") },
            AlarmConfig.TTS_INIT_TIMEOUT.inWholeMilliseconds,
        )
        tts = TextToSpeech(context) { status ->
            handler.post {
                if (stopped) return@post
                if (status != TextToSpeech.SUCCESS) {
                    fail("TTS init failed: $status")
                    return@post
                }
                val t = tts ?: return@post
                val langResult = t.setLanguage(locale)
                if (langResult == TextToSpeech.LANG_MISSING_DATA || langResult == TextToSpeech.LANG_NOT_SUPPORTED) {
                    fail("TTS language unavailable: $langResult")
                    return@post
                }
                t.setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build(),
                )
                t.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {}

                    override fun onDone(utteranceId: String?) {
                        handler.postDelayed({ speakOnce() }, AlarmConfig.TTS_REPEAT_GAP.inWholeMilliseconds)
                    }

                    @Deprecated("Deprecated in platform API")
                    override fun onError(utteranceId: String?) {
                        handler.post { fail("TTS utterance error") }
                    }

                    override fun onError(utteranceId: String?, errorCode: Int) {
                        handler.post { fail("TTS utterance error: $errorCode") }
                    }
                })
                initialized.set(true)
                speakOnce()
            }
        }
    }

    fun setVolume(volume: Float) {
        currentVolume = volume // applied per-utterance; player volume, not stream volume (FR-008)
    }

    fun stop() {
        stopped = true
        handler.removeCallbacksAndMessages(null)
        tts?.run {
            runCatching { stop() }
            shutdown()
        }
        tts = null
    }

    private fun speakOnce() {
        if (stopped || failed.get()) return
        val text = loopText ?: return
        val params = Bundle().apply { putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, currentVolume) }
        val result = tts?.speak(text, TextToSpeech.QUEUE_FLUSH, params, UTTERANCE_ID)
        if (result != TextToSpeech.SUCCESS) fail("TTS speak() returned $result")
    }

    private fun fail(reason: String) {
        if (stopped || !failed.compareAndSet(false, true)) return
        Log.w(TAG, "Falling back to tone: $reason")
        stop()
        stopped = false // engine may still receive setVolume calls; harmless
        onUnavailable()
    }

    private companion object {
        const val TAG = "TtsEngine"
        const val UTTERANCE_ID = "flashbang_card_front"
    }
}

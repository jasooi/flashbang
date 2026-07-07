package com.flashbang.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.util.Log
import com.flashbang.R

/**
 * Looping bundled alarm tone on the alarm stream (FR-006, FR-017). Used when
 * TTS is unavailable or the card can't be loaded — the alarm never fails silent.
 */
class FallbackTonePlayer(private val context: Context) {

    private var player: MediaPlayer? = null

    fun start(initialVolume: Float) {
        if (player != null) return
        player = try {
            MediaPlayer.create(
                context,
                R.raw.fallback_alarm,
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
                /* audioSessionId = */ 0,
            )?.apply {
                isLooping = true
                setVolume(initialVolume, initialVolume)
                start()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Fallback tone failed to start", e)
            null
        }
    }

    fun setVolume(volume: Float) {
        player?.setVolume(volume, volume)
    }

    fun stop() {
        player?.run {
            runCatching { stop() }
            release()
        }
        player = null
    }

    private companion object {
        const val TAG = "FallbackTonePlayer"
    }
}

package com.flashbang.tts

import android.content.Context
import android.content.Intent
import android.speech.tts.TextToSpeech
import java.util.Locale

/**
 * Deck-setup-time voice availability check (FR-015). This is the ONLY place a
 * voice download can be triggered — never the ring path.
 */
class TtsVoiceChecker(private val context: Context) {

    enum class Status { AVAILABLE, MISSING_DATA, NOT_SUPPORTED }

    fun check(locale: Locale, onResult: (Status) -> Unit) {
        var tts: TextToSpeech? = null
        tts = TextToSpeech(context) { initStatus ->
            val status = if (initStatus != TextToSpeech.SUCCESS) {
                Status.NOT_SUPPORTED
            } else {
                when (tts?.isLanguageAvailable(locale)) {
                    TextToSpeech.LANG_AVAILABLE,
                    TextToSpeech.LANG_COUNTRY_AVAILABLE,
                    TextToSpeech.LANG_COUNTRY_VAR_AVAILABLE,
                    -> Status.AVAILABLE
                    TextToSpeech.LANG_MISSING_DATA -> Status.MISSING_DATA
                    else -> Status.NOT_SUPPORTED
                }
            }
            tts?.shutdown()
            onResult(status)
        }
    }

    /** Opens the system TTS data installer (used on MISSING_DATA). */
    fun launchVoiceDownload() {
        context.startActivity(
            Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}

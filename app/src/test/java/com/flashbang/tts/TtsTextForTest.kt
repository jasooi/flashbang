package com.flashbang.tts

import com.flashbang.data.Card
import org.junit.Assert.assertEquals
import org.junit.Test

class TtsTextForTest {

    private fun card(front: String, reading: String?) =
        Card(deckId = 1, front = front, back = "meaning", reading = reading)

    @Test
    fun `speaks reading when present`() {
        // Kanji misreading guard (FR-016): 辛い must be spoken as からい
        assertEquals("からい", ttsTextFor(card("辛い", "からい")))
    }

    @Test
    fun `speaks front when reading is null`() {
        assertEquals("water", ttsTextFor(card("water", null)))
    }

    @Test
    fun `speaks front when reading is blank`() {
        assertEquals("行く", ttsTextFor(card("行く", "  ")))
    }
}

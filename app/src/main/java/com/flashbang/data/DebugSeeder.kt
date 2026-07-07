package com.flashbang.data

import android.content.Context
import androidx.room.withTransaction
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * PHASE1-DEV-ONLY: seeds one deck + three cards + one alarm so the ring path is
 * manually testable before the Phase 2 deck UI / sample-deck seeding exist.
 * Debug builds only (guarded at the call site in FlashbangApp).
 */
object DebugSeeder {

    fun seedIfEmpty(context: Context) {
        val db = FlashbangDatabase.get(context)
        CoroutineScope(Dispatchers.IO).launch {
            if (db.deckDao().count() > 0) return@launch
            db.withTransaction {
                val deckId = db.deckDao().insert(Deck(name = "Dev Test Deck", language = Language.JA))
                db.cardDao().insertAll(
                    listOf(
                        Card(deckId = deckId, front = "辛い", back = "spicy", reading = "からい"),
                        Card(deckId = deckId, front = "行く", back = "to go", reading = "いく"),
                        Card(deckId = deckId, front = "水", back = "water", reading = "みず"),
                    ),
                )
                db.alarmDao().insert(
                    Alarm(deckId = deckId, timeMinutes = 7 * 60, daysOfWeekMask = 0, enabled = false),
                )
            }
        }
    }
}

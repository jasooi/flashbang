package com.flashbang.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Supported deck languages. Stored as the enum name string; writes go through the
 * type-converted enum so an invalid language cannot be persisted from app code.
 * (Room cannot express a SQL CHECK constraint on generated tables — logged as a
 * deviation in implementation-notes.md.)
 */
enum class Language { JA, EN, ZH }

@Entity(tableName = "deck")
data class Deck(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val language: Language,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "card",
    foreignKeys = [
        ForeignKey(
            entity = Deck::class,
            parentColumns = ["id"],
            childColumns = ["deck_id"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [Index("deck_id")],
)
data class Card(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    // NOT NULL by construction: non-null Kotlin type, no default escape hatch.
    @ColumnInfo(name = "deck_id") val deckId: Long,
    val front: String,
    val back: String,
    val reading: String? = null,
    val hint: String? = null,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis(),
)

/** Progress belongs to the deck (PRD §6.1): composite key (card_id, deck_id). Schema only in Phase 1. */
@Entity(
    tableName = "card_progress",
    primaryKeys = ["card_id", "deck_id"],
    foreignKeys = [
        ForeignKey(
            entity = Card::class,
            parentColumns = ["id"],
            childColumns = ["card_id"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = Deck::class,
            parentColumns = ["id"],
            childColumns = ["deck_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("deck_id")],
)
data class CardProgress(
    @ColumnInfo(name = "card_id") val cardId: Long,
    @ColumnInfo(name = "deck_id") val deckId: Long,
    @ColumnInfo(name = "ease_score") val easeScore: Double = 0.0,
    @ColumnInfo(name = "last_shown_at") val lastShownAt: Long? = null,
    @ColumnInfo(name = "last_outcome") val lastOutcome: String? = null,
    @ColumnInfo(name = "pre_hint_count") val preHintCount: Int = 0,
    @ColumnInfo(name = "post_hint_count") val postHintCount: Int = 0,
    @ColumnInfo(name = "wrong_attempt_count") val wrongAttemptCount: Int = 0,
    @ColumnInfo(name = "snooze_count") val snoozeCount: Int = 0,
    @ColumnInfo(name = "escape_count") val escapeCount: Int = 0,
)

@Entity(
    tableName = "alarm",
    foreignKeys = [
        ForeignKey(
            entity = Deck::class,
            parentColumns = ["id"],
            childColumns = ["deck_id"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [Index("deck_id")],
)
data class Alarm(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "deck_id") val deckId: Long,
    /** Minutes since midnight, local time (0..1439). */
    @ColumnInfo(name = "time_minutes") val timeMinutes: Int,
    /** Bitmask, bit 0 = Monday .. bit 6 = Sunday. 0 = every day. */
    @ColumnInfo(name = "days_of_week") val daysOfWeekMask: Int = 0,
    val enabled: Boolean = true,
    @ColumnInfo(name = "snooze_duration_minutes") val snoozeDurationMinutes: Int = 5,
)

/** Single-row table (id fixed to 0). Schema only in Phase 1; logic ships in Phase 5. */
@Entity(tableName = "streak_state")
data class StreakState(
    @PrimaryKey val id: Int = 0,
    @ColumnInfo(name = "current_streak") val currentStreak: Int = 0,
    @ColumnInfo(name = "best_streak") val bestStreak: Int = 0,
    @ColumnInfo(name = "last_answered_date") val lastAnsweredDate: String? = null,
)

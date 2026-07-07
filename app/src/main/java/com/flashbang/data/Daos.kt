package com.flashbang.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface DeckDao {
    @Insert suspend fun insert(deck: Deck): Long

    @Query("SELECT * FROM deck WHERE id = :id") suspend fun byId(id: Long): Deck?

    @Query("SELECT COUNT(*) FROM deck") suspend fun count(): Int
}

@Dao
interface CardDao {
    @Insert suspend fun insertAll(cards: List<Card>): List<Long>

    @Query("SELECT * FROM card WHERE deck_id = :deckId ORDER BY id") suspend fun byDeck(deckId: Long): List<Card>

    /**
     * Phase 1 card resolution: first card of the deck. The Phase 4 ease sampler
     * replaces this query as the single point where "which card rings" is decided.
     */
    @Query("SELECT * FROM card WHERE deck_id = :deckId ORDER BY id LIMIT 1")
    suspend fun firstCardOfDeck(deckId: Long): Card?
}

@Dao
interface AlarmDao {
    @Insert suspend fun insert(alarm: Alarm): Long

    @Update suspend fun update(alarm: Alarm)

    @Query("SELECT * FROM alarm WHERE id = :id") suspend fun byId(id: Long): Alarm?

    @Query("SELECT * FROM alarm WHERE enabled = 1") suspend fun enabled(): List<Alarm>

    @Query("SELECT * FROM alarm ORDER BY id") fun observeAll(): Flow<List<Alarm>>
}

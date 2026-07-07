package com.flashbang.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DaoTest {

    private lateinit var db: FlashbangDatabase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, FlashbangDatabase::class.java).build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun deckWithCardsAndAlarmRoundTrip() = runTest {
        val deckId = db.deckDao().insert(Deck(name = "JLPT N5", language = Language.JA))
        db.cardDao().insertAll(
            listOf(
                Card(deckId = deckId, front = "水", back = "water", reading = "みず"),
                Card(deckId = deckId, front = "辛い", back = "spicy", reading = "からい"),
            ),
        )
        val alarmId = db.alarmDao().insert(Alarm(deckId = deckId, timeMinutes = 405))

        val cards = db.cardDao().byDeck(deckId)
        assertEquals(2, cards.size)
        assertEquals("水", cards.first().front)

        val alarm = db.alarmDao().byId(alarmId)
        assertNotNull(alarm)
        assertEquals(deckId, alarm!!.deckId)
        assertEquals(405, alarm.timeMinutes)
    }

    @Test
    fun firstCardOfDeckIsStableAndOrdered() = runTest {
        val deckId = db.deckDao().insert(Deck(name = "d", language = Language.EN))
        db.cardDao().insertAll(
            listOf(
                Card(deckId = deckId, front = "a", back = "1"),
                Card(deckId = deckId, front = "b", back = "2"),
            ),
        )
        assertEquals("a", db.cardDao().firstCardOfDeck(deckId)!!.front)
    }

    @Test
    fun enabledAlarmsFiltersDisabled() = runTest {
        val deckId = db.deckDao().insert(Deck(name = "d", language = Language.EN))
        db.alarmDao().insert(Alarm(deckId = deckId, timeMinutes = 60, enabled = true))
        db.alarmDao().insert(Alarm(deckId = deckId, timeMinutes = 120, enabled = false))
        val enabled = db.alarmDao().enabled()
        assertEquals(1, enabled.size)
        assertTrue(enabled.single().enabled)
    }

    @Test
    fun deletingDeckCascadesToCardsAndAlarms() = runTest {
        val deckId = db.deckDao().insert(Deck(name = "d", language = Language.ZH))
        db.cardDao().insertAll(listOf(Card(deckId = deckId, front = "f", back = "b")))
        val alarmId = db.alarmDao().insert(Alarm(deckId = deckId, timeMinutes = 60))

        db.openHelper.writableDatabase.execSQL("DELETE FROM deck WHERE id = $deckId")

        assertEquals(0, db.cardDao().byDeck(deckId).size)
        assertEquals(null, db.alarmDao().byId(alarmId))
    }
}

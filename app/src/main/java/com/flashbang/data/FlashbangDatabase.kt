package com.flashbang.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters

class Converters {
    @TypeConverter fun fromLanguage(value: Language): String = value.name

    @TypeConverter fun toLanguage(value: String): Language = Language.valueOf(value)
}

@Database(
    entities = [Deck::class, Card::class, CardProgress::class, Alarm::class, StreakState::class],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class FlashbangDatabase : RoomDatabase() {
    abstract fun deckDao(): DeckDao

    abstract fun cardDao(): CardDao

    abstract fun alarmDao(): AlarmDao

    companion object {
        @Volatile private var instance: FlashbangDatabase? = null

        fun get(context: Context): FlashbangDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    FlashbangDatabase::class.java,
                    "flashbang.db",
                ).build().also { instance = it }
            }
    }
}

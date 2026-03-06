package com.quicktimer.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        TimerPresetEntity::class,
        RunningTimerEntity::class,
        TimerHistoryEntity::class
    ],
    version = 4,
    exportSchema = false
)
abstract class QuickTimerDatabase : RoomDatabase() {
    abstract fun timerPresetDao(): TimerPresetDao
    abstract fun runningTimerDao(): RunningTimerDao
    abstract fun timerHistoryDao(): TimerHistoryDao

    companion object {
        @Volatile
        private var instance: QuickTimerDatabase? = null

        fun getInstance(context: Context): QuickTimerDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    QuickTimerDatabase::class.java,
                    "quick_timer.db"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { instance = it }
            }
        }
    }
}

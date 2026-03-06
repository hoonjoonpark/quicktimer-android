package com.quicktimer.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface TimerPresetDao {
    @Query("SELECT * FROM timer_presets ORDER BY position ASC")
    fun observeAll(): Flow<List<TimerPresetEntity>>

    @Query("SELECT * FROM timer_presets ORDER BY position ASC")
    suspend fun getAll(): List<TimerPresetEntity>

    @Query("SELECT COUNT(*) FROM timer_presets")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<TimerPresetEntity>)

    @Query("DELETE FROM timer_presets")
    suspend fun clearAll()

    @Transaction
    suspend fun replaceAll(items: List<TimerPresetEntity>) {
        clearAll()
        if (items.isNotEmpty()) {
            upsertAll(items)
        }
    }
}

@Dao
interface RunningTimerDao {
    @Query("SELECT * FROM running_timers ORDER BY position ASC")
    suspend fun getAll(): List<RunningTimerEntity>

    @Query("SELECT MAX(id) FROM running_timers")
    suspend fun maxId(): Int?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<RunningTimerEntity>)

    @Query("DELETE FROM running_timers")
    suspend fun clearAll()

    @Query("DELETE FROM running_timers WHERE id NOT IN (:ids)")
    suspend fun deleteMissing(ids: List<Int>)

    @Transaction
    suspend fun syncAll(items: List<RunningTimerEntity>) {
        if (items.isEmpty()) {
            clearAll()
        } else {
            upsertAll(items)
            deleteMissing(items.map { it.id })
        }
    }
}

@Dao
interface TimerHistoryDao {
    @Query("SELECT * FROM timer_history ORDER BY endedAtEpochMs DESC")
    fun observeAll(): Flow<List<TimerHistoryEntity>>

    @Query("SELECT * FROM timer_history WHERE sessionId = :sessionId")
    suspend fun findBySessionId(sessionId: Long): TimerHistoryEntity?

    @Query("SELECT MAX(sessionId) FROM timer_history")
    suspend fun maxSessionId(): Long?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: TimerHistoryEntity)

    @Query("DELETE FROM timer_history WHERE sessionId = :sessionId")
    suspend fun deleteBySessionId(sessionId: Long)

    @Query("DELETE FROM timer_history")
    suspend fun deleteAll()
}

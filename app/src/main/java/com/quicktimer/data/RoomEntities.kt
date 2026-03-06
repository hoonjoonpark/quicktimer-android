package com.quicktimer.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "timer_presets")
data class TimerPresetEntity(
    @PrimaryKey val id: Long,
    val durationSeconds: Int,
    val label: String,
    val position: Int
)

@Entity(tableName = "running_timers")
data class RunningTimerEntity(
    @PrimaryKey val id: Int,
    val totalMillis: Long,
    val label: String,
    val remainingMillis: Long,
    val isPaused: Boolean,
    val updatedAtElapsedMs: Long,
    val deferredByDelayMode: Boolean,
    val deferredWakeElapsedMs: Long,
    val sessionId: Long,
    val sessionStartedAtEpochMs: Long,
    val extensionCount: Int,
    val lapsSerialized: String,
    val position: Int
)

@Entity(tableName = "timer_history")
data class TimerHistoryEntity(
    @PrimaryKey val sessionId: Long,
    val label: String,
    val durationSeconds: Int,
    val startedAtEpochMs: Long,
    val endedAtEpochMs: Long,
    val status: String,
    val extensionCount: Int,
    val lapsSerialized: String
)

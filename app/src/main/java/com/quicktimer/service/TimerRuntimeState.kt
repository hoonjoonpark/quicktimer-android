package com.quicktimer.service

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class ActiveTimerState(
    val id: Int,
    val totalMillis: Long,
    val remainingMillis: Long,
    val label: String = "",
    val isPrimary: Boolean = false,
    val isPaused: Boolean = false,
    val updatedAtElapsedMs: Long = 0L,
    val laps: List<String> = emptyList()
)

data class RunningTimerState(
    val totalMillis: Long = 0L,
    val remainingMillis: Long = 0L,
    val isRunning: Boolean = false,
    val isPaused: Boolean = false,
    val laps: List<String> = emptyList(),
    val activeTimers: List<ActiveTimerState> = emptyList(),
    val isAlarmRinging: Boolean = false,
    val elapsedRealtimeMs: Long = 0L,
    val scheduledWakeElapsedMs: Long = -1L,
    val exactAlarmAllowed: Boolean = true
)

object TimerRuntimeState {
    private val _state = MutableStateFlow(RunningTimerState())
    val state: StateFlow<RunningTimerState> = _state.asStateFlow()

    fun update(state: RunningTimerState) {
        _state.value = state
    }
}

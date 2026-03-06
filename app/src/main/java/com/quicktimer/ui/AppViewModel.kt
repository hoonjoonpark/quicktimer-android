package com.quicktimer.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.quicktimer.QuickTimerApplication
import com.quicktimer.data.AppSettings
import com.quicktimer.data.FontSize
import com.quicktimer.data.TimerHistory
import com.quicktimer.data.TimerPreset
import com.quicktimer.service.RunningTimerState
import com.quicktimer.service.TimerRuntimeState
import com.quicktimer.service.TimerServiceController
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class AppUiState(
    val presets: List<TimerPreset> = emptyList(),
    val history: List<TimerHistory> = emptyList(),
    val settings: AppSettings = AppSettings(),
    val runningTimer: RunningTimerState = RunningTimerState(),
    val logs: List<String> = emptyList()
)

class AppViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as QuickTimerApplication

    init {
        viewModelScope.launch {
            app.presetStore.ensureDefaultsIfNeeded()
        }
    }

    val uiState: StateFlow<AppUiState> = combine(
        app.presetStore.presetsFlow,
        app.historyStore.historyFlow,
        app.settingsStore.settingsFlow,
        TimerRuntimeState.state,
        app.logStore.logsFlow
    ) { presets, history, settings, runningTimer, logs ->
        AppUiState(
            presets = presets,
            history = history,
            settings = settings,
            runningTimer = runningTimer,
            logs = logs
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = AppUiState()
    )

    fun ensureService() {
        TimerServiceController.ensureService(getApplication())
    }

    fun startTimer(
        seconds: Int,
        label: String = "",
        source: String = TimerServiceController.START_SOURCE_USER
    ) {
        TimerServiceController.startTimer(getApplication(), seconds, label, source)
    }

    fun pauseTimer(timerId: Int = 0) {
        TimerServiceController.pause(getApplication(), timerId)
    }

    fun resumeTimer(timerId: Int = 0) {
        TimerServiceController.resume(getApplication(), timerId)
    }

    fun stopTimer(timerId: Int = 0) {
        TimerServiceController.stop(getApplication(), timerId)
    }

    fun addLap(timerId: Int = 0) {
        TimerServiceController.lap(getApplication(), timerId)
    }

    fun acknowledgeAlarm() {
        TimerServiceController.acknowledgeAlarm(getApplication())
    }

    fun addPreset(seconds: Int, label: String) {
        viewModelScope.launch {
            app.presetStore.addPreset(seconds, label)
            TimerServiceController.refreshQuickActions(getApplication())
        }
    }

    fun updatePreset(id: Long, seconds: Int, label: String) {
        viewModelScope.launch {
            app.presetStore.updatePreset(id, seconds, label)
            TimerServiceController.refreshQuickActions(getApplication())
        }
    }

    fun deletePreset(id: Long) {
        viewModelScope.launch {
            app.presetStore.removePreset(id)
            TimerServiceController.refreshQuickActions(getApplication())
        }
    }

    fun deleteHistory(sessionId: Long) {
        viewModelScope.launch {
            app.historyStore.deleteHistory(sessionId)
        }
    }

    fun clearAllHistory() {
        viewModelScope.launch {
            app.historyStore.clearAllHistory()
        }
    }

    fun movePreset(fromIndex: Int, toIndex: Int) {
        viewModelScope.launch {
            app.presetStore.movePreset(fromIndex, toIndex)
            TimerServiceController.refreshQuickActions(getApplication())
        }
    }

    fun reorderPresets(orderedIds: List<Long>) {
        viewModelScope.launch {
            app.presetStore.reorderPresets(orderedIds)
            TimerServiceController.refreshQuickActions(getApplication())
        }
    }

    fun setLanguage(tag: String) {
        viewModelScope.launch { app.settingsStore.setLanguageTag(tag) }
    }

    fun setFontSize(fontSize: FontSize) {
        viewModelScope.launch { app.settingsStore.setFontSize(fontSize) }
    }

    fun setAdsRemoved(removed: Boolean) {
        viewModelScope.launch { app.settingsStore.setAdsRemoved(removed) }
    }

    fun setDelayIntervention(enabled: Boolean) {
        viewModelScope.launch { app.settingsStore.setDelayIntervention(enabled) }
    }

    fun logEvent(message: String) {
        app.logStore.append(message)
    }

    fun clearLogs() {
        app.logStore.clear()
    }
}

package com.quicktimer.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.quicktimer.QuickTimerApplication
import com.quicktimer.data.AppThemeMode
import com.quicktimer.data.AppSettings
import com.quicktimer.data.FontSize
import com.quicktimer.data.TimerHistory
import com.quicktimer.data.TimerPreset
import com.quicktimer.service.RunningTimerState
import com.quicktimer.service.TimerRuntimeState
import com.quicktimer.service.TimerServiceController
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AppViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as QuickTimerApplication

    init {
        viewModelScope.launch {
            app.presetStore.ensureDefaultsIfNeeded()
        }
    }

    val presetsState: StateFlow<List<TimerPreset>> = app.presetStore.presetsFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val historyState: StateFlow<List<TimerHistory>> = app.historyStore.historyFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val settingsState: StateFlow<AppSettings> = app.settingsStore.settingsFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = AppSettings()
    )

    val runningTimerState: StateFlow<RunningTimerState> = TimerRuntimeState.state.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = RunningTimerState()
    )

    val logsState: StateFlow<List<String>> = app.logStore.logsFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
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

    fun setThemeMode(themeMode: AppThemeMode) {
        viewModelScope.launch { app.settingsStore.setThemeMode(themeMode) }
    }

    fun setAdsRemoved(removed: Boolean) {
        viewModelScope.launch { app.settingsStore.setAdsRemoved(removed) }
    }

    fun setDelayIntervention(enabled: Boolean) {
        viewModelScope.launch { app.settingsStore.setDelayIntervention(enabled) }
    }

    fun setAlarmSoundEnabled(enabled: Boolean) {
        viewModelScope.launch { app.settingsStore.setAlarmSoundEnabled(enabled) }
    }

    fun setAlarmVibrationEnabled(enabled: Boolean) {
        viewModelScope.launch { app.settingsStore.setAlarmVibrationEnabled(enabled) }
    }

    fun logEvent(message: String) {
        app.logStore.append(message)
    }

    fun clearLogs() {
        app.logStore.clear()
    }
}

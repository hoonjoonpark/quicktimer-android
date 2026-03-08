package com.quicktimer.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore by preferencesDataStore(name = "app_settings")

class SettingsStore(private val context: Context) {
    private val languageKey = stringPreferencesKey("language_tag")
    private val themeModeKey = stringPreferencesKey("theme_mode")
    private val fontKey = stringPreferencesKey("font_size")
    private val adsRemovedKey = booleanPreferencesKey("ads_removed")
    private val delayInterventionKey = booleanPreferencesKey("delay_intervention")
    private val alarmSoundEnabledKey = booleanPreferencesKey("alarm_sound_enabled")
    private val alarmVibrationEnabledKey = booleanPreferencesKey("alarm_vibration_enabled")

    val settingsFlow: Flow<AppSettings> = context.settingsDataStore.data.map { prefs ->
        AppSettings(
            languageTag = prefs[languageKey] ?: "system",
            themeMode = runCatching {
                AppThemeMode.valueOf(prefs[themeModeKey] ?: AppThemeMode.DARK.name)
            }.getOrElse { AppThemeMode.DARK },
            fontSize = runCatching { FontSize.valueOf(prefs[fontKey] ?: FontSize.NORMAL.name) }
                .getOrElse { FontSize.NORMAL },
            adsRemoved = prefs[adsRemovedKey] ?: false,
            delayIntervention = prefs[delayInterventionKey] ?: false,
            alarmSoundEnabled = prefs[alarmSoundEnabledKey] ?: true,
            alarmVibrationEnabled = prefs[alarmVibrationEnabledKey] ?: true
        )
    }

    suspend fun setLanguageTag(tag: String) {
        context.settingsDataStore.edit { it[languageKey] = tag }
    }

    suspend fun setThemeMode(mode: AppThemeMode) {
        context.settingsDataStore.edit { it[themeModeKey] = mode.name }
    }

    suspend fun setFontSize(size: FontSize) {
        context.settingsDataStore.edit { it[fontKey] = size.name }
    }

    suspend fun setAdsRemoved(removed: Boolean) {
        context.settingsDataStore.edit { it[adsRemovedKey] = removed }
    }

    suspend fun setDelayIntervention(enabled: Boolean) {
        context.settingsDataStore.edit { it[delayInterventionKey] = enabled }
    }

    suspend fun setAlarmSoundEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { it[alarmSoundEnabledKey] = enabled }
    }

    suspend fun setAlarmVibrationEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { it[alarmVibrationEnabledKey] = enabled }
    }
}

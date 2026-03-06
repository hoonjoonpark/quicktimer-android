package com.quicktimer.data

import java.util.Locale

data class TimerPreset(
    val id: Long,
    val durationSeconds: Int,
    val label: String = ""
)

data class TimerHistory(
    val sessionId: Long,
    val label: String,
    val durationSeconds: Int,
    val startedAtEpochMs: Long,
    val endedAtEpochMs: Long,
    val status: TimerHistoryStatus,
    val extensionCount: Int,
    val laps: List<String> = emptyList()
)

enum class TimerHistoryStatus {
    COMPLETED,
    STOPPED;

    companion object {
        fun fromStorage(raw: String): TimerHistoryStatus {
            return values().firstOrNull { it.name == raw } ?: COMPLETED
        }
    }
}

enum class FontSize {
    SMALL,
    NORMAL,
    LARGE
}

data class AppSettings(
    val languageTag: String = "system",
    val fontSize: FontSize = FontSize.NORMAL,
    val adsRemoved: Boolean = false,
    val delayIntervention: Boolean = false,
    val alarmSoundEnabled: Boolean = true,
    val alarmVibrationEnabled: Boolean = true
)

fun defaultPresets(): List<TimerPreset> {
    return listOf(
        TimerPreset(id = 1L, durationSeconds = 8 * 60, label = "파스타"),
        TimerPreset(id = 2L, durationSeconds = 10 * 60, label = "계란삶기"),
        TimerPreset(id = 3L, durationSeconds = 15 * 60, label = "낮잠")
    )
}

fun formatDuration(totalSeconds: Int): String {
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format(Locale.getDefault(), "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
    }
}

fun formatDurationMillis(totalMillis: Long): String {
    val clamped = totalMillis.coerceAtLeast(0)
    val totalSeconds = if (clamped == 0L) 0L else (clamped + 999L) / 1000L
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format(Locale.getDefault(), "%02d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
    }
}

fun displayLabel(durationSeconds: Int, label: String): String {
    return if (label.isBlank()) formatDuration(durationSeconds) else label
}

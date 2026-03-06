package com.quicktimer.data

import android.content.Context
import android.util.Base64
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.nio.charset.StandardCharsets
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class LogStore(private val context: Context) {
    private val prefs by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    private val formatter = DateTimeFormatter.ofPattern("MM-dd HH:mm:ss.SSS")
    private val _logs = MutableStateFlow(loadLogs())
    val logsFlow: StateFlow<List<String>> = _logs.asStateFlow()

    @Synchronized
    fun append(message: String) {
        val timestamp = LocalDateTime.now().format(formatter)
        val entry = "$timestamp  $message"
        val updated = ArrayList<String>(MAX_LOGS).apply {
            add(entry)
            addAll(_logs.value.take(MAX_LOGS - 1))
        }
        _logs.value = updated
        prefs.edit().putString(KEY_LOGS, encode(updated)).apply()
    }

    @Synchronized
    fun clear() {
        _logs.value = emptyList()
        prefs.edit().putString(KEY_LOGS, "").apply()
    }

    private fun loadLogs(): List<String> {
        val raw = prefs.getString(KEY_LOGS, null).orEmpty()
        return decode(raw)
    }

    private fun encode(lines: List<String>): String {
        return lines.joinToString(SEPARATOR) { line ->
            Base64.encodeToString(line.toByteArray(StandardCharsets.UTF_8), Base64.NO_WRAP)
        }
    }

    private fun decode(raw: String): List<String> {
        if (raw.isBlank()) return emptyList()
        return raw.split(SEPARATOR)
            .mapNotNull { token ->
                runCatching {
                    val bytes = Base64.decode(token, Base64.DEFAULT)
                    String(bytes, StandardCharsets.UTF_8)
                }.getOrNull()
            }
    }

    companion object {
        private const val PREFS_NAME = "quick_timer_logs"
        private const val KEY_LOGS = "logs_v1"
        private const val SEPARATOR = ","
        private const val MAX_LOGS = 500
    }
}

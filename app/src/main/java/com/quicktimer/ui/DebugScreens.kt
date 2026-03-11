package com.quicktimer.ui

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.quicktimer.R
import com.quicktimer.data.displayLabel
import com.quicktimer.data.formatDurationMillis
import com.quicktimer.service.ActiveTimerState
import com.quicktimer.service.RunningTimerState
import java.io.File

@Composable
fun RuntimeDebugScreen(
    modifier: Modifier,
    runningTimer: RunningTimerState,
    fontScale: Float
) {
    val sortedTimers = sortedByExpectedFinish(runningTimer.activeTimers)
    val scheduledWake = runningTimer.scheduledWakeElapsedMs
    val nowElapsed = runningTimer.elapsedRealtimeMs
    val wakeInMillis = if (scheduledWake > 0L && nowElapsed > 0L) {
        (scheduledWake - nowElapsed).coerceAtLeast(0L)
    } else {
        -1L
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = stringResource(R.string.runtime_debug_alarm_section),
                        fontSize = (18 * fontScale).sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = stringResource(
                            R.string.runtime_debug_exact_alarm,
                            if (runningTimer.exactAlarmAllowed) "true" else "false"
                        ),
                        fontSize = (13 * fontScale).sp
                    )
                    Text(
                        text = stringResource(
                            R.string.runtime_debug_scheduled_elapsed,
                            if (scheduledWake > 0L) scheduledWake.toString() else "-"
                        ),
                        fontSize = (13 * fontScale).sp
                    )
                    Text(
                        text = stringResource(
                            R.string.runtime_debug_wake_in,
                            if (wakeInMillis >= 0L) formatDurationMillis(wakeInMillis) else "-"
                        ),
                        fontSize = (13 * fontScale).sp
                    )
                }
            }
        }

        item {
            Text(
                text = stringResource(R.string.runtime_debug_timers_section),
                fontSize = (18 * fontScale).sp,
                fontWeight = FontWeight.SemiBold
            )
        }

        if (sortedTimers.isEmpty()) {
            item {
                Text(
                    text = stringResource(R.string.no_running_timers),
                    fontSize = (14 * fontScale).sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            items(sortedTimers, key = { "debug-${it.id}" }) { timer ->
                val title = displayLabel((timer.totalMillis / 1000L).toInt(), timer.label)
                val elapsed = (timer.totalMillis - timer.remainingMillis).coerceAtLeast(0L)
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = title,
                            fontSize = (17 * fontScale).sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = stringResource(
                                R.string.runtime_debug_status,
                                if (timer.isPaused) {
                                    stringResource(R.string.runtime_debug_paused)
                                } else {
                                    stringResource(R.string.runtime_debug_running)
                                }
                            ),
                            fontSize = (13 * fontScale).sp
                        )
                        Text(
                            text = stringResource(R.string.runtime_debug_elapsed, formatDurationMillis(elapsed)),
                            fontSize = (13 * fontScale).sp
                        )
                        Text(
                            text = stringResource(
                                R.string.runtime_debug_remaining,
                                formatDurationMillis(timer.remainingMillis)
                            ),
                            fontSize = (13 * fontScale).sp
                        )
                        Text(
                            text = stringResource(
                                R.string.runtime_debug_total,
                                formatDurationMillis(timer.totalMillis)
                            ),
                            fontSize = (13 * fontScale).sp
                        )
                        Text(
                            text = stringResource(
                                R.string.runtime_debug_updated_elapsed,
                                timer.updatedAtElapsedMs
                            ),
                            fontSize = (12 * fontScale).sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun LogsScreen(
    modifier: Modifier,
    logs: List<String>,
    onExport: () -> Unit,
    onClear: () -> Unit,
    fontScale: Float
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
        ) {
            TextButton(onClick = onExport) {
                Text(stringResource(R.string.export_logs), fontSize = (13 * fontScale).sp)
            }
            TextButton(onClick = onClear) {
                Text(stringResource(R.string.clear_logs), fontSize = (13 * fontScale).sp)
            }
        }
        if (logs.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(stringResource(R.string.no_logs), fontSize = (14 * fontScale).sp)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(logs) { line ->
                    val isAlarmManager = line.contains("[ALARM_MANAGER]")
                    val isTickLoop = line.contains("[TICK_LOOP]")
                    val isRefreshLoop = line.contains("[REFRESH_LOOP]")
                    val isPowerState = line.contains("POWER[")
                    val containerColor = when {
                        isAlarmManager -> MaterialTheme.colorScheme.primaryContainer
                        isPowerState -> MaterialTheme.colorScheme.errorContainer
                        isRefreshLoop -> MaterialTheme.colorScheme.tertiaryContainer
                        isTickLoop -> MaterialTheme.colorScheme.tertiaryContainer
                        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                    }
                    val contentColor = when {
                        isAlarmManager -> MaterialTheme.colorScheme.onPrimaryContainer
                        isPowerState -> MaterialTheme.colorScheme.onErrorContainer
                        isRefreshLoop -> MaterialTheme.colorScheme.onTertiaryContainer
                        isTickLoop -> MaterialTheme.colorScheme.onTertiaryContainer
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    }

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = containerColor)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            if (isAlarmManager || isTickLoop || isRefreshLoop || isPowerState) {
                                Text(
                                    text = when {
                                        isAlarmManager -> "ALARM_MANAGER"
                                        isPowerState -> "POWER_STATE"
                                        isRefreshLoop -> "REFRESH_LOOP"
                                        else -> "TICK_LOOP"
                                    },
                                    fontSize = (10 * fontScale).sp,
                                    fontWeight = FontWeight.Bold,
                                    color = contentColor
                                )
                                Text(
                                    text = when {
                                        isAlarmManager -> stringResource(R.string.log_hint_alarm_manager)
                                        isPowerState -> stringResource(R.string.log_hint_power_state)
                                        isRefreshLoop -> stringResource(R.string.log_hint_refresh_loop)
                                        else -> stringResource(R.string.log_hint_tick_loop)
                                    },
                                    fontSize = (11 * fontScale).sp,
                                    color = contentColor
                                )
                            }
                            Text(
                                text = line,
                                fontSize = (12 * fontScale).sp,
                                color = contentColor
                            )
                        }
                    }
                }
            }
        }
    }
}

fun exportLogs(context: Context, logs: List<String>) {
    if (logs.isEmpty()) {
        Toast.makeText(context, context.getString(R.string.no_logs_to_export), Toast.LENGTH_SHORT).show()
        return
    }

    runCatching {
        val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
        val file = File(exportDir, "quick_timer_logs.csv")
        val csvContent = buildString {
            append("timestamp,message\n")
            logs.asReversed().forEach { line ->
                val splitIndex = line.indexOf("  ")
                val timestamp = if (splitIndex >= 0) line.substring(0, splitIndex).trim() else ""
                val message = if (splitIndex >= 0) line.substring(splitIndex + 2).trim() else line
                append(csvEscape(timestamp))
                append(',')
                append(csvEscape(message))
                append('\n')
            }
        }
        file.writeText(csvContent)

        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, context.getString(R.string.logs_title))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(
            Intent.createChooser(shareIntent, context.getString(R.string.export_logs))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }.onFailure {
        Toast.makeText(context, context.getString(R.string.export_failed), Toast.LENGTH_SHORT).show()
    }
}

private fun csvEscape(value: String): String {
    val escaped = value.replace("\"", "\"\"")
    return "\"$escaped\""
}

fun sortedByExpectedFinish(timers: List<ActiveTimerState>): List<ActiveTimerState> {
    return timers.sortedWith(
        compareBy<ActiveTimerState>(
            { it.remainingMillis }
        ).thenBy { it.id }
    )
}

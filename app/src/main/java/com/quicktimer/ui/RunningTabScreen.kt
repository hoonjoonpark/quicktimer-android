package com.quicktimer.ui

import android.os.SystemClock
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.quicktimer.R
import com.quicktimer.data.formatDuration
import com.quicktimer.data.formatDurationMillis
import com.quicktimer.service.ActiveTimerState
import com.quicktimer.service.RunningTimerState
import kotlinx.coroutines.delay
import kotlin.math.max

private fun runningTimerTitle(active: ActiveTimerState): String {
    val durationLabel = formatDuration((active.totalMillis / 1000L).toInt())
    return if (active.label.isBlank()) {
        durationLabel
    } else {
        "${active.label} ($durationLabel)"
    }
}

@Composable
private fun RunningTimerCard(
    active: ActiveTimerState,
    nowElapsedMs: Long,
    stateElapsedRealtimeMs: Long,
    highlighted: Boolean,
    onPause: (Int) -> Unit,
    onResume: (Int) -> Unit,
    onStop: (Int) -> Unit,
    onLap: (Int) -> Unit,
    fontScale: Float
) {
    val titleLabel = runningTimerTitle(active)
    val liveRemainingMillis = if (active.isPaused) {
        active.remainingMillis
    } else {
        val elapsedSinceState = (nowElapsedMs - stateElapsedRealtimeMs).coerceAtLeast(0L)
        (active.remainingMillis - elapsedSinceState).coerceAtLeast(0L)
    }
    val defaultContainerColor = if (MaterialTheme.colorScheme.surface.luminance() > 0.5f) {
        MaterialTheme.colorScheme.surfaceVariant
    } else {
        MaterialTheme.colorScheme.surface
    }
    val containerColor = if (highlighted) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        defaultContainerColor
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                text = titleLabel,
                fontSize = (18 * fontScale).sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = formatDurationMillis(liveRemainingMillis),
                fontSize = (30 * fontScale).sp,
                fontWeight = FontWeight.ExtraBold
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { onLap(active.id) },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                ) {
                    Text(
                        stringResource(R.string.lap),
                        fontSize = (13 * fontScale).sp,
                        maxLines = 1,
                        softWrap = false
                    )
                }
                Button(
                    onClick = { if (active.isPaused) onResume(active.id) else onPause(active.id) },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (active.isPaused) {
                            MaterialTheme.colorScheme.tertiaryContainer
                        } else {
                            MaterialTheme.colorScheme.primaryContainer
                        },
                        contentColor = if (active.isPaused) {
                            MaterialTheme.colorScheme.onTertiaryContainer
                        } else {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        }
                    )
                ) {
                    Text(
                        if (active.isPaused) stringResource(R.string.resume) else stringResource(R.string.pause),
                        fontSize = (13 * fontScale).sp,
                        maxLines = 1,
                        softWrap = false
                    )
                }
                Button(
                    onClick = { onStop(active.id) },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                    )
                ) {
                    Text(
                        stringResource(R.string.stop),
                        fontSize = (13 * fontScale).sp,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }
            if (active.laps.isNotEmpty()) {
                Text(
                    text = active.laps.joinToString("   "),
                    fontSize = (13 * fontScale).sp,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
fun RunningTab(
    modifier: Modifier,
    runningTimer: RunningTimerState,
    highlightTimerId: Int?,
    highlightRequestId: Long?,
    onHighlightConsumed: () -> Unit,
    onPause: (Int) -> Unit,
    onResume: (Int) -> Unit,
    onStop: (Int) -> Unit,
    onLap: (Int) -> Unit,
    fontScale: Float
) {
    val listState = rememberLazyListState()
    var flashingTimerId by remember { mutableStateOf<Int?>(null) }
    var nowElapsedMs by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    val sortedActiveTimers = sortedByExpectedFinish(runningTimer.activeTimers)
    val runningIds = sortedActiveTimers.map { it.id }

    LaunchedEffect(runningTimer.elapsedRealtimeMs) {
        if (runningTimer.elapsedRealtimeMs > 0L) {
            nowElapsedMs = max(nowElapsedMs, runningTimer.elapsedRealtimeMs)
        }
    }

    LaunchedEffect(sortedActiveTimers.any { !it.isPaused }) {
        if (!sortedActiveTimers.any { !it.isPaused }) return@LaunchedEffect
        while (sortedActiveTimers.any { !it.isPaused }) {
            nowElapsedMs = max(nowElapsedMs, SystemClock.elapsedRealtime())
            delay(200)
        }
    }

    LaunchedEffect(highlightRequestId, runningIds) {
        val requestId = highlightRequestId ?: return@LaunchedEffect
        val targetTimerId = highlightTimerId ?: return@LaunchedEffect
        if (requestId <= 0L) return@LaunchedEffect
        val index = runningIds.indexOf(targetTimerId)
        if (index >= 0) {
            listState.animateScrollToItem(index)
            flashingTimerId = targetTimerId
            onHighlightConsumed()
        }
    }

    LaunchedEffect(flashingTimerId) {
        if (flashingTimerId == null) return@LaunchedEffect
        delay(2500)
        flashingTimerId = null
    }

    if (sortedActiveTimers.isEmpty()) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
            contentAlignment = Alignment.Center
        ) {
            Text(stringResource(R.string.no_running_timers), fontSize = (16 * fontScale).sp)
        }
        return
    }

    LazyColumn(
        state = listState,
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(start = 16.dp, end = 16.dp, top = 0.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(top = 12.dp, bottom = 16.dp)
    ) {
        itemsIndexed(sortedActiveTimers, key = { _, timer -> "running-${timer.id}" }) { _, active ->
            RunningTimerCard(
                active = active,
                nowElapsedMs = nowElapsedMs,
                stateElapsedRealtimeMs = runningTimer.elapsedRealtimeMs,
                highlighted = active.id == flashingTimerId,
                onPause = onPause,
                onResume = onResume,
                onStop = onStop,
                onLap = onLap,
                fontScale = fontScale
            )
        }
    }
}

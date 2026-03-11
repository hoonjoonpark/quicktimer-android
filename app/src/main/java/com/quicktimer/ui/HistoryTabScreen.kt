package com.quicktimer.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.quicktimer.R
import com.quicktimer.data.TimerHistory
import com.quicktimer.data.TimerHistoryStatus
import com.quicktimer.data.formatDuration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun HistoryTab(
    modifier: Modifier,
    history: List<TimerHistory>,
    onDelete: (Long) -> Unit,
    fontScale: Float
) {
    if (history.isEmpty()) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
            contentAlignment = Alignment.Center
        ) {
            Text(stringResource(R.string.no_history), fontSize = (16 * fontScale).sp)
        }
        return
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(start = 16.dp, end = 16.dp, top = 0.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(top = 12.dp, bottom = 16.dp)
    ) {
        itemsIndexed(history, key = { _, item -> "history-${item.sessionId}" }) { _, item ->
            HistoryRow(
                item = item,
                onDelete = onDelete,
                fontScale = fontScale
            )
        }
    }
}

@Composable
private fun HistoryRow(
    item: TimerHistory,
    onDelete: (Long) -> Unit,
    fontScale: Float
) {
    var horizontalOffsetPx by remember(item.sessionId) { mutableFloatStateOf(0f) }
    val revealWidthPx = with(LocalDensity.current) { 72.dp.toPx() }
    val animatedOffset by animateFloatAsState(targetValue = horizontalOffsetPx, label = "historySwipeOffset")
    val revealProgress = (-animatedOffset / revealWidthPx).coerceIn(0f, 1f)
    val rowContainerColor = if (MaterialTheme.colorScheme.surface.luminance() > 0.5f) {
        MaterialTheme.colorScheme.surfaceVariant
    } else {
        MaterialTheme.colorScheme.surface
    }

    Box(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = revealProgress),
                    shape = RoundedCornerShape(12.dp)
                ),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = {
                    horizontalOffsetPx = 0f
                    onDelete(item.sessionId)
                },
                modifier = Modifier.padding(end = 8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = stringResource(R.string.delete),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = revealProgress)
                )
            }
        }

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer {
                    translationX = animatedOffset
                }
                .pointerInput(item.sessionId) {
                    detectHorizontalDragGestures(
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            horizontalOffsetPx = (horizontalOffsetPx + dragAmount).coerceIn(-revealWidthPx, 0f)
                        },
                        onDragEnd = {
                            horizontalOffsetPx = if (horizontalOffsetPx < -revealWidthPx / 2f) {
                                -revealWidthPx
                            } else {
                                0f
                            }
                        }
                    )
                },
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(
                containerColor = rowContainerColor
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                val title = if (item.label.isBlank()) {
                    formatDuration(item.durationSeconds)
                } else {
                    item.label
                }
                val statusText = when (item.status) {
                    TimerHistoryStatus.COMPLETED -> stringResource(R.string.history_status_completed)
                    TimerHistoryStatus.STOPPED -> stringResource(R.string.history_status_stopped)
                }
                val startedLabel = stringResource(R.string.history_started_label)
                val endedLabel = stringResource(R.string.history_ended_label)
                val lapsLabel = stringResource(R.string.history_laps)
                val mutedText = MaterialTheme.colorScheme.onSurfaceVariant
                val (statusBadgeContainer, statusBadgeContent) = when (item.status) {
                    TimerHistoryStatus.COMPLETED -> {
                        MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
                    }
                    TimerHistoryStatus.STOPPED -> {
                        MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
                    }
                }
                val extensionBadgeContainer = MaterialTheme.colorScheme.tertiaryContainer
                val extensionBadgeContent = MaterialTheme.colorScheme.onTertiaryContainer

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = title,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontSize = (18 * fontScale).sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = stringResource(R.string.history_extensions, item.extensionCount),
                        fontSize = (12 * fontScale).sp,
                        color = extensionBadgeContent,
                        modifier = Modifier
                            .background(
                                color = extensionBadgeContainer,
                                shape = RoundedCornerShape(999.dp)
                            )
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                    Text(
                        text = statusText,
                        fontSize = (12 * fontScale).sp,
                        color = statusBadgeContent,
                        modifier = Modifier
                            .background(
                                color = statusBadgeContainer,
                                shape = RoundedCornerShape(999.dp)
                            )
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = formatDuration(item.durationSeconds),
                        fontSize = (18 * fontScale).sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Medium
                    )
                }

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(1.dp)
                ) {
                    Text(
                        text = "$startedLabel · ${formatHistoryTimestamp(item.startedAtEpochMs)}",
                        fontSize = (14 * fontScale).sp,
                        color = mutedText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "$endedLabel · ${formatHistoryTimestamp(item.endedAtEpochMs)}",
                        fontSize = (14 * fontScale).sp,
                        color = mutedText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (item.laps.isNotEmpty()) {
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
                        thickness = 1.dp
                    )
                    Text(
                        text = buildAnnotatedString {
                            withStyle(SpanStyle(fontWeight = FontWeight.SemiBold, color = mutedText)) {
                                append("$lapsLabel ")
                            }
                            append(item.laps.joinToString(" · "))
                        },
                        fontSize = (13 * fontScale).sp,
                        color = mutedText,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

private fun formatHistoryTimestamp(epochMs: Long): String {
    val locale = Locale.getDefault()
    val formatter = when (locale.language) {
        "ko" -> DateTimeFormatter.ofPattern("yyyy년 M월 d일 a h:mm:ss", locale)
        "ja" -> DateTimeFormatter.ofPattern("yyyy年M月d日 H:mm:ss", locale)
        else -> DateTimeFormatter.ofPattern("MMM d, yyyy 'at' h:mm:ss a", locale)
    }
    return Instant.ofEpochMilli(epochMs)
        .atZone(ZoneId.systemDefault())
        .toLocalDateTime()
        .format(formatter)
}

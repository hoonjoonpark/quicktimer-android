package com.quicktimer.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.quicktimer.R
import com.quicktimer.data.TimerPreset
import com.quicktimer.data.formatDuration
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

@Composable
fun TimerTab(
    modifier: Modifier,
    presets: List<TimerPreset>,
    reorderMode: Boolean,
    onReorderModeChange: (Boolean) -> Unit,
    onStart: (TimerPreset) -> Unit,
    onDelete: (Long) -> Unit,
    onEdit: (TimerPreset) -> Unit,
    onReorderPresets: (List<Long>) -> Unit,
    onAddTimerClick: () -> Unit,
    fontScale: Float
) {
    val context = LocalContext.current
    val listState = rememberLazyListState()
    val localPresets = remember { mutableStateListOf<TimerPreset>().apply { addAll(presets) } }
    val selectedIds = remember { mutableStateListOf<Long>() }
    var previousPresetIds by remember { mutableStateOf(presets.map { it.id }) }
    var pendingScrollTargetId by remember { mutableStateOf<Long?>(null) }

    val reorderableState = rememberReorderableLazyListState(listState) { from, to ->
        if (!reorderMode) return@rememberReorderableLazyListState
        val fromIndex = from.index
        val toIndex = to.index
        if (fromIndex !in localPresets.indices || toIndex !in localPresets.indices) return@rememberReorderableLazyListState
        localPresets.swap(fromIndex, toIndex)
        onReorderPresets(localPresets.map { it.id })
    }

    LaunchedEffect(reorderMode) {
        if (!reorderMode) selectedIds.clear()
    }

    LaunchedEffect(presets, reorderMode) {
        val incomingIds = presets.map { it.id }
        val previousIdSet = previousPresetIds.toSet()
        val addedPresetId = if (incomingIds.size > previousPresetIds.size) {
            incomingIds.firstOrNull { it !in previousIdSet }
        } else {
            null
        }

        val sameContent = localPresets.size == presets.size &&
            localPresets.zip(presets).all { (local, incoming) -> local == incoming }
        if (!sameContent) {
            localPresets.clear()
            localPresets.addAll(presets)
        }

        if (!reorderMode && addedPresetId != null) {
            pendingScrollTargetId = addedPresetId
        }
        previousPresetIds = incomingIds
    }

    LaunchedEffect(pendingScrollTargetId, localPresets.size) {
        val targetId = pendingScrollTargetId ?: return@LaunchedEffect
        val index = localPresets.indexOfFirst { it.id == targetId }
        if (index >= 0) {
            listState.animateScrollToItem(index)
            pendingScrollTargetId = null
        }
    }

    if (localPresets.isEmpty()) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(horizontal = 16.dp, vertical = 24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(stringResource(R.string.no_timers), fontSize = (16 * fontScale).sp)
                Button(
                    onClick = onAddTimerClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                ) {
                    Text(stringResource(R.string.add_timer), fontSize = (16 * fontScale).sp)
                }
            }
        }
        return
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(start = 16.dp, end = 16.dp, top = 0.dp)
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(top = 12.dp, bottom = 16.dp)
        ) {
            itemsIndexed(localPresets, key = { _, item -> item.id }) { _, timer ->
                ReorderableItem(reorderableState, key = timer.id) { isDragging ->
                    Box(modifier = Modifier.padding(vertical = 4.dp)) {
                        TimerRow(
                            timer = timer,
                            reorderMode = reorderMode,
                            selected = selectedIds.contains(timer.id),
                            isDragging = isDragging,
                            onStart = onStart,
                            onDelete = onDelete,
                            onEdit = onEdit,
                            onToggleSelected = { id ->
                                if (selectedIds.contains(id)) selectedIds.remove(id) else selectedIds.add(id)
                            },
                            onLongPressEnterEditMode = { id ->
                                if (!reorderMode) onReorderModeChange(true)
                                if (!selectedIds.contains(id)) selectedIds.add(id)
                            },
                            handleModifier = if (reorderMode) Modifier.draggableHandle() else Modifier,
                            fontScale = fontScale
                        )
                    }
                }
            }

            item {
                Button(
                    onClick = onAddTimerClick,
                    enabled = !reorderMode,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .padding(top = 4.dp)
                ) {
                    Text(stringResource(R.string.add_timer), fontSize = (16 * fontScale).sp)
                }
            }
        }

        if (reorderMode) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = {
                        android.widget.Toast.makeText(context, "준비 중", android.widget.Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("그룹", fontSize = (14 * fontScale).sp)
                }
                Button(
                    onClick = {
                        android.widget.Toast.makeText(context, "준비 중", android.widget.Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("이동", fontSize = (14 * fontScale).sp)
                }
                Button(
                    onClick = {
                        if (selectedIds.isNotEmpty()) {
                            selectedIds.toList().forEach(onDelete)
                            selectedIds.clear()
                        }
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("삭제", fontSize = (14 * fontScale).sp)
                }
            }
        }
    }
}

@Composable
private fun TimerRow(
    timer: TimerPreset,
    reorderMode: Boolean,
    selected: Boolean,
    isDragging: Boolean,
    onStart: (TimerPreset) -> Unit,
    onDelete: (Long) -> Unit,
    onEdit: (TimerPreset) -> Unit,
    onToggleSelected: (Long) -> Unit,
    onLongPressEnterEditMode: (Long) -> Unit,
    handleModifier: Modifier,
    fontScale: Float
) {
    val actionAreaHeight = 40.dp
    var horizontalOffsetPx by remember(timer.id) { mutableFloatStateOf(0f) }
    val revealWidthPx = with(LocalDensity.current) { 120.dp.toPx() }
    val animatedOffset by animateFloatAsState(targetValue = horizontalOffsetPx, label = "swipeOffset")
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
            IconButton(onClick = {
                horizontalOffsetPx = 0f
                onEdit(timer)
            }) {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = stringResource(R.string.edit),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = revealProgress)
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            IconButton(
                onClick = {
                    horizontalOffsetPx = 0f
                    onDelete(timer.id)
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
                    if (isDragging) {
                        alpha = 0.99f
                        scaleX = 1.008f
                        scaleY = 1.008f
                        shadowElevation = 0f
                        clip = false
                    }
                }
                .pointerInput(timer.id, reorderMode) {
                    detectTapGestures(
                        onTap = {
                            if (reorderMode) {
                                onToggleSelected(timer.id)
                            }
                        },
                        onLongPress = {
                            if (!reorderMode) {
                                horizontalOffsetPx = 0f
                                onLongPressEnterEditMode(timer.id)
                            }
                        }
                    )
                }
                .pointerInput(timer.id, reorderMode, isDragging) {
                    detectHorizontalDragGestures(
                        onHorizontalDrag = { change, dragAmount ->
                            if (reorderMode || isDragging) return@detectHorizontalDragGestures
                            change.consume()
                            horizontalOffsetPx = (horizontalOffsetPx + dragAmount).coerceIn(-revealWidthPx, 0f)
                        },
                        onDragEnd = {
                            if (reorderMode || isDragging) return@detectHorizontalDragGestures
                            horizontalOffsetPx = if (horizontalOffsetPx < -revealWidthPx / 2f) -revealWidthPx else 0f
                        }
                    )
                },
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = rowContainerColor)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (reorderMode) {
                    Box(modifier = Modifier.padding(end = 6.dp)) {
                        Box(
                            modifier = Modifier
                                .size(26.dp)
                                .border(
                                    width = 1.5.dp,
                                    color = if (selected) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.outline
                                    },
                                    shape = CircleShape
                                )
                                .background(
                                    color = if (selected) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.surface
                                    },
                                    shape = CircleShape
                                )
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null
                                ) { onToggleSelected(timer.id) },
                            contentAlignment = Alignment.Center
                        ) {
                            if (selected) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    val hasLabel = timer.label.isNotBlank()
                    Text(
                        text = if (timer.label.isBlank()) formatDuration(timer.durationSeconds) else timer.label,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontSize = ((if (hasLabel) 22 else 28) * fontScale).sp,
                        fontWeight = FontWeight.Bold
                    )
                    if (hasLabel) {
                        Text(
                            text = formatDuration(timer.durationSeconds),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            fontSize = (18 * fontScale).sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
                if (reorderMode) {
                    Box(
                        modifier = handleModifier
                            .width(28.dp)
                            .height(actionAreaHeight),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "↕",
                            fontSize = (20 * fontScale).sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    Button(onClick = { onStart(timer) }) {
                        Text(stringResource(R.string.start), fontSize = (14 * fontScale).sp)
                    }
                }
            }
        }
    }
}

private fun MutableList<TimerPreset>.swap(from: Int, to: Int) {
    if (from == to) return
    val item = removeAt(from)
    add(to, item)
}

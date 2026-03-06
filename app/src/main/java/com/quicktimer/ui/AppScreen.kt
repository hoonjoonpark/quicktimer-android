package com.quicktimer.ui

import android.content.Intent
import android.view.ViewGroup
import android.widget.NumberPicker
import android.widget.Toast
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.quicktimer.R
import com.quicktimer.data.FontSize
import com.quicktimer.data.TimerHistory
import com.quicktimer.data.TimerHistoryStatus
import com.quicktimer.data.TimerPreset
import com.quicktimer.data.displayLabel
import com.quicktimer.data.formatDuration
import com.quicktimer.data.formatDurationMillis
import com.quicktimer.service.RunningTimerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.text.style.TextOverflow
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.delay
private enum class MainTab { TIMERS, RUNNING, HISTORY, SETTINGS }

data class RunningTabNavigationRequest(
    val requestId: Long,
    val targetTimerId: Int
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickTimerApp(
    viewModel: AppViewModel,
    runningTabNavigationRequest: RunningTabNavigationRequest? = null,
    onRunningTabNavigationConsumed: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var tab by remember { mutableStateOf(MainTab.TIMERS) }
    var showLogs by remember { mutableStateOf(false) }
    var showAddDialog by remember { mutableStateOf(false) }
    var editingPreset by remember { mutableStateOf<TimerPreset?>(null) }
    var showClearHistoryDialog by remember { mutableStateOf(false) }
    var highlightTimerId by remember { mutableStateOf<Int?>(null) }
    var highlightRequestId by remember { mutableStateOf<Long?>(null) }
    val runningCount = uiState.runningTimer.activeTimers.size
    val isAlarmRinging = uiState.runningTimer.isAlarmRinging

    LaunchedEffect(runningTabNavigationRequest?.requestId) {
        val request = runningTabNavigationRequest ?: return@LaunchedEffect
        tab = MainTab.RUNNING
        highlightTimerId = request.targetTimerId.takeIf { it > 0 }
        highlightRequestId = request.requestId
        onRunningTabNavigationConsumed()
    }

    val fontScale = when (uiState.settings.fontSize) {
        FontSize.SMALL -> 0.9f
        FontSize.NORMAL -> 1f
        FontSize.LARGE -> 1.15f
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (showLogs) stringResource(R.string.logs_title) else stringResource(R.string.app_name),
                        fontSize = (20 * fontScale).sp
                    )
                },
                navigationIcon = {
                    if (showLogs) {
                        IconButton(onClick = { showLogs = false }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.back)
                            )
                        }
                    }
                },
                actions = {
                    if (!showLogs) {
                        when (tab) {
                            MainTab.TIMERS -> {
                                IconButton(onClick = { showAddDialog = true }) {
                                    Icon(
                                        imageVector = Icons.Default.Add,
                                        contentDescription = stringResource(R.string.add_timer)
                                    )
                                }
                            }
                            MainTab.HISTORY -> {
                                IconButton(
                                    onClick = { showClearHistoryDialog = true },
                                    enabled = uiState.history.isNotEmpty()
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = stringResource(R.string.history_clear_all)
                                    )
                                }
                            }
                            else -> Unit
                        }
                    }
                }
            )
        },
        bottomBar = {
            if (!showLogs) {
                Column {
                    if (isAlarmRinging) {
                        Button(
                            onClick = viewModel::acknowledgeAlarm,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            Text(stringResource(R.string.stop_alarm), fontSize = (15 * fontScale).sp)
                        }
                    }
                    if (!uiState.settings.adsRemoved) {
                        AdBanner()
                    }
                    NavigationBar {
                        NavigationBarItem(
                            selected = tab == MainTab.TIMERS,
                            onClick = { tab = MainTab.TIMERS },
                            icon = {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_tab_timer),
                                    contentDescription = stringResource(R.string.tab_timers),
                                    modifier = Modifier.height(24.dp)
                                )
                            },
                            label = { Text(stringResource(R.string.tab_timers), fontSize = (12 * fontScale).sp) }
                        )
                        NavigationBarItem(
                            selected = tab == MainTab.RUNNING,
                            onClick = { tab = MainTab.RUNNING },
                            icon = {
                                BadgedBox(
                                    badge = {
                                        if (runningCount > 0) {
                                            Badge(modifier = Modifier.offset(x = 4.dp)) {
                                                Text(if (runningCount > 99) "99+" else runningCount.toString())
                                            }
                                        }
                                    }
                                ) {
                                    Icon(
                                        painter = painterResource(id = R.drawable.ic_tab_running),
                                        contentDescription = stringResource(R.string.tab_running),
                                        modifier = Modifier.height(24.dp)
                                    )
                                }
                            },
                            label = { Text(stringResource(R.string.tab_running), fontSize = (12 * fontScale).sp) }
                        )
                        NavigationBarItem(
                            selected = tab == MainTab.HISTORY,
                            onClick = { tab = MainTab.HISTORY },
                            icon = {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_tab_history),
                                    contentDescription = stringResource(R.string.tab_history),
                                    modifier = Modifier.height(22.dp)
                                )
                            },
                            label = { Text(stringResource(R.string.tab_history), fontSize = (12 * fontScale).sp) }
                        )
                        NavigationBarItem(
                            selected = tab == MainTab.SETTINGS,
                            onClick = { tab = MainTab.SETTINGS },
                            icon = {
                                Icon(
                                    imageVector = Icons.Filled.Settings,
                                    contentDescription = stringResource(R.string.tab_settings)
                                )
                            },
                            label = { Text(stringResource(R.string.tab_settings), fontSize = (12 * fontScale).sp) }
                        )
                    }
                }
            }
        }
    ) { padding ->
        if (showLogs) {
            LogsScreen(
                modifier = Modifier.padding(padding),
                logs = uiState.logs,
                onExport = { exportLogs(context, uiState.logs) },
                onClear = viewModel::clearLogs,
                fontScale = fontScale
            )
        } else {
            when (tab) {
                MainTab.TIMERS -> TimerTab(
                    modifier = Modifier.padding(padding),
                    presets = uiState.presets,
                    onStart = { preset ->
                        viewModel.startTimer(preset.durationSeconds, preset.label)
                        val timerText = displayLabel(preset.durationSeconds, preset.label)
                        Toast.makeText(
                            context,
                            context.getString(R.string.starting_timer_toast, timerText),
                            Toast.LENGTH_SHORT
                        ).show()
                    },
                    onDelete = viewModel::deletePreset,
                    onEdit = { preset -> editingPreset = preset },
                    onReorderPresets = viewModel::reorderPresets,
                    onAddTimerClick = { showAddDialog = true },
                    fontScale = fontScale
                )

                MainTab.RUNNING -> RunningTab(
                    modifier = Modifier.padding(padding),
                    runningTimer = uiState.runningTimer,
                    highlightTimerId = highlightTimerId,
                    highlightRequestId = highlightRequestId,
                    onPause = viewModel::pauseTimer,
                    onResume = viewModel::resumeTimer,
                    onStop = viewModel::stopTimer,
                    onLap = viewModel::addLap,
                    fontScale = fontScale
                )

                MainTab.HISTORY -> HistoryTab(
                    modifier = Modifier.padding(padding),
                    history = uiState.history,
                    onDelete = viewModel::deleteHistory,
                    fontScale = fontScale
                )

                MainTab.SETTINGS -> SettingsTab(
                    modifier = Modifier.padding(padding),
                    state = uiState,
                    onLanguage = viewModel::setLanguage,
                    onFontSize = viewModel::setFontSize,
                    onAdsRemoved = viewModel::setAdsRemoved,
                    onDelayIntervention = viewModel::setDelayIntervention,
                    onAlarmSoundEnabled = viewModel::setAlarmSoundEnabled,
                    onAlarmVibrationEnabled = viewModel::setAlarmVibrationEnabled,
                    onOpenLogs = { showLogs = true },
                    fontScale = fontScale
                )
            }
        }
    }

    if (showAddDialog || editingPreset != null) {
        val target = editingPreset
        AddTimerBottomSheet(
            title = if (target == null) stringResource(R.string.add_timer) else stringResource(R.string.edit_timer),
            initialDurationSeconds = target?.durationSeconds ?: 10 * 60,
            initialLabel = target?.label.orEmpty(),
            onDismiss = {
                showAddDialog = false
                editingPreset = null
            },
            onSave = { seconds, label ->
                if (target == null) {
                    viewModel.addPreset(seconds, label)
                } else {
                    viewModel.updatePreset(target.id, seconds, label)
                }
                showAddDialog = false
                editingPreset = null
            }
        )
    }

    if (showClearHistoryDialog) {
        AlertDialog(
            onDismissRequest = { showClearHistoryDialog = false },
            title = { Text(stringResource(R.string.history_clear_all_title)) },
            text = { Text(stringResource(R.string.history_clear_all_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.clearAllHistory()
                        showClearHistoryDialog = false
                    }
                ) {
                    Text(stringResource(R.string.history_clear_all_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearHistoryDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

@Composable
private fun TimerTab(
    modifier: Modifier,
    presets: List<TimerPreset>,
    onStart: (TimerPreset) -> Unit,
    onDelete: (Long) -> Unit,
    onEdit: (TimerPreset) -> Unit,
    onReorderPresets: (List<Long>) -> Unit,
    onAddTimerClick: () -> Unit,
    fontScale: Float
) {
    val localPresets = remember {
        mutableStateListOf<TimerPreset>().apply { addAll(presets) }
    }
    var draggingId by remember { mutableStateOf<Long?>(null) }
    var draggingOffsetY by remember { mutableFloatStateOf(0f) }
    var itemHeightPx by remember { mutableFloatStateOf(0f) }
    var pendingReorderCommit by remember { mutableStateOf(false) }

    LaunchedEffect(presets, draggingId, pendingReorderCommit) {
        val localIds = localPresets.map { it.id }
        val incomingIds = presets.map { it.id }

        if (pendingReorderCommit) {
            // Ignore transient stale emissions while DataStore commit is in flight.
            if (incomingIds == localIds) {
                pendingReorderCommit = false
            } else {
                return@LaunchedEffect
            }
        }

        if (draggingId == null) {
            val sameContent = localPresets.size == presets.size &&
                localPresets.zip(presets).all { (local, incoming) -> local == incoming }
            if (!sameContent) {
                localPresets.clear()
                localPresets.addAll(presets)
            }
        }
    }

    if (localPresets.isEmpty()) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface)
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

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .padding(start = 16.dp, end = 16.dp, top = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 16.dp)
    ) {
        itemsIndexed(localPresets, key = { _, item -> item.id }) { index, timer ->
            TimerRow(
                timer = timer,
                isDragging = draggingId == timer.id,
                draggingOffsetY = if (draggingId == timer.id) draggingOffsetY else 0f,
                onStart = onStart,
                onDelete = onDelete,
                onEdit = onEdit,
                onMeasured = { measured -> itemHeightPx = measured.toFloat() },
                onDragStart = {
                    draggingId = timer.id
                    draggingOffsetY = 0f
                },
                onDrag = { dragY ->
                    if (itemHeightPx <= 0f) return@TimerRow
                    draggingOffsetY += dragY

                    while (draggingOffsetY > itemHeightPx / 2f) {
                        val id = draggingId ?: break
                        val i = localPresets.indexOfFirst { it.id == id }
                        if (i < 0) break
                        if (i >= localPresets.lastIndex) break
                        localPresets.swap(i, i + 1)
                        draggingOffsetY -= itemHeightPx
                    }

                    while (draggingOffsetY < -itemHeightPx / 2f) {
                        val id = draggingId ?: break
                        val i = localPresets.indexOfFirst { it.id == id }
                        if (i < 0) break
                        if (i <= 0) break
                        localPresets.swap(i, i - 1)
                        draggingOffsetY += itemHeightPx
                    }
                },
                onDragEnd = {
                    draggingId = null
                    draggingOffsetY = 0f
                    pendingReorderCommit = true
                    onReorderPresets(localPresets.map { it.id })
                },
                fontScale = fontScale
            )
        }

        item {
            Button(
                onClick = onAddTimerClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .padding(top = 4.dp)
            ) {
                Text(stringResource(R.string.add_timer), fontSize = (16 * fontScale).sp)
            }
        }
    }
}

@Composable
private fun RunningTab(
    modifier: Modifier,
    runningTimer: RunningTimerState,
    highlightTimerId: Int?,
    highlightRequestId: Long?,
    onPause: (Int) -> Unit,
    onResume: (Int) -> Unit,
    onStop: (Int) -> Unit,
    onLap: (Int) -> Unit,
    fontScale: Float
) {
    val listState = rememberLazyListState()
    var flashingTimerId by remember { mutableStateOf<Int?>(null) }
    val runningIds = runningTimer.activeTimers.map { it.id }

    LaunchedEffect(highlightRequestId, runningIds) {
        val requestId = highlightRequestId ?: return@LaunchedEffect
        val targetTimerId = highlightTimerId ?: return@LaunchedEffect
        if (requestId <= 0L) return@LaunchedEffect
        val index = runningIds.indexOf(targetTimerId)
        if (index >= 0) {
            listState.animateScrollToItem(index)
            flashingTimerId = targetTimerId
        }
    }

    LaunchedEffect(flashingTimerId) {
        if (flashingTimerId == null) return@LaunchedEffect
        delay(2500L)
        flashingTimerId = null
    }

    if (runningTimer.activeTimers.isEmpty()) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface),
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
            .background(MaterialTheme.colorScheme.surface)
            .padding(start = 16.dp, end = 16.dp, top = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 16.dp)
    ) {
        itemsIndexed(runningTimer.activeTimers, key = { _, timer -> "running-${timer.id}" }) { _, active ->
            val isHighlighted = active.id == flashingTimerId
            val cardModifier = Modifier.fillMaxWidth()
            if (isHighlighted) {
                Card(
                    modifier = cardModifier,
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        val titleLabel = displayLabel((active.totalMillis / 1000L).toInt(), active.label)
                        Text(
                            text = titleLabel,
                            fontSize = (18 * fontScale).sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = formatDurationMillis(active.remainingMillis),
                            fontSize = (30 * fontScale).sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { onLap(active.id) }) {
                                Text(stringResource(R.string.lap), fontSize = (14 * fontScale).sp)
                            }
                            Button(onClick = { if (active.isPaused) onResume(active.id) else onPause(active.id) }) {
                                Text(
                                    if (active.isPaused) stringResource(R.string.resume) else stringResource(R.string.pause),
                                    fontSize = (14 * fontScale).sp
                                )
                            }
                            Button(onClick = { onStop(active.id) }) {
                                Text(stringResource(R.string.stop), fontSize = (14 * fontScale).sp)
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
            } else {
                Card(
                    modifier = cardModifier
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        val titleLabel = displayLabel((active.totalMillis / 1000L).toInt(), active.label)
                        Text(
                            text = titleLabel,
                            fontSize = (18 * fontScale).sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = formatDurationMillis(active.remainingMillis),
                            fontSize = (30 * fontScale).sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { onLap(active.id) }) {
                                Text(stringResource(R.string.lap), fontSize = (14 * fontScale).sp)
                            }
                            Button(onClick = { if (active.isPaused) onResume(active.id) else onPause(active.id) }) {
                                Text(
                                    if (active.isPaused) stringResource(R.string.resume) else stringResource(R.string.pause),
                                    fontSize = (14 * fontScale).sp
                                )
                            }
                            Button(onClick = { onStop(active.id) }) {
                                Text(stringResource(R.string.stop), fontSize = (14 * fontScale).sp)
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
        }
    }
}

@Composable
private fun HistoryTab(
    modifier: Modifier,
    history: List<TimerHistory>,
    onDelete: (Long) -> Unit,
    fontScale: Float
) {
    if (history.isEmpty()) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface),
            contentAlignment = Alignment.Center
        ) {
            Text(stringResource(R.string.no_history), fontSize = (16 * fontScale).sp)
        }
        return
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .padding(start = 16.dp, end = 16.dp, top = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 16.dp)
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

    Box(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .matchParentSize()
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
                }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
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
                val metaTextColor = MaterialTheme.colorScheme.onSurface
                val metaLabelColor = MaterialTheme.colorScheme.primary
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = title,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            fontSize = (18 * fontScale).sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = formatDuration(item.durationSeconds),
                            fontSize = (14 * fontScale).sp,
                            color = metaTextColor
                        )
                        Column(
                            verticalArrangement = Arrangement.spacedBy(1.dp)
                        ) {
                            Text(
                                text = buildAnnotatedString {
                                    withStyle(
                                        SpanStyle(
                                            fontWeight = FontWeight.Bold,
                                            color = metaLabelColor
                                        )
                                    ) {
                                        append(startedLabel)
                                    }
                                    append(": ")
                                    append(formatHistoryTimestamp(item.startedAtEpochMs))
                                },
                                fontSize = (12 * fontScale).sp,
                                color = metaTextColor,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = buildAnnotatedString {
                                    withStyle(
                                        SpanStyle(
                                            fontWeight = FontWeight.Bold,
                                            color = metaLabelColor
                                        )
                                    ) {
                                        append(endedLabel)
                                    }
                                    append(": ")
                                    append(formatHistoryTimestamp(item.endedAtEpochMs))
                                },
                                fontSize = (12 * fontScale).sp,
                                color = metaTextColor,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    Column(
                        horizontalAlignment = Alignment.End,
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.history_status, statusText),
                            fontSize = (12 * fontScale).sp,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1
                        )
                        Text(
                            text = stringResource(R.string.history_extensions, item.extensionCount),
                            fontSize = (12 * fontScale).sp,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1
                        )
                    }
                }
                if (item.laps.isNotEmpty()) {
                    Text(
                        text = buildAnnotatedString {
                            withStyle(
                                SpanStyle(
                                    fontWeight = FontWeight.Bold,
                                    color = metaLabelColor
                                )
                            ) {
                                append(lapsLabel)
                            }
                            append(": ")
                            append(item.laps.joinToString(", "))
                        },
                        fontSize = (12 * fontScale).sp,
                        color = metaTextColor,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

private fun formatHistoryTimestamp(epochMs: Long): String {
    val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    return Instant.ofEpochMilli(epochMs)
        .atZone(ZoneId.systemDefault())
        .toLocalDateTime()
        .format(formatter)
}

@Composable
private fun LogsScreen(
    modifier: Modifier,
    logs: List<String>,
    onExport: () -> Unit,
    onClear: () -> Unit,
    fontScale: Float
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
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
                    val containerColor = when {
                        isAlarmManager -> MaterialTheme.colorScheme.primaryContainer
                        isTickLoop -> MaterialTheme.colorScheme.tertiaryContainer
                        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                    }
                    val contentColor = when {
                        isAlarmManager -> MaterialTheme.colorScheme.onPrimaryContainer
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
                            if (isAlarmManager || isTickLoop) {
                                Text(
                                    text = if (isAlarmManager) "ALARM_MANAGER" else "TICK_LOOP",
                                    fontSize = (10 * fontScale).sp,
                                    fontWeight = FontWeight.Bold,
                                    color = contentColor
                                )
                                Text(
                                    text = if (isAlarmManager) {
                                        stringResource(R.string.log_hint_alarm_manager)
                                    } else {
                                        stringResource(R.string.log_hint_tick_loop)
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

private fun exportLogs(context: android.content.Context, logs: List<String>) {
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

@Composable
private fun TimerRow(
    timer: TimerPreset,
    isDragging: Boolean,
    draggingOffsetY: Float,
    onStart: (TimerPreset) -> Unit,
    onDelete: (Long) -> Unit,
    onEdit: (TimerPreset) -> Unit,
    onMeasured: (Int) -> Unit,
    onDragStart: () -> Unit,
    onDrag: (Float) -> Unit,
    onDragEnd: () -> Unit,
    fontScale: Float
) {
    var horizontalOffsetPx by remember(timer.id) { mutableFloatStateOf(0f) }
    val revealWidthPx = with(LocalDensity.current) { 120.dp.toPx() }
    val animatedOffset by animateFloatAsState(targetValue = horizontalOffsetPx, label = "swipeOffset")
    val revealProgress = (-animatedOffset / revealWidthPx).coerceIn(0f, 1f)

    Box(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .matchParentSize()
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
                .onSizeChanged { onMeasured(it.height) }
                .graphicsLayer {
                    translationX = animatedOffset
                    translationY = draggingOffsetY
                    alpha = if (isDragging) 0.92f else 1f
                    scaleX = if (isDragging) 1.02f else 1f
                    scaleY = if (isDragging) 1.02f else 1f
                    shadowElevation = if (isDragging) 24f else 0f
                }
                .pointerInput(timer.id, isDragging) {
                    detectHorizontalDragGestures(
                        onHorizontalDrag = { change, dragAmount ->
                            if (isDragging) return@detectHorizontalDragGestures
                            change.consume()
                            horizontalOffsetPx = (horizontalOffsetPx + dragAmount).coerceIn(-revealWidthPx, 0f)
                        },
                        onDragEnd = {
                            if (isDragging) return@detectHorizontalDragGestures
                            horizontalOffsetPx = if (horizontalOffsetPx < -revealWidthPx / 2f) -revealWidthPx else 0f
                        }
                    )
                }
                .pointerInput(timer.id) {
                    detectDragGesturesAfterLongPress(
                        onDragStart = {
                            horizontalOffsetPx = 0f
                            onDragStart()
                        },
                        onDragEnd = onDragEnd,
                        onDragCancel = onDragEnd,
                        onDrag = { change, dragAmount ->
                            change.consume()
                            onDrag(dragAmount.y)
                        }
                    )
                }
        ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = if (timer.label.isBlank()) formatDuration(timer.durationSeconds) else timer.label,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontSize = (24 * fontScale).sp,
                    fontWeight = FontWeight.Bold
                )
                if (timer.label.isNotBlank()) {
                    Text(
                        text = formatDuration(timer.durationSeconds),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontSize = (14 * fontScale).sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Button(onClick = { onStart(timer) }) {
                Text(stringResource(R.string.start), fontSize = (14 * fontScale).sp)
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

@Composable
private fun SettingsTab(
    modifier: Modifier,
    state: AppUiState,
    onLanguage: (String) -> Unit,
    onFontSize: (FontSize) -> Unit,
    onAdsRemoved: (Boolean) -> Unit,
    onDelayIntervention: (Boolean) -> Unit,
    onAlarmSoundEnabled: (Boolean) -> Unit,
    onAlarmVibrationEnabled: (Boolean) -> Unit,
    onOpenLogs: () -> Unit,
    fontScale: Float
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            HorizontalDivider()
            Text(stringResource(R.string.language), fontSize = (18 * fontScale).sp, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LanguageChip("system", state.settings.languageTag, "System", onLanguage, fontScale)
                LanguageChip("ko", state.settings.languageTag, "한국어", onLanguage, fontScale)
                LanguageChip("en", state.settings.languageTag, "English", onLanguage, fontScale)
                LanguageChip("ja", state.settings.languageTag, "日本語", onLanguage, fontScale)
            }
        }

        item {
            HorizontalDivider()
            Text(stringResource(R.string.font_size), fontSize = (18 * fontScale).sp, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(8.dp))
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                listOf(FontSize.SMALL, FontSize.NORMAL, FontSize.LARGE).forEachIndexed { index, size ->
                    SegmentedButton(
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = 3),
                        selected = state.settings.fontSize == size,
                        onClick = { onFontSize(size) },
                        label = {
                            Text(
                                when (size) {
                                    FontSize.SMALL -> stringResource(R.string.small)
                                    FontSize.NORMAL -> stringResource(R.string.normal)
                                    FontSize.LARGE -> stringResource(R.string.large)
                                },
                                fontSize = (13 * fontScale).sp
                            )
                        }
                    )
                }
            }
        }

        item {
            HorizontalDivider()
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.ads_removed), fontSize = (18 * fontScale).sp, fontWeight = FontWeight.SemiBold)
                    Text(stringResource(R.string.simulate_purchase_hint), fontSize = (13 * fontScale).sp)
                }
                Switch(checked = state.settings.adsRemoved, onCheckedChange = onAdsRemoved)
            }
        }

        item {
            HorizontalDivider()
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.delay_intervention),
                        fontSize = (18 * fontScale).sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        stringResource(R.string.delay_intervention_hint),
                        fontSize = (13 * fontScale).sp
                    )
                }
                Switch(
                    checked = state.settings.delayIntervention,
                    onCheckedChange = onDelayIntervention
                )
            }
        }

        item {
            HorizontalDivider()
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.alarm_sound),
                        fontSize = (18 * fontScale).sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        stringResource(R.string.alarm_sound_hint),
                        fontSize = (13 * fontScale).sp
                    )
                }
                Switch(
                    checked = state.settings.alarmSoundEnabled,
                    onCheckedChange = onAlarmSoundEnabled
                )
            }
        }

        item {
            HorizontalDivider()
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.alarm_vibration),
                        fontSize = (18 * fontScale).sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        stringResource(R.string.alarm_vibration_hint),
                        fontSize = (13 * fontScale).sp
                    )
                }
                Switch(
                    checked = state.settings.alarmVibrationEnabled,
                    onCheckedChange = onAlarmVibrationEnabled
                )
            }
        }

        item {
            HorizontalDivider()
            Button(
                onClick = onOpenLogs,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.logs_button), fontSize = (14 * fontScale).sp)
            }
        }
    }
}

@Composable
private fun LanguageChip(
    code: String,
    selectedCode: String,
    label: String,
    onLanguage: (String) -> Unit,
    fontScale: Float
) {
    AssistChip(
        onClick = { onLanguage(code) },
        label = { Text(label, fontSize = (13 * fontScale).sp) },
        leadingIcon = if (selectedCode == code) {
            {
                Text("●", fontSize = (10 * fontScale).sp)
            }
        } else {
            null
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddTimerBottomSheet(
    title: String,
    initialDurationSeconds: Int,
    initialLabel: String,
    onDismiss: () -> Unit,
    onSave: (Int, String) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var hours by remember(initialDurationSeconds) { mutableIntStateOf((initialDurationSeconds / 3600).coerceIn(0, 23)) }
    var minutes by remember(initialDurationSeconds) { mutableIntStateOf(((initialDurationSeconds % 3600) / 60).coerceIn(0, 59)) }
    var seconds by remember(initialDurationSeconds) { mutableIntStateOf((initialDurationSeconds % 60).coerceIn(0, 59)) }
    var label by remember(initialLabel) { mutableStateOf(initialLabel) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = title,
                fontSize = 22.sp,
                fontWeight = FontWeight.SemiBold
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                WheelPicker(maxValue = 23, label = stringResource(R.string.hours), initialValue = hours) { hours = it }
                WheelPicker(maxValue = 59, label = stringResource(R.string.minutes), initialValue = minutes) { minutes = it }
                WheelPicker(maxValue = 59, label = stringResource(R.string.seconds), initialValue = seconds) { seconds = it }
            }
            OutlinedTextField(
                value = label,
                onValueChange = { label = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text(stringResource(R.string.timer_label)) }
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.cancel))
                }
                Button(
                    onClick = {
                        val total = hours * 3600 + minutes * 60 + seconds
                        if (total > 0) onSave(total, label)
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.save))
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
        }
    }
}

@Composable
private fun WheelPicker(
    maxValue: Int,
    label: String,
    initialValue: Int,
    onValueChanged: (Int) -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        AndroidView(
            factory = { context ->
                NumberPicker(context).apply {
                    minValue = 0
                    this.maxValue = maxValue
                    value = initialValue.coerceIn(0, maxValue)
                    wrapSelectorWheel = true
                    setOnValueChangedListener { _, _, newVal -> onValueChanged(newVal) }
                }
            },
            update = { picker ->
                picker.value = initialValue.coerceIn(0, maxValue)
            },
            modifier = Modifier
                .height(140.dp)
                .padding(horizontal = 6.dp)
        )
        Text(label, fontSize = 16.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun AdBanner() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
        contentAlignment = Alignment.Center
    ) {
        AndroidView(
            factory = { context ->
                AdView(context).apply {
                    setAdSize(AdSize.BANNER)
                    setAdUnitId("ca-app-pub-3940256099942544/6300978111")
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    )
                    loadAd(AdRequest.Builder().build())
                }
            }
        )
    }
}

package com.quicktimer.ui

import android.content.Intent
import android.app.AlarmManager
import android.app.NotificationManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
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
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.core.content.FileProvider
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.quicktimer.BuildConfig
import com.quicktimer.R
import com.quicktimer.data.AppThemeMode
import com.quicktimer.data.FontSize
import com.quicktimer.data.TimerHistory
import com.quicktimer.data.TimerHistoryStatus
import com.quicktimer.data.TimerPreset
import com.quicktimer.data.displayLabel
import com.quicktimer.data.formatDuration
import com.quicktimer.data.formatDurationMillis
import com.quicktimer.service.ActiveTimerState
import com.quicktimer.service.RunningTimerState
import com.quicktimer.service.TimerForegroundService
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.text.style.TextOverflow
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
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
    onRunningTabNavigationConsumed: () -> Unit = {},
    onRequestNotificationPermissionFlow: () -> Unit = {},
    onOpenNotificationSettings: () -> Unit = {},
    onOpenExactAlarmSettings: () -> Unit = {},
    onOpenFullScreenIntentSettings: () -> Unit = {},
    onOpenAlarmChannelSettings: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var tab by remember { mutableStateOf(MainTab.TIMERS) }
    var showLogs by remember { mutableStateOf(false) }
    var showRuntimeDebug by remember { mutableStateOf(false) }
    var showPermissionManager by remember { mutableStateOf(false) }
    var timerOrderMode by remember { mutableStateOf(false) }
    var showTimerOptionsMenu by remember { mutableStateOf(false) }
    var showAddDialog by remember { mutableStateOf(false) }
    var editingPreset by remember { mutableStateOf<TimerPreset?>(null) }
    var showClearHistoryDialog by remember { mutableStateOf(false) }
    var bottomNavBarHeightPx by remember { mutableIntStateOf(0) }
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

    var lastBackPressedAt by remember { mutableLongStateOf(0L) }
    BackHandler {
        when {
            showAddDialog || editingPreset != null -> {
                showAddDialog = false
                editingPreset = null
            }
            showClearHistoryDialog -> {
                showClearHistoryDialog = false
            }
            showLogs || showRuntimeDebug || showPermissionManager -> {
                showLogs = false
                showRuntimeDebug = false
                showPermissionManager = false
                tab = MainTab.SETTINGS
            }
            tab != MainTab.TIMERS -> {
                tab = MainTab.TIMERS
            }
            else -> {
                val now = System.currentTimeMillis()
                if (now - lastBackPressedAt < 2000L) {
                    (context as? android.app.Activity)?.finish()
                } else {
                    lastBackPressedAt = now
                    Toast.makeText(
                        context,
                        context.getString(R.string.press_back_again_to_exit),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    val fontScale = when (uiState.settings.fontSize) {
        FontSize.SMALL -> 0.9f
        FontSize.NORMAL -> 1f
        FontSize.LARGE -> 1.15f
    }
    val navigationItemColors = NavigationBarItemDefaults.colors(
        selectedIconColor = MaterialTheme.colorScheme.onSurface,
        selectedTextColor = MaterialTheme.colorScheme.onSurface,
        indicatorColor = MaterialTheme.colorScheme.surface,
        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
    )
    val topBarScrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()

    LaunchedEffect(showPermissionManager) {
        if (showPermissionManager) {
            topBarScrollBehavior.state.heightOffset = 0f
            topBarScrollBehavior.state.contentOffset = 0f
        }
    }

    Scaffold(
        modifier = Modifier.nestedScroll(topBarScrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                scrollBehavior = topBarScrollBehavior,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                    navigationIconContentColor = MaterialTheme.colorScheme.onBackground,
                    actionIconContentColor = MaterialTheme.colorScheme.onBackground
                ),
                title = {
                    Text(
                        text = when {
                            showLogs -> stringResource(R.string.logs_title)
                            showRuntimeDebug -> stringResource(R.string.runtime_debug_title)
                            showPermissionManager -> stringResource(R.string.permission_management)
                            else -> stringResource(R.string.app_name)
                        },
                        fontSize = (18 * fontScale).sp
                    )
                },
                navigationIcon = {
                    if (showLogs || showRuntimeDebug || showPermissionManager) {
                        IconButton(
                            onClick = {
                                showLogs = false
                                showRuntimeDebug = false
                                showPermissionManager = false
                            }
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.back)
                            )
                        }
                    }
                },
                actions = {
                    if (!showLogs && !showRuntimeDebug && !showPermissionManager) {
                        when (tab) {
                            MainTab.TIMERS -> {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(onClick = { showAddDialog = true }) {
                                        Icon(
                                            imageVector = Icons.Default.Add,
                                            contentDescription = stringResource(R.string.add_timer)
                                        )
                                    }
                                    if (timerOrderMode) {
                                        TextButton(onClick = { timerOrderMode = false }) {
                                            Text(text = stringResource(R.string.order_done))
                                        }
                                    } else {
                                        Box {
                                            IconButton(onClick = { showTimerOptionsMenu = true }) {
                                                Icon(
                                                    imageVector = Icons.Default.MoreVert,
                                                    contentDescription = stringResource(R.string.options)
                                                )
                                            }
                                            DropdownMenu(
                                                expanded = showTimerOptionsMenu,
                                                onDismissRequest = { showTimerOptionsMenu = false }
                                            ) {
                                                DropdownMenuItem(
                                                    text = { Text(text = stringResource(R.string.order)) },
                                                    onClick = {
                                                        timerOrderMode = true
                                                        showTimerOptionsMenu = false
                                                    }
                                                )
                                            }
                                        }
                                    }
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
            if (!showLogs && !showRuntimeDebug && !showPermissionManager) {
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
                    NavigationBar(
                        modifier = Modifier.onSizeChanged { size ->
                            bottomNavBarHeightPx = size.height
                        },
                        containerColor = MaterialTheme.colorScheme.background,
                        tonalElevation = 0.dp
                    ) {
                        NavigationBarItem(
                            selected = tab == MainTab.TIMERS,
                            onClick = { tab = MainTab.TIMERS },
                            colors = navigationItemColors,
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
                            colors = navigationItemColors,
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
                            colors = navigationItemColors,
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
                            colors = navigationItemColors,
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
        val contentModifier = Modifier.padding(padding)
        if (showLogs) {
            LogsScreen(
                modifier = contentModifier,
                logs = uiState.logs,
                onExport = { exportLogs(context, uiState.logs) },
                onClear = viewModel::clearLogs,
                fontScale = fontScale
            )
        } else if (showRuntimeDebug) {
            RuntimeDebugScreen(
                modifier = contentModifier,
                runningTimer = uiState.runningTimer,
                fontScale = fontScale
            )
        } else if (showPermissionManager) {
            PermissionManagementScreen(
                modifier = contentModifier,
                fontScale = fontScale,
                onRequestNotificationPermissionFlow = onRequestNotificationPermissionFlow,
                onOpenNotificationSettings = onOpenNotificationSettings,
                onOpenExactAlarmSettings = onOpenExactAlarmSettings,
                onOpenFullScreenIntentSettings = onOpenFullScreenIntentSettings,
                onOpenAlarmChannelSettings = onOpenAlarmChannelSettings
            )
        } else {
            when (tab) {
                MainTab.TIMERS -> TimerTab(
                    modifier = contentModifier,
                    presets = uiState.presets,
                    reorderMode = timerOrderMode,
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
                    modifier = contentModifier,
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
                    modifier = contentModifier,
                    history = uiState.history,
                    onDelete = viewModel::deleteHistory,
                    fontScale = fontScale
                )

                MainTab.SETTINGS -> SettingsTab(
                    modifier = contentModifier,
                    state = uiState,
                    onLanguage = viewModel::setLanguage,
                    onThemeMode = viewModel::setThemeMode,
                    onFontSize = viewModel::setFontSize,
                    onAdsRemoved = viewModel::setAdsRemoved,
                    onDelayIntervention = viewModel::setDelayIntervention,
                    onAlarmSoundEnabled = viewModel::setAlarmSoundEnabled,
                    onAlarmVibrationEnabled = viewModel::setAlarmVibrationEnabled,
                    onOpenPermissionManager = { showPermissionManager = true },
                    onOpenFullScreenPreview = {
                        context.startActivity(
                            Intent(context, AlarmFullscreenActivity::class.java)
                                .putExtra(TimerForegroundService.EXTRA_ALARM_LABEL, "Quick Timer")
                                .putExtra(TimerForegroundService.EXTRA_ALARM_DURATION_SECONDS, 300)
                                .putExtra(
                                    TimerForegroundService.EXTRA_ALARM_COMPLETED_AT_MS,
                                    System.currentTimeMillis() - 3000L
                                )
                                .putExtra(AlarmFullscreenActivity.EXTRA_PREVIEW_MODE, true)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    },
                    onOpenLogs = { showLogs = true },
                    onOpenRuntimeDebug = { showRuntimeDebug = true },
                    fontScale = fontScale
                )
            }
        }
    }

    if (showAddDialog || editingPreset != null) {
        val target = editingPreset
        val bottomOffset = with(LocalDensity.current) { bottomNavBarHeightPx.toDp() }
        AddTimerBottomSheet(
            title = if (target == null) stringResource(R.string.add_timer) else stringResource(R.string.edit_timer),
            initialDurationSeconds = target?.durationSeconds ?: 10 * 60,
            initialLabel = target?.label.orEmpty(),
            bottomOffset = bottomOffset,
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
    reorderMode: Boolean,
    onStart: (TimerPreset) -> Unit,
    onDelete: (Long) -> Unit,
    onEdit: (TimerPreset) -> Unit,
    onReorderPresets: (List<Long>) -> Unit,
    onAddTimerClick: () -> Unit,
    fontScale: Float
) {
    val listState = rememberLazyListState()
    val localPresets = remember {
        mutableStateListOf<TimerPreset>().apply { addAll(presets) }
    }
    var previousPresetCount by remember { mutableIntStateOf(presets.size) }
    var presetCountInitialized by remember { mutableStateOf(false) }
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

    LaunchedEffect(presets.size) {
        if (presetCountInitialized && presets.size > previousPresetCount) {
            listState.animateScrollToItem(0)
        }
        previousPresetCount = presets.size
        presetCountInitialized = true
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

    LazyColumn(
        state = listState,
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(start = 16.dp, end = 16.dp, top = 0.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(top = 12.dp, bottom = 16.dp)
    ) {
        itemsIndexed(localPresets, key = { _, item -> item.id }) { index, timer ->
            TimerRow(
                timer = timer,
                reorderMode = reorderMode,
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
    highlighted: Boolean,
    onPause: (Int) -> Unit,
    onResume: (Int) -> Unit,
    onStop: (Int) -> Unit,
    onLap: (Int) -> Unit,
    fontScale: Float
) {
    val titleLabel = runningTimerTitle(active)
    val containerColor = if (highlighted) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surface
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
                text = formatDurationMillis(active.remainingMillis),
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
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
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
    val sortedActiveTimers = sortedByExpectedFinish(runningTimer.activeTimers)
    val runningIds = sortedActiveTimers.map { it.id }

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
                },
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
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
                val (badgeContainer, badgeContent) = when (item.status) {
                    TimerHistoryStatus.COMPLETED -> {
                        MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
                    }
                    TimerHistoryStatus.STOPPED -> {
                        MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
                    }
                }

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
                        color = badgeContent,
                        modifier = Modifier
                            .background(
                                color = badgeContainer,
                                shape = RoundedCornerShape(999.dp)
                            )
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                    Text(
                        text = statusText,
                        fontSize = (12 * fontScale).sp,
                        color = badgeContent,
                        modifier = Modifier
                            .background(
                                color = badgeContainer,
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

@Composable
private fun TimerRow(
    timer: TimerPreset,
    reorderMode: Boolean,
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
    val dragLiftScale by animateFloatAsState(
        targetValue = if (isDragging) 1.015f else 1f,
        label = "dragLiftScale"
    )
    val dragLiftAlpha by animateFloatAsState(
        targetValue = if (isDragging) 0.985f else 1f,
        label = "dragLiftAlpha"
    )
    val dragLiftElevation by animateFloatAsState(
        targetValue = if (isDragging) 10f else 0f,
        label = "dragLiftElevation"
    )

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
                    alpha = dragLiftAlpha
                    scaleX = dragLiftScale
                    scaleY = dragLiftScale
                    shadowElevation = dragLiftElevation
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
                },
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (reorderMode) {
                Box(
                    modifier = Modifier
                        .width(24.dp)
                        .pointerInput(timer.id, reorderMode) {
                            detectDragGestures(
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
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "≡",
                        fontSize = (20 * fontScale).sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
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
private fun AdBanner() {
    val context = LocalContext.current
    val density = LocalDensity.current

    BoxWithConstraints(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        val adWidthDp = maxWidth.value.toInt().coerceAtLeast(1)
        val adSize = remember(adWidthDp) {
            AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(context, adWidthDp)
        }
        val adHeightDp = with(density) {
            adSize.getHeightInPixels(context).toDp().coerceAtLeast(50.dp)
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(adHeightDp + 6.dp),
            contentAlignment = Alignment.Center
        ) {
            AndroidView(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(adHeightDp),
                factory = { viewContext ->
                    AdView(viewContext).apply {
                        setAdUnitId(BuildConfig.AD_UNIT_BANNER)
                        setAdSize(adSize)
                        loadAd(AdRequest.Builder().build())
                    }
                },
                update = { adView ->
                    if (adView.adSize != adSize) {
                        adView.setAdSize(adSize)
                        adView.loadAd(AdRequest.Builder().build())
                    }
                }
            )
        }
    }
}

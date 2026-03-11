package com.quicktimer.ui

import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Button
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.quicktimer.BuildConfig
import com.quicktimer.R
import com.quicktimer.data.AppThemeMode
import com.quicktimer.data.FontSize
import com.quicktimer.data.TimerPreset
import com.quicktimer.data.displayLabel
import com.quicktimer.service.TimerForegroundService
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Settings
private enum class MainTab { TIMERS, RUNNING, HISTORY, SETTINGS }
private enum class AuxiliaryScreen { NONE, LOGS, RUNTIME_DEBUG, PERMISSION_MANAGER }

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
    QuickTimerRoute(
        viewModel = viewModel,
        runningTabNavigationRequest = runningTabNavigationRequest,
        onRunningTabNavigationConsumed = onRunningTabNavigationConsumed,
        onRequestNotificationPermissionFlow = onRequestNotificationPermissionFlow,
        onOpenNotificationSettings = onOpenNotificationSettings,
        onOpenExactAlarmSettings = onOpenExactAlarmSettings,
        onOpenFullScreenIntentSettings = onOpenFullScreenIntentSettings,
        onOpenAlarmChannelSettings = onOpenAlarmChannelSettings
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QuickTimerRoute(
    viewModel: AppViewModel,
    runningTabNavigationRequest: RunningTabNavigationRequest?,
    onRunningTabNavigationConsumed: () -> Unit,
    onRequestNotificationPermissionFlow: () -> Unit,
    onOpenNotificationSettings: () -> Unit,
    onOpenExactAlarmSettings: () -> Unit,
    onOpenFullScreenIntentSettings: () -> Unit,
    onOpenAlarmChannelSettings: () -> Unit
) {
    val context = LocalContext.current
    val settings by viewModel.settingsState.collectAsStateWithLifecycle()
    val runningTimer by viewModel.runningTimerState.collectAsStateWithLifecycle()
    val history by viewModel.historyState.collectAsStateWithLifecycle()

    var tab by rememberSaveable { mutableStateOf(MainTab.TIMERS) }
    var auxiliaryScreen by rememberSaveable { mutableStateOf(AuxiliaryScreen.NONE) }
    var timerOrderMode by rememberSaveable { mutableStateOf(false) }
    var showTimerOptionsMenu by rememberSaveable { mutableStateOf(false) }
    var showAddDialog by rememberSaveable { mutableStateOf(false) }
    var editingPreset by remember { mutableStateOf<TimerPreset?>(null) }
    var showClearHistoryDialog by rememberSaveable { mutableStateOf(false) }
    var bottomNavBarHeightPx by remember { mutableIntStateOf(0) }
    var highlightTimerId by rememberSaveable { mutableStateOf<Int?>(null) }
    var highlightRequestId by rememberSaveable { mutableStateOf<Long?>(null) }
    val runningCount = runningTimer.activeTimers.size
    val isAlarmRinging = runningTimer.isAlarmRinging

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
            auxiliaryScreen != AuxiliaryScreen.NONE -> {
                auxiliaryScreen = AuxiliaryScreen.NONE
                tab = MainTab.SETTINGS
            }
            timerOrderMode -> {
                timerOrderMode = false
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

    val fontScale = when (settings.fontSize) {
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

    LaunchedEffect(auxiliaryScreen) {
        if (auxiliaryScreen == AuxiliaryScreen.PERMISSION_MANAGER) {
            topBarScrollBehavior.state.heightOffset = 0f
            topBarScrollBehavior.state.contentOffset = 0f
        }
    }

    Scaffold(
        modifier = Modifier.nestedScroll(topBarScrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            MainTopBar(
                tab = tab,
                auxiliaryScreen = auxiliaryScreen,
                timerOrderMode = timerOrderMode,
                showTimerOptionsMenu = showTimerOptionsMenu,
                canClearHistory = history.isNotEmpty(),
                fontScale = fontScale,
                scrollBehavior = topBarScrollBehavior,
                onShowAdd = { showAddDialog = true },
                onBackFromAuxiliary = { auxiliaryScreen = AuxiliaryScreen.NONE },
                onToggleOrderMode = { timerOrderMode = it },
                onTimerOptionsMenuChange = { showTimerOptionsMenu = it },
                onShowClearHistory = { showClearHistoryDialog = true }
            )
        },
        bottomBar = {
            MainBottomBar(
                auxiliaryScreen = auxiliaryScreen,
                isAlarmRinging = isAlarmRinging,
                adsRemoved = settings.adsRemoved,
                tab = tab,
                runningCount = runningCount,
                fontScale = fontScale,
                navigationItemColors = navigationItemColors,
                onTabSelected = { tab = it },
                onAcknowledgeAlarm = viewModel::acknowledgeAlarm,
                onBottomBarMeasured = { bottomNavBarHeightPx = it }
            )
        }
    ) { padding ->
        MainContent(
            modifier = Modifier.padding(padding),
            viewModel = viewModel,
            auxiliaryScreen = auxiliaryScreen,
            tab = tab,
            timerOrderMode = timerOrderMode,
            onTimerOrderModeChange = { timerOrderMode = it },
            highlightTimerId = highlightTimerId,
            highlightRequestId = highlightRequestId,
            onRunningHighlightConsumed = {
                highlightTimerId = null
                highlightRequestId = null
            },
            fontScale = fontScale,
            onStartTimer = { preset ->
                viewModel.startTimer(preset.durationSeconds, preset.label)
                val timerText = displayLabel(preset.durationSeconds, preset.label)
                Toast.makeText(
                    context,
                    context.getString(R.string.starting_timer_toast, timerText),
                    Toast.LENGTH_SHORT
                ).show()
            },
            onDeletePreset = viewModel::deletePreset,
            onEditPreset = { editingPreset = it },
            onReorderPresets = viewModel::reorderPresets,
            onAddTimerClick = { showAddDialog = true },
            onPauseTimer = viewModel::pauseTimer,
            onResumeTimer = viewModel::resumeTimer,
            onStopTimer = viewModel::stopTimer,
            onLapTimer = viewModel::addLap,
            onDeleteHistory = viewModel::deleteHistory,
            onLanguageChange = viewModel::setLanguage,
            onThemeModeChange = viewModel::setThemeMode,
            onFontSizeChange = viewModel::setFontSize,
            onAdsRemovedChange = viewModel::setAdsRemoved,
            onDelayInterventionChange = viewModel::setDelayIntervention,
            onAlarmSoundEnabledChange = viewModel::setAlarmSoundEnabled,
            onAlarmVibrationEnabledChange = viewModel::setAlarmVibrationEnabled,
            onOpenPermissionManager = { auxiliaryScreen = AuxiliaryScreen.PERMISSION_MANAGER },
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
            onOpenLogs = { auxiliaryScreen = AuxiliaryScreen.LOGS },
            onOpenRuntimeDebug = { auxiliaryScreen = AuxiliaryScreen.RUNTIME_DEBUG },
            onClearLogs = viewModel::clearLogs,
            onExportLogs = { logs -> exportLogs(context, logs) },
            onRequestNotificationPermissionFlow = onRequestNotificationPermissionFlow,
            onOpenNotificationSettings = onOpenNotificationSettings,
            onOpenExactAlarmSettings = onOpenExactAlarmSettings,
            onOpenFullScreenIntentSettings = onOpenFullScreenIntentSettings,
            onOpenAlarmChannelSettings = onOpenAlarmChannelSettings
        )
    }

    OverlayHost(
        showAddDialog = showAddDialog,
        editingPreset = editingPreset,
        showClearHistoryDialog = showClearHistoryDialog,
        bottomNavBarHeightPx = bottomNavBarHeightPx,
        onDismissAddDialog = {
            showAddDialog = false
            editingPreset = null
        },
        onSaveTimer = { targetId, seconds, label ->
            if (targetId == null) {
                viewModel.addPreset(seconds, label)
            } else {
                viewModel.updatePreset(targetId, seconds, label)
            }
            showAddDialog = false
            editingPreset = null
        },
        onDismissClearHistory = { showClearHistoryDialog = false },
        onConfirmClearHistory = {
            viewModel.clearAllHistory()
            showClearHistoryDialog = false
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainTopBar(
    tab: MainTab,
    auxiliaryScreen: AuxiliaryScreen,
    timerOrderMode: Boolean,
    showTimerOptionsMenu: Boolean,
    canClearHistory: Boolean,
    fontScale: Float,
    scrollBehavior: androidx.compose.material3.TopAppBarScrollBehavior,
    onShowAdd: () -> Unit,
    onBackFromAuxiliary: () -> Unit,
    onToggleOrderMode: (Boolean) -> Unit,
    onTimerOptionsMenuChange: (Boolean) -> Unit,
    onShowClearHistory: () -> Unit
) {
    TopAppBar(
        scrollBehavior = scrollBehavior,
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.background,
            scrolledContainerColor = MaterialTheme.colorScheme.background,
            titleContentColor = MaterialTheme.colorScheme.onBackground,
            navigationIconContentColor = MaterialTheme.colorScheme.onBackground,
            actionIconContentColor = MaterialTheme.colorScheme.onBackground
        ),
        title = {
            Text(
                text = when (auxiliaryScreen) {
                    AuxiliaryScreen.LOGS -> stringResource(R.string.logs_title)
                    AuxiliaryScreen.RUNTIME_DEBUG -> stringResource(R.string.runtime_debug_title)
                    AuxiliaryScreen.PERMISSION_MANAGER -> stringResource(R.string.permission_management)
                    AuxiliaryScreen.NONE -> stringResource(R.string.app_name)
                },
                fontSize = (18 * fontScale).sp
            )
        },
        navigationIcon = {
            if (auxiliaryScreen != AuxiliaryScreen.NONE) {
                IconButton(onClick = onBackFromAuxiliary) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.back)
                    )
                }
            }
        },
        actions = {
            if (auxiliaryScreen == AuxiliaryScreen.NONE) {
                when (tab) {
                    MainTab.TIMERS -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = onShowAdd) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = stringResource(R.string.add_timer)
                                )
                            }
                            if (timerOrderMode) {
                                TextButton(onClick = { onToggleOrderMode(false) }) {
                                    Text(text = stringResource(R.string.order_done))
                                }
                            } else {
                                Box {
                                    IconButton(onClick = { onTimerOptionsMenuChange(true) }) {
                                        Icon(
                                            imageVector = Icons.Default.MoreVert,
                                            contentDescription = stringResource(R.string.options)
                                        )
                                    }
                                    DropdownMenu(
                                        expanded = showTimerOptionsMenu,
                                        onDismissRequest = { onTimerOptionsMenuChange(false) }
                                    ) {
                                        DropdownMenuItem(
                                            text = { Text(text = stringResource(R.string.order)) },
                                            onClick = {
                                                onToggleOrderMode(true)
                                                onTimerOptionsMenuChange(false)
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                    MainTab.HISTORY -> {
                        IconButton(
                            onClick = onShowClearHistory,
                            enabled = canClearHistory
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
}

@Composable
private fun MainBottomBar(
    auxiliaryScreen: AuxiliaryScreen,
    isAlarmRinging: Boolean,
    adsRemoved: Boolean,
    tab: MainTab,
    runningCount: Int,
    fontScale: Float,
    navigationItemColors: androidx.compose.material3.NavigationBarItemColors,
    onTabSelected: (MainTab) -> Unit,
    onAcknowledgeAlarm: () -> Unit,
    onBottomBarMeasured: (Int) -> Unit
) {
    if (auxiliaryScreen != AuxiliaryScreen.NONE) return
    Column {
        if (isAlarmRinging) {
            Button(
                onClick = onAcknowledgeAlarm,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text(stringResource(R.string.stop_alarm), fontSize = (15 * fontScale).sp)
            }
        }
        if (!adsRemoved) {
            AdBanner()
        }
        NavigationBar(
            modifier = Modifier.onSizeChanged { size ->
                onBottomBarMeasured(size.height)
            },
            containerColor = MaterialTheme.colorScheme.background,
            tonalElevation = 0.dp
        ) {
            NavigationBarItem(
                selected = tab == MainTab.TIMERS,
                onClick = { onTabSelected(MainTab.TIMERS) },
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
                onClick = { onTabSelected(MainTab.RUNNING) },
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
                onClick = { onTabSelected(MainTab.HISTORY) },
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
                onClick = { onTabSelected(MainTab.SETTINGS) },
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

@Composable
private fun MainContent(
    modifier: Modifier,
    viewModel: AppViewModel,
    auxiliaryScreen: AuxiliaryScreen,
    tab: MainTab,
    timerOrderMode: Boolean,
    onTimerOrderModeChange: (Boolean) -> Unit,
    highlightTimerId: Int?,
    highlightRequestId: Long?,
    onRunningHighlightConsumed: () -> Unit,
    fontScale: Float,
    onStartTimer: (TimerPreset) -> Unit,
    onDeletePreset: (Long) -> Unit,
    onEditPreset: (TimerPreset) -> Unit,
    onReorderPresets: (List<Long>) -> Unit,
    onAddTimerClick: () -> Unit,
    onPauseTimer: (Int) -> Unit,
    onResumeTimer: (Int) -> Unit,
    onStopTimer: (Int) -> Unit,
    onLapTimer: (Int) -> Unit,
    onDeleteHistory: (Long) -> Unit,
    onLanguageChange: (String) -> Unit,
    onThemeModeChange: (AppThemeMode) -> Unit,
    onFontSizeChange: (FontSize) -> Unit,
    onAdsRemovedChange: (Boolean) -> Unit,
    onDelayInterventionChange: (Boolean) -> Unit,
    onAlarmSoundEnabledChange: (Boolean) -> Unit,
    onAlarmVibrationEnabledChange: (Boolean) -> Unit,
    onOpenPermissionManager: () -> Unit,
    onOpenFullScreenPreview: () -> Unit,
    onOpenLogs: () -> Unit,
    onOpenRuntimeDebug: () -> Unit,
    onClearLogs: () -> Unit,
    onExportLogs: (List<String>) -> Unit,
    onRequestNotificationPermissionFlow: () -> Unit,
    onOpenNotificationSettings: () -> Unit,
    onOpenExactAlarmSettings: () -> Unit,
    onOpenFullScreenIntentSettings: () -> Unit,
    onOpenAlarmChannelSettings: () -> Unit
) {
    when (auxiliaryScreen) {
        AuxiliaryScreen.LOGS -> {
            val logs by viewModel.logsState.collectAsStateWithLifecycle()
            LogsScreen(
                modifier = modifier,
                logs = logs,
                onExport = { onExportLogs(logs) },
                onClear = onClearLogs,
                fontScale = fontScale
            )
        }
        AuxiliaryScreen.RUNTIME_DEBUG -> {
            val runningTimer by viewModel.runningTimerState.collectAsStateWithLifecycle()
            RuntimeDebugScreen(
                modifier = modifier,
                runningTimer = runningTimer,
                fontScale = fontScale
            )
        }
        AuxiliaryScreen.PERMISSION_MANAGER -> PermissionManagementScreen(
            modifier = modifier,
            fontScale = fontScale,
            onRequestNotificationPermissionFlow = onRequestNotificationPermissionFlow,
            onOpenNotificationSettings = onOpenNotificationSettings,
            onOpenExactAlarmSettings = onOpenExactAlarmSettings,
            onOpenFullScreenIntentSettings = onOpenFullScreenIntentSettings,
            onOpenAlarmChannelSettings = onOpenAlarmChannelSettings
        )
        AuxiliaryScreen.NONE -> {
            when (tab) {
                MainTab.TIMERS -> {
                    val presets by viewModel.presetsState.collectAsStateWithLifecycle()
                    TimerTab(
                        modifier = modifier,
                        presets = presets,
                        reorderMode = timerOrderMode,
                        onReorderModeChange = onTimerOrderModeChange,
                        onStart = onStartTimer,
                        onDelete = onDeletePreset,
                        onEdit = onEditPreset,
                        onReorderPresets = onReorderPresets,
                        onAddTimerClick = onAddTimerClick,
                        fontScale = fontScale
                    )
                }
                MainTab.RUNNING -> {
                    val runningTimer by viewModel.runningTimerState.collectAsStateWithLifecycle()
                    RunningTab(
                        modifier = modifier,
                        runningTimer = runningTimer,
                        highlightTimerId = highlightTimerId,
                        highlightRequestId = highlightRequestId,
                        onHighlightConsumed = onRunningHighlightConsumed,
                        onPause = onPauseTimer,
                        onResume = onResumeTimer,
                        onStop = onStopTimer,
                        onLap = onLapTimer,
                        fontScale = fontScale
                    )
                }
                MainTab.HISTORY -> {
                    val history by viewModel.historyState.collectAsStateWithLifecycle()
                    HistoryTab(
                        modifier = modifier,
                        history = history,
                        onDelete = onDeleteHistory,
                        fontScale = fontScale
                    )
                }
                MainTab.SETTINGS -> {
                    val settings by viewModel.settingsState.collectAsStateWithLifecycle()
                    SettingsTab(
                        modifier = modifier,
                        settings = settings,
                        onLanguage = onLanguageChange,
                        onThemeMode = onThemeModeChange,
                        onFontSize = onFontSizeChange,
                        onAdsRemoved = onAdsRemovedChange,
                        onDelayIntervention = onDelayInterventionChange,
                        onAlarmSoundEnabled = onAlarmSoundEnabledChange,
                        onAlarmVibrationEnabled = onAlarmVibrationEnabledChange,
                        onOpenPermissionManager = onOpenPermissionManager,
                        onOpenFullScreenPreview = onOpenFullScreenPreview,
                        onOpenLogs = onOpenLogs,
                        onOpenRuntimeDebug = onOpenRuntimeDebug,
                        fontScale = fontScale
                    )
                }
            }
        }
    }
}

@Composable
private fun OverlayHost(
    showAddDialog: Boolean,
    editingPreset: TimerPreset?,
    showClearHistoryDialog: Boolean,
    bottomNavBarHeightPx: Int,
    onDismissAddDialog: () -> Unit,
    onSaveTimer: (targetId: Long?, seconds: Int, label: String) -> Unit,
    onDismissClearHistory: () -> Unit,
    onConfirmClearHistory: () -> Unit
) {
    if (showAddDialog || editingPreset != null) {
        val target = editingPreset
        val bottomOffset = with(LocalDensity.current) { bottomNavBarHeightPx.toDp() }
        AddTimerBottomSheet(
            title = if (target == null) stringResource(R.string.add_timer) else stringResource(R.string.edit_timer),
            initialDurationSeconds = target?.durationSeconds ?: 10 * 60,
            initialLabel = target?.label.orEmpty(),
            bottomOffset = bottomOffset,
            onDismiss = onDismissAddDialog,
            onSave = { seconds, label -> onSaveTimer(target?.id, seconds, label) }
        )
    }
    if (showClearHistoryDialog) {
        AlertDialog(
            onDismissRequest = onDismissClearHistory,
            title = { Text(stringResource(R.string.history_clear_all_title)) },
            text = { Text(stringResource(R.string.history_clear_all_message)) },
            confirmButton = {
                TextButton(onClick = onConfirmClearHistory) {
                    Text(stringResource(R.string.history_clear_all_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissClearHistory) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
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

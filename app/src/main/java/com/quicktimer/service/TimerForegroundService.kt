package com.quicktimer.service

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.KeyguardManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.quicktimer.MainActivity
import com.quicktimer.QuickTimerApplication
import com.quicktimer.R
import com.quicktimer.data.LogStore
import com.quicktimer.data.RunningTimerEntity
import com.quicktimer.data.RunningTimerStore
import com.quicktimer.data.TimerHistoryStore
import com.quicktimer.data.TimerHistoryStatus
import com.quicktimer.data.TimerPreset
import com.quicktimer.data.defaultPresets
import com.quicktimer.data.displayLabel
import com.quicktimer.data.formatDuration
import com.quicktimer.data.formatDurationMillis
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.max

class TimerForegroundService : Service() {
    private data class TimerEntry(
        val id: Int,
        val totalMillis: Long,
        val label: String,
        val sessionId: Long,
        val sessionStartedAtEpochMs: Long,
        var extensionCount: Int,
        var remainingMillis: Long,
        var isPaused: Boolean,
        var updatedAtElapsedMs: Long,
        var deferredByDelayMode: Boolean = false,
        var deferredWakeElapsedMs: Long = -1L,
        val laps: MutableList<String> = mutableListOf()
    )

    private data class CompletedTimerSpec(
        val durationSeconds: Int,
        val label: String,
        val sessionId: Long,
        val sessionStartedAtEpochMs: Long,
        val extensionCount: Int
    )

    private data class CompletedHistoryRecord(
        val sessionId: Long,
        val label: String,
        val durationSeconds: Int,
        val startedAtEpochMs: Long,
        val endedAtEpochMs: Long,
        val extensionCount: Int,
        val laps: List<String>
    )

    private enum class CompletionTrigger(val tag: String) {
        ALARM_MANAGER("ALARM_MANAGER")
    }

    private val serviceScope = CoroutineScope(
        SupervisorJob() + Dispatchers.Default.limitedParallelism(1)
    )

    private var presets: List<TimerPreset> = defaultPresets()
    private val timers = mutableListOf<TimerEntry>()
    private var nextTimerId = 1
    private var nextSessionId = 1L

    private var completionRingtone: Ringtone? = null
    private var completionVibrator: Vibrator? = null
    private var completionAlertActive = false
    private var lastCompletedSpec: CompletedTimerSpec? = null
    private var lastCompletedAtEpochMs: Long = -1L
    private var scheduledWakeElapsedMs: Long = -1L
    private var delayInterventionEnabled = false
    private var alarmSoundEnabled = true
    private var alarmVibrationEnabled = true
    private var quickNotificationDirty = true
    private lateinit var logStore: LogStore
    private lateinit var runningTimerStore: RunningTimerStore
    private lateinit var historyStore: TimerHistoryStore
    private var restoreJob: kotlinx.coroutines.Job? = null
    private var runningNotificationRefreshJob: Job? = null
    private val interactiveStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: android.content.Context?, intent: Intent?) {
            val action = intent?.action.orEmpty()
            logEvent("POWER[$action] ${powerStateSummary()}")
            restartRunningNotificationRefreshLoop()
        }
    }

    override fun onCreate() {
        super.onCreate()
        createChannels()
        clearStaleNotifications()
        startForeground(QUICK_NOTIFICATION_ID, buildQuickNotification())
        registerInteractiveStateReceiver()

        val app = application as QuickTimerApplication
        logStore = app.logStore
        runningTimerStore = app.runningTimerStore
        historyStore = app.historyStore
        restoreJob = serviceScope.launch {
            restoreTimersFromStore()
        }
        serviceScope.launch {
            app.presetStore.presetsFlow.collectLatest { list ->
                presets = list
                quickNotificationDirty = true
                syncNotifications()
            }
        }
        serviceScope.launch {
            app.settingsStore.settingsFlow.collectLatest { settings ->
                if (delayInterventionEnabled != settings.delayIntervention) {
                    delayInterventionEnabled = settings.delayIntervention
                    logEvent("SETTING delay_intervention=$delayInterventionEnabled")
                }
                if (alarmSoundEnabled != settings.alarmSoundEnabled) {
                    alarmSoundEnabled = settings.alarmSoundEnabled
                    logEvent("SETTING alarm_sound=$alarmSoundEnabled")
                    if (!alarmSoundEnabled && completionAlertActive) {
                        runCatching { completionRingtone?.stop() }
                    }
                }
                if (alarmVibrationEnabled != settings.alarmVibrationEnabled) {
                    alarmVibrationEnabled = settings.alarmVibrationEnabled
                    logEvent("SETTING alarm_vibration=$alarmVibrationEnabled")
                    if (!alarmVibrationEnabled && completionAlertActive) {
                        runCatching { completionVibrator?.cancel() }
                    }
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == TimerServiceController.ACTION_ACK_ALARM) {
            ensureForegroundNotification()
            serviceScope.launch {
                acknowledgeCompletionAlert()
            }
            return START_STICKY
        }

        ensureForegroundNotification()
        serviceScope.launch {
            awaitRestore()
            when (intent?.action) {
                TimerServiceController.ACTION_START -> {
                    val duration = intent.getIntExtra(TimerServiceController.EXTRA_DURATION_SECONDS, 0)
                    val label = intent.getStringExtra(TimerServiceController.EXTRA_LABEL).orEmpty()
                    val source = intent.getStringExtra(TimerServiceController.EXTRA_START_SOURCE)
                        ?: TimerServiceController.START_SOURCE_UNKNOWN
                    val sessionId = intent.getLongExtra(TimerServiceController.EXTRA_SESSION_ID, 0L)
                    val sessionStartedAt = intent.getLongExtra(
                        TimerServiceController.EXTRA_SESSION_STARTED_AT_MS,
                        0L
                    )
                    val extensionCount = intent.getIntExtra(TimerServiceController.EXTRA_EXTENSION_COUNT, -1)
                    if (source == TimerServiceController.START_SOURCE_EXTEND) {
                        val accepted = consumeActiveCompletionForExtend(
                            sessionIdHint = sessionId,
                            sessionStartedAtHint = sessionStartedAt,
                            extensionCountHint = extensionCount
                        )
                        if (!accepted) {
                            syncNotifications()
                            return@launch
                        }
                    }
                    stopCompletionAlert()
                    NotificationManagerCompat.from(this@TimerForegroundService).cancel(COMPLETION_NOTIFICATION_ID)
                    startTimer(
                        durationSeconds = duration,
                        label = label,
                        source = source,
                        sessionIdHint = sessionId,
                        sessionStartedAtHint = sessionStartedAt,
                        extensionCountHint = extensionCount
                    )
                    val text = displayLabel(duration, label)
                    logEvent("START[$source] $text (${formatDuration(duration)})")
                }

                TimerServiceController.ACTION_PAUSE -> {
                    val timerId = intent.getIntExtra(TimerServiceController.EXTRA_TIMER_ID, 0)
                    pauseTimer(timerId)
                }

                TimerServiceController.ACTION_RESUME -> {
                    val timerId = intent.getIntExtra(TimerServiceController.EXTRA_TIMER_ID, 0)
                    resumeTimer(timerId)
                }

                TimerServiceController.ACTION_STOP -> {
                    val timerId = intent.getIntExtra(TimerServiceController.EXTRA_TIMER_ID, 0)
                    stopTimer(timerId)
                }

                TimerServiceController.ACTION_LAP -> {
                    val timerId = intent.getIntExtra(TimerServiceController.EXTRA_TIMER_ID, 0)
                    recordLap(timerId)
                }
                TimerServiceController.ACTION_REFRESH -> {
                    quickNotificationDirty = true
                    syncNotifications()
                }

                TimerServiceController.ACTION_EXACT_WAKE -> onExactWake(intent)
                else -> syncNotifications()
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        restoreJob?.cancel()
        runningNotificationRefreshJob?.cancel()
        unregisterInteractiveStateReceiver()
        cancelExactWake()
        stopCompletionAlert()
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun logEvent(message: String) {
        if (this::logStore.isInitialized) {
            logStore.append(message)
        }
    }

    private suspend fun awaitRestore() {
        restoreJob?.join()
    }

    private suspend fun restoreTimersFromStore() {
        val restored = runningTimerStore.loadAll()
        val historyNextSessionId = historyStore.nextSessionIdHint().coerceAtLeast(1L)
        timers.clear()
        if (restored.isEmpty()) {
            nextTimerId = runningTimerStore.nextIdHint().coerceAtLeast(1)
            nextSessionId = historyNextSessionId
            publishState()
            syncNotifications()
            return
        }

        val now = SystemClock.elapsedRealtime()
        val nowEpochMs = System.currentTimeMillis()
        var fallbackSessionId = historyNextSessionId
        restored.forEach { persisted ->
            val adjustedRemaining = if (persisted.isPaused) {
                persisted.remainingMillis.coerceAtLeast(0L)
            } else {
                val delta = (now - persisted.updatedAtElapsedMs).coerceAtLeast(0L)
                max(0L, persisted.remainingMillis - delta)
            }
            val sessionId = if (persisted.sessionId > 0L) {
                persisted.sessionId
            } else {
                fallbackSessionId++
            }
            timers.add(
                TimerEntry(
                    id = persisted.id,
                    totalMillis = persisted.totalMillis,
                    label = persisted.label,
                    sessionId = sessionId,
                    sessionStartedAtEpochMs = if (persisted.sessionStartedAtEpochMs > 0L) {
                        persisted.sessionStartedAtEpochMs
                    } else {
                        nowEpochMs
                    },
                    extensionCount = persisted.extensionCount.coerceAtLeast(0),
                    remainingMillis = adjustedRemaining,
                    isPaused = persisted.isPaused,
                    updatedAtElapsedMs = now,
                    deferredByDelayMode = persisted.deferredByDelayMode,
                    deferredWakeElapsedMs = persisted.deferredWakeElapsedMs,
                    laps = decodeLaps(persisted.lapsSerialized)
                )
            )
        }
        nextTimerId = (timers.maxOfOrNull { it.id } ?: 0) + 1
        nextSessionId = maxOf(
            historyNextSessionId,
            fallbackSessionId,
            (timers.maxOfOrNull { it.sessionId } ?: 0L) + 1L
        )
        logEvent("RESTORE loaded=${timers.size}")

        val changed = tickTimers(CompletionTrigger.ALARM_MANAGER)
        if (changed) persistTimers()
        publishState()
        syncNotifications()
    }

    private suspend fun persistTimers() {
        runningTimerStore.syncAll(
            timers.mapIndexed { index, timer ->
                RunningTimerEntity(
                    id = timer.id,
                    totalMillis = timer.totalMillis,
                    label = timer.label,
                    remainingMillis = timer.remainingMillis,
                    isPaused = timer.isPaused,
                    updatedAtElapsedMs = timer.updatedAtElapsedMs,
                    deferredByDelayMode = timer.deferredByDelayMode,
                    deferredWakeElapsedMs = timer.deferredWakeElapsedMs,
                    sessionId = timer.sessionId,
                    sessionStartedAtEpochMs = timer.sessionStartedAtEpochMs,
                    extensionCount = timer.extensionCount,
                    lapsSerialized = encodeLaps(timer.laps),
                    position = index
                )
            }
        )
    }

    private fun encodeLaps(laps: List<String>): String {
        return laps.joinToString(LAP_SEPARATOR)
    }

    private fun decodeLaps(raw: String): MutableList<String> {
        if (raw.isBlank()) return mutableListOf()
        return raw.split(LAP_SEPARATOR)
            .filter { it.isNotBlank() }
            .toMutableList()
    }

    private suspend fun startTimer(
        durationSeconds: Int,
        label: String,
        source: String,
        sessionIdHint: Long,
        sessionStartedAtHint: Long,
        extensionCountHint: Int
    ) {
        if (durationSeconds <= 0) return
        val nowElapsed = SystemClock.elapsedRealtime()
        val nowEpochMs = System.currentTimeMillis()
        val total = durationSeconds * 1000L
        val useExistingSession = source == TimerServiceController.START_SOURCE_EXTEND && sessionIdHint > 0L
        val sessionId = if (useExistingSession) {
            sessionIdHint
        } else {
            nextSessionId++
        }
        nextSessionId = maxOf(nextSessionId, sessionId + 1L)
        val sessionStartedAt = if (useExistingSession && sessionStartedAtHint > 0L) {
            sessionStartedAtHint
        } else {
            nowEpochMs
        }
        val extensionCount = if (useExistingSession) {
            extensionCountHint.coerceAtLeast(0) + 1
        } else {
            0
        }
        timers.add(
            0,
            TimerEntry(
                id = nextTimerId++,
                totalMillis = total,
                label = if (label.isBlank()) "" else label,
                sessionId = sessionId,
                sessionStartedAtEpochMs = sessionStartedAt,
                extensionCount = extensionCount,
                remainingMillis = total,
                isPaused = false,
                updatedAtElapsedMs = nowElapsed,
                deferredByDelayMode = false,
                deferredWakeElapsedMs = -1L
            )
        )
        persistTimers()
        publishState()
        syncNotifications()
        if (source == TimerServiceController.START_SOURCE_WIDGET) {
            vibrateForWidgetStart()
        }
    }

    private suspend fun pauseTimer(timerId: Int) {
        val timer = resolveTimer(timerId) ?: return
        if (timer.isPaused) return
        val now = SystemClock.elapsedRealtime()
        tickOne(timer, now)
        timer.isPaused = true
        timer.updatedAtElapsedMs = now
        persistTimers()
        publishState()
        syncNotifications()
    }

    private suspend fun resumeTimer(timerId: Int) {
        val timer = resolveTimer(timerId) ?: return
        if (!timer.isPaused) return
        timer.isPaused = false
        timer.updatedAtElapsedMs = SystemClock.elapsedRealtime()
        persistTimers()
        publishState()
        syncNotifications()
    }

    private suspend fun stopTimer(timerId: Int) {
        val targetId = resolveTimerId(timerId) ?: return
        val targetIndex = timers.indexOfFirst { it.id == targetId }
        if (targetIndex < 0) return
        val timer = timers[targetIndex]
        tickOne(timer, SystemClock.elapsedRealtime())
        recordHistory(
            timer = timer,
            endedAtEpochMs = System.currentTimeMillis(),
            status = TimerHistoryStatus.STOPPED
        )
        timers.removeAt(targetIndex)
        if (timers.isEmpty()) stopCompletionAlert()
        persistTimers()
        publishState()
        syncNotifications()
    }

    private suspend fun recordLap(timerId: Int) {
        val timer = resolveTimer(timerId) ?: return
        if (timer.isPaused) return
        val now = SystemClock.elapsedRealtime()
        val lapValue = formatDurationMillis(currentRemainingMillis(timer, now))
        val previousLap = timer.laps.firstOrNull()
        if (previousLap == lapValue) return
        timer.laps.add(0, lapValue)
        if (timer.laps.size > 20) timer.laps.removeAt(timer.laps.lastIndex)
        persistTimers()
        publishState()
        syncNotifications()
    }

    private suspend fun tickTimers(trigger: CompletionTrigger): Boolean {
        val now = SystemClock.elapsedRealtime()
        var anyCompleted = false
        var stateChanged = false
        var completedSpec: CompletedTimerSpec? = null
        val completedHistoryRecords = mutableListOf<CompletedHistoryRecord>()
        val iterator = timers.iterator()
        while (iterator.hasNext()) {
            val timer = iterator.next()
            val remainingNow = currentRemainingMillis(timer, now)
            if (remainingNow <= 0L) {
                if (trigger == CompletionTrigger.ALARM_MANAGER && delayInterventionEnabled) {
                    if (!timer.deferredByDelayMode) {
                        timer.deferredByDelayMode = true
                        timer.deferredWakeElapsedMs = now + DELAY_SIMULATION_WAKE_MS
                        stateChanged = true
                        logEvent(
                            "DELAY_SIM[ALARM_MANAGER] defer completion " +
                                "${displayLabel((timer.totalMillis / 1000L).toInt(), timer.label)}"
                        )
                    }
                    continue
                }
                timer.remainingMillis = 0L
                timer.updatedAtElapsedMs = now
                if (completedSpec == null) {
                    completedSpec = CompletedTimerSpec(
                        durationSeconds = (timer.totalMillis / 1000L).toInt(),
                        label = timer.label,
                        sessionId = timer.sessionId,
                        sessionStartedAtEpochMs = timer.sessionStartedAtEpochMs,
                        extensionCount = timer.extensionCount
                    )
                }
                if (timer.deferredByDelayMode && trigger == CompletionTrigger.ALARM_MANAGER) {
                    logEvent(
                        "DELAY_SIM[ALARM_MANAGER] completed deferred timer " +
                            "${displayLabel((timer.totalMillis / 1000L).toInt(), timer.label)}"
                    )
                }
                completedHistoryRecords.add(
                    CompletedHistoryRecord(
                        sessionId = timer.sessionId,
                        label = timer.label,
                        durationSeconds = (timer.totalMillis / 1000L).toInt(),
                        startedAtEpochMs = timer.sessionStartedAtEpochMs,
                        endedAtEpochMs = System.currentTimeMillis(),
                        extensionCount = timer.extensionCount,
                        laps = timer.laps.toList()
                    )
                )
                iterator.remove()
                anyCompleted = true
                stateChanged = true
            }
        }
        completedHistoryRecords.forEach { record ->
            historyStore.recordHistory(
                sessionId = record.sessionId,
                label = record.label,
                durationSeconds = record.durationSeconds,
                startedAtEpochMs = record.startedAtEpochMs,
                endedAtEpochMs = record.endedAtEpochMs,
                status = TimerHistoryStatus.COMPLETED,
                extensionCount = record.extensionCount,
                laps = record.laps
            )
        }
        if (anyCompleted) {
            if (completedSpec != null) {
                lastCompletedSpec = completedSpec
                lastCompletedAtEpochMs = System.currentTimeMillis()
                logEvent(
                    "TIMER_COMPLETE[${trigger.tag}] ${displayLabel(completedSpec.durationSeconds, completedSpec.label)} " +
                        "(${formatDuration(completedSpec.durationSeconds)})"
                )
            }
            startCompletionAlert(trigger)
        }
        return stateChanged
    }

    private fun consumeActiveCompletionForExtend(
        sessionIdHint: Long,
        sessionStartedAtHint: Long,
        extensionCountHint: Int
    ): Boolean {
        if (!completionAlertActive) {
            logEvent("ALARM_EXTEND ignore reason=not_ringing sessionId=$sessionIdHint")
            return false
        }
        val activeSpec = lastCompletedSpec
        if (activeSpec == null) {
            logEvent("ALARM_EXTEND ignore reason=no_active_spec sessionId=$sessionIdHint")
            return false
        }
        if (sessionIdHint <= 0L || sessionIdHint != activeSpec.sessionId) {
            logEvent(
                "ALARM_EXTEND ignore reason=session_mismatch active=${activeSpec.sessionId} req=$sessionIdHint"
            )
            return false
        }
        if (
            activeSpec.sessionStartedAtEpochMs > 0L &&
            sessionStartedAtHint > 0L &&
            sessionStartedAtHint != activeSpec.sessionStartedAtEpochMs
        ) {
            logEvent(
                "ALARM_EXTEND ignore reason=start_mismatch active=${activeSpec.sessionStartedAtEpochMs} req=$sessionStartedAtHint"
            )
            return false
        }
        if (extensionCountHint >= 0 && extensionCountHint != activeSpec.extensionCount) {
            logEvent(
                "ALARM_EXTEND ignore reason=stale_extension active=${activeSpec.extensionCount} req=$extensionCountHint"
            )
            return false
        }
        logEvent("ALARM_EXTEND accept sessionId=${activeSpec.sessionId} extension=${activeSpec.extensionCount}")
        return true
    }

    private suspend fun recordHistory(
        timer: TimerEntry,
        endedAtEpochMs: Long,
        status: TimerHistoryStatus
    ) {
        historyStore.recordHistory(
            sessionId = timer.sessionId,
            label = timer.label,
            durationSeconds = (timer.totalMillis / 1000L).toInt(),
            startedAtEpochMs = timer.sessionStartedAtEpochMs,
            endedAtEpochMs = endedAtEpochMs,
            status = status,
            extensionCount = timer.extensionCount,
            laps = timer.laps.toList()
        )
    }

    private fun tickOne(timer: TimerEntry, now: Long) {
        if (timer.isPaused) return
        timer.remainingMillis = currentRemainingMillis(timer, now)
        timer.updatedAtElapsedMs = now
    }

    private fun currentRemainingMillis(timer: TimerEntry, nowElapsedMs: Long): Long {
        if (timer.isPaused) return timer.remainingMillis.coerceAtLeast(0L)
        return (expectedWakeElapsedMs(timer) - nowElapsedMs).coerceAtLeast(0L)
    }

    private fun resolveTimer(timerId: Int): TimerEntry? {
        val id = resolveTimerId(timerId) ?: return null
        return timers.firstOrNull { it.id == id }
    }

    private fun resolveTimerId(timerId: Int): Int? {
        return if (timerId == 0) timers.firstOrNull()?.id else timerId
    }

    private fun expectedWakeElapsedMs(timer: TimerEntry): Long {
        return if (timer.deferredByDelayMode && timer.deferredWakeElapsedMs > 0L) {
            timer.deferredWakeElapsedMs
        } else {
            timer.updatedAtElapsedMs + timer.remainingMillis
        }
    }

    private fun selectSoonestTimer(): TimerEntry? {
        val runningTimer = timers.asSequence()
            .filter { !it.isPaused }
            .minWithOrNull(
                compareBy<TimerEntry> { expectedWakeElapsedMs(it) }
                    .thenBy { it.id }
            )
        if (runningTimer != null) return runningTimer
        return timers.minWithOrNull(compareBy<TimerEntry> { it.remainingMillis }.thenBy { it.id })
    }

    private fun completionNotificationTitle(spec: CompletedTimerSpec?): String {
        if (spec == null || spec.durationSeconds <= 0) return getString(R.string.running_timer)
        val durationLabel = formatDuration(spec.durationSeconds)
        return if (spec.label.isBlank()) {
            durationLabel
        } else {
            "${spec.label} ($durationLabel)"
        }
    }

    private fun publishState() {
        val nowElapsed = SystemClock.elapsedRealtime()
        val exactAlarmAllowed = runCatching {
            val alarmManager = getSystemService(ALARM_SERVICE) as AlarmManager
            Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()
        }.getOrDefault(true)
        val primary = selectSoonestTimer()
        val active = timers.map { timer ->
            val remainingNow = currentRemainingMillis(timer, nowElapsed)
            ActiveTimerState(
                id = timer.id,
                totalMillis = timer.totalMillis,
                remainingMillis = remainingNow,
                label = timer.label,
                isPaused = timer.isPaused,
                updatedAtElapsedMs = timer.updatedAtElapsedMs,
                laps = timer.laps.toList()
            )
        }
        TimerRuntimeState.update(
            RunningTimerState(
                totalMillis = primary?.totalMillis ?: 0L,
                remainingMillis = primary?.let { currentRemainingMillis(it, nowElapsed) } ?: 0L,
                isRunning = primary != null,
                isPaused = primary?.isPaused == true,
                laps = primary?.laps?.toList().orEmpty(),
                activeTimers = active,
                isAlarmRinging = completionAlertActive,
                elapsedRealtimeMs = nowElapsed,
                scheduledWakeElapsedMs = scheduledWakeElapsedMs,
                exactAlarmAllowed = exactAlarmAllowed
            )
        )
    }

    private suspend fun onExactWake(intent: Intent) {
        val expectedElapsed = intent.getLongExtra(TimerServiceController.EXTRA_EXPECTED_ELAPSED_MS, -1L)
        if (scheduledWakeElapsedMs > 0L && expectedElapsed > 0L && expectedElapsed != scheduledWakeElapsedMs) {
            logEvent("ALARM_WAKE[ALARM_MANAGER] ignored stale expected=$expectedElapsed scheduled=$scheduledWakeElapsedMs")
            return
        }
        logEvent("ALARM_WAKE[ALARM_MANAGER] fired expected=$expectedElapsed")
        scheduledWakeElapsedMs = -1L
        if (timers.isEmpty()) {
            syncNotifications()
            return
        }
        val changed = tickTimers(CompletionTrigger.ALARM_MANAGER)
        if (changed) persistTimers()
        publishState()
        syncNotifications()
    }

    private fun scheduleExactWake() {
        val nowElapsed = SystemClock.elapsedRealtime()
        val nextWakeElapsed = timers
            .asSequence()
            .filter { !it.isPaused }
            .map { timer -> expectedWakeElapsedMs(timer) }
            .minOrNull()

        if (nextWakeElapsed == null) {
            cancelExactWake()
            return
        }
        if (nextWakeElapsed <= nowElapsed) {
            serviceScope.launch {
                val changed = tickTimers(CompletionTrigger.ALARM_MANAGER)
                if (changed) persistTimers()
                publishState()
                syncNotifications()
            }
            return
        }
        if (scheduledWakeElapsedMs == nextWakeElapsed) return

        val alarmManager = getSystemService(ALARM_SERVICE) as AlarmManager
        val pending = exactWakePendingIntent(nextWakeElapsed)
        val canUseExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()

        var mode = "inexact"
        runCatching {
            if (canUseExact) {
                mode = "exact"
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.ELAPSED_REALTIME_WAKEUP,
                    nextWakeElapsed,
                    pending
                )
            } else {
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.ELAPSED_REALTIME_WAKEUP,
                    nextWakeElapsed,
                    pending
                )
            }
        }.onFailure {
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.ELAPSED_REALTIME_WAKEUP,
                nextWakeElapsed,
                pending
            )
            mode = "fallback"
        }

        scheduledWakeElapsedMs = nextWakeElapsed
        val remainingMs = (nextWakeElapsed - nowElapsed).coerceAtLeast(0L)
        logEvent("ALARM_SCHEDULE[$mode] in=${remainingMs}ms atElapsed=$nextWakeElapsed")
        publishState()
    }

    private fun cancelExactWake() {
        if (scheduledWakeElapsedMs <= 0L) return
        val alarmManager = getSystemService(ALARM_SERVICE) as AlarmManager
        logEvent("ALARM_CANCEL elapsed=$scheduledWakeElapsedMs")
        alarmManager.cancel(exactWakePendingIntent(scheduledWakeElapsedMs))
        scheduledWakeElapsedMs = -1L
        publishState()
    }

    private fun exactWakePendingIntent(expectedElapsedMs: Long): PendingIntent {
        val intent = Intent(this, TimerForegroundService::class.java)
            .setAction(TimerServiceController.ACTION_EXACT_WAKE)
            .putExtra(TimerServiceController.EXTRA_EXPECTED_ELAPSED_MS, expectedElapsedMs)
        return PendingIntent.getForegroundService(
            this,
            950,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    private fun syncNotifications(completed: Boolean = false) {
        if (!quickNotificationDirty && !isNotificationActive(QUICK_NOTIFICATION_ID)) {
            quickNotificationDirty = true
        }
        if (quickNotificationDirty || completed) {
            quickNotificationDirty = !updateQuickNotification(completed)
        }
        if (timers.isNotEmpty()) {
            updateRunningNotification()
        } else {
            NotificationManagerCompat.from(this).cancel(RUNNING_NOTIFICATION_ID)
        }
        if (completionAlertActive) {
            updateCompletionNotification()
        } else if (!completed) {
            NotificationManagerCompat.from(this).cancel(COMPLETION_NOTIFICATION_ID)
        }
        scheduleExactWake()
        updateRunningNotificationRefreshLoop()
    }

    private fun ensureForegroundNotification() {
        val notification = buildQuickNotification()
        runCatching {
            startForeground(QUICK_NOTIFICATION_ID, notification)
            true
        }.onFailure {
            logEvent("FOREGROUND_ENSURE_FAIL ${it::class.java.simpleName}")
        }
    }

    private fun isNotificationActive(notificationId: Int): Boolean {
        val manager = ContextCompat.getSystemService(this, NotificationManager::class.java) ?: return false
        return runCatching {
            manager.activeNotifications.any { it.id == notificationId }
        }.getOrDefault(false)
    }

    private fun updateQuickNotification(completed: Boolean = false): Boolean {
        return notifySafely(
            QUICK_NOTIFICATION_ID,
            buildQuickNotification(completed)
        )
    }

    private fun updateRunningNotification() {
        if (timers.isEmpty()) return
        notifySafely(
            RUNNING_NOTIFICATION_ID,
            buildRunningNotification()
        )
    }

    private fun updateCompletionNotification() {
        notifySafely(
            COMPLETION_NOTIFICATION_ID,
            buildCompletionNotification()
        )
    }

    @SuppressLint("MissingPermission")
    private fun notifySafely(notificationId: Int, notification: Notification): Boolean {
        if (!hasNotificationPermission()) return false
        return runCatching {
            NotificationManagerCompat.from(this).notify(notificationId, notification)
            true
        }.onFailure {
            logEvent("NOTIFY_FAIL id=$notificationId ${it::class.java.simpleName}")
        }.getOrDefault(false)
    }

    private fun hasNotificationPermission(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun buildQuickNotification(completed: Boolean = false): Notification {
        val openIntent = PendingIntent.getActivity(
            this,
            1,
            mainActivityIntent(),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val builder = NotificationCompat.Builder(this, QUICK_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(openIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setContentTitle(
                if (completed) getString(R.string.quick_timer_ready) else getString(R.string.quick_actions)
            )
            .setContentText(getString(R.string.tap_to_open))

        presets.take(10).forEachIndexed { index, preset ->
            val actionLabel = if (preset.label.isBlank()) {
                formatDuration(preset.durationSeconds)
            } else {
                "${preset.label} ${formatDuration(preset.durationSeconds)}"
            }
            builder.addAction(
                0,
                actionLabel,
                startIntentForPreset(
                    preset.durationSeconds,
                    preset.label,
                    300 + index,
                    TimerServiceController.START_SOURCE_QUICK_ACTION
                )
            )
        }
        return builder.build()
    }

    private fun buildRunningNotification(): Notification {
        val primary = selectSoonestTimer() ?: return buildQuickNotification()
        val nowElapsed = SystemClock.elapsedRealtime()
        val remainingMillis = currentRemainingMillis(primary, nowElapsed)
        val openIntent = PendingIntent.getActivity(
            this,
            2,
            mainActivityIntent()
                .putExtra(MainActivity.EXTRA_FROM_RUNNING_NOTIFICATION, true)
                .putExtra(MainActivity.EXTRA_TARGET_TIMER_ID, primary.id),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val remainingText = formatDurationMillisFloor(remainingMillis)
        val durationLabel = formatDuration((primary.totalMillis / 1000L).toInt())
        val contentLabel = if (primary.label.isBlank()) {
            durationLabel
        } else {
            "${primary.label} ($durationLabel)"
        }
        val totalMillis = primary.totalMillis.coerceAtLeast(1L)
        val elapsedMillis = (totalMillis - remainingMillis).coerceIn(0L, totalMillis)
        val progressMax = 1000
        val progress = ((elapsedMillis * progressMax) / totalMillis).toInt()
        val expandedView = RemoteViews(packageName, R.layout.notification_running_expanded).apply {
            setTextViewText(R.id.running_title, contentLabel)
            setTextViewText(R.id.running_remaining, remainingText)
            setProgressBar(R.id.running_progress, progressMax, progress, false)
        }

        val builder = NotificationCompat.Builder(this, RUNNING_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(openIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setContentTitle(contentLabel)
            .setContentText(remainingText)
            .setSubText(null)
            .setCustomBigContentView(expandedView)
            .setStyle(
                NotificationCompat.DecoratedCustomViewStyle()
            )
            .addAction(
                if (primary.isPaused) android.R.drawable.ic_media_play else android.R.drawable.ic_media_pause,
                if (primary.isPaused) getString(R.string.resume) else getString(R.string.pause),
                actionIntent(
                    if (primary.isPaused) TimerServiceController.ACTION_RESUME else TimerServiceController.ACTION_PAUSE,
                    202,
                    primary.id
                )
            )
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                getString(R.string.stop),
                actionIntent(TimerServiceController.ACTION_STOP, 203, primary.id)
            )
            .addAction(
                android.R.drawable.ic_menu_recent_history,
                getString(R.string.lap),
                actionIntent(TimerServiceController.ACTION_LAP, 201, primary.id)
            )

        builder
            .setWhen(0L)
            .setShowWhen(false)
            .setUsesChronometer(false)
        return builder.build()
    }

    private fun formatDurationMillisFloor(totalMillis: Long): String {
        val clamped = totalMillis.coerceAtLeast(0L)
        val totalSeconds = clamped / 1000L
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return if (hours > 0) {
            String.format(java.util.Locale.getDefault(), "%02d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(java.util.Locale.getDefault(), "%02d:%02d", minutes, seconds)
        }
    }

    private fun buildCompletionNotification(): Notification {
        val spec = lastCompletedSpec
        val fullScreenIntent = fullScreenAlarmPendingIntent()
        val openMainIntent = completionOpenAppPendingIntent()
        val extendIntent = completionExtendPendingIntent(spec)
        val builder = NotificationCompat.Builder(this, COMPLETION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(openMainIntent)
            .setContentTitle(completionNotificationTitle(spec))
            .setContentText(getString(R.string.tap_to_stop_alarm))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setAutoCancel(false)
            .setOnlyAlertOnce(true)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setCustomHeadsUpContentView(
                buildCompletionHeadsUpView(
                    spec = spec,
                    openMainIntent = openMainIntent,
                    extendIntent = extendIntent
                )
            )
            .setStyle(NotificationCompat.DecoratedCustomViewStyle())

        if (isDeviceLocked()) {
            builder.setFullScreenIntent(fullScreenIntent, true)
        }

        if (extendIntent != null) {
            builder.addAction(
                android.R.drawable.ic_popup_sync,
                getString(R.string.extend_time),
                extendIntent
            )
        }
        return builder.build()
    }

    private fun buildCompletionHeadsUpView(
        spec: CompletedTimerSpec?,
        openMainIntent: PendingIntent,
        extendIntent: PendingIntent?
    ): RemoteViews {
        val view = RemoteViews(packageName, R.layout.notification_completion_heads_up)
        view.setTextViewText(R.id.completion_heads_up_title, completionNotificationTitle(spec))
        view.setTextViewText(R.id.completion_heads_up_message, getString(R.string.tap_to_stop_alarm))
        view.setTextViewText(R.id.completion_heads_up_extend, getString(R.string.extend_time))
        view.setOnClickPendingIntent(R.id.completion_heads_up_root, openMainIntent)

        if (extendIntent != null) {
            view.setViewVisibility(R.id.completion_heads_up_extend, android.view.View.VISIBLE)
            view.setOnClickPendingIntent(R.id.completion_heads_up_extend, extendIntent)
        } else {
            view.setViewVisibility(R.id.completion_heads_up_extend, android.view.View.GONE)
        }
        return view
    }

    private fun completionOpenAppPendingIntent(): PendingIntent {
        return PendingIntent.getActivity(
            this,
            REQUEST_CODE_COMPLETION_OPEN_APP,
            mainActivityIntent()
                .putExtra(MainActivity.EXTRA_FROM_ALARM, true)
                .putExtra(MainActivity.EXTRA_FROM_NOTIFICATION, true),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    private fun completionExtendPendingIntent(spec: CompletedTimerSpec?): PendingIntent? {
        val activeSpec = spec ?: return null
        if (activeSpec.durationSeconds <= 0) return null
        val intent = Intent(this, TimerForegroundService::class.java)
            .setAction(TimerServiceController.ACTION_START)
            .putExtra(TimerServiceController.EXTRA_DURATION_SECONDS, activeSpec.durationSeconds)
            .putExtra(TimerServiceController.EXTRA_LABEL, activeSpec.label)
            .putExtra(TimerServiceController.EXTRA_START_SOURCE, TimerServiceController.START_SOURCE_EXTEND)
            .putExtra(TimerServiceController.EXTRA_SESSION_ID, activeSpec.sessionId)
            .putExtra(TimerServiceController.EXTRA_SESSION_STARTED_AT_MS, activeSpec.sessionStartedAtEpochMs)
            .putExtra(TimerServiceController.EXTRA_EXTENSION_COUNT, activeSpec.extensionCount)
        return PendingIntent.getService(
            this,
            REQUEST_CODE_COMPLETION_EXTEND,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_ONE_SHOT
        )
    }

    private fun isDeviceLocked(): Boolean {
        val keyguardManager = ContextCompat.getSystemService(this, KeyguardManager::class.java)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
            keyguardManager?.isDeviceLocked == true
        } else {
            keyguardManager?.isKeyguardLocked == true
        }
    }

    private fun registerInteractiveStateReceiver() {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_USER_PRESENT)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                addAction(Intent.ACTION_USER_UNLOCKED)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                addAction(PowerManager.ACTION_DEVICE_IDLE_MODE_CHANGED)
            }
        }
        ContextCompat.registerReceiver(
            this,
            interactiveStateReceiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    private fun unregisterInteractiveStateReceiver() {
        runCatching { unregisterReceiver(interactiveStateReceiver) }
    }

    private fun isInteractiveUpdateAllowed(): Boolean {
        val powerManager = ContextCompat.getSystemService(this, PowerManager::class.java) ?: return false
        return powerManager.isInteractive
    }

    private fun powerStateSummary(): String {
        val powerManager = ContextCompat.getSystemService(this, PowerManager::class.java)
        val interactive = powerManager?.isInteractive == true
        val idle = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            powerManager?.isDeviceIdleMode == true
        } else {
            false
        }
        val locked = isDeviceLocked()
        return "interactive=$interactive idle=$idle locked=$locked"
    }

    private fun shouldRunRunningNotificationRefresh(): Boolean {
        return timers.any { !it.isPaused } &&
            !completionAlertActive &&
            hasNotificationPermission() &&
            isInteractiveUpdateAllowed()
    }

    private fun restartRunningNotificationRefreshLoop() {
        runningNotificationRefreshJob?.cancel()
        runningNotificationRefreshJob = null
        updateRunningNotificationRefreshLoop()
    }

    private fun updateRunningNotificationRefreshLoop() {
        if (runningNotificationRefreshJob?.isActive == true) return

        runningNotificationRefreshJob = serviceScope.launch {
            var pausedByInteractiveState = false
            if (shouldRunRunningNotificationRefresh()) {
                val primary = selectSoonestTimer()
                updateRunningNotification()
                if (primary != null) {
                    val remaining = currentRemainingMillis(primary, SystemClock.elapsedRealtime())
                    logEvent("LOOP[REFRESH_LOOP] tick timerId=${primary.id} remaining=${remaining}ms")
                }
            }
            while (isActive) {
                val keepLoop =
                    timers.any { !it.isPaused } && !completionAlertActive && hasNotificationPermission()
                if (!keepLoop) break

                val sleepMs = if (shouldRunRunningNotificationRefresh()) {
                    if (pausedByInteractiveState) {
                        pausedByInteractiveState = false
                        logEvent("POWER[WAKE] refresh_resume ${powerStateSummary()}")
                    }
                    val primary = selectSoonestTimer()
                    updateRunningNotification()
                    if (primary != null) {
                        val remaining = currentRemainingMillis(primary, SystemClock.elapsedRealtime())
                        logEvent("LOOP[REFRESH_LOOP] tick timerId=${primary.id} remaining=${remaining}ms")
                    }
                    val now = SystemClock.elapsedRealtime()
                    (1000L - (now % 1000L)).coerceAtLeast(120L)
                } else {
                    if (!pausedByInteractiveState) {
                        pausedByInteractiveState = true
                        logEvent("POWER[SLEEP] refresh_pause ${powerStateSummary()}")
                    }
                    5000L
                }
                delay(sleepMs)
            }
            logEvent("LOOP[REFRESH_LOOP] stop timers_running=${timers.any { !it.isPaused }} alarm=$completionAlertActive")
            runningNotificationRefreshJob = null
        }
    }

    private fun startCompletionAlert(trigger: CompletionTrigger) {
        if (completionAlertActive) return
        completionAlertActive = true
        val summary = lastCompletedSpec?.let { spec ->
            "${displayLabel(spec.durationSeconds, spec.label)} (${formatDuration(spec.durationSeconds)})"
        } ?: "unknown"
        logEvent("ALARM_RING[${trigger.tag}] start $summary")
        if (alarmSoundEnabled) {
            if (completionRingtone == null) {
                val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                    ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                completionRingtone = RingtoneManager.getRingtone(this, uri)?.apply {
                    isLooping = true
                    audioAttributes = AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                }
            }
            runCatching { completionRingtone?.play() }
        }

        if (alarmVibrationEnabled) {
            if (completionVibrator == null) {
                completionVibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    val manager = getSystemService(VIBRATOR_MANAGER_SERVICE) as VibratorManager
                    manager.defaultVibrator
                } else {
                    @Suppress("DEPRECATION")
                    getSystemService(VIBRATOR_SERVICE) as Vibrator
                }
            }
            val pattern = longArrayOf(0, 400, 250, 400)
            runCatching {
                completionVibrator?.vibrate(VibrationEffect.createWaveform(pattern, 0))
            }
        }
    }

    private fun stopCompletionAlert() {
        completionAlertActive = false
        runCatching { completionRingtone?.stop() }
        runCatching { completionVibrator?.cancel() }
    }

    private fun acknowledgeCompletionAlert() {
        logEvent("ALARM_ACK user action")
        stopCompletionAlert()
        NotificationManagerCompat.from(this).cancel(COMPLETION_NOTIFICATION_ID)
        publishState()
        syncNotifications()
    }

    private fun vibrateForWidgetStart() {
        val vibrator = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val manager = getSystemService(VIBRATOR_MANAGER_SERVICE) as VibratorManager
                manager.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                getSystemService(VIBRATOR_SERVICE) as Vibrator
            }
        }.getOrNull() ?: return

        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(70L, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(70L)
            }
        }
    }

    private fun actionIntent(action: String, requestCode: Int, timerId: Int = 0): PendingIntent {
        val intent = Intent(this, TimerForegroundService::class.java)
            .setAction(action)
            .putExtra(TimerServiceController.EXTRA_TIMER_ID, timerId)
        return PendingIntent.getService(
            this,
            requestCode,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    private fun startIntentForPreset(
        durationSeconds: Int,
        label: String,
        requestCode: Int,
        startSource: String,
        sessionId: Long = 0L,
        sessionStartedAtEpochMs: Long = 0L,
        extensionCount: Int = -1
    ): PendingIntent {
        val intent = Intent(this, TimerForegroundService::class.java)
            .setAction(TimerServiceController.ACTION_START)
            .putExtra(TimerServiceController.EXTRA_DURATION_SECONDS, durationSeconds)
            .putExtra(TimerServiceController.EXTRA_LABEL, label)
            .putExtra(TimerServiceController.EXTRA_START_SOURCE, startSource)
        if (sessionId > 0L) {
            intent.putExtra(TimerServiceController.EXTRA_SESSION_ID, sessionId)
            intent.putExtra(TimerServiceController.EXTRA_SESSION_STARTED_AT_MS, sessionStartedAtEpochMs)
            intent.putExtra(TimerServiceController.EXTRA_EXTENSION_COUNT, extensionCount)
        }
        return PendingIntent.getService(
            this,
            requestCode,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    private fun mainActivityIntent(): Intent {
        return Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
    }

    private fun clearStaleNotifications() {
        val manager = NotificationManagerCompat.from(this)
        manager.cancel(RUNNING_NOTIFICATION_ID)
        manager.cancel(COMPLETION_NOTIFICATION_ID)
        for (id in 3000..3999) {
            manager.cancel(id)
        }
    }

    private fun createChannels() {
        val quickChannel = NotificationChannel(
            QUICK_CHANNEL_ID,
            getString(R.string.notification_channel_quick_name),
            NotificationManager.IMPORTANCE_LOW
        )
        quickChannel.description = getString(R.string.notification_channel_quick_description)
        quickChannel.enableVibration(false)
        quickChannel.setSound(null, null)

        val runningChannel = NotificationChannel(
            RUNNING_CHANNEL_ID,
            getString(R.string.notification_channel_running_name),
            NotificationManager.IMPORTANCE_LOW
        )
        runningChannel.description = getString(R.string.notification_channel_running_description)
        runningChannel.enableVibration(false)
        runningChannel.setSound(null, null)

        val completionChannel = NotificationChannel(
            COMPLETION_CHANNEL_ID,
            getString(R.string.notification_channel_completion_name),
            NotificationManager.IMPORTANCE_HIGH
        )
        completionChannel.description = getString(R.string.notification_channel_completion_description)
        completionChannel.enableVibration(true)
        completionChannel.enableLights(true)
        completionChannel.lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        val alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        completionChannel.setSound(
            alarmUri,
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        )
        val manager = ContextCompat.getSystemService(this, NotificationManager::class.java)
        manager?.createNotificationChannel(quickChannel)
        manager?.createNotificationChannel(runningChannel)
        manager?.createNotificationChannel(completionChannel)
    }

    private fun fullScreenAlarmPendingIntent(): PendingIntent {
        val spec = lastCompletedSpec
        val label = spec?.label.orEmpty()
        val durationSeconds = spec?.durationSeconds ?: 0
        val sessionId = spec?.sessionId ?: 0L
        val sessionStartedAt = spec?.sessionStartedAtEpochMs ?: 0L
        val extensionCount = spec?.extensionCount ?: -1
        val completedAt = if (lastCompletedAtEpochMs > 0L) lastCompletedAtEpochMs else System.currentTimeMillis()
        return PendingIntent.getActivity(
            this,
            9,
            Intent(this, com.quicktimer.ui.AlarmFullscreenActivity::class.java)
                .putExtra(EXTRA_ALARM_LABEL, label)
                .putExtra(EXTRA_ALARM_DURATION_SECONDS, durationSeconds)
                .putExtra(EXTRA_ALARM_SESSION_ID, sessionId)
                .putExtra(EXTRA_ALARM_SESSION_STARTED_AT_MS, sessionStartedAt)
                .putExtra(EXTRA_ALARM_EXTENSION_COUNT, extensionCount)
                .putExtra(EXTRA_ALARM_COMPLETED_AT_MS, completedAt)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    companion object {
        private const val DELAY_SIMULATION_WAKE_MS = 250L
        private const val LAP_SEPARATOR = "\u001F"
        private const val REQUEST_CODE_COMPLETION_OPEN_APP = 910
        private const val REQUEST_CODE_COMPLETION_EXTEND = 901
        private const val QUICK_CHANNEL_ID = "quick_timer_quick_channel_v2"
        private const val RUNNING_CHANNEL_ID = "quick_timer_running_channel_v2"
        private const val COMPLETION_CHANNEL_ID = TimerServiceController.COMPLETION_CHANNEL_ID
        private const val QUICK_NOTIFICATION_ID = 1001
        private const val RUNNING_NOTIFICATION_ID = 1002
        private const val COMPLETION_NOTIFICATION_ID = 1003
        const val EXTRA_ALARM_LABEL = "alarm_label"
        const val EXTRA_ALARM_DURATION_SECONDS = "alarm_duration_seconds"
        const val EXTRA_ALARM_SESSION_ID = "alarm_session_id"
        const val EXTRA_ALARM_SESSION_STARTED_AT_MS = "alarm_session_started_at_ms"
        const val EXTRA_ALARM_EXTENSION_COUNT = "alarm_extension_count"
        const val EXTRA_ALARM_COMPLETED_AT_MS = "alarm_completed_at_ms"
    }
}

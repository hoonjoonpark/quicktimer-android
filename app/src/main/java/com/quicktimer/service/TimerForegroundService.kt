package com.quicktimer.service

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
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
        TICK_LOOP("TICK_LOOP"),
        ALARM_MANAGER("ALARM_MANAGER")
    }

    private val serviceScope = CoroutineScope(
        SupervisorJob() + Dispatchers.Default.limitedParallelism(1)
    )

    private var presets: List<TimerPreset> = defaultPresets()
    private var tickerJob: Job? = null

    private val timers = mutableListOf<TimerEntry>()
    private var nextTimerId = 1
    private var nextSessionId = 1L

    private var completionRingtone: Ringtone? = null
    private var completionVibrator: Vibrator? = null
    private var completionAlertActive = false
    private var lastCompletedSpec: CompletedTimerSpec? = null
    private var scheduledWakeElapsedMs: Long = -1L
    private var delayInterventionEnabled = false
    private var alarmSoundEnabled = true
    private var alarmVibrationEnabled = true
    private var quickNotificationDirty = true
    private lateinit var logStore: LogStore
    private lateinit var runningTimerStore: RunningTimerStore
    private lateinit var historyStore: TimerHistoryStore
    private var restoreJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        createChannels()
        clearStaleNotifications()
        startForeground(QUICK_NOTIFICATION_ID, buildQuickNotification())

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
            acknowledgeCompletionAlert()
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
                    stopCompletionAlert()
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
                    logEvent("PAUSE request timerId=$timerId")
                    pauseTimer(timerId)
                }

                TimerServiceController.ACTION_RESUME -> {
                    val timerId = intent.getIntExtra(TimerServiceController.EXTRA_TIMER_ID, 0)
                    logEvent("RESUME request timerId=$timerId")
                    resumeTimer(timerId)
                }

                TimerServiceController.ACTION_STOP -> {
                    val timerId = intent.getIntExtra(TimerServiceController.EXTRA_TIMER_ID, 0)
                    logEvent("STOP request timerId=$timerId")
                    stopTimer(timerId)
                }

                TimerServiceController.ACTION_LAP -> {
                    val timerId = intent.getIntExtra(TimerServiceController.EXTRA_TIMER_ID, 0)
                    logEvent("LAP request timerId=$timerId")
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
        tickerJob?.cancel()
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
        if (timers.any { !it.isPaused }) {
            ensureTicker(reschedule = true)
        }
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
        ensureTicker(reschedule = true)
        publishState()
        syncNotifications()
    }

    private suspend fun pauseTimer(timerId: Int) {
        val timer = resolveTimer(timerId) ?: return
        if (timer.isPaused) return
        val now = SystemClock.elapsedRealtime()
        tickOne(timer, now)
        timer.isPaused = true
        timer.updatedAtElapsedMs = now
        if (timers.none { !it.isPaused }) {
            tickerJob?.cancel()
            tickerJob = null
        }
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
        ensureTicker(reschedule = true)
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
        if (timers.none { !it.isPaused }) {
            tickerJob?.cancel()
            tickerJob = null
        }
        if (timers.isEmpty()) stopCompletionAlert()
        persistTimers()
        publishState()
        syncNotifications()
    }

    private suspend fun recordLap(timerId: Int) {
        val timer = resolveTimer(timerId) ?: return
        if (timer.isPaused) return
        tickOne(timer, SystemClock.elapsedRealtime())
        val lapValue = formatDurationMillis(timer.remainingMillis)
        val previousLap = timer.laps.firstOrNull()
        if (previousLap == lapValue) return
        timer.laps.add(0, lapValue)
        if (timer.laps.size > 20) timer.laps.removeAt(timer.laps.lastIndex)
        persistTimers()
        publishState()
        syncNotifications()
    }

    private fun ensureTicker(reschedule: Boolean = false) {
        if (reschedule && tickerJob?.isActive == true) {
            tickerJob?.cancel()
            tickerJob = null
        }
        if (tickerJob?.isActive == true) return
        if (timers.none { !it.isPaused }) return
        tickerJob = serviceScope.launch {
            var nextTick = SystemClock.elapsedRealtime() + 1000L
            while (timers.any { !it.isPaused }) {
                val now = SystemClock.elapsedRealtime()
                val sleepMs = (nextTick - now).coerceAtLeast(1L)
                delay(sleepMs)
                val changed = tickTimers(CompletionTrigger.TICK_LOOP)
                if (changed) persistTimers()
                publishState()
                syncNotifications()
                val afterTick = SystemClock.elapsedRealtime()
                do {
                    nextTick += 1000L
                } while (nextTick <= afterTick)
            }
            tickerJob = null
        }
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
            tickOne(timer, now)
            if (timer.remainingMillis <= 0L) {
                if (trigger == CompletionTrigger.TICK_LOOP && delayInterventionEnabled) {
                    if (!timer.deferredByDelayMode) {
                        timer.deferredByDelayMode = true
                        timer.deferredWakeElapsedMs = now + DELAY_SIMULATION_WAKE_MS
                        stateChanged = true
                        logEvent(
                            "DELAY_SIM[TICK_LOOP] defer completion to ALARM_MANAGER " +
                                "${displayLabel((timer.totalMillis / 1000L).toInt(), timer.label)}"
                        )
                    }
                    continue
                }
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
                logEvent(
                    "TIMER_COMPLETE[${trigger.tag}] ${displayLabel(completedSpec.durationSeconds, completedSpec.label)} " +
                        "(${formatDuration(completedSpec.durationSeconds)})"
                )
            }
            startCompletionAlert(trigger)
        }
        return stateChanged
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
        val delta = (now - timer.updatedAtElapsedMs).coerceAtLeast(0L)
        if (delta <= 0L) return
        timer.remainingMillis = max(0L, timer.remainingMillis - delta)
        timer.updatedAtElapsedMs = now
    }

    private fun resolveTimer(timerId: Int): TimerEntry? {
        val id = resolveTimerId(timerId) ?: return null
        return timers.firstOrNull { it.id == id }
    }

    private fun resolveTimerId(timerId: Int): Int? {
        return if (timerId == 0) timers.firstOrNull()?.id else timerId
    }

    private fun publishState() {
        val primary = timers.firstOrNull()
        val active = timers.mapIndexed { index, timer ->
            ActiveTimerState(
                id = timer.id,
                totalMillis = timer.totalMillis,
                remainingMillis = timer.remainingMillis,
                label = timer.label,
                isPrimary = index == 0,
                isPaused = timer.isPaused,
                laps = timer.laps.toList()
            )
        }
        TimerRuntimeState.update(
            RunningTimerState(
                totalMillis = primary?.totalMillis ?: 0L,
                remainingMillis = primary?.remainingMillis ?: 0L,
                isRunning = primary != null,
                isPaused = primary?.isPaused == true,
                laps = primary?.laps?.toList().orEmpty(),
                activeTimers = active,
                isAlarmRinging = completionAlertActive
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
        val nextWakeElapsed = timers
            .asSequence()
            .filter { !it.isPaused }
            .map { timer ->
                if (timer.deferredByDelayMode && timer.deferredWakeElapsedMs > 0L) {
                    timer.deferredWakeElapsedMs
                } else {
                    timer.updatedAtElapsedMs + timer.remainingMillis
                }
            }
            .minOrNull()

        if (nextWakeElapsed == null) {
            cancelExactWake()
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
        val remainingMs = (nextWakeElapsed - SystemClock.elapsedRealtime()).coerceAtLeast(0L)
        logEvent("ALARM_SCHEDULE[$mode] in=${remainingMs}ms atElapsed=$nextWakeElapsed")
    }

    private fun cancelExactWake() {
        if (scheduledWakeElapsedMs <= 0L) return
        val alarmManager = getSystemService(ALARM_SERVICE) as AlarmManager
        logEvent("ALARM_CANCEL elapsed=$scheduledWakeElapsedMs")
        alarmManager.cancel(exactWakePendingIntent(scheduledWakeElapsedMs))
        scheduledWakeElapsedMs = -1L
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
            Intent(this, MainActivity::class.java),
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
        val primary = timers.firstOrNull() ?: return buildQuickNotification()
        val openIntent = PendingIntent.getActivity(
            this,
            2,
            Intent(this, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_FROM_RUNNING_NOTIFICATION, true)
                .putExtra(MainActivity.EXTRA_TARGET_TIMER_ID, primary.id),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val remainingText = formatDurationMillis(primary.remainingMillis)
        val durationLabel = formatDuration((primary.totalMillis / 1000L).toInt())
        val contentLabel = if (primary.label.isBlank()) {
            durationLabel
        } else {
            "${primary.label} ($durationLabel)"
        }

        return NotificationCompat.Builder(this, RUNNING_CHANNEL_ID)
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
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .setBigContentTitle(contentLabel)
                    .bigText(remainingText)
            )
            .setWhen(0L)
            .setShowWhen(false)
            .setUsesChronometer(false)
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
            .build()
    }

    private fun buildCompletionNotification(): Notification {
        val openIntent = PendingIntent.getActivity(
            this,
            9,
            Intent(this, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_FROM_ALARM, true)
                .putExtra(MainActivity.EXTRA_FROM_NOTIFICATION, true),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val builder = NotificationCompat.Builder(this, COMPLETION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(openIntent)
            .setContentTitle(getString(R.string.running_timer))
            .setContentText(getString(R.string.tap_to_stop_alarm))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setOngoing(true)
            .setAutoCancel(false)
            .setOnlyAlertOnce(true)

        val spec = lastCompletedSpec
        if (spec != null && spec.durationSeconds > 0) {
            builder.addAction(
                android.R.drawable.ic_popup_sync,
                getString(R.string.extend_time),
                startIntentForPreset(
                    spec.durationSeconds,
                    spec.label,
                    901,
                    TimerServiceController.START_SOURCE_EXTEND,
                    sessionId = spec.sessionId,
                    sessionStartedAtEpochMs = spec.sessionStartedAtEpochMs,
                    extensionCount = spec.extensionCount
                )
            )
        }

        return builder.build()
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
            "Quick timer",
            NotificationManager.IMPORTANCE_LOW
        )
        quickChannel.description = "Quick action buttons"
        quickChannel.enableVibration(false)
        quickChannel.setSound(null, null)

        val runningChannel = NotificationChannel(
            RUNNING_CHANNEL_ID,
            "Running timer",
            NotificationManager.IMPORTANCE_LOW
        )
        runningChannel.description = "Running countdown and controls"
        runningChannel.enableVibration(false)
        runningChannel.setSound(null, null)

        val completionChannel = NotificationChannel(
            COMPLETION_CHANNEL_ID,
            "Timer complete",
            NotificationManager.IMPORTANCE_HIGH
        )
        completionChannel.description = "Timer completion alert"
        val manager = ContextCompat.getSystemService(this, NotificationManager::class.java)
        manager?.createNotificationChannel(quickChannel)
        manager?.createNotificationChannel(runningChannel)
        manager?.createNotificationChannel(completionChannel)
    }

    companion object {
        private const val DELAY_SIMULATION_WAKE_MS = 250L
        private const val LAP_SEPARATOR = "\u001F"
        private const val QUICK_CHANNEL_ID = "quick_timer_quick_channel_v2"
        private const val RUNNING_CHANNEL_ID = "quick_timer_running_channel_v2"
        private const val COMPLETION_CHANNEL_ID = "quick_timer_completion_channel_v1"
        private const val QUICK_NOTIFICATION_ID = 1001
        private const val RUNNING_NOTIFICATION_ID = 1002
        private const val COMPLETION_NOTIFICATION_ID = 1003
    }
}

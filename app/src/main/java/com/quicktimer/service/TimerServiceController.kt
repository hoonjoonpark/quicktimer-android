package com.quicktimer.service

import android.content.Context
import android.content.Intent

object TimerServiceController {
    const val ACTION_START = "com.quicktimer.action.START"
    const val ACTION_PAUSE = "com.quicktimer.action.PAUSE"
    const val ACTION_RESUME = "com.quicktimer.action.RESUME"
    const val ACTION_STOP = "com.quicktimer.action.STOP"
    const val ACTION_LAP = "com.quicktimer.action.LAP"
    const val ACTION_REFRESH = "com.quicktimer.action.REFRESH"
    const val ACTION_ACK_ALARM = "com.quicktimer.action.ACK_ALARM"
    const val ACTION_EXACT_WAKE = "com.quicktimer.action.EXACT_WAKE"

    const val EXTRA_DURATION_SECONDS = "duration_seconds"
    const val EXTRA_LABEL = "label"
    const val EXTRA_TIMER_ID = "timer_id"
    const val EXTRA_EXPECTED_ELAPSED_MS = "expected_elapsed_ms"
    const val EXTRA_START_SOURCE = "start_source"
    const val EXTRA_SESSION_ID = "session_id"
    const val EXTRA_SESSION_STARTED_AT_MS = "session_started_at_ms"
    const val EXTRA_EXTENSION_COUNT = "extension_count"

    const val START_SOURCE_USER = "user_start"
    const val START_SOURCE_QUICK_ACTION = "quick_action"
    const val START_SOURCE_EXTEND = "extend_action"
    const val START_SOURCE_WIDGET = "widget"
    const val START_SOURCE_UNKNOWN = "unknown"
    const val COMPLETION_CHANNEL_ID = "quick_timer_completion_channel_v3"

    fun ensureService(context: Context) {
        val intent = Intent(context, TimerForegroundService::class.java)
        context.startForegroundService(intent)
    }

    fun startTimer(
        context: Context,
        durationSeconds: Int,
        label: String = "",
        startSource: String = START_SOURCE_USER
    ) {
        if (durationSeconds <= 0) return
        context.startForegroundService(
            buildStartIntent(
                context = context,
                durationSeconds = durationSeconds,
                label = label,
                startSource = startSource
            )
        )
    }

    fun pause(context: Context, timerId: Int = 0) {
        val intent = Intent(context, TimerForegroundService::class.java).setAction(ACTION_PAUSE)
            .putExtra(EXTRA_TIMER_ID, timerId)
        context.startForegroundService(intent)
    }

    fun resume(context: Context, timerId: Int = 0) {
        val intent = Intent(context, TimerForegroundService::class.java).setAction(ACTION_RESUME)
            .putExtra(EXTRA_TIMER_ID, timerId)
        context.startForegroundService(intent)
    }

    fun stop(context: Context, timerId: Int = 0) {
        val intent = Intent(context, TimerForegroundService::class.java).setAction(ACTION_STOP)
            .putExtra(EXTRA_TIMER_ID, timerId)
        context.startForegroundService(intent)
    }

    fun lap(context: Context, timerId: Int = 0) {
        val intent = Intent(context, TimerForegroundService::class.java).setAction(ACTION_LAP)
            .putExtra(EXTRA_TIMER_ID, timerId)
        context.startForegroundService(intent)
    }

    fun refreshQuickActions(context: Context) {
        val intent = Intent(context, TimerForegroundService::class.java).setAction(ACTION_REFRESH)
        context.startForegroundService(intent)
    }

    fun extendFromAlarm(
        context: Context,
        durationSeconds: Int,
        label: String,
        sessionId: Long,
        sessionStartedAtEpochMs: Long,
        extensionCount: Int
    ) {
        if (durationSeconds <= 0 || sessionId <= 0L) return
        context.startForegroundService(
            buildStartIntent(
                context = context,
                durationSeconds = durationSeconds,
                label = label,
                startSource = START_SOURCE_EXTEND,
                sessionId = sessionId,
                sessionStartedAtEpochMs = sessionStartedAtEpochMs,
                extensionCount = extensionCount
            )
        )
    }

    fun startFromWidget(
        context: Context,
        durationSeconds: Int,
        label: String
    ) {
        startTimer(
            context = context,
            durationSeconds = durationSeconds,
            label = label,
            startSource = START_SOURCE_WIDGET
        )
    }

    fun acknowledgeAlarm(context: Context) {
        val intent = Intent(context, TimerForegroundService::class.java).setAction(ACTION_ACK_ALARM)
        runCatching { context.startService(intent) }
            .onFailure { context.startForegroundService(intent) }
    }

    private fun buildStartIntent(
        context: Context,
        durationSeconds: Int,
        label: String,
        startSource: String,
        sessionId: Long = 0L,
        sessionStartedAtEpochMs: Long = 0L,
        extensionCount: Int = -1
    ): Intent {
        return Intent(context, TimerForegroundService::class.java)
            .setAction(ACTION_START)
            .putExtra(EXTRA_DURATION_SECONDS, durationSeconds)
            .putExtra(EXTRA_LABEL, label)
            .putExtra(EXTRA_START_SOURCE, startSource)
            .apply {
                if (sessionId > 0L) {
                    putExtra(EXTRA_SESSION_ID, sessionId)
                    putExtra(EXTRA_SESSION_STARTED_AT_MS, sessionStartedAtEpochMs)
                    putExtra(EXTRA_EXTENSION_COUNT, extensionCount)
                }
            }
    }
}

package com.quicktimer.ui

import android.app.KeyguardManager
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.quicktimer.R
import com.quicktimer.data.displayLabel
import com.quicktimer.data.formatDuration
import com.quicktimer.service.TimerForegroundService
import com.quicktimer.service.TimerRuntimeState
import com.quicktimer.service.TimerServiceController
import com.quicktimer.ui.theme.QuickTimerTheme
import kotlinx.coroutines.delay
import java.util.Locale

class AlarmFullscreenActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
            )
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
            keyguardManager?.requestDismissKeyguard(this, null)
        }

        val label = intent.getStringExtra(TimerForegroundService.EXTRA_ALARM_LABEL).orEmpty()
        val durationSeconds = intent.getIntExtra(TimerForegroundService.EXTRA_ALARM_DURATION_SECONDS, 0)
        val sessionId = intent.getLongExtra(TimerForegroundService.EXTRA_ALARM_SESSION_ID, 0L)
        val sessionStartedAt = intent.getLongExtra(TimerForegroundService.EXTRA_ALARM_SESSION_STARTED_AT_MS, 0L)
        val extensionCount = intent.getIntExtra(TimerForegroundService.EXTRA_ALARM_EXTENSION_COUNT, -1)
        val completedAtEpochMs = intent.getLongExtra(
            TimerForegroundService.EXTRA_ALARM_COMPLETED_AT_MS,
            System.currentTimeMillis()
        )
        val isPreview = intent.getBooleanExtra(EXTRA_PREVIEW_MODE, false)

        setContent {
            QuickTimerTheme(themeConfig = (application as com.quicktimer.QuickTimerApplication).themeConfig) {
                AlarmFullscreenScreen(
                    label = label,
                    durationSeconds = durationSeconds,
                    completedAtEpochMs = completedAtEpochMs,
                    autoCloseWhenAlarmStops = !isPreview,
                    onExtend = {
                        TimerServiceController.extendFromAlarm(
                            context = this,
                            durationSeconds = durationSeconds,
                            label = label,
                            sessionId = sessionId,
                            sessionStartedAtEpochMs = sessionStartedAt,
                            extensionCount = extensionCount
                        )
                        finish()
                    },
                    onDismiss = {
                        if (!isPreview) {
                            TimerServiceController.acknowledgeAlarm(this)
                        }
                        finish()
                    },
                    onAutoClose = { finish() }
                )
            }
        }
    }

    companion object {
        const val EXTRA_PREVIEW_MODE = "preview_mode"
    }
}

@Composable
private fun AlarmFullscreenScreen(
    label: String,
    durationSeconds: Int,
    completedAtEpochMs: Long,
    autoCloseWhenAlarmStops: Boolean,
    onExtend: () -> Unit,
    onDismiss: () -> Unit,
    onAutoClose: () -> Unit
) {
    val runtimeState by TimerRuntimeState.state.collectAsStateWithLifecycle()
    LaunchedEffect(runtimeState.isAlarmRinging, autoCloseWhenAlarmStops) {
        if (autoCloseWhenAlarmStops && !runtimeState.isAlarmRinging) onAutoClose()
    }

    var nowEpochMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            nowEpochMs = System.currentTimeMillis()
            delay(200L)
        }
    }
    val overtimeText = formatOvertime(nowEpochMs - completedAtEpochMs)
    val title = displayLabel(durationSeconds, label)
    val subtitle = if (durationSeconds > 0) formatDuration(durationSeconds) else ""

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color(0xFF14213D),
                        Color(0xFF2A6F97),
                        Color(0xFF8A5AAB)
                    )
                )
            )
            .padding(24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = title,
                    color = Color.White,
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Bold
                )
                if (subtitle.isNotBlank()) {
                    Text(
                        text = subtitle,
                        color = Color.White.copy(alpha = 0.9f),
                        fontSize = 18.sp,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
                Text(
                    text = overtimeText,
                    color = Color.White,
                    fontSize = 56.sp,
                    fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier.padding(top = 18.dp)
                )
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(bottom = 6.dp)
            ) {
                Button(
                    onClick = onExtend,
                    shape = RoundedCornerShape(24.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White.copy(alpha = 0.18f),
                        contentColor = Color.White
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                ) {
                    Text(text = androidx.compose.ui.res.stringResource(R.string.extend_time), fontSize = 20.sp)
                }

                Spacer(modifier = Modifier.height(20.dp))

                Surface(
                    color = Color.White.copy(alpha = 0.16f),
                    shape = CircleShape
                ) {
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(70.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = androidx.compose.ui.res.stringResource(R.string.stop_alarm),
                            tint = Color.White,
                            modifier = Modifier.size(36.dp)
                        )
                    }
                }
                Text(
                    text = androidx.compose.ui.res.stringResource(R.string.stop_alarm),
                    color = Color.White.copy(alpha = 0.95f),
                    fontSize = 16.sp,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
    }
}

private fun formatOvertime(elapsedMs: Long): String {
    val elapsed = elapsedMs.coerceAtLeast(0L)
    val totalSeconds = elapsed / 1000L
    val hours = totalSeconds / 3600L
    val minutes = (totalSeconds % 3600L) / 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0L) {
        String.format(Locale.getDefault(), "-%02d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.getDefault(), "-%02d:%02d", minutes, seconds)
    }
}

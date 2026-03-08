package com.quicktimer.ui

import android.app.AlarmManager
import android.app.NotificationManager
import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.quicktimer.R
import com.quicktimer.data.AppThemeMode
import com.quicktimer.data.FontSize

@Composable
fun SettingsTab(
    modifier: Modifier,
    state: AppUiState,
    onLanguage: (String) -> Unit,
    onThemeMode: (AppThemeMode) -> Unit,
    onFontSize: (FontSize) -> Unit,
    onAdsRemoved: (Boolean) -> Unit,
    onDelayIntervention: (Boolean) -> Unit,
    onAlarmSoundEnabled: (Boolean) -> Unit,
    onAlarmVibrationEnabled: (Boolean) -> Unit,
    onOpenPermissionManager: () -> Unit,
    onOpenFullScreenPreview: () -> Unit,
    onOpenLogs: () -> Unit,
    onOpenRuntimeDebug: () -> Unit,
    fontScale: Float
) {
    val segmentedColors = SegmentedButtonDefaults.colors(
        activeContainerColor = MaterialTheme.colorScheme.primaryContainer,
        activeContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        activeBorderColor = MaterialTheme.colorScheme.primaryContainer,
        inactiveContainerColor = MaterialTheme.colorScheme.surfaceVariant,
        inactiveContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        inactiveBorderColor = MaterialTheme.colorScheme.outlineVariant
    )
    val settingSwitchColors = SwitchDefaults.colors(
        checkedThumbColor = MaterialTheme.colorScheme.primary,
        checkedTrackColor = MaterialTheme.colorScheme.primaryContainer,
        checkedBorderColor = MaterialTheme.colorScheme.primaryContainer,
        uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
        uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant,
        uncheckedBorderColor = MaterialTheme.colorScheme.outlineVariant
    )

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        item {
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
            Text(stringResource(R.string.theme), fontSize = (18 * fontScale).sp, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(8.dp))
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                listOf(AppThemeMode.SYSTEM, AppThemeMode.LIGHT, AppThemeMode.DARK).forEachIndexed { index, mode ->
                    SegmentedButton(
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = 3),
                        colors = segmentedColors,
                        selected = state.settings.themeMode == mode,
                        onClick = { onThemeMode(mode) },
                        label = {
                            Text(
                                text = when (mode) {
                                    AppThemeMode.SYSTEM -> stringResource(R.string.system)
                                    AppThemeMode.LIGHT -> stringResource(R.string.light)
                                    AppThemeMode.DARK -> stringResource(R.string.dark)
                                },
                                fontSize = (13 * fontScale).sp
                            )
                        }
                    )
                }
            }
        }

        item {
            Text(stringResource(R.string.font_size), fontSize = (18 * fontScale).sp, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(8.dp))
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                listOf(FontSize.SMALL, FontSize.NORMAL, FontSize.LARGE).forEachIndexed { index, size ->
                    SegmentedButton(
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = 3),
                        colors = segmentedColors,
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.ads_removed), fontSize = (18 * fontScale).sp, fontWeight = FontWeight.SemiBold)
                    Text(stringResource(R.string.simulate_purchase_hint), fontSize = (13 * fontScale).sp)
                }
                Switch(
                    checked = state.settings.adsRemoved,
                    onCheckedChange = onAdsRemoved,
                    colors = settingSwitchColors
                )
            }
        }

        item {
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
                    onCheckedChange = onDelayIntervention,
                    colors = settingSwitchColors
                )
            }
        }

        item {
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
                    onCheckedChange = onAlarmSoundEnabled,
                    colors = settingSwitchColors
                )
            }
        }

        item {
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
                    onCheckedChange = onAlarmVibrationEnabled,
                    colors = settingSwitchColors
                )
            }
        }

        item {
            Button(
                onClick = onOpenPermissionManager,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.permission_management), fontSize = (14 * fontScale).sp)
            }
        }

        item {
            Button(
                onClick = onOpenFullScreenPreview,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.full_screen_preview_button), fontSize = (14 * fontScale).sp)
            }
        }

        item {
            Button(
                onClick = onOpenLogs,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.logs_button), fontSize = (14 * fontScale).sp)
            }
        }

        item {
            Button(
                onClick = onOpenRuntimeDebug,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.runtime_debug_button), fontSize = (14 * fontScale).sp)
            }
        }
    }
}

@Composable
fun PermissionManagementScreen(
    modifier: Modifier,
    fontScale: Float,
    onRequestNotificationPermissionFlow: () -> Unit,
    onOpenNotificationSettings: () -> Unit,
    onOpenExactAlarmSettings: () -> Unit,
    onOpenFullScreenIntentSettings: () -> Unit,
    onOpenAlarmChannelSettings: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var notificationAllowed by remember { mutableStateOf(false) }
    var exactAlarmAllowed by remember { mutableStateOf(true) }
    var fullScreenAllowed by remember { mutableStateOf(true) }

    fun refresh() {
        notificationAllowed = NotificationManagerCompat.from(context).areNotificationsEnabled()
        exactAlarmAllowed = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(AlarmManager::class.java)?.canScheduleExactAlarms() == true
        } else {
            true
        }
        fullScreenAllowed = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            context.getSystemService(NotificationManager::class.java)?.canUseFullScreenIntent() == true
        } else {
            true
        }
    }

    DisposableEffect(lifecycleOwner) {
        refresh()
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refresh()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        item {
            Text(
                text = stringResource(R.string.permission_management_hint),
                fontSize = (13 * fontScale).sp
            )
        }
        item {
            PermissionRow(
                title = stringResource(R.string.notification_permission_label),
                checked = notificationAllowed,
                onToggle = { desired ->
                    if (desired && !notificationAllowed) {
                        onRequestNotificationPermissionFlow()
                    } else if (!desired && notificationAllowed) {
                        onOpenNotificationSettings()
                    }
                },
                fontScale = fontScale
            )
        }
        item {
            PermissionRow(
                title = stringResource(R.string.exact_alarm_permission_label),
                checked = exactAlarmAllowed,
                onToggle = { onOpenExactAlarmSettings() },
                fontScale = fontScale
            )
        }
        item {
            PermissionRow(
                title = stringResource(R.string.full_screen_permission_label),
                checked = fullScreenAllowed,
                onToggle = { onOpenFullScreenIntentSettings() },
                fontScale = fontScale,
                enabled = Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE
            )
        }
        item {
            Button(
                onClick = onOpenAlarmChannelSettings,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.open_alarm_channel_settings), fontSize = (14 * fontScale).sp)
            }
        }
    }
}

@Composable
private fun PermissionRow(
    title: String,
    checked: Boolean,
    onToggle: (Boolean) -> Unit,
    fontScale: Float,
    enabled: Boolean = true
) {
    val switchColors = SwitchDefaults.colors(
        checkedThumbColor = MaterialTheme.colorScheme.primary,
        checkedTrackColor = MaterialTheme.colorScheme.primaryContainer,
        checkedBorderColor = MaterialTheme.colorScheme.primaryContainer,
        uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
        uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant,
        uncheckedBorderColor = MaterialTheme.colorScheme.outlineVariant
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            fontSize = (17 * fontScale).sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f)
        )
        Switch(
            checked = checked,
            onCheckedChange = { onToggle(it) },
            enabled = enabled,
            colors = switchColors
        )
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

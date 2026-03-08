package com.quicktimer

import android.Manifest
import android.app.AlarmManager
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.core.os.LocaleListCompat
import androidx.core.view.WindowCompat
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.quicktimer.ui.AppViewModel
import com.quicktimer.ui.QuickTimerApp
import com.quicktimer.ui.RunningTabNavigationRequest
import com.quicktimer.data.AppThemeMode
import com.quicktimer.ui.theme.QuickTimerTheme
import com.quicktimer.service.TimerServiceController
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val viewModel: AppViewModel by viewModels()
    private var interstitialAd: InterstitialAd? = null
    private var runningTabNavigationRequest: RunningTabNavigationRequest? by mutableStateOf(null)
    private var notificationDialog: AlertDialog? = null
    private val permissionPrefs by lazy {
        getSharedPreferences("permission_prompts", MODE_PRIVATE)
    }

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                notificationDialog?.dismiss()
                viewModel.ensureService()
                TimerServiceController.refreshQuickActions(this)
                maybeRequestExactAlarmPermission()
            } else {
                handleNotificationPermissionDenied()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        maybeRequestNotificationPermission()
        maybeRequestExactAlarmPermission()
        viewModel.ensureService()
        TimerServiceController.refreshQuickActions(this)
        handleNotificationEntry(intent)

        setContent {
            val state by viewModel.uiState.collectAsStateWithLifecycle()

            LaunchedEffect(state.settings.languageTag) {
                val locales = if (state.settings.languageTag == "system") {
                    LocaleListCompat.getEmptyLocaleList()
                } else {
                    LocaleListCompat.forLanguageTags(state.settings.languageTag)
                }
                AppCompatDelegate.setApplicationLocales(locales)
            }
            LaunchedEffect(state.settings.themeMode) {
                applySystemBarAppearance(state.settings.themeMode)
            }

            QuickTimerTheme(
                themeConfig = (application as QuickTimerApplication).themeConfig,
                themeMode = state.settings.themeMode
            ) {
                QuickTimerApp(
                    viewModel = viewModel,
                    runningTabNavigationRequest = runningTabNavigationRequest,
                    onRunningTabNavigationConsumed = { runningTabNavigationRequest = null },
                    onRequestNotificationPermissionFlow = { maybeRequestNotificationPermission() },
                    onOpenNotificationSettings = { openNotificationSettings() },
                    onOpenExactAlarmSettings = { openExactAlarmSettings() },
                    onOpenFullScreenIntentSettings = { openFullScreenIntentSettings() },
                    onOpenAlarmChannelSettings = { openAlarmChannelSettings() }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleNotificationEntry(intent)
    }

    private fun maybeRequestNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        if (hasNotificationPermission()) return

        val requestedBefore = permissionPrefs.getBoolean(KEY_POST_NOTIFICATIONS_REQUESTED, false)
        when {
            !requestedBefore -> showNotificationPrimerDialog()
            shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS) ->
                showNotificationRationaleDialog()
            else -> showNotificationSettingsDialog()
        }
    }

    private fun applySystemBarAppearance(themeMode: AppThemeMode) {
        val isDark = when (themeMode) {
            AppThemeMode.DARK -> true
            AppThemeMode.LIGHT -> false
            AppThemeMode.SYSTEM -> {
                val uiMode = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
                uiMode == Configuration.UI_MODE_NIGHT_YES
            }
        }
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = !isDark
            isAppearanceLightNavigationBars = !isDark
        }
    }

    private fun maybeRequestExactAlarmPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        if (!hasNotificationPermissionCompat()) return
        val alarmManager = getSystemService(AlarmManager::class.java) ?: return
        if (alarmManager.canScheduleExactAlarms()) return
        val requestedBefore = permissionPrefs.getBoolean(KEY_EXACT_ALARM_REQUESTED, false)
        if (requestedBefore) return

        permissionPrefs.edit()
            .putBoolean(KEY_EXACT_ALARM_REQUESTED, true)
            .apply()
        showNotificationDialog(
            title = getString(R.string.exact_alarm_permission_required_title),
            message = getString(R.string.exact_alarm_permission_message),
            positiveLabel = getString(R.string.request_exact_alarm_permission),
            onPositive = { openExactAlarmSettings() }
        )
    }

    private fun hasNotificationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun hasNotificationPermissionCompat(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            hasNotificationPermission()
        } else {
            true
        }
    }

    private fun markNotificationPermissionRequested() {
        permissionPrefs.edit()
            .putBoolean(KEY_POST_NOTIFICATIONS_REQUESTED, true)
            .apply()
    }

    private fun handleNotificationPermissionDenied() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        if (shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)) {
            showNotificationRationaleDialog()
        } else {
            showNotificationSettingsDialog()
        }
    }

    private fun showNotificationPrimerDialog() {
        showNotificationDialog(
            title = getString(R.string.notification_permission_required_title),
            message = getString(R.string.notification_permission_primer_message),
            positiveLabel = getString(R.string.notification_permission_continue),
            onPositive = {
                markNotificationPermissionRequested()
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        )
    }

    private fun showNotificationRationaleDialog() {
        showNotificationDialog(
            title = getString(R.string.notification_permission_required_title),
            message = getString(R.string.notification_permission_rationale_message),
            positiveLabel = getString(R.string.notification_permission_retry),
            onPositive = {
                markNotificationPermissionRequested()
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        )
    }

    private fun showNotificationSettingsDialog() {
        showNotificationDialog(
            title = getString(R.string.notification_permission_required_title),
            message = getString(R.string.notification_permission_settings_message),
            positiveLabel = getString(R.string.open_settings),
            onPositive = { openNotificationSettings() }
        )
    }

    private fun showNotificationDialog(
        title: String,
        message: String,
        positiveLabel: String,
        onPositive: () -> Unit
    ) {
        if (isFinishing || isDestroyed) return
        notificationDialog?.dismiss()
        notificationDialog = AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton(positiveLabel) { _, _ -> onPositive() }
            .setNegativeButton(R.string.later, null)
            .create()
        notificationDialog?.show()
    }

    private fun openNotificationSettings() {
        val notificationSettingsIntent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
            putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
        }
        runCatching { startActivity(notificationSettingsIntent) }
            .onFailure {
                val fallbackIntent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", packageName, null)
                }
                startActivity(fallbackIntent)
            }
    }

    private fun openExactAlarmSettings() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
            data = Uri.fromParts("package", packageName, null)
        }
        runCatching { startActivity(intent) }
            .onFailure {
                val fallbackIntent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", packageName, null)
                }
                startActivity(fallbackIntent)
            }
    }

    private fun openFullScreenIntentSettings() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return
        val intent = Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT).apply {
            data = Uri.fromParts("package", packageName, null)
        }
        runCatching { startActivity(intent) }
            .onFailure {
                val fallbackIntent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", packageName, null)
                }
                startActivity(fallbackIntent)
            }
    }

    private fun openAlarmChannelSettings() {
        val intent = Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS).apply {
            putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
            putExtra(Settings.EXTRA_CHANNEL_ID, TimerServiceController.COMPLETION_CHANNEL_ID)
        }
        runCatching { startActivity(intent) }
            .onFailure {
                val fallbackIntent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                    putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
                }
                startActivity(fallbackIntent)
            }
    }

    private fun handleNotificationEntry(intent: Intent?) {
        val fromAlarm = intent?.getBooleanExtra(EXTRA_FROM_ALARM, false) == true
        val fromRunningNotification = intent?.getBooleanExtra(EXTRA_FROM_RUNNING_NOTIFICATION, false) == true
        val fromNotification = intent?.getBooleanExtra(EXTRA_FROM_NOTIFICATION, false) == true
        when {
            fromAlarm -> {
                TimerServiceController.acknowledgeAlarm(this)
                clearNotificationExtras(intent)
                if (fromNotification) {
                    maybeShowInterstitialAd()
                }
            }

            fromRunningNotification -> {
                val targetTimerId = intent?.getIntExtra(EXTRA_TARGET_TIMER_ID, 0) ?: 0
                runningTabNavigationRequest = RunningTabNavigationRequest(
                    requestId = System.currentTimeMillis(),
                    targetTimerId = targetTimerId
                )
                clearNotificationExtras(intent)
            }

            fromNotification -> {
                clearNotificationExtras(intent)
                maybeShowInterstitialAd()
            }
        }
    }

    private fun clearNotificationExtras(intent: Intent?) {
        intent?.removeExtra(EXTRA_FROM_ALARM)
        intent?.removeExtra(EXTRA_FROM_RUNNING_NOTIFICATION)
        intent?.removeExtra(EXTRA_FROM_NOTIFICATION)
        intent?.removeExtra(EXTRA_TARGET_TIMER_ID)
    }

    private fun maybeShowInterstitialAd() {
        lifecycleScope.launch {
            val adsRemoved = runCatching {
                (application as QuickTimerApplication).settingsStore.settingsFlow.first().adsRemoved
            }.getOrDefault(false)
            if (adsRemoved) return@launch
            showInterstitialAd()
        }
    }

    private fun showInterstitialAd() {
        InterstitialAd.load(
            this,
            BuildConfig.AD_UNIT_INTERSTITIAL,
            AdRequest.Builder().build(),
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    interstitialAd = ad
                    interstitialAd?.fullScreenContentCallback = object : FullScreenContentCallback() {
                        override fun onAdDismissedFullScreenContent() {
                            interstitialAd = null
                        }
                    }
                    interstitialAd?.show(this@MainActivity)
                }

                override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                    interstitialAd = null
                }
            }
        )
    }

    companion object {
        const val EXTRA_FROM_NOTIFICATION = "from_notification"
        const val EXTRA_FROM_ALARM = "from_alarm"
        const val EXTRA_FROM_RUNNING_NOTIFICATION = "from_running_notification"
        const val EXTRA_TARGET_TIMER_ID = "target_timer_id"
        private const val KEY_POST_NOTIFICATIONS_REQUESTED = "post_notifications_requested"
        private const val KEY_EXACT_ALARM_REQUESTED = "exact_alarm_requested"
    }
}

package com.quicktimer

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
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
            } else {
                handleNotificationPermissionDenied()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }

        maybeRequestNotificationPermission()
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

            QuickTimerTheme(themeConfig = (application as QuickTimerApplication).themeConfig) {
                QuickTimerApp(
                    viewModel = viewModel,
                    runningTabNavigationRequest = runningTabNavigationRequest,
                    onRunningTabNavigationConsumed = { runningTabNavigationRequest = null }
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

    private fun hasNotificationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
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

    private fun handleNotificationEntry(intent: Intent?) {
        val fromAlarm = intent?.getBooleanExtra(EXTRA_FROM_ALARM, false) == true
        val fromRunningNotification = intent?.getBooleanExtra(EXTRA_FROM_RUNNING_NOTIFICATION, false) == true
        val fromNotification = intent?.getBooleanExtra(EXTRA_FROM_NOTIFICATION, false) == true
        if (fromAlarm) {
            TimerServiceController.acknowledgeAlarm(this)
            intent?.removeExtra(EXTRA_FROM_ALARM)
            if (fromNotification) {
                intent?.removeExtra(EXTRA_FROM_NOTIFICATION)
                maybeShowInterstitialAd()
            }
            return
        }
        if (fromRunningNotification) {
            val targetTimerId = intent?.getIntExtra(EXTRA_TARGET_TIMER_ID, 0) ?: 0
            runningTabNavigationRequest = RunningTabNavigationRequest(
                requestId = System.currentTimeMillis(),
                targetTimerId = targetTimerId
            )
            intent?.removeExtra(EXTRA_FROM_RUNNING_NOTIFICATION)
            intent?.removeExtra(EXTRA_TARGET_TIMER_ID)
            intent?.removeExtra(EXTRA_FROM_NOTIFICATION)
            return
        }
        if (!fromNotification) return
        intent?.removeExtra(EXTRA_FROM_NOTIFICATION)
        maybeShowInterstitialAd()
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
            "ca-app-pub-3940256099942544/1033173712",
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
    }
}

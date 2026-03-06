package com.quicktimer

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
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

class MainActivity : ComponentActivity() {
    private val viewModel: AppViewModel by viewModels()
    private var interstitialAd: InterstitialAd? = null
    private var runningTabNavigationRequest: RunningTabNavigationRequest? by mutableStateOf(null)

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                viewModel.ensureService()
                TimerServiceController.refreshQuickActions(this)
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    private fun handleNotificationEntry(intent: Intent?) {
        val fromAlarm = intent?.getBooleanExtra(EXTRA_FROM_ALARM, false) == true
        val fromRunningNotification = intent?.getBooleanExtra(EXTRA_FROM_RUNNING_NOTIFICATION, false) == true
        val fromNotification = intent?.getBooleanExtra(EXTRA_FROM_NOTIFICATION, false) == true
        if (fromAlarm) {
            TimerServiceController.acknowledgeAlarm(this)
            intent?.removeExtra(EXTRA_FROM_ALARM)
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
        showInterstitialAd()
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
    }
}

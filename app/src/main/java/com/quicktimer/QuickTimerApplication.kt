package com.quicktimer

import android.app.Application
import com.google.android.gms.ads.MobileAds
import com.quicktimer.data.LogStore
import com.quicktimer.data.QuickTimerDatabase
import com.quicktimer.data.RunningTimerStore
import com.quicktimer.data.SettingsStore
import com.quicktimer.data.TimerHistoryStore
import com.quicktimer.data.TimerPresetStore
import com.quicktimer.ui.theme.ThemeConfig
import com.quicktimer.ui.theme.ThemeConfigLoader

class QuickTimerApplication : Application() {
    lateinit var database: QuickTimerDatabase
        private set

    lateinit var presetStore: TimerPresetStore
        private set

    lateinit var runningTimerStore: RunningTimerStore
        private set

    lateinit var historyStore: TimerHistoryStore
        private set

    lateinit var settingsStore: SettingsStore
        private set

    lateinit var logStore: LogStore
        private set

    lateinit var themeConfig: ThemeConfig
        private set

    override fun onCreate() {
        super.onCreate()
        database = QuickTimerDatabase.getInstance(this)
        presetStore = TimerPresetStore(this, database.timerPresetDao())
        runningTimerStore = RunningTimerStore(database.runningTimerDao())
        historyStore = TimerHistoryStore(database.timerHistoryDao())
        settingsStore = SettingsStore(this)
        logStore = LogStore(this)
        themeConfig = ThemeConfigLoader.load(this)
        MobileAds.initialize(this)
    }
}

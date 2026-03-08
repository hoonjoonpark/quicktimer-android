package com.quicktimer.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.RemoteViews
import com.quicktimer.MainActivity
import com.quicktimer.R
import com.quicktimer.service.TimerServiceController

class TimerPresetWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        appWidgetIds.forEach { appWidgetId ->
            updateWidget(context, appWidgetManager, appWidgetId)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action != ACTION_START_FROM_WIDGET) return

        val durationSeconds = intent.getIntExtra(TimerServiceController.EXTRA_DURATION_SECONDS, 0)
        val label = intent.getStringExtra(TimerServiceController.EXTRA_LABEL).orEmpty()
        if (durationSeconds <= 0) return

        TimerServiceController.startFromWidget(context, durationSeconds, label)
    }

    companion object {
        const val ACTION_START_FROM_WIDGET = "com.quicktimer.widget.action.START_PRESET"

        fun notifyDataChanged(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(
                ComponentName(context, TimerPresetWidgetProvider::class.java)
            )
            if (ids.isEmpty()) return
            manager.notifyAppWidgetViewDataChanged(ids, R.id.widget_timer_list)
            ids.forEach { appWidgetId ->
                updateWidget(context, manager, appWidgetId)
            }
        }

        private fun updateWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int
        ) {
            val views = RemoteViews(context.packageName, R.layout.widget_timer_preset)
            val openAppIntent = PendingIntent.getActivity(
                context,
                appWidgetId + 1000,
                Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                },
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            views.setOnClickPendingIntent(R.id.widget_header, openAppIntent)

            val svcIntent = Intent(context, TimerPresetWidgetService::class.java).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                data = Uri.parse(toUri(Intent.URI_INTENT_SCHEME))
            }
            views.setRemoteAdapter(R.id.widget_timer_list, svcIntent)
            views.setEmptyView(R.id.widget_timer_list, R.id.widget_empty)

            val clickTemplate = PendingIntent.getBroadcast(
                context,
                appWidgetId,
                Intent(context, TimerPresetWidgetProvider::class.java).setAction(ACTION_START_FROM_WIDGET),
                PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            views.setPendingIntentTemplate(R.id.widget_timer_list, clickTemplate)

            appWidgetManager.updateAppWidget(appWidgetId, views)
            appWidgetManager.notifyAppWidgetViewDataChanged(appWidgetId, R.id.widget_timer_list)
        }
    }
}

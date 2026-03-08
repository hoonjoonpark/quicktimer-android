package com.quicktimer.widget

import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import com.quicktimer.R
import com.quicktimer.data.QuickTimerDatabase
import com.quicktimer.data.TimerPreset
import com.quicktimer.data.displayLabel
import com.quicktimer.data.formatDuration
import com.quicktimer.service.TimerServiceController
import kotlinx.coroutines.runBlocking

class TimerPresetWidgetService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory {
        return TimerPresetRemoteViewsFactory(applicationContext)
    }
}

private class TimerPresetRemoteViewsFactory(
    private val context: Context
) : RemoteViewsService.RemoteViewsFactory {
    private var presets: List<TimerPreset> = emptyList()

    override fun onCreate() = Unit

    override fun onDataSetChanged() {
        val dao = QuickTimerDatabase.getInstance(context).timerPresetDao()
        presets = runBlocking {
            dao.getAll().map { entity ->
                TimerPreset(
                    id = entity.id,
                    durationSeconds = entity.durationSeconds,
                    label = entity.label
                )
            }
        }
    }

    override fun onDestroy() {
        presets = emptyList()
    }

    override fun getCount(): Int = presets.size

    override fun getViewAt(position: Int): RemoteViews {
        if (position !in presets.indices) {
            return RemoteViews(context.packageName, R.layout.widget_timer_preset_item)
        }
        val preset = presets[position]
        val title = if (preset.label.isBlank()) {
            formatDuration(preset.durationSeconds)
        } else {
            displayLabel(preset.durationSeconds, preset.label)
        }
        val subtitle = if (preset.label.isBlank()) "" else formatDuration(preset.durationSeconds)
        val fillInIntent = Intent().apply {
            putExtra(TimerServiceController.EXTRA_DURATION_SECONDS, preset.durationSeconds)
            putExtra(TimerServiceController.EXTRA_LABEL, preset.label)
        }

        return RemoteViews(context.packageName, R.layout.widget_timer_preset_item).apply {
            setTextViewText(R.id.widget_item_title, title)
            setTextViewText(R.id.widget_item_subtitle, subtitle)
            setViewVisibility(
                R.id.widget_item_subtitle,
                if (subtitle.isBlank()) View.GONE else View.VISIBLE
            )
            setOnClickFillInIntent(R.id.widget_item_start, fillInIntent)
        }
    }

    override fun getLoadingView(): RemoteViews? = null

    override fun getViewTypeCount(): Int = 1

    override fun getItemId(position: Int): Long {
        return presets.getOrNull(position)?.id ?: position.toLong()
    }

    override fun hasStableIds(): Boolean = true
}

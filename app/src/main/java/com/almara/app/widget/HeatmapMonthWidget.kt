package com.almara.app.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Monthly activity heatmap widget. Database reads run off the launcher main thread. */
class HeatmapMonthWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try { appWidgetIds.forEach { manager.updateAppWidget(it, WidgetViews.heatmap(context)) } }
            finally { pending.finish() }
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_REFRESH) updateAll(context)
    }

    companion object {
        const val ACTION_REFRESH = "com.almara.app.widget.REFRESH"
        internal fun updateAll(context: Context) {
            CoroutineScope(SupervisorJob() + Dispatchers.IO).launch { WidgetViews.update(context, HeatmapMonthWidget::class.java, WidgetViews::heatmap) }
        }
    }
}

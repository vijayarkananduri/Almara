package com.almara.app.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Compact list of saved virtual folders from the encrypted local store. */
class FolderListWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try { appWidgetIds.forEach { manager.updateAppWidget(it, WidgetViews.folders(context)) } }
            finally { pending.finish() }
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == HeatmapMonthWidget.ACTION_REFRESH) updateAll(context)
    }

    companion object {
        internal fun updateAll(context: Context) {
            CoroutineScope(SupervisorJob() + Dispatchers.IO).launch { WidgetViews.update(context, FolderListWidget::class.java, WidgetViews::folders) }
        }
    }
}

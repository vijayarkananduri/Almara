package com.almara.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.widget.RemoteViews
import com.almara.app.MainActivity
import com.almara.app.R
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

internal object WidgetViews {
    private const val REQUEST_HEATMAP = 4101
    private const val REQUEST_FOLDERS = 4102

    fun launchIntent(context: Context, requestCode: Int): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        return PendingIntent.getActivity(context, requestCode, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    suspend fun heatmap(context: Context): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_heatmap_month)
        val month = YearMonth.now()
        val counts = runCatching { AlmaraWidgetData.provider.monthActivity(context, month) }.getOrDefault(emptyMap())
        views.setTextViewText(R.id.widget_month_title, month.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault())))
        views.setOnClickPendingIntent(R.id.widget_heatmap_root, launchIntent(context, REQUEST_HEATMAP))
        val max = counts.values.maxOrNull()?.coerceAtLeast(1) ?: 1
        val days = month.lengthOfMonth()
        for (cellIndex in 1..42) {
            val cell = context.resources.getIdentifier("widget_day_$cellIndex", "id", context.packageName)
            if (cell == 0) continue
            val day = cellIndex - month.atDay(1).dayOfWeek.value + 1
            if (day in 1..days) {
                val count = counts[day]?.coerceAtLeast(0) ?: 0
                val strength = count.toFloat() / max
                val color = if (count == 0) 0xFFECEAE5.toInt() else when { strength < .34f -> 0xFFC3D1B9.toInt(); strength < .67f -> 0xFF829D72.toInt(); else -> 0xFF48643E.toInt() }
                views.setTextViewText(cell, day.toString())
                views.setInt(cell, "setBackgroundColor", color)
                views.setTextColor(cell, if (count > 0 && strength >= .67f) Color.WHITE else 0xFF332F29.toInt())
                views.setContentDescription(cell, "$day, $count items")
            } else {
                views.setTextViewText(cell, "")
                views.setInt(cell, "setBackgroundColor", Color.TRANSPARENT)
            }
        }
        views.setTextViewText(R.id.widget_heatmap_empty, if (counts.values.sum() == 0) "No activity yet" else "")
        return views
    }

    suspend fun folders(context: Context): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_folder_list)
        views.setOnClickPendingIntent(R.id.widget_folder_root, launchIntent(context, REQUEST_FOLDERS))
        val folders = runCatching { AlmaraWidgetData.provider.folders(context) }.getOrDefault(emptyList()).take(5)
        val ids = listOf(R.id.widget_folder_1, R.id.widget_folder_2, R.id.widget_folder_3, R.id.widget_folder_4, R.id.widget_folder_5)
        ids.forEachIndexed { index, id -> views.setTextViewText(id, folders.getOrNull(index)?.let { "▰  $it" } ?: "") }
        views.setTextViewText(R.id.widget_folder_empty, if (folders.isEmpty()) "No folders yet" else "")
        return views
    }

    suspend fun update(context: Context, component: Class<out android.appwidget.AppWidgetProvider>, factory: suspend (Context) -> RemoteViews) {
        val manager = AppWidgetManager.getInstance(context)
        manager.getAppWidgetIds(ComponentName(context, component)).forEach { id -> manager.updateAppWidget(id, factory(context)) }
    }
}

package com.almara.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.content.res.Configuration
import android.widget.RemoteViews
import com.almara.app.MainActivity
import com.almara.app.R
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

internal object WidgetViews {
    private const val REQUEST_HEATMAP = 4101
    private const val REQUEST_FOLDERS = 4102

    private fun palette(context: Context): IntArray {
        val mode = context.getSharedPreferences("almara", Context.MODE_PRIVATE).getString("theme", "SYSTEM") ?: "SYSTEM"
        val systemDark = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
        val dark = mode == "DARK" || mode == "AMOLED" || (mode == "SYSTEM" && systemDark)
        return if (mode == "AMOLED") intArrayOf(Color.BLACK, 0xFF080807.toInt(), 0xFFE6A86C.toInt(), 0xFFB5C9A5.toInt())
        else if (dark) intArrayOf(0xFF151311.toInt(), 0xFF211E1A.toInt(), 0xFFE6A86C.toInt(), 0xFFB5C9A5.toInt())
        else intArrayOf(0xFFFAF5EB.toInt(), 0xFFFFFCF6.toInt(), 0xFF81502D.toInt(), 0xFF536B48.toInt())
    }

    fun launchIntent(context: Context, requestCode: Int): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        return PendingIntent.getActivity(context, requestCode, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    suspend fun heatmap(context: Context): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_heatmap_month)
        val palette = palette(context)
        views.setInt(R.id.widget_heatmap_root, "setBackgroundColor", palette[0])
        views.setTextColor(R.id.widget_month_title, palette[2])
        views.setTextColor(R.id.widget_heatmap_empty, palette[3])
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
                val color = if (count == 0) palette[1] else when { strength < .34f -> blend(palette[0], palette[3], .28f); strength < .67f -> blend(palette[0], palette[3], .56f); else -> palette[3] }
                views.setTextViewText(cell, day.toString())
                views.setInt(cell, "setBackgroundColor", color)
                views.setTextColor(cell, if (count > 0 && strength >= .67f) Color.WHITE else palette[2])
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
        val palette = palette(context)
        views.setInt(R.id.widget_folder_root, "setBackgroundColor", palette[0])
        listOf(R.id.widget_folder_1, R.id.widget_folder_2, R.id.widget_folder_3, R.id.widget_folder_4, R.id.widget_folder_5).forEach { views.setTextColor(it, palette[3]) }
        views.setTextColor(R.id.widget_folder_empty, palette[2])
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

    private fun blend(from: Int, to: Int, ratio: Float): Int {
        val r = (Color.red(from) * (1 - ratio) + Color.red(to) * ratio).toInt()
        val g = (Color.green(from) * (1 - ratio) + Color.green(to) * ratio).toInt()
        val b = (Color.blue(from) * (1 - ratio) + Color.blue(to) * ratio).toInt()
        return Color.rgb(r, g, b)
    }
}

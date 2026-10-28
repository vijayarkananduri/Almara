package com.almara.app.widget

import android.content.Context
import com.almara.app.data.AlmaraDatabase
import com.almara.app.data.SqlCipherKeyRepository
import java.time.YearMonth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import net.sqlcipher.database.SQLiteDatabase

/** Widget data comes only from the encrypted on-device index and saved virtual-folder database. */
interface WidgetDataProvider {
    suspend fun monthActivity(context: Context, month: YearMonth): Map<Int, Int>
    suspend fun folders(context: Context): List<String>
}

object AlmaraWidgetData {
    @Volatile
    var provider: WidgetDataProvider = DatabaseWidgetDataProvider

    fun refresh(context: Context) {
        HeatmapMonthWidget.updateAll(context.applicationContext)
        FolderListWidget.updateAll(context.applicationContext)
    }
}

private object DatabaseWidgetDataProvider : WidgetDataProvider {
    override suspend fun monthActivity(context: Context, month: YearMonth): Map<Int, Int> = withContext(Dispatchers.IO) {
        query(context) { db -> db.uriMetadataDao().observeDailyCounts().first().mapNotNull { row ->
            runCatching { val date = java.time.LocalDate.parse(row.day); if (YearMonth.from(date) == month) date.dayOfMonth to row.count else null }.getOrNull()
        }.toMap() }
    }

    override suspend fun folders(context: Context): List<String> = withContext(Dispatchers.IO) {
        query(context) { db -> db.folderDao().getAll().filter { it.archivedUntilMillis == null }.take(5).map { it.name } }
    }

    private suspend fun <T> query(context: Context, block: suspend (AlmaraDatabase) -> T): T {
        SQLiteDatabase.loadLibs(context)
        val db = AlmaraDatabase.open(context, SqlCipherKeyRepository(context))
        return try { block(db) } finally { db.close() }
    }
}

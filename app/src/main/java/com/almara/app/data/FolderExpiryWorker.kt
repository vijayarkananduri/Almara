package com.almara.app.data

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.almara.app.expiry.ExpiryAction
import com.almara.app.expiry.ExpiryNotifications
import com.almara.app.expiry.ExpiryOptions
import com.almara.app.expiry.ExpiryPolicy
import com.almara.app.expiry.ExpiringFolder
import com.almara.app.widget.AlmaraWidgetData
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.sqlcipher.database.SQLiteDatabase

class FolderExpiryWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            SQLiteDatabase.loadLibs(applicationContext)
            val db = AlmaraDatabase.open(applicationContext, SqlCipherKeyRepository(applicationContext))
            try {
                val now = System.currentTimeMillis()
                val folders = db.folderDao().getExpiring()
                folders.forEach { folder ->
                    val expiresAt = folder.expiresAtMillis ?: return@forEach
                    if (expiresAt <= now) {
                        if (folder.archiveOnExpiry) {
                            db.folderDao().upsert(folder.copy(expiresAtMillis = null, archiveOnExpiry = false, archivedUntilMillis = now + TimeUnit.DAYS.toMillis(7), noticeSentAtMillis = null, updatedAtMillis = now))
                        } else {
                            db.folderDao().deleteFolderAndMembership(folder.id)
                            folder.ruleId?.let { db.virtualRuleDao().delete(it) }
                        }
                    } else if (folder.noticeSentAtMillis == null) {
                        val lifetime = (expiresAt - folder.updatedAtMillis).coerceAtLeast(1L)
                        val noticeAt = ExpiryPolicy.notificationDueAt(folder.updatedAtMillis, ExpiryOptions(enabled = true, lifetimeMillis = lifetime, archiveOnExpiry = folder.archiveOnExpiry))
                        if (noticeAt != null && now >= noticeAt) {
                            val posted = ExpiryNotifications.post(applicationContext, ExpiringFolder(folder.id, folder.name, folder.updatedAtMillis, expiresAt, folder.archiveOnExpiry), AlmaraExpiryActionReceiver::class.java)
                            if (posted) db.folderDao().upsert(folder.copy(noticeSentAtMillis = now))
                        }
                    }
                }
                db.folderDao().getArchivesPastRetention(now).forEach { folder ->
                    db.folderDao().deleteFolderAndMembership(folder.id)
                    folder.ruleId?.let { db.virtualRuleDao().delete(it) }
                }
            } finally { db.close() }
            AlmaraWidgetData.refresh(applicationContext)
            Result.success()
        } catch (_: Exception) { Result.retry() }
    }

    companion object {
        private const val PERIODIC_NAME = "almara-folder-expiry"
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<FolderExpiryWorker>(15, TimeUnit.MINUTES).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(PERIODIC_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
        fun applyAction(context: Context, folderId: String, action: ExpiryAction) {
            val data = workDataOf("folder_id" to folderId, "action" to action.name)
            val request = OneTimeWorkRequestBuilder<ExpiryActionWorker>().setInputData(data).build()
            WorkManager.getInstance(context).enqueueUniqueWork("almara-expiry-action-$folderId", ExistingWorkPolicy.REPLACE, request)
        }
    }
}

class ExpiryActionWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val id = inputData.getString("folder_id") ?: return@withContext Result.failure()
        val action = runCatching { ExpiryAction.valueOf(inputData.getString("action") ?: "") }.getOrNull() ?: return@withContext Result.failure()
        try {
            SQLiteDatabase.loadLibs(applicationContext)
            val db = AlmaraDatabase.open(applicationContext, SqlCipherKeyRepository(applicationContext))
            try {
                val folder = db.folderDao().get(id) ?: return@withContext Result.success()
                when (action) {
                    ExpiryAction.KEEP -> db.folderDao().upsert(folder.copy(expiresAtMillis = null, archiveOnExpiry = false, noticeSentAtMillis = null, updatedAtMillis = System.currentTimeMillis()))
                    ExpiryAction.LET_EXPIRE -> db.folderDao().upsert(folder.copy(expiresAtMillis = System.currentTimeMillis(), noticeSentAtMillis = null))
                }
            } finally { db.close() }
            Result.success()
        } catch (_: Exception) { Result.retry() }
    }
}

class AlmaraExpiryActionReceiver : com.almara.app.expiry.ExpiryActionReceiver() {
    override fun onExpiryAction(context: Context, folderId: String, action: ExpiryAction) {
        FolderExpiryWorker.applyAction(context, folderId, action)
    }
}

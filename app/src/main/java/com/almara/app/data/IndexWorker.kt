package com.almara.app.data

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import androidx.documentfile.provider.DocumentFile
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.almara.app.domain.dateFromFilenameOrModified
import com.almara.app.domain.detectSource
import com.almara.app.widget.AlmaraWidgetData
import java.time.ZoneOffset
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.sqlcipher.database.SQLiteDatabase

/** Device-only scheduled indexing. No constraints request network, and files are never copied or mutated. */
class IndexWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        if (!hasAnyGrantedSource(applicationContext)) return@withContext Result.success()
        try {
            SQLiteDatabase.loadLibs(applicationContext)
            val db = AlmaraDatabase.open(applicationContext, SqlCipherKeyRepository(applicationContext))
            try {
                db.uriMetadataDao().applyFullScan(scanGrantedFiles(applicationContext))
                db.uriMetadataDao().deleteStale(System.currentTimeMillis() - TimeUnit.DAYS.toMillis(30))
            } finally { db.close() }
            AlmaraWidgetData.refresh(applicationContext)
            Result.success()
        } catch (_: SecurityException) {
            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }
}

private fun hasAnyGrantedSource(context: Context): Boolean {
    val permissions = if (Build.VERSION.SDK_INT >= 34) listOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO, Manifest.permission.READ_MEDIA_AUDIO, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED) else if (Build.VERSION.SDK_INT >= 33) listOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO, Manifest.permission.READ_MEDIA_AUDIO) else listOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    return permissions.any { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED } ||
        context.getSharedPreferences("almara", Context.MODE_PRIVATE).getStringSet("trees", emptySet()).orEmpty().isNotEmpty()
}

object IndexingScheduler {
    private const val PERIODIC_NAME = "almara-periodic-index"
    private const val IMMEDIATE_NAME = "almara-on-demand-index"

    fun schedule(context: Context) {
        val constraints = Constraints.Builder().setRequiredNetworkType(NetworkType.NOT_REQUIRED).build()
        val periodic = PeriodicWorkRequestBuilder<IndexWorker>(6, TimeUnit.HOURS).setConstraints(constraints).build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(PERIODIC_NAME, ExistingPeriodicWorkPolicy.KEEP, periodic)
    }

    fun refreshNow(context: Context) {
        val request = OneTimeWorkRequestBuilder<IndexWorker>().build()
        WorkManager.getInstance(context).enqueueUniqueWork(IMMEDIATE_NAME, ExistingWorkPolicy.REPLACE, request)
    }
}

private fun scanGrantedFiles(context: Context): List<UriMetadataEntity> {
    val now = System.currentTimeMillis()
    val rows = LinkedHashMap<String, UriMetadataEntity>()
    val permissions = if (Build.VERSION.SDK_INT >= 34) listOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO, Manifest.permission.READ_MEDIA_AUDIO, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED) else if (Build.VERSION.SDK_INT >= 33) listOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO, Manifest.permission.READ_MEDIA_AUDIO) else listOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    if (permissions.any { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }) {
        val collection = MediaStore.Files.getContentUri("external")
        val projection = arrayOf(MediaStore.Files.FileColumns._ID, MediaStore.MediaColumns.DISPLAY_NAME, MediaStore.MediaColumns.MIME_TYPE, MediaStore.MediaColumns.SIZE, MediaStore.MediaColumns.DATE_MODIFIED, MediaStore.MediaColumns.RELATIVE_PATH)
        context.contentResolver.query(collection, projection, null, null, "${MediaStore.MediaColumns.DATE_MODIFIED} DESC")?.use { cursor ->
            val id = cursor.getColumnIndex(MediaStore.Files.FileColumns._ID)
            val name = cursor.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME)
            val mime = cursor.getColumnIndex(MediaStore.MediaColumns.MIME_TYPE)
            val size = cursor.getColumnIndex(MediaStore.MediaColumns.SIZE)
            val modified = cursor.getColumnIndex(MediaStore.MediaColumns.DATE_MODIFIED)
            val path = cursor.getColumnIndex(MediaStore.MediaColumns.RELATIVE_PATH)
            while (cursor.moveToNext()) {
                val filename = if (name >= 0) cursor.getString(name).orEmpty() else "Untitled"
                val mediaType = if (mime >= 0) cursor.getString(mime) else null
                if (!isIndexable(filename, mediaType) || id < 0) continue
                val uri = ContentUris.withAppendedId(collection, cursor.getLong(id)).toString()
                val changed = if (modified >= 0) cursor.getLong(modified) * 1000L else now
                val fullPath = (if (path >= 0) cursor.getString(path).orEmpty() else "") + filename
                rows[uri] = UriMetadataEntity(uri, filename, mediaType, if (size >= 0) cursor.getLong(size) else 0L, changed, dateFromFilenameOrModified(filename, changed, ZoneOffset.UTC).toString(), detectSource(fullPath).name.lowercase(), now)
            }
        }
    }
    val trees = context.getSharedPreferences("almara", Context.MODE_PRIVATE).getStringSet("trees", emptySet()).orEmpty()
    trees.forEach { raw -> runCatching { walkTree(context, Uri.parse(raw), now, rows) } }
    return rows.values.toList()
}

private fun walkTree(context: Context, tree: Uri, now: Long, rows: MutableMap<String, UriMetadataEntity>) {
    val root = DocumentFile.fromTreeUri(context, tree) ?: return
    val queue = ArrayDeque<DocumentFile>().apply { add(root) }
    var visited = 0
    while (queue.isNotEmpty() && visited < 20_000) {
        val current = queue.removeFirst(); visited++
        if (current.isDirectory) current.listFiles().forEach { child ->
            if (child.isDirectory) queue.add(child)
            else if (child.isFile) {
                val name = child.name ?: return@forEach
                val mime = child.type
                if (isIndexable(name, mime)) {
                    val uri = child.uri.toString(); val modified = child.lastModified().takeIf { it > 0 } ?: now
                    rows[uri] = UriMetadataEntity(uri, name, mime, child.length(), modified, dateFromFilenameOrModified(name, modified, ZoneOffset.UTC).toString(), detectSource(name).name.lowercase(), now)
                }
            }
        }
    }
}

private fun isIndexable(name: String, mime: String?): Boolean {
    val ext = name.substringAfterLast('.', "").lowercase()
    return mime?.let { it.startsWith("image/") || it.startsWith("audio/") || it.startsWith("video/") } == true ||
        ext in setOf("pdf", "doc", "docx", "odt", "rtf", "txt", "md", "xls", "xlsx", "csv", "ods", "ppt", "pptx", "odp", "zip", "tar", "gz", "rar", "7z", "jpg", "jpeg", "png", "webp", "gif", "bmp", "heic", "heif", "mp3", "opus", "ogg", "wav", "m4a", "aac", "flac", "mp4", "mkv", "webm", "mov", "avi")
}

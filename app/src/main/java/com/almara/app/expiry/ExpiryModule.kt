package com.almara.app.expiry

import android.Manifest
import android.app.Activity
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import java.util.concurrent.TimeUnit

/** Expiry is opt-in: a newly created folder has no expiry unless explicitly enabled. */
data class ExpiryOptions(
    val enabled: Boolean = false,
    val lifetimeMillis: Long = TimeUnit.DAYS.toMillis(30),
    val archiveOnExpiry: Boolean = false,
    val archiveRetentionMillis: Long = TimeUnit.DAYS.toMillis(7),
) {
    init {
        require(lifetimeMillis > 0) { "lifetimeMillis must be positive" }
        require(archiveRetentionMillis == TimeUnit.DAYS.toMillis(7)) {
            "The supported archive retention is exactly seven days"
        }
    }
}

/** Pure deterministic policy, suitable for unit tests; lead time scales with folder lifetime. */
object ExpiryPolicy {
    private val MIN_LEAD = TimeUnit.HOURS.toMillis(1)
    private val MAX_LEAD = TimeUnit.DAYS.toMillis(7)
    private const val LEAD_FRACTION = 0.10

    fun notificationLeadTime(lifetimeMillis: Long): Long {
        require(lifetimeMillis > 0)
        val oneHour = TimeUnit.HOURS.toMillis(1)
        val oneDay = TimeUnit.DAYS.toMillis(1)
        val oneWeek = TimeUnit.DAYS.toMillis(7)
        return when {
            lifetimeMillis == oneHour -> TimeUnit.MINUTES.toMillis(10)
            lifetimeMillis == oneDay -> TimeUnit.HOURS.toMillis(2)
            lifetimeMillis == oneWeek -> TimeUnit.DAYS.toMillis(1)
            else -> (lifetimeMillis * LEAD_FRACTION).toLong().coerceIn(MIN_LEAD, MAX_LEAD)
        }
    }

    fun notificationDueAt(createdAtMillis: Long, options: ExpiryOptions): Long? =
        if (!options.enabled) null else safeAdd(
            safeAdd(createdAtMillis, options.lifetimeMillis),
            -notificationLeadTime(options.lifetimeMillis),
        )

    fun expiresAt(createdAtMillis: Long, options: ExpiryOptions): Long? =
        if (options.enabled) safeAdd(createdAtMillis, options.lifetimeMillis) else null

    fun cleanupAt(archivedAtMillis: Long, options: ExpiryOptions): Long? =
        if (options.enabled && options.archiveOnExpiry) safeAdd(archivedAtMillis, options.archiveRetentionMillis) else null

    private fun safeAdd(a: Long, b: Long): Long = when {
        b > 0 && a > Long.MAX_VALUE - b -> Long.MAX_VALUE
        b < 0 && a < Long.MIN_VALUE - b -> Long.MIN_VALUE
        else -> a + b
    }
}

data class ExpiringFolder(
    val folderId: String,
    val displayName: String,
    val createdAtMillis: Long,
    val expiresAtMillis: Long,
    val archiveOnExpiry: Boolean,
)

enum class ExpiryAction { KEEP, LET_EXPIRE }

/**
 * Repository boundary deliberately exposes folder metadata and folder membership only.
 * Implementations may change folder rows/references, but MUST NOT open, mutate, move, or delete
 * any referenced/original file or content URI. A non-archiving expiry removes folder membership,
 * never the underlying files. Cleanup purges only archived folder records/references.
 */
interface ExpiryRepository {
    suspend fun findFoldersNeedingNotice(nowMillis: Long): List<ExpiringFolder>
    suspend fun applyUserAction(folderId: String, action: ExpiryAction, nowMillis: Long)
    suspend fun expireFolder(folderId: String, nowMillis: Long, archive: Boolean)
    suspend fun cleanupExpiredArchives(nowMillis: Long): Int
}

/** Platform-neutral scheduling seam; implementations should be idempotent by folder ID. */
interface ExpiryScheduler {
    fun schedule(folderId: String, atMillis: Long)
    fun cancel(folderId: String)
}

object ExpiryNotifications {
    const val CHANNEL_ID = "folder_expiry"
    const val ACTION_KEEP = "com.almara.app.expiry.KEEP"
    const val ACTION_LET_EXPIRE = "com.almara.app.expiry.LET_EXPIRE"
    const val EXTRA_FOLDER_ID = "com.almara.app.expiry.FOLDER_ID"

    /** Create/update the channel on Android 8+; call during app startup or before posting. */
    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Folder expiry", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "Reminders before an opted-in folder expires"
                },
            )
        }
    }

    /** Android 13+ requires runtime POST_NOTIFICATIONS permission before notifications can post. */
    fun hasNotificationPermission(context: Context): Boolean =
        Build.VERSION.SDK_INT < 33 || context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    /** Call from an Activity only after explaining the opt-in reminder; result arrives via requestPermissions. */
    fun requestNotificationPermission(activity: Activity, requestCode: Int): Boolean {
        if (Build.VERSION.SDK_INT < 33 || hasNotificationPermission(activity)) return true
        activity.requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), requestCode)
        return false
    }

    fun dismiss(context: Context, folderId: String) {
        NotificationManagerCompat.from(context).cancel(folderId.hashCode())
    }
    /** Posts Keep/Let expire actions as broadcasts to the host-supplied receiver component. */
    fun post(
        context: Context,
        folder: ExpiringFolder,
        actionReceiver: Class<out android.content.BroadcastReceiver>,
        notificationId: Int = folder.folderId.hashCode(),
    ): Boolean {
        ensureChannel(context)
        if (!hasNotificationPermission(context)) return false
        fun action(action: String, label: String, requestCode: Int): NotificationCompat.Action {
            val intent = Intent(context, actionReceiver).setAction(action)
                .putExtra(EXTRA_FOLDER_ID, folder.folderId)
            val pending = PendingIntent.getBroadcast(
                context, requestCode, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or if (Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE else 0,
            )
            return NotificationCompat.Action.Builder(0, label, pending).build()
        }
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("Folder expires soon")
            .setContentText("${folder.displayName} is scheduled to expire. Keep it or let it expire.")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .addAction(action(ACTION_KEEP, "Keep", notificationId * 2))
            .addAction(action(ACTION_LET_EXPIRE, "Let expire", notificationId * 2 + 1))
            .build()
        NotificationManagerCompat.from(context).notify(notificationId, notification)
        return true
    }
}

/** Base receiver for host integration. Override and dispatch to an application-owned repository. */
abstract class ExpiryActionReceiver : android.content.BroadcastReceiver() {
    final override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getStringExtra(ExpiryNotifications.EXTRA_FOLDER_ID) ?: return
        val action = when (intent.action) {
            ExpiryNotifications.ACTION_KEEP -> ExpiryAction.KEEP
            ExpiryNotifications.ACTION_LET_EXPIRE -> ExpiryAction.LET_EXPIRE
            else -> return
        }
        onExpiryAction(context.applicationContext, id, action)
    }

    protected abstract fun onExpiryAction(context: Context, folderId: String, action: ExpiryAction)
}

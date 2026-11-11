package dev.juicey.juicebox.upload

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import dev.juicey.juicebox.CopyLinkActivity
import dev.juicey.juicebox.MainActivity
import dev.juicey.juicebox.R
import dev.juicey.juicebox.core.upload.UploadProgress

/**
 * Results are announced on the default channel. While the app is in the
 * background an additional silent, ongoing notification mirrors active
 * transfers so the work is never invisible.
 */
object UploadNotifications {

    const val CHANNEL_RESULTS = "upload_results"
    const val CHANNEL_PROGRESS = "upload_progress"
    private const val GROUP = "dev.juicey.juicebox.uploads"
    private const val PROGRESS_ID = 900_001

    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_RESULTS,
                "Upload results",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = "Links for the files you upload"
            },
        )
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_PROGRESS,
                "Upload progress",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "Silent progress while uploads run in the background"
                setSound(null, null)
                enableVibration(false)
                setShowBadge(false)
            },
        )
    }

    /** Posts or refreshes the silent ongoing card for background transfers. */
    fun showOngoing(
        context: Context,
        active: List<UploadTask>,
        progress: Map<String, UploadProgress>,
    ) {
        if (active.isEmpty()) {
            cancelOngoing(context)
            return
        }
        runCatching {
            NotificationManagerCompat.from(context)
                .notify(PROGRESS_ID, ongoing(context, active, progress))
        }
    }

    fun cancelOngoing(context: Context) {
        runCatching { NotificationManagerCompat.from(context).cancel(PROGRESS_ID) }
    }

    private fun ongoing(
        context: Context,
        active: List<UploadTask>,
        progress: Map<String, UploadProgress>,
    ): Notification {
        val first = active.firstOrNull()
        val current = first?.let { progress[it.localId] }
        val percent = current?.fraction?.times(100)?.toInt()?.coerceIn(0, 100) ?: 0
        val title = if (active.size == 1) {
            "Uploading ${first?.filename?.ifBlank { "file" } ?: "file"}"
        } else {
            "Uploading ${active.size} files"
        }
        val detail = when {
            current != null && current.totalBytes > 0 && active.size == 1 -> {
                "$percent% - ${UploadRunner.formatBytes(current.sentBytes)} of " +
                    UploadRunner.formatBytes(current.totalBytes)
            }
            active.size > 1 -> "$percent% on the current file"
            else -> "Waiting to start"
        }
        return NotificationCompat.Builder(context, CHANNEL_PROGRESS)
            .setSmallIcon(R.drawable.ic_jb_upload)
            .setContentTitle(title)
            .setContentText(detail)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setSilent(true)
            .setShowWhen(false)
            .setProgress(100, percent, current == null)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setGroup(GROUP)
            .setContentIntent(openApp(context))
            .build()
    }

    fun notificationId(localId: String): Int =
        1000 + (localId.hashCode() and 0x0FFFFFFF) % 100000

    fun success(
        context: Context,
        task: UploadTask,
        link: String,
        copied: Boolean,
    ): Notification {
        val title = if (copied) "Uploaded - link copied" else "Uploaded"
        val builder = NotificationCompat.Builder(context, CHANNEL_RESULTS)
            .setSmallIcon(R.drawable.ic_jb_checkbox_on)
            .setContentTitle(title)
            .setContentText(task.filename.ifBlank { link })
            .setStyle(NotificationCompat.BigTextStyle().bigText(link))
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setGroup(GROUP)
            .setContentIntent(openApp(context))

        if (link.isNotBlank()) {
            builder.addAction(0, "Copy", copyIntent(context, task.localId, link))
            builder.addAction(0, "Share", shareIntent(context, link, task.filename))
        }
        return builder.build()
    }

    fun failure(
        context: Context,
        task: UploadTask,
        message: String,
    ): Notification =
        NotificationCompat.Builder(context, CHANNEL_RESULTS)
            .setSmallIcon(R.drawable.ic_jb_warning)
            .setContentTitle("Upload failed")
            .setContentText(message)
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("${task.filename.ifBlank { "File" }}\n$message"),
            )
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_ERROR)
            .setGroup(GROUP)
            .setContentIntent(openApp(context))
            .addAction(0, "Retry", retryIntent(context, task.localId))
            .build()

    fun cancel(context: Context, localId: String) {
        NotificationManagerCompat.from(context).cancel(notificationId(localId))
    }

    fun openApp(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        return PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun copyIntent(context: Context, localId: String, link: String): PendingIntent {
        val intent = Intent(context, CopyLinkActivity::class.java)
            .putExtra(CopyLinkActivity.EXTRA_LINK, link)
            .putExtra(CopyLinkActivity.EXTRA_TASK_ID, localId)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        return PendingIntent.getActivity(
            context,
            localId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun shareIntent(context: Context, link: String, name: String): PendingIntent {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, link)
            putExtra(Intent.EXTRA_SUBJECT, name)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return PendingIntent.getActivity(
            context,
            link.hashCode(),
            Intent.createChooser(intent, "Share link"),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun retryIntent(context: Context, localId: String): PendingIntent {
        val intent = Intent(context, UploadActionReceiver::class.java)
            .setAction(UploadActionReceiver.ACTION_RETRY)
            .putExtra(UploadActionReceiver.EXTRA_TASK_ID, localId)
        return PendingIntent.getBroadcast(
            context,
            localId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}

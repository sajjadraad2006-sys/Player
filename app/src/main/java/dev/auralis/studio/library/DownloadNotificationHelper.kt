package dev.auralis.studio.library

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import dev.auralis.studio.R
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

object DownloadNotificationHelper {

    const val CHANNEL_ID = "downloads_channel"
    private val lastUpdateTimes = ConcurrentHashMap<Int, Long>()

    fun init(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = context.getString(R.string.downloads)
            val channel = NotificationChannel(
                CHANNEL_ID,
                name,
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = context.getString(R.string.download_notifications_desc)
                setShowBadge(false)
                enableVibration(false)
            }
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            manager?.createNotificationChannel(channel)
        }
    }

    fun showStarting(context: Context, notificationId: Int, title: String, artist: String?) {
        init(context)
        val text = if (!artist.isNullOrBlank()) "$artist - $title" else title
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(context.getString(R.string.downloading))
            .setContentText(text)
            .setProgress(100, 0, true)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        runCatching {
            NotificationManagerCompat.from(context).notify(notificationId, notification)
        }
    }

    fun showProgress(
        context: Context,
        notificationId: Int,
        title: String,
        artist: String?,
        bytesDownloaded: Long,
        bytesTotal: Long,
    ) {
        val now = System.currentTimeMillis()
        val lastUpdate = lastUpdateTimes[notificationId] ?: 0L
        if (now - lastUpdate < 350L && bytesDownloaded < bytesTotal && bytesDownloaded > 0) {
            return
        }
        lastUpdateTimes[notificationId] = now

        init(context)
        val percent = if (bytesTotal > 0) ((bytesDownloaded * 100) / bytesTotal).toInt().coerceIn(0, 100) else 0
        val mbDownloaded = bytesDownloaded / 1_048_576.0
        val mbTotal = bytesTotal / 1_048_576.0
        val progressText = if (bytesTotal > 0) {
            String.format(Locale.getDefault(), "%.1f MB / %.1f MB (%d%%)", mbDownloaded, mbTotal, percent)
        } else {
            String.format(Locale.getDefault(), "%.1f MB", mbDownloaded)
        }

        val trackText = if (!artist.isNullOrBlank()) "$artist - $title" else title
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(trackText)
            .setContentText(progressText)
            .setProgress(100, percent, bytesTotal <= 0)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        runCatching {
            NotificationManagerCompat.from(context).notify(notificationId, notification)
        }
    }

    fun showCompleted(context: Context, notificationId: Int, title: String, artist: String?) {
        lastUpdateTimes.remove(notificationId)
        init(context)
        val trackText = if (!artist.isNullOrBlank()) "$artist - $title" else title
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle(context.getString(R.string.downloaded))
            .setContentText(trackText)
            .setOngoing(false)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        runCatching {
            NotificationManagerCompat.from(context).notify(notificationId, notification)
        }
    }

    fun showError(context: Context, notificationId: Int, title: String, errorMessage: String) {
        lastUpdateTimes.remove(notificationId)
        init(context)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setContentTitle(context.getString(R.string.download_failed))
            .setContentText("$title: $errorMessage")
            .setOngoing(false)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        runCatching {
            NotificationManagerCompat.from(context).notify(notificationId, notification)
        }
    }

    fun cancel(context: Context, notificationId: Int) {
        lastUpdateTimes.remove(notificationId)
        runCatching {
            NotificationManagerCompat.from(context).cancel(notificationId)
        }
    }
}

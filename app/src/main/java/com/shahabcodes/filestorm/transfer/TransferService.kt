package com.shahabcodes.filestorm.transfer

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.shahabcodes.filestorm.MainActivity
import com.shahabcodes.filestorm.util.Formatters
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Foreground service that keeps transfers alive when the app is backgrounded
 * and mirrors TransferManager state into a progress notification.
 */
class TransferService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, buildNotification("Preparing…", 0, true))
        scope.launch {
            TransferManager.state.collectLatest { job ->
                when (job.state) {
                    JobState.PREPARING -> notify(buildNotification("Calculating size…", 0, true))
                    JobState.RUNNING -> {
                        val pct = (job.progress * 100).toInt()
                        val text = "$pct% · ${Formatters.speed(job.speedBps)} · " +
                            "${Formatters.bytes(job.bytesDone)} of ${Formatters.bytes(job.totalBytes)}"
                        notify(buildNotification(text, pct, false))
                    }
                    JobState.DONE, JobState.CANCELLED -> {
                        val verb = if (job.op == TransferOp.MOVE) "moved" else "copied"
                        val summary = when {
                            job.state == JobState.CANCELLED -> "Transfer cancelled"
                            job.failedCount > 0 -> "${job.doneCount} $verb, ${job.failedCount} failed"
                            else -> "${job.doneCount} item(s) $verb"
                        }
                        finish(summary)
                    }
                    JobState.IDLE -> stopSelfSafely()
                }
            }
        }
        return START_NOT_STICKY
    }

    private fun finish(summary: String) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(
            DONE_NOTIFICATION_ID,
            NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setContentTitle("FileStorm")
                .setContentText(summary)
                .setContentIntent(contentIntent())
                .setAutoCancel(true)
                .build()
        )
        stopSelfSafely()
    }

    private fun stopSelfSafely() {
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun notify(notification: Notification) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, notification)
    }

    private fun contentIntent(): PendingIntent = PendingIntent.getActivity(
        this, 0,
        Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE
    )

    private fun buildNotification(text: String, progress: Int, indeterminate: Boolean): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(if (TransferManager.state.value.op == TransferOp.MOVE) "Moving files" else "Copying files")
            .setContentText(text)
            .setProgress(100, progress, indeterminate)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setContentIntent(contentIntent())
            .build()

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL_ID = "transfers"
        private const val NOTIFICATION_ID = 42
        private const val DONE_NOTIFICATION_ID = 43

        fun start(context: Context) {
            val intent = Intent(context, TransferService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "File transfers", NotificationManager.IMPORTANCE_LOW
            ).apply { description = "Progress of copy and move operations" }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }
}

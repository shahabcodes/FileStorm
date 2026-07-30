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
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.shahabcodes.filestorm.MainActivity
import com.shahabcodes.filestorm.data.jobs.JobPhase
import com.shahabcodes.filestorm.data.jobs.JobRunner
import com.shahabcodes.filestorm.util.Formatters
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/** Foreground service keeping organize jobs alive with a live notification. */
class JobService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "Organize jobs", NotificationManager.IMPORTANCE_LOW
            ).apply { description = "Progress of monthly organize jobs" }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, build("Scanning sources…", 0, true))
        if (wakeLock == null) {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "FileStorm:job")
                .apply { acquire(2 * 60 * 60 * 1000L) }
        }
        scope.launch {
            JobRunner.state.collectLatest { s ->
                when (s.phase) {
                    JobPhase.SCANNING -> notify(build("Scanning sources…", 0, true))
                    JobPhase.RUNNING -> {
                        val pct = (s.progress * 100).toInt()
                        val month = s.months.getOrNull(s.currentMonthIndex)?.label ?: ""
                        notify(
                            build(
                                "$month · $pct% · ${Formatters.speed(s.speedBps)} · ${s.filesLeft} files left",
                                pct, false,
                            )
                        )
                    }
                    JobPhase.DONE, JobPhase.CANCELLED, JobPhase.FAILED -> {
                        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                        val text = when (s.phase) {
                            JobPhase.DONE ->
                                if (s.failedFiles > 0)
                                    "Job finished: ${s.doneFiles} done, ${s.skippedFiles} skipped, ${s.failedFiles} failed"
                                else
                                    "Job finished: ${s.doneFiles} organized, ${s.skippedFiles} already in place"
                            JobPhase.CANCELLED -> "Job cancelled"
                            else -> "Job failed: ${s.error ?: "unknown error"}"
                        }
                        manager.notify(
                            DONE_NOTIFICATION_ID,
                            NotificationCompat.Builder(this@JobService, CHANNEL_ID)
                                .setSmallIcon(android.R.drawable.stat_sys_download_done)
                                .setContentTitle("File Storm · ${s.jobName}")
                                .setContentText(text)
                                .setContentIntent(contentIntent())
                                .setAutoCancel(true)
                                .build()
                        )
                        stopForeground(STOP_FOREGROUND_REMOVE)
                        stopSelf()
                    }
                    JobPhase.IDLE -> {
                        stopForeground(STOP_FOREGROUND_REMOVE)
                        stopSelf()
                    }
                }
            }
        }
        return START_NOT_STICKY
    }

    private fun notify(notification: Notification) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, notification)
    }

    private fun contentIntent(): PendingIntent = PendingIntent.getActivity(
        this, 1,
        Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE
    )

    private fun build(text: String, progress: Int, indeterminate: Boolean): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("Organizing · ${JobRunner.state.value.jobName}")
            .setContentText(text)
            .setProgress(100, progress, indeterminate)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setContentIntent(contentIntent())
            .build()

    override fun onDestroy() {
        runCatching { wakeLock?.takeIf { it.isHeld }?.release() }
        wakeLock = null
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL_ID = "jobs"
        private const val NOTIFICATION_ID = 44
        private const val DONE_NOTIFICATION_ID = 45

        fun start(context: Context) {
            val intent = Intent(context, JobService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }
}

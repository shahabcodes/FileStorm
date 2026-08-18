package com.shahaabapps.filestorm.transfer

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
import com.shahaabapps.filestorm.MainActivity
import com.shahaabapps.filestorm.data.jobs.VerifyPhase
import com.shahaabapps.filestorm.data.jobs.VerifyRunner
import com.shahaabapps.filestorm.util.Formatters
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/** Foreground service keeping verification passes alive with a live notification. */
class VerifyService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "Verification", NotificationManager.IMPORTANCE_LOW
            ).apply { description = "Progress of transfer verification" }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, build("Scanning sources…", 0, true))
        if (wakeLock == null) {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "FileStorm:verify")
                .apply { acquire(2 * 60 * 60 * 1000L) }
        }
        scope.launch {
            VerifyRunner.state.collectLatest { s ->
                when (s.phase) {
                    VerifyPhase.SCANNING -> notify(build("Scanning sources…", 0, true))
                    VerifyPhase.VERIFYING -> {
                        val pct = (s.progress * 100).toInt()
                        notify(
                            build(
                                "$pct% · ${Formatters.speed(s.speedBps)} · ${s.filesLeft} files left",
                                pct, false,
                            )
                        )
                    }
                    VerifyPhase.DONE, VerifyPhase.CANCELLED, VerifyPhase.FAILED -> {
                        if (!s.cleaning) {
                            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                            val text = when {
                                s.phase == VerifyPhase.CANCELLED -> "Verification cancelled"
                                s.phase == VerifyPhase.FAILED -> "Verification failed: ${s.error ?: "unknown"}"
                                s.issues.isEmpty() -> "All ${s.verifiedFiles} files verified — safe to clean up"
                                else -> "${s.verifiedFiles} verified · ${s.issues.size} need attention"
                            }
                            manager.notify(
                                DONE_NOTIFICATION_ID,
                                NotificationCompat.Builder(this@VerifyService, CHANNEL_ID)
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
                    }
                    VerifyPhase.IDLE -> {
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
        this, 2,
        Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE
    )

    private fun build(text: String, progress: Int, indeterminate: Boolean): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("Verifying · ${VerifyRunner.state.value.jobName}")
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
        private const val CHANNEL_ID = "verify"
        private const val NOTIFICATION_ID = 46
        private const val DONE_NOTIFICATION_ID = 47

        fun start(context: Context) {
            val intent = Intent(context, VerifyService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }
}

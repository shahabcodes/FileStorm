package com.shahabcodes.filestorm.data.vault

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.os.PowerManager
import com.shahabcodes.filestorm.MainActivity
import com.shahabcodes.filestorm.util.Formatters
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Keeps a vault run going while the app is in the background, and shows what it
 * is doing. Encrypting a large folder takes long enough that it will always
 * outlive the screen being on.
 */
class VaultService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_CANCEL) {
            VaultSession.cancel()
            return START_NOT_STICKY
        }
        startForeground(NOTIFICATION_ID, build("Preparing…", 0, true))
        if (wakeLock == null) {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "FileStorm:vault")
                .apply { acquire(4 * 60 * 60 * 1000L) }
        }
        scope.launch {
            VaultSession.run.collectLatest { run ->
                if (!run.active) return@collectLatest
                val p = run.progress
                val verb = if (run.locking) "Encrypting" else "Decrypting"
                val text = when (p.phase) {
                    VaultPhase.SCANNING -> "Looking through the folder…"
                    VaultPhase.RESUMING -> "Finishing interrupted work…"
                    VaultPhase.CLEANING -> "Tidying up…"
                    else -> buildString {
                        append("${p.fileIndex} of ${p.fileCount} · ")
                        append("${Formatters.bytes(p.bytesDone)} of ${Formatters.bytes(p.bytesTotal)}")
                        if (p.speedBps > 1.0) append(" · ${Formatters.speed(p.speedBps)}")
                        if (p.etaSeconds > 0) append(" · ${Formatters.eta(p.etaSeconds)} left")
                        if (p.failed > 0) append(" · ${p.failed} failed")
                    }
                }
                notify(build("$verb · $text", (p.fraction * 100).toInt(), p.phase == VaultPhase.SCANNING))
            }
        }
        return START_STICKY
    }

    private fun build(text: String, percent: Int, indeterminate: Boolean): Notification {
        val open = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java)
                .setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val cancel = PendingIntent.getService(
            this, 1,
            Intent(this, VaultService::class.java).setAction(ACTION_CANCEL),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setContentTitle("File Storm vault")
            .setContentText(text)
            .setStyle(Notification.BigTextStyle().bigText(text))
            .setProgress(100, percent, indeterminate)
            .setContentIntent(open)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .addAction(
                Notification.Action.Builder(
                    android.graphics.drawable.Icon.createWithResource(
                        this, android.R.drawable.ic_menu_close_clear_cancel,
                    ),
                    "Stop", cancel,
                ).build()
            )
            .build()
    }

    private fun notify(notification: Notification) {
        runCatching {
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                .notify(NOTIFICATION_ID, notification)
        }
    }

    private fun createChannel() {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Vault", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Progress while a folder is being encrypted or decrypted"
                setShowBadge(false)
            }
        )
    }

    override fun onDestroy() {
        runCatching { wakeLock?.takeIf { it.isHeld }?.release() }
        wakeLock = null
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL_ID = "filestorm_vault"
        private const val NOTIFICATION_ID = 4413
        const val ACTION_CANCEL = "com.shahabcodes.filestorm.VAULT_CANCEL"

        fun start(context: Context) {
            runCatching {
                context.startForegroundService(Intent(context, VaultService::class.java))
            }
        }

        fun stop(context: Context) {
            runCatching { context.stopService(Intent(context, VaultService::class.java)) }
        }
    }
}

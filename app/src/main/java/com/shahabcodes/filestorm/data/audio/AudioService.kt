package com.shahabcodes.filestorm.data.audio

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.IBinder
import com.shahabcodes.filestorm.MainActivity

/**
 * Keeps playback alive when the app is backgrounded and puts the usual media
 * controls in the notification shade and on the lock screen. The session is
 * what makes hardware and headset buttons work, so it is worth having even
 * though the notification could be built without one.
 */
class AudioService : Service() {

    private var session: MediaSession? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
        session = MediaSession(this, "FileStorm").apply {
            setCallback(object : MediaSession.Callback() {
                override fun onPlay() = AudioPlayer.resume()
                override fun onPause() = AudioPlayer.pause()
                override fun onSkipToNext() = AudioPlayer.next()
                override fun onSkipToPrevious() = AudioPlayer.previous()
                override fun onSeekTo(pos: Long) = AudioPlayer.seekTo(pos)
                override fun onStop() = AudioPlayer.stop()
            })
            isActive = true
        }
        AudioPlayer.onStateChanged = { refresh() }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_TOGGLE -> AudioPlayer.togglePlay()
            ACTION_NEXT -> AudioPlayer.next()
            ACTION_PREVIOUS -> AudioPlayer.previous()
            ACTION_STOP -> {
                AudioPlayer.stop()
                return START_NOT_STICKY
            }
        }
        startForeground(NOTIFICATION_ID, buildNotification())
        refresh()
        return START_STICKY
    }

    private fun refresh() {
        val state = AudioPlayer.state.value
        val track = state.current
        if (track == null) {
            stopSelf()
            return
        }
        session?.setMetadata(
            MediaMetadata.Builder()
                .putString(MediaMetadata.METADATA_KEY_TITLE, track.displayTitle)
                .putString(MediaMetadata.METADATA_KEY_ARTIST, track.displayArtist)
                .putString(MediaMetadata.METADATA_KEY_ALBUM, track.album)
                .putLong(MediaMetadata.METADATA_KEY_DURATION, state.durationMs)
                .build()
        )
        session?.setPlaybackState(
            PlaybackState.Builder()
                .setActions(
                    PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE or
                        PlaybackState.ACTION_SKIP_TO_NEXT or
                        PlaybackState.ACTION_SKIP_TO_PREVIOUS or
                        PlaybackState.ACTION_SEEK_TO or PlaybackState.ACTION_STOP
                )
                .setState(
                    if (state.playing) PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED,
                    state.positionMs,
                    state.speed,
                )
                .build()
        )
        runCatching {
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                .notify(NOTIFICATION_ID, buildNotification())
        }
    }

    private fun action(name: String, icon: Int, label: String): Notification.Action {
        val intent = Intent(this, AudioService::class.java).setAction(name)
        val pending = PendingIntent.getService(
            this, name.hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return Notification.Action.Builder(Icon_(icon), label, pending).build()
    }

    @Suppress("FunctionName")
    private fun Icon_(res: Int) = android.graphics.drawable.Icon.createWithResource(this, res)

    private fun buildNotification(): Notification {
        val state = AudioPlayer.state.value
        val track = state.current

        val open = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java)
                .setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val builder = Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle(track?.displayTitle ?: "File Storm")
            .setContentText(track?.displayArtist.orEmpty())
            .setContentIntent(open)
            .setOnlyAlertOnce(true)
            .setOngoing(state.playing)
            .setVisibility(Notification.VISIBILITY_PUBLIC)

        builder.addAction(
            action(ACTION_PREVIOUS, android.R.drawable.ic_media_previous, "Previous")
        )
        builder.addAction(
            if (state.playing) action(ACTION_TOGGLE, android.R.drawable.ic_media_pause, "Pause")
            else action(ACTION_TOGGLE, android.R.drawable.ic_media_play, "Play")
        )
        builder.addAction(action(ACTION_NEXT, android.R.drawable.ic_media_next, "Next"))
        builder.addAction(action(ACTION_STOP, android.R.drawable.ic_menu_close_clear_cancel, "Stop"))

        session?.sessionToken?.let { token ->
            builder.style = Notification.MediaStyle()
                .setMediaSession(token)
                // Which actions stay visible on the collapsed notification.
                .setShowActionsInCompactView(0, 1, 2)
        }
        AudioPlayer.artworkFor(track?.path.orEmpty(), maxSize = 320)?.let {
            builder.setLargeIcon(it)
        }
        return builder.build()
    }

    private fun createChannel() {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Playback", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Controls for audio playing in File Storm"
                setShowBadge(false)
            }
        )
    }

    override fun onDestroy() {
        AudioPlayer.onStateChanged = null
        session?.isActive = false
        session?.release()
        session = null
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL_ID = "filestorm_playback"
        private const val NOTIFICATION_ID = 4411
        const val ACTION_TOGGLE = "com.shahabcodes.filestorm.PLAY_TOGGLE"
        const val ACTION_NEXT = "com.shahabcodes.filestorm.PLAY_NEXT"
        const val ACTION_PREVIOUS = "com.shahabcodes.filestorm.PLAY_PREVIOUS"
        const val ACTION_STOP = "com.shahabcodes.filestorm.PLAY_STOP"

        fun start(context: Context) {
            runCatching {
                context.startForegroundService(Intent(context, AudioService::class.java))
            }
        }

        fun stop(context: Context) {
            runCatching { context.stopService(Intent(context, AudioService::class.java)) }
        }
    }
}

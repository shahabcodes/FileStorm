package com.shahabcodes.filestorm.data.audio

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.File

enum class RepeatMode { OFF, ALL, ONE }

data class Track(
    val path: String,
    val name: String,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
) {
    val displayTitle: String get() = title.ifBlank { name.substringBeforeLast('.') }
    val displayArtist: String get() = artist.ifBlank { "Unknown artist" }
}

data class AudioState(
    val queue: List<Track> = emptyList(),
    /** Playback order; shuffle reorders this rather than the queue itself. */
    val order: List<Int> = emptyList(),
    val orderIndex: Int = -1,
    val playing: Boolean = false,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val repeat: RepeatMode = RepeatMode.OFF,
    val shuffle: Boolean = false,
    val speed: Float = 1f,
    val error: String? = null,
) {
    val current: Track? get() = order.getOrNull(orderIndex)?.let { queue.getOrNull(it) }
    val hasNext: Boolean get() = orderIndex < order.lastIndex || repeat != RepeatMode.OFF
    val hasPrevious: Boolean get() = orderIndex > 0 || repeat != RepeatMode.OFF
    val progress: Float
        get() = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
}

/**
 * Audio playback for the whole app. Unlike the image and video viewer this
 * outlives the screen that started it — you can carry on browsing, or leave the
 * app entirely, and the music keeps going — so the player is a singleton and the
 * UI is only ever a view onto it.
 */
object AudioPlayer {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val _state = MutableStateFlow(AudioState())
    val state: StateFlow<AudioState> = _state

    private var player: MediaPlayer? = null
    private var appContext: Context? = null
    private var focusRequest: AudioFocusRequest? = null
    private var ticker: kotlinx.coroutines.Job? = null
    /** Set when focus was lost while playing, so it can resume when handed back. */
    private var resumeOnFocus = false
    /** MediaPlayer only accepts start() once prepared; these bridge the gap. */
    private var prepared = false
    private var pendingPlay = false

    /** Notified whenever playback state changes, so the service can re-notify. */
    var onStateChanged: (() -> Unit)? = null

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    /** Reads tags off the file. Cheap enough for a folder's worth of audio. */
    fun readTrack(file: File): Track {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.absolutePath)
            Track(
                path = file.absolutePath,
                name = file.name,
                title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE).orEmpty(),
                artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST).orEmpty(),
                album = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM).orEmpty(),
                durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                    ?.toLongOrNull() ?: 0L,
            )
        } catch (t: Throwable) {
            Track(file.absolutePath, file.name, "", "", "", 0L)
        } finally {
            runCatching { retriever.release() }
        }
    }

    /** Embedded cover art, or null. Decoded small since it only fills a card. */
    fun artworkFor(path: String, maxSize: Int = 512): Bitmap? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(path)
            val bytes = retriever.embeddedPicture ?: return null
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            var sample = 1
            while (bounds.outWidth / sample > maxSize || bounds.outHeight / sample > maxSize) {
                sample *= 2
            }
            BitmapFactory.decodeByteArray(
                bytes, 0, bytes.size,
                BitmapFactory.Options().apply { inSampleSize = sample },
            )
        } catch (t: Throwable) {
            null
        } finally {
            runCatching { retriever.release() }
        }
    }

    /** Starts a queue at [startIndex]. Tags are read off the main thread. */
    fun play(paths: List<String>, startIndex: Int) {
        val context = appContext ?: return
        if (paths.isEmpty()) return
        val safeStart = startIndex.coerceIn(0, paths.lastIndex)

        // Show the tapped track immediately from its filename, then fill in the
        // real tags — reading a whole folder's metadata takes long enough to be
        // felt as a delay before anything appears.
        val provisional = paths.map { path ->
            val file = File(path)
            Track(path, file.name, "", "", "", 0L)
        }
        val order = buildOrder(provisional.indices.toList(), _state.value.shuffle, safeStart)
        _state.value = _state.value.copy(
            queue = provisional,
            order = order,
            orderIndex = order.indexOf(safeStart),
            error = null,
        )
        openCurrent(autoPlay = true)
        AudioService.start(context)

        scope.launch(Dispatchers.IO) {
            val full = provisional.map { readTrack(File(it.path)) }
            scope.launch {
                // Only apply if the same queue is still loaded.
                if (_state.value.queue.map { it.path } == full.map { it.path }) {
                    _state.value = _state.value.copy(queue = full)
                    onStateChanged?.invoke()
                }
            }
        }
    }

    /**
     * Plays [path] with the rest of the audio in its folder queued behind it,
     * which is what every music player does and saves the caller from having to
     * know the folder's contents. The listing runs off the main thread because
     * some of this user's folders hold tens of thousands of files.
     */
    fun playFolderOf(path: String) {
        scope.launch(Dispatchers.IO) {
            val file = File(path)
            val siblings = file.parentFile?.listFiles()
                ?.filter { it.isFile && isAudio(it.name) }
                ?.sortedBy { it.name.lowercase() }
                ?.map { it.absolutePath }
                ?.ifEmpty { null }
                ?: listOf(path)
            val index = siblings.indexOf(path).coerceAtLeast(0)
            scope.launch { play(siblings, index) }
        }
    }

    private fun isAudio(name: String): Boolean =
        com.shahabcodes.filestorm.data.FsEntry.kindOf(name, false) ==
            com.shahabcodes.filestorm.data.FileKind.AUDIO

    private fun buildOrder(indices: List<Int>, shuffle: Boolean, first: Int): List<Int> =
        if (!shuffle) indices
        else listOf(first) + (indices - first).shuffled()

    private fun openCurrent(autoPlay: Boolean) {
        val track = _state.value.current ?: return
        releasePlayer()
        prepared = false
        pendingPlay = autoPlay
        val created = runCatching {
            MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                setDataSource(track.path)
                setOnCompletionListener { onCompletion() }
                setOnErrorListener { _, _, _ ->
                    _state.value = _state.value.copy(error = "Could not play ${track.name}")
                    true
                }
                setOnPreparedListener { mp ->
                    prepared = true
                    _state.value = _state.value.copy(
                        durationMs = mp.duration.toLong().coerceAtLeast(0L),
                        positionMs = 0,
                    )
                    applySpeed(_state.value.speed, restartIfPlaying = false)
                    if (pendingPlay) {
                        pendingPlay = false
                        resume()
                    } else {
                        onStateChanged?.invoke()
                    }
                }
                // Asynchronous on purpose: prepare() blocks the caller while the
                // file is opened and decoded, and on the main thread that ate
                // any tap made in the moment right after skipping a track.
                prepareAsync()
            }
        }.getOrNull()

        if (created == null) {
            _state.value = _state.value.copy(error = "Could not play ${track.name}", playing = false)
            return
        }
        player = created
        // Reflect the intent straight away; playback follows once prepared.
        if (autoPlay) _state.value = _state.value.copy(playing = true, error = null)
        onStateChanged?.invoke()
    }

    private fun onCompletion() {
        when (_state.value.repeat) {
            RepeatMode.ONE -> {
                seekTo(0)
                resume()
            }
            RepeatMode.ALL -> next()
            RepeatMode.OFF -> {
                if (_state.value.orderIndex < _state.value.order.lastIndex) next()
                else {
                    _state.value = _state.value.copy(playing = false, positionMs = 0)
                    stopTicker()
                    abandonFocus()
                    onStateChanged?.invoke()
                }
            }
        }
    }

    fun togglePlay() {
        if (_state.value.playing) pause() else resume()
    }

    fun resume() {
        // The player is released whenever the surface goes away or playback was
        // stopped, so a queued track with no player must be reopened rather
        // than leaving the button looking dead.
        if (player == null) {
            if (_state.value.current == null) return
            openCurrent(autoPlay = false)
        }
        val player = player ?: return
        // Still opening: remember the intent and let the prepared callback
        // start it, so the button reacts now rather than being ignored.
        if (!prepared) {
            pendingPlay = true
            _state.value = _state.value.copy(playing = true, error = null)
            onStateChanged?.invoke()
            return
        }
        if (!requestFocus()) {
            _state.value = _state.value.copy(
                error = "Another app is using the audio right now",
            )
            onStateChanged?.invoke()
            return
        }
        runCatching { player.start() }
        _state.value = _state.value.copy(playing = true, error = null)
        startTicker()
        onStateChanged?.invoke()
    }

    fun pause() {
        // Cancels a play that was waiting on prepare, or it would start anyway.
        pendingPlay = false
        runCatching { player?.pause() }
        _state.value = _state.value.copy(playing = false)
        stopTicker()
        onStateChanged?.invoke()
    }

    fun next() {
        val s = _state.value
        if (s.order.isEmpty()) return
        val nextIndex = when {
            s.orderIndex < s.order.lastIndex -> s.orderIndex + 1
            s.repeat != RepeatMode.OFF -> 0
            else -> return
        }
        _state.value = s.copy(orderIndex = nextIndex)
        openCurrent(autoPlay = true)
    }

    fun previous() {
        val s = _state.value
        if (s.order.isEmpty()) return
        // Matches every other player: rewind first, and only step back if the
        // track has barely started.
        if (s.positionMs > 3000) {
            seekTo(0)
            return
        }
        val prevIndex = when {
            s.orderIndex > 0 -> s.orderIndex - 1
            s.repeat != RepeatMode.OFF -> s.order.lastIndex
            else -> return
        }
        _state.value = s.copy(orderIndex = prevIndex)
        openCurrent(autoPlay = true)
    }

    fun playAt(queueIndex: Int) {
        val s = _state.value
        val at = s.order.indexOf(queueIndex)
        if (at < 0) return
        _state.value = s.copy(orderIndex = at)
        openCurrent(autoPlay = true)
    }

    fun seekTo(positionMs: Long) {
        val player = player ?: return
        val clamped = positionMs.coerceIn(0, _state.value.durationMs.coerceAtLeast(0))
        runCatching { player.seekTo(clamped.toInt()) }
        _state.value = _state.value.copy(positionMs = clamped)
        onStateChanged?.invoke()
    }

    fun toggleShuffle() {
        val s = _state.value
        val currentTrack = s.orderIndex.let { s.order.getOrNull(it) } ?: 0
        val shuffle = !s.shuffle
        val order = buildOrder(s.queue.indices.toList(), shuffle, currentTrack)
        _state.value = s.copy(
            shuffle = shuffle,
            order = order,
            orderIndex = order.indexOf(currentTrack),
        )
        onStateChanged?.invoke()
    }

    fun cycleRepeat() {
        _state.value = _state.value.copy(
            repeat = when (_state.value.repeat) {
                RepeatMode.OFF -> RepeatMode.ALL
                RepeatMode.ALL -> RepeatMode.ONE
                RepeatMode.ONE -> RepeatMode.OFF
            }
        )
        onStateChanged?.invoke()
    }

    fun setSpeed(speed: Float) {
        applySpeed(speed, restartIfPlaying = true)
    }

    private fun applySpeed(speed: Float, restartIfPlaying: Boolean) {
        val player = player ?: run {
            _state.value = _state.value.copy(speed = speed)
            return
        }
        runCatching {
            val wasPlaying = _state.value.playing
            player.playbackParams = player.playbackParams.setSpeed(speed)
            // Setting params starts playback on some devices, so put it back.
            if (!wasPlaying && restartIfPlaying) player.pause()
        }
        _state.value = _state.value.copy(speed = speed)
        onStateChanged?.invoke()
    }

    /** Clears everything and lets the service stop. */
    fun stop() {
        releasePlayer()
        abandonFocus()
        stopTicker()
        _state.value = AudioState(repeat = _state.value.repeat, shuffle = _state.value.shuffle)
        appContext?.let { AudioService.stop(it) }
    }

    private fun releasePlayer() {
        prepared = false
        runCatching {
            player?.setOnCompletionListener(null)
            player?.release()
        }
        player = null
    }

    private fun startTicker() {
        stopTicker()
        ticker = scope.launch {
            while (true) {
                val p = player
                if (p != null && _state.value.playing) {
                    _state.value = _state.value.copy(
                        positionMs = runCatching { p.currentPosition.toLong() }.getOrDefault(0L)
                    )
                }
                delay(500)
            }
        }
    }

    private fun stopTicker() {
        ticker?.cancel()
        ticker = null
    }

    // ── Audio focus ────────────────────────────────────────────────────
    private fun requestFocus(): Boolean {
        val context = appContext ?: return true
        val manager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        if (focusRequest == null) {
            focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                .setOnAudioFocusChangeListener { change ->
                    when (change) {
                        AudioManager.AUDIOFOCUS_LOSS -> {
                            resumeOnFocus = false
                            pause()
                        }
                        AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                            resumeOnFocus = _state.value.playing
                            pause()
                        }
                        AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK ->
                            runCatching { player?.setVolume(0.25f, 0.25f) }
                        AudioManager.AUDIOFOCUS_GAIN -> {
                            runCatching { player?.setVolume(1f, 1f) }
                            if (resumeOnFocus) {
                                resumeOnFocus = false
                                resume()
                            }
                        }
                    }
                }
                .build()
        }
        return manager.requestAudioFocus(focusRequest!!) ==
            AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }

    private fun abandonFocus() {
        val context = appContext ?: return
        val manager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        focusRequest?.let { manager.abandonAudioFocusRequest(it) }
    }
}

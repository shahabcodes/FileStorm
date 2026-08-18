package com.shahaabapps.filestorm.ui.viewer

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * A handle on whichever video is on screen, so picture-in-picture can drive it.
 *
 * The player itself stays inside the composable that owns its surface — pulling
 * it out here would mean managing the TextureView's lifecycle by hand. This
 * only holds what the activity needs: what is playing, its shape, and a way to
 * toggle it from the floating window's controls.
 */
object VideoController {

    /** True while the activity is in picture-in-picture. Chrome hides itself. */
    var inPip by mutableStateOf(false)

    var activePath by mutableStateOf<String?>(null)
        private set
    var playing by mutableStateOf(false)
        private set
    var videoWidth by mutableIntStateOf(0)
        private set
    var videoHeight by mutableIntStateOf(0)
        private set

    private var toggleAction: (() -> Unit)? = null
    private var pauseAction: (() -> Unit)? = null

    /** Notified when playback state changes so the PiP actions can be rebuilt. */
    var onStateChanged: (() -> Unit)? = null

    val available: Boolean get() = activePath != null

    fun bind(path: String, onToggle: () -> Unit, onPause: () -> Unit) {
        activePath = path
        toggleAction = onToggle
        pauseAction = onPause
        onStateChanged?.invoke()
    }

    fun unbind(path: String) {
        if (activePath != path) return
        activePath = null
        toggleAction = null
        pauseAction = null
        playing = false
        videoWidth = 0
        videoHeight = 0
        onStateChanged?.invoke()
    }

    fun reportPlaying(value: Boolean) {
        if (playing == value) return
        playing = value
        onStateChanged?.invoke()
    }

    fun reportSize(width: Int, height: Int) {
        videoWidth = width
        videoHeight = height
        onStateChanged?.invoke()
    }

    fun toggle() {
        toggleAction?.invoke()
    }

    /** Used when the app leaves the screen, including closing the PiP window. */
    fun pause() {
        pauseAction?.invoke()
    }
}

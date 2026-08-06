package com.shahabcodes.filestorm.data.vault

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.shahabcodes.filestorm.data.FileRepository
import com.shahabcodes.filestorm.data.FsEntry
import com.shahabcodes.filestorm.data.TrashManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.io.File

data class VaultRun(
    val root: String = "",
    val locking: Boolean = true,
    val active: Boolean = false,
    val progress: VaultProgress = VaultProgress(VaultPhase.DONE),
    val summary: VaultSummary? = null,
)

/**
 * Holds whichever vaults are currently open, and runs the engine.
 *
 * Master keys live only in memory here — never written anywhere, never in
 * Keystore unless the user asks for biometric convenience, and dropped when the
 * vault locks. Locking is therefore just forgetting the key.
 */
object VaultSession {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val unlocked = HashMap<String, ByteArray>()
    private val unlockedAt = HashMap<String, Long>()

    private val _run = MutableStateFlow(VaultRun())
    val run: StateFlow<VaultRun> = _run

    /** Bumped whenever a vault opens or closes so the UI recomposes. */
    var revision by mutableStateOf(0)
        private set

    @Volatile
    private var cancelRequested = false

    fun isUnlocked(root: File): Boolean {
        expire()
        return unlocked.containsKey(root.absolutePath)
    }

    fun keyFor(root: File): ByteArray? {
        expire()
        return unlocked[root.absolutePath]
    }

    suspend fun unlock(root: File, passphrase: CharArray): Boolean = withContext(Dispatchers.Default) {
        val folder = VaultFolder(root)
        val key = folder.unlock(passphrase)
        if (key == null) {
            VaultLog.error("unlock refused for ${VaultEngine.shortId(root.absolutePath)}")
            return@withContext false
        }
        remember(root, key)
        VaultLog.info("unlocked ${VaultEngine.shortId(root.absolutePath)}")
        true
    }

    suspend fun unlockWithRecoveryCode(root: File, code: String): Boolean =
        withContext(Dispatchers.Default) {
            val key = VaultFolder(root).unlockWithRecoveryCode(code) ?: return@withContext false
            remember(root, key)
            VaultLog.info("unlocked with recovery code")
            true
        }

    private fun remember(root: File, key: ByteArray) {
        unlocked[root.absolutePath] = key
        unlockedAt[root.absolutePath] = System.currentTimeMillis()
        revision++
    }

    fun lock(root: File) {
        VaultCrypto.wipe(unlocked.remove(root.absolutePath))
        unlockedAt.remove(root.absolutePath)
        revision++
        VaultLog.info("locked ${VaultEngine.shortId(root.absolutePath)}")
    }

    /** Called when the app leaves the foreground. */
    fun lockAllIfLeaving() {
        if (VaultPrefs.autoLock == AutoLock.ON_LEAVE) lockAll()
    }

    fun lockAll() {
        unlocked.values.forEach { VaultCrypto.wipe(it) }
        unlocked.clear()
        unlockedAt.clear()
        revision++
    }

    private fun expire() {
        val window = VaultPrefs.autoLock.millis
        if (window <= 0L || window == Long.MAX_VALUE) return
        val now = System.currentTimeMillis()
        val stale = unlockedAt.filterValues { now - it > window }.keys
        if (stale.isEmpty()) return
        stale.forEach {
            VaultCrypto.wipe(unlocked.remove(it))
            unlockedAt.remove(it)
        }
        revision++
    }

    // ── Running the engine ─────────────────────────────────────────────

    fun cancel() {
        cancelRequested = true
    }

    fun startLock(context: Context, root: File) = start(context, root, locking = true)

    fun startUnlock(context: Context, root: File) = start(context, root, locking = false)

    private fun start(context: Context, root: File, locking: Boolean) {
        if (_run.value.active) return
        val key = keyFor(root) ?: return
        cancelRequested = false
        _run.value = VaultRun(root.absolutePath, locking, active = true)
        VaultService.start(context)

        scope.launch {
            val folder = VaultFolder(root)
            VaultLog.info(
                (if (locking) "locking " else "unlocking ") +
                    VaultEngine.shortId(root.absolutePath)
            )
            val options = VaultPrefs.options { original -> disposeOriginal(original) }
            val listener = VaultEngine.Listener { progress ->
                _run.value = _run.value.copy(progress = progress)
            }
            val summary = if (locking) {
                VaultEngine.lockFolder(folder, key, options, listener, { cancelRequested }) {
                    VaultLog.detail(it)
                }
            } else {
                VaultEngine.unlockFolder(folder, key, options, listener, { cancelRequested }) {
                    VaultLog.detail(it)
                }
            }
            VaultLog.info(
                "finished: ${summary.succeeded} ok, ${summary.failed} failed" +
                    if (summary.cancelled) ", cancelled" else ""
            )
            summary.failures.take(20).forEach { VaultLog.error("${it.path}: ${it.reason}") }
            FileRepository.invalidate(root.absolutePath)
            if (!locking && summary.failed == 0 && !summary.cancelled) lock(root)
            _run.value = _run.value.copy(active = false, summary = summary)
            revision++
            VaultService.stop(context)
        }
    }

    /**
     * Trash keeps a way back but holds the space until it is emptied, which
     * matters when a folder is larger than the free space left. Deleting is the
     * default, and only ever happens after the encrypted copy has been verified.
     */
    private fun disposeOriginal(original: File): Boolean = runCatching {
        if (!VaultPrefs.originalsToTrash) return@runCatching original.delete()
        val entry = FsEntry.from(original)
        val failed = runBlocking { TrashManager.moveToTrash(listOf(entry)) }
        failed == 0
    }.getOrDefault(false)

    fun clearSummary() {
        _run.value = _run.value.copy(summary = null)
    }
}

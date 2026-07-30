package com.shahabcodes.filestorm.data

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.File

/** Whole-storage census by file type, cached so the dashboard appears instantly. */
object StorageAnalyzer {

    data class CategoryStat(val kind: FileKind, val count: Int, val bytes: Long)

    data class Snapshot(
        val stats: List<CategoryStat>,
        val scannedAt: Long,
    ) {
        val totalCategorizedBytes: Long get() = stats.sumOf { it.bytes }
        val totalFiles: Int get() = stats.sumOf { it.count }
    }

    private lateinit var sp: SharedPreferences
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    var snapshot by mutableStateOf<Snapshot?>(null)
        private set
    var scanning by mutableStateOf(false)
        private set

    private val kinds = listOf(
        FileKind.IMAGE, FileKind.VIDEO, FileKind.AUDIO, FileKind.DOCUMENT,
        FileKind.ARCHIVE, FileKind.APK, FileKind.OTHER,
    )

    fun init(context: Context) {
        sp = context.getSharedPreferences("filestorm_dashboard", Context.MODE_PRIVATE)
        snapshot = runCatching {
            val json = sp.getString("snapshot", null) ?: return@runCatching null
            val o = JSONObject(json)
            Snapshot(
                stats = kinds.map { kind ->
                    val k = o.optJSONObject(kind.name)
                    CategoryStat(kind, k?.optInt("count") ?: 0, k?.optLong("bytes") ?: 0L)
                },
                scannedAt = o.optLong("scannedAt"),
            )
        }.getOrNull()
    }

    /** Re-scans everything in the background; safe to call repeatedly. */
    fun refresh() {
        if (scanning) return
        scanning = true
        scope.launch {
            val counts = HashMap<FileKind, Int>()
            val bytes = HashMap<FileKind, Long>()
            val queue = ArrayDeque<File>()
            queue.add(File(FileRepository.rootPath))
            while (queue.isNotEmpty()) {
                val dir = queue.removeFirst()
                val children = dir.listFiles() ?: continue
                for (child in children) {
                    if (child.name == ".FileStorm") continue
                    if (child.name.startsWith(".")) continue
                    if (child.isDirectory) {
                        // Android/data & Android/obb are app-private and mostly unreadable.
                        if (!(dir.absolutePath == FileRepository.rootPath && child.name == "Android")) {
                            queue.add(child)
                        }
                    } else {
                        val kind = FsEntry.kindOf(child.name, false)
                        counts[kind] = (counts[kind] ?: 0) + 1
                        bytes[kind] = (bytes[kind] ?: 0L) + child.length()
                    }
                }
            }
            val result = Snapshot(
                stats = kinds.map { CategoryStat(it, counts[it] ?: 0, bytes[it] ?: 0L) },
                scannedAt = System.currentTimeMillis(),
            )
            snapshot = result
            persist(result)
            scanning = false
        }
    }

    private fun persist(s: Snapshot) {
        runCatching {
            val o = JSONObject().put("scannedAt", s.scannedAt)
            s.stats.forEach { stat ->
                o.put(stat.kind.name, JSONObject().put("count", stat.count).put("bytes", stat.bytes))
            }
            sp.edit().putString("snapshot", o.toString()).apply()
        }
    }
}

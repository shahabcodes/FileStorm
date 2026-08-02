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
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Everything the dashboard knows about how storage is being used, gathered in a
 * single pass over the filesystem.
 *
 * The pass only collects what the enabled cards actually need: turn a card off
 * and its work disappears from the walk, and turn every scanning card off and
 * the walk never starts. That is why [refresh] takes the enabled set rather
 * than computing everything and letting the UI choose.
 */
object StorageInsights {

    private const val TOP_FILES = 20
    private const val TOP_FOLDERS = 20
    private const val RECENT_LIMIT = 40
    private const val RECENT_WINDOW_MS = 7L * 24 * 60 * 60 * 1000
    /** Long enough to be worth reporting, short enough not to be a wall of text. */
    private const val CLEANUP_LIMIT = 500

    data class FileEntry(
        val path: String,
        val name: String,
        val size: Long,
        val modified: Long,
    ) {
        val folder: String get() = path.substringBeforeLast(File.separatorChar, "")
    }

    data class FolderEntry(
        val path: String,
        val name: String,
        val bytes: Long,
        val files: Int,
    )

    data class MonthBucket(val key: String, val label: String, val bytes: Long, val count: Int)

    data class Snapshot(
        val biggestFiles: List<FileEntry> = emptyList(),
        val largestFolders: List<FolderEntry> = emptyList(),
        val months: List<MonthBucket> = emptyList(),
        val recent: List<FileEntry> = emptyList(),
        val emptyFolders: List<String> = emptyList(),
        val zeroByteFiles: List<FileEntry> = emptyList(),
        val emptyFolderCount: Int = 0,
        val zeroByteCount: Int = 0,
        /** Which cards this snapshot actually has data for. */
        val covered: Set<String> = emptySet(),
        /** Whether hidden files counted, so the cards can say so. */
        val includedHidden: Boolean = false,
        val scannedAt: Long = 0L,
    )

    private lateinit var sp: SharedPreferences
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val monthFormat = SimpleDateFormat("MMMM yyyy", Locale.getDefault())
    private val monthKeyFormat = SimpleDateFormat("yyyy-MM", Locale.US)

    var snapshot by mutableStateOf<Snapshot?>(null)
        private set
    var scanning by mutableStateOf(false)
        private set
    var scannedFiles by mutableStateOf(0)
        private set

    fun init(context: Context) {
        sp = context.getSharedPreferences("filestorm_insights", Context.MODE_PRIVATE)
        snapshot = runCatching { read(sp.getString("snapshot", null) ?: return@runCatching null) }
            .getOrNull()
    }

    /**
     * True when the enabled cards need something the stored snapshot does not
     * have, or the snapshot has gone stale. Callers use this so that simply
     * opening the dashboard does not trigger a full walk every time.
     */
    fun needsRefresh(wanted: Set<DashboardCard>, maxAgeMillis: Long = 6 * 60 * 60 * 1000): Boolean {
        if (wanted.isEmpty()) return false
        val current = snapshot ?: return true
        if (System.currentTimeMillis() - current.scannedAt > maxAgeMillis) return true
        // Flipping Show Hidden changes the totals, so the old numbers are wrong.
        if (current.includedHidden != Prefs.showHidden) return true
        return wanted.any { it.key !in current.covered }
    }

    fun refresh(wanted: Set<DashboardCard>) {
        if (scanning || wanted.isEmpty()) return
        scanning = true
        scannedFiles = 0
        scope.launch {
            val result = runCatching { walk(wanted) }.getOrNull()
            if (result != null) {
                snapshot = result
                persist(result)
            }
            scanning = false
        }
    }

    private fun walk(wanted: Set<DashboardCard>): Snapshot {
        val wantFiles = DashboardCard.BIGGEST_FILES in wanted
        val wantFolders = DashboardCard.LARGEST_FOLDERS in wanted
        val wantGrowth = DashboardCard.GROWTH in wanted
        val wantRecent = DashboardCard.RECENT in wanted
        val wantReclaim = DashboardCard.RECLAIM in wanted

        val biggest = ArrayList<FileEntry>()
        val recent = ArrayList<FileEntry>()
        val folderBytes = HashMap<String, Long>()
        val folderFiles = HashMap<String, Int>()
        val monthBytes = HashMap<String, Long>()
        val monthCount = HashMap<String, Int>()
        val emptyFolders = ArrayList<String>()
        val zeroByte = ArrayList<FileEntry>()
        var emptyFolderCount = 0
        var zeroByteCount = 0

        // Hidden files are real files taking real space — a .thumbnails folder
        // holding gigabytes is exactly what these cards exist to surface — so
        // the walk follows the app's Show Hidden setting rather than always
        // skipping them.
        val includeHidden = Prefs.showHidden
        val recentCutoff = System.currentTimeMillis() - RECENT_WINDOW_MS
        val root = File(FileRepository.rootPath)
        val rootPath = root.absolutePath.trimEnd(File.separatorChar)
        val queue = ArrayDeque<File>()
        queue.add(root)
        var seen = 0

        while (queue.isNotEmpty()) {
            val dir = queue.removeFirst()
            val children = dir.listFiles()
            if (children == null) continue
            if (children.isEmpty() && wantReclaim && dir.absolutePath != rootPath) {
                emptyFolderCount++
                if (emptyFolders.size < CLEANUP_LIMIT) emptyFolders.add(dir.absolutePath)
                continue
            }
            for (child in children) {
                val name = child.name
                if (name == ".FileStorm") continue
                if (!includeHidden && name.startsWith(".")) continue
                if (child.isDirectory) {
                    // Android/data and Android/obb are app-private and unreadable;
                    // Android/media is not, and holds real user files.
                    if (dir.absolutePath == rootPath && name == "Android") {
                        val media = File(child, "media")
                        if (media.isDirectory) queue.add(media)
                        continue
                    }
                    queue.add(child)
                    continue
                }

                val size = child.length()
                val modified = child.lastModified()
                seen++
                if (seen % 1000 == 0) scannedFiles = seen

                if (wantFiles) {
                    // Keep only the top N: append, and once the buffer grows past
                    // twice the target, trim it back down.
                    biggest.add(FileEntry(child.absolutePath, name, size, modified))
                    if (biggest.size > TOP_FILES * 4) {
                        biggest.sortByDescending { it.size }
                        while (biggest.size > TOP_FILES) biggest.removeAt(biggest.lastIndex)
                    }
                }
                if (wantRecent && modified >= recentCutoff) {
                    recent.add(FileEntry(child.absolutePath, name, size, modified))
                    if (recent.size > RECENT_LIMIT * 4) {
                        recent.sortByDescending { it.modified }
                        while (recent.size > RECENT_LIMIT) recent.removeAt(recent.lastIndex)
                    }
                }
                if (wantFolders) {
                    val parent = dir.absolutePath
                    folderBytes[parent] = (folderBytes[parent] ?: 0L) + size
                    folderFiles[parent] = (folderFiles[parent] ?: 0) + 1
                }
                if (wantGrowth && modified > 0) {
                    val key = monthKeyFormat.format(java.util.Date(modified))
                    monthBytes[key] = (monthBytes[key] ?: 0L) + size
                    monthCount[key] = (monthCount[key] ?: 0) + 1
                }
                // Never offer a dotfile for cleanup even when hidden files are
                // being counted: .nomedia is 0 bytes and deleting it would
                // change how the gallery treats the whole folder.
                if (wantReclaim && size == 0L && !name.startsWith(".")) {
                    zeroByteCount++
                    if (zeroByte.size < CLEANUP_LIMIT) {
                        zeroByte.add(FileEntry(child.absolutePath, name, 0L, modified))
                    }
                }
            }
        }
        scannedFiles = seen

        biggest.sortByDescending { it.size }
        recent.sortByDescending { it.modified }

        // Folder totals so far count only direct children. Roll each folder's
        // bytes up into its ancestors so a parent reports everything beneath it.
        val rolled = HashMap<String, Long>()
        val rolledFiles = HashMap<String, Int>()
        if (wantFolders) {
            for ((path, bytes) in folderBytes) {
                val files = folderFiles[path] ?: 0
                var cursor: String? = path
                while (cursor != null && cursor.length >= rootPath.length) {
                    rolled[cursor] = (rolled[cursor] ?: 0L) + bytes
                    rolledFiles[cursor] = (rolledFiles[cursor] ?: 0) + files
                    if (cursor == rootPath) break
                    cursor = File(cursor).parent
                }
            }
            rolled.remove(rootPath)
        }

        val folders = rolled.entries
            .sortedByDescending { it.value }
            .take(TOP_FOLDERS)
            .map {
                FolderEntry(
                    path = it.key,
                    name = it.key.substringAfterLast(File.separatorChar),
                    bytes = it.value,
                    files = rolledFiles[it.key] ?: 0,
                )
            }

        val months = monthBytes.entries
            .sortedByDescending { it.key }
            .take(12)
            .map {
                val parsed = runCatching { monthKeyFormat.parse(it.key) }.getOrNull()
                MonthBucket(
                    key = it.key,
                    label = if (parsed != null) monthFormat.format(parsed) else it.key,
                    bytes = it.value,
                    count = monthCount[it.key] ?: 0,
                )
            }

        return Snapshot(
            biggestFiles = biggest.take(TOP_FILES),
            largestFolders = folders,
            months = months,
            recent = recent.take(RECENT_LIMIT),
            emptyFolders = emptyFolders,
            zeroByteFiles = zeroByte,
            emptyFolderCount = emptyFolderCount,
            zeroByteCount = zeroByteCount,
            covered = wanted.map { it.key }.toSet(),
            includedHidden = includeHidden,
            scannedAt = System.currentTimeMillis(),
        )
    }

    /** Removes the empty folders found by the last scan, deepest first. */
    suspend fun deleteEmptyFolders(paths: List<String>): Int =
        kotlinx.coroutines.withContext(Dispatchers.IO) {
            var removed = 0
            // Deepest first, so clearing a child can leave its parent empty and
            // that parent is still considered on this pass.
            paths.sortedByDescending { it.count { c -> c == File.separatorChar } }.forEach { path ->
                val dir = File(path)
                if (dir.isDirectory && (dir.listFiles()?.isEmpty() != false)) {
                    if (dir.delete()) removed++
                }
            }
            removed
        }

    private fun persist(s: Snapshot) {
        runCatching {
            val o = JSONObject()
                .put("scannedAt", s.scannedAt)
                .put("covered", JSONArray(s.covered.toList()))
                .put("includedHidden", s.includedHidden)
                .put("emptyFolderCount", s.emptyFolderCount)
                .put("zeroByteCount", s.zeroByteCount)
                .put("biggest", filesToJson(s.biggestFiles))
                .put("recent", filesToJson(s.recent))
                .put("zeroByte", filesToJson(s.zeroByteFiles))
                .put("emptyFolders", JSONArray(s.emptyFolders))
                .put(
                    "folders",
                    JSONArray().apply {
                        s.largestFolders.forEach {
                            put(
                                JSONObject()
                                    .put("path", it.path)
                                    .put("name", it.name)
                                    .put("bytes", it.bytes)
                                    .put("files", it.files)
                            )
                        }
                    },
                )
                .put(
                    "months",
                    JSONArray().apply {
                        s.months.forEach {
                            put(
                                JSONObject()
                                    .put("key", it.key)
                                    .put("label", it.label)
                                    .put("bytes", it.bytes)
                                    .put("count", it.count)
                            )
                        }
                    },
                )
            sp.edit().putString("snapshot", o.toString()).apply()
        }
    }

    private fun filesToJson(list: List<FileEntry>): JSONArray = JSONArray().apply {
        list.forEach {
            put(
                JSONObject()
                    .put("path", it.path)
                    .put("name", it.name)
                    .put("size", it.size)
                    .put("modified", it.modified)
            )
        }
    }

    private fun filesFromJson(array: JSONArray?): List<FileEntry> = buildList {
        if (array == null) return@buildList
        for (i in 0 until array.length()) {
            val o = array.getJSONObject(i)
            add(
                FileEntry(
                    path = o.getString("path"),
                    name = o.getString("name"),
                    size = o.optLong("size"),
                    modified = o.optLong("modified"),
                )
            )
        }
    }

    private fun read(json: String): Snapshot {
        val o = JSONObject(json)
        val coveredArray = o.optJSONArray("covered")
        val emptyArray = o.optJSONArray("emptyFolders")
        val foldersArray = o.optJSONArray("folders")
        val monthsArray = o.optJSONArray("months")
        return Snapshot(
            biggestFiles = filesFromJson(o.optJSONArray("biggest")),
            recent = filesFromJson(o.optJSONArray("recent")),
            zeroByteFiles = filesFromJson(o.optJSONArray("zeroByte")),
            emptyFolders = buildList {
                if (emptyArray != null) for (i in 0 until emptyArray.length()) add(emptyArray.getString(i))
            },
            largestFolders = buildList {
                if (foldersArray != null) for (i in 0 until foldersArray.length()) {
                    val f = foldersArray.getJSONObject(i)
                    add(
                        FolderEntry(
                            path = f.getString("path"),
                            name = f.getString("name"),
                            bytes = f.optLong("bytes"),
                            files = f.optInt("files"),
                        )
                    )
                }
            },
            months = buildList {
                if (monthsArray != null) for (i in 0 until monthsArray.length()) {
                    val m = monthsArray.getJSONObject(i)
                    add(
                        MonthBucket(
                            key = m.getString("key"),
                            label = m.optString("label"),
                            bytes = m.optLong("bytes"),
                            count = m.optInt("count"),
                        )
                    )
                }
            },
            includedHidden = o.optBoolean("includedHidden"),
            emptyFolderCount = o.optInt("emptyFolderCount"),
            zeroByteCount = o.optInt("zeroByteCount"),
            covered = buildList {
                if (coveredArray != null) for (i in 0 until coveredArray.length()) add(coveredArray.getString(i))
            }.toSet(),
            scannedAt = o.optLong("scannedAt"),
        )
    }
}

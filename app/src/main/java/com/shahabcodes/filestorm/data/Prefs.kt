package com.shahabcodes.filestorm.data

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

enum class ViewMode(val label: String) {
    LIST("List"),
    DETAILED("Detailed"),
    GRID("Grid"),
    GALLERY("Gallery"),
    TIMELINE("Timeline"),
}

enum class SortField(val label: String, val defaultAscending: Boolean) {
    NAME("Name", true),
    DATE("Date", false),
    SIZE("Size", false),
    TYPE("Type", true),
}

enum class Appearance(val label: String) {
    SYSTEM("Automatic"),
    LIGHT("Light"),
    DARK("Dark"),
}

enum class Accent(val label: String, val light: Long, val dark: Long) {
    BLUE("Ocean Blue", 0xFF007AFF, 0xFF0A84FF),
    PURPLE("Violet", 0xFFAF52DE, 0xFFBF5AF2),
    PINK("Rose", 0xFFFF2D55, 0xFFFF375F),
    RED("Crimson", 0xFFFF3B30, 0xFFFF453A),
    ORANGE("Sunset", 0xFFFF9500, 0xFFFF9F0A),
    GREEN("Mint", 0xFF34C759, 0xFF30D158),
    TEAL("Lagoon", 0xFF30B0C7, 0xFF40C8E0),
    INDIGO("Twilight", 0xFF5856D6, 0xFF5E5CE6),
}

/** App settings, Compose-observable and persisted. */
object Prefs {
    private lateinit var sp: SharedPreferences

    var showHidden by mutableStateOf(false)
        private set
    var biometricLock by mutableStateOf(false)
        private set
    var appearance by mutableStateOf(Appearance.SYSTEM)
        private set
    var accent by mutableStateOf(Accent.BLUE)
        private set
    var viewMode by mutableStateOf(ViewMode.LIST)
        private set
    var sortField by mutableStateOf(SortField.NAME)
        private set
    var sortAscending by mutableStateOf(true)
        private set
    var appIcon by mutableStateOf("opt25")
        private set
    var appName by mutableStateOf("storm")
        private set

    fun init(context: Context) {
        sp = context.getSharedPreferences("filestorm", Context.MODE_PRIVATE)
        showHidden = sp.getBoolean("show_hidden", false)
        biometricLock = sp.getBoolean("biometric_lock", false)
        viewMode = runCatching {
            ViewMode.valueOf(sp.getString("view_mode", ViewMode.LIST.name)!!)
        }.getOrDefault(ViewMode.LIST)
        sortField = runCatching {
            SortField.valueOf(sp.getString("sort_field", SortField.NAME.name)!!)
        }.getOrDefault(SortField.NAME)
        sortAscending = sp.getBoolean("sort_ascending", true)
        appIcon = sp.getString("app_icon", "opt25") ?: "opt25"
        appName = sp.getString("app_name", "storm") ?: "storm"
        appearance = runCatching {
            Appearance.valueOf(sp.getString("appearance", Appearance.SYSTEM.name)!!)
        }.getOrDefault(Appearance.SYSTEM)
        accent = runCatching {
            Accent.valueOf(sp.getString("accent", Accent.BLUE.name)!!)
        }.getOrDefault(Accent.BLUE)
    }

    fun updateShowHidden(value: Boolean) {
        showHidden = value
        sp.edit().putBoolean("show_hidden", value).apply()
    }

    fun updateBiometricLock(value: Boolean) {
        biometricLock = value
        sp.edit().putBoolean("biometric_lock", value).apply()
    }

    fun updateAppearance(value: Appearance) {
        appearance = value
        sp.edit().putString("appearance", value.name).apply()
    }

    fun updateAccent(value: Accent) {
        accent = value
        sp.edit().putString("accent", value.name).apply()
    }

    fun updateViewMode(value: ViewMode) {
        viewMode = value
        sp.edit().putString("view_mode", value.name).apply()
    }

    fun updateAppIcon(key: String) {
        appIcon = key
        sp.edit().putString("app_icon", key).apply()
    }

    fun updateAppName(key: String) {
        appName = key
        sp.edit().putString("app_name", key).apply()
    }

    fun updateSort(field: SortField, ascending: Boolean) {
        sortField = field
        sortAscending = ascending
        sp.edit().putString("sort_field", field.name).putBoolean("sort_ascending", ascending).apply()
    }
}

/**
 * Per-folder view mode. Changing the view inside a folder only affects that
 * folder; anything never customised falls back to [Prefs.viewMode] as the
 * app-wide default.
 */
object FolderViews {
    private lateinit var sp: SharedPreferences
    private val modes = androidx.compose.runtime.mutableStateMapOf<String, ViewMode>()
    private val columns = androidx.compose.runtime.mutableStateMapOf<String, Int>()

    fun init(context: Context) {
        sp = context.getSharedPreferences("filestorm_folder_views", Context.MODE_PRIVATE)
        sp.all.forEach { (key, value) ->
            when {
                key.startsWith("cols:") && value is Int -> columns[key.removePrefix("cols:")] = value
                value is String -> runCatching { modes[key] = ViewMode.valueOf(value) }
            }
        }
    }

    fun viewFor(key: String): ViewMode = modes[key] ?: Prefs.viewMode

    fun hasOverride(key: String): Boolean = modes.containsKey(key)

    fun setView(key: String, mode: ViewMode) {
        modes[key] = mode
        sp.edit().putString(key, mode.name).apply()
    }

    /** Tile columns for grid-like views, adjusted by pinching. */
    fun columnsFor(key: String, mode: ViewMode): Int {
        val stored = columns["$key|${mode.name}"]
        return stored ?: when (mode) {
            ViewMode.GALLERY -> 2
            else -> 3
        }
    }

    fun setColumns(key: String, mode: ViewMode, value: Int) {
        val clamped = value.coerceIn(1, if (mode == ViewMode.GALLERY) 4 else 6)
        columns["$key|${mode.name}"] = clamped
        sp.edit().putInt("cols:$key|${mode.name}", clamped).apply()
    }

    /** Drops this folder's override so it follows the app default again. */
    fun clear(key: String) {
        modes.remove(key)
        sp.edit().remove(key).apply()
    }
}

/** Per-folder visual customisation: icon colour and bold name. Keyed by absolute path. */
object FolderStyles {
    private lateinit var sp: SharedPreferences
    private val colors = androidx.compose.runtime.mutableStateMapOf<String, Int>()
    private val boldPaths = androidx.compose.runtime.mutableStateMapOf<String, Boolean>()
    private val icons = androidx.compose.runtime.mutableStateMapOf<String, String>()

    fun init(context: Context) {
        sp = context.getSharedPreferences("filestorm_folder_styles", Context.MODE_PRIVATE)
        sp.all.forEach { (key, value) ->
            when {
                key.startsWith("color:") && value is Int -> colors[key.removePrefix("color:")] = value
                key.startsWith("bold:") && value == true -> boldPaths[key.removePrefix("bold:")] = true
                key.startsWith("icon:") && value is String -> icons[key.removePrefix("icon:")] = value
            }
        }
    }

    /** Key from [FolderIcons], or null for the default folder glyph. */
    fun iconOf(path: String): String? = icons[path]

    fun setIcon(path: String, key: String?) {
        if (key == null) {
            icons.remove(path)
            sp.edit().remove("icon:$path").apply()
        } else {
            icons[path] = key
            sp.edit().putString("icon:$path", key).apply()
        }
    }

    fun colorOf(path: String): Int? = colors[path]

    fun isBold(path: String): Boolean = boldPaths[path] == true

    fun setColor(path: String, argb: Int?) {
        if (argb == null) {
            colors.remove(path)
            sp.edit().remove("color:$path").apply()
        } else {
            colors[path] = argb
            sp.edit().putInt("color:$path", argb).apply()
        }
    }

    fun setBold(path: String, bold: Boolean) {
        if (bold) {
            boldPaths[path] = true
            sp.edit().putBoolean("bold:$path", true).apply()
        } else {
            boldPaths.remove(path)
            sp.edit().remove("bold:$path").apply()
        }
    }
}

/** Favorite folders shown on the home screen, in the order they were added. */
object Favorites {
    private lateinit var sp: SharedPreferences
    var paths by mutableStateOf<List<String>>(emptyList())
        private set

    fun init(context: Context) {
        sp = context.getSharedPreferences("filestorm_favorites", Context.MODE_PRIVATE)
        paths = runCatching {
            val array = org.json.JSONArray(sp.getString("paths", "[]")!!)
            buildList { for (i in 0 until array.length()) add(array.getString(i)) }
        }.getOrDefault(emptyList())
    }

    fun isFavorite(path: String): Boolean = path in paths

    fun toggle(path: String) {
        paths = if (path in paths) paths - path else paths + path
        sp.edit().putString("paths", org.json.JSONArray(paths).toString()).apply()
    }
}

/**
 * Per-folder biometric locks. A locked folder needs one successful unlock per
 * foreground session; leaving the app clears the session unlocks.
 */
object FolderLocks {
    private lateinit var sp: SharedPreferences
    private val locked = androidx.compose.runtime.mutableStateMapOf<String, Boolean>()
    private val sessionUnlocked = mutableSetOf<String>()

    fun init(context: Context) {
        sp = context.getSharedPreferences("filestorm_folder_locks", Context.MODE_PRIVATE)
        sp.all.forEach { (key, value) -> if (value == true) locked[key] = true }
    }

    fun isLocked(path: String): Boolean = locked[path] == true

    /** True when opening this folder must first pass biometric auth. */
    fun requiresAuth(path: String): Boolean = isLocked(path) && path !in sessionUnlocked

    fun markUnlocked(path: String) {
        sessionUnlocked.add(path)
    }

    fun setLocked(path: String, value: Boolean) {
        if (value) {
            locked[path] = true
            sp.edit().putBoolean(path, true).apply()
        } else {
            locked.remove(path)
            sessionUnlocked.remove(path)
            sp.edit().remove(path).apply()
        }
    }

    /** Called when the app leaves the foreground: everything re-locks. */
    fun clearSession() {
        sessionUnlocked.clear()
    }
}

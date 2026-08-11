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
    MOSAIC("Mosaic"),
    REEL("Reel"),
    TIMELINE("Timeline"),
}

enum class SortField(val label: String, val defaultAscending: Boolean) {
    NAME("Name", true),
    DATE("Date", false),
    SIZE("Size", false),
    TYPE("Type", true),
}

/**
 * [swatchStart]/[swatchEnd] are 0 for the plain modes and a gradient for the
 * decorated ones, so the settings list can show what a theme looks like
 * instead of only naming it.
 */
/**
 * How bright the app is. Independent of [ThemePalette]: every palette has a
 * light and a dark form, so there is no "Blossom Night" to pick separately —
 * choosing Blossom and Dark gets you exactly that.
 */
enum class Appearance(
    val label: String,
    val blurb: String,
    val swatchStart: Long,
    val swatchEnd: Long,
) {
    SYSTEM("Automatic", "Follows the system light/dark setting", 0xFFFFFFFF, 0xFF1C1C1E),
    LIGHT("Light", "Bright surfaces all day", 0xFFFFFFFF, 0xFFDDDDE2),
    DARK("Dark", "Deep surfaces, easy at night", 0xFF48484A, 0xFF000000),
    ;

    fun isDark(systemDark: Boolean): Boolean = when (this) {
        SYSTEM -> systemDark
        LIGHT -> false
        DARK -> true
    }
}

/** Which colour family the app wears. Each one has a light and a dark form. */
enum class ThemePalette(
    val label: String,
    val blurb: String,
    val swatchStart: Long,
    val swatchEnd: Long,
) {
    DEFAULT("Classic", "The palette File Storm ships with", 0xFF007AFF, 0xFF34C759),
    BLOSSOM("Blossom", "Frosted rose and violet, drawn from the Glass icon", 0xFF7A5AF0, 0xFFDB3E90),
    SAKURA("Sakura", "Cherry petals on cream, with sage and apricot", 0xFFEE6F9C, 0xFFE8A860),
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
    BLOSSOM("Blossom", 0xFFF06CA8, 0xFFFF8FC4),
    GOLD("Honey", 0xFFC79000, 0xFFEFB01A),
    GRAPHITE("Graphite", 0xFF5B6572, 0xFF98A2B3),
}

/** How the ranked dashboard cards draw their data. */
enum class ChartStyle(val label: String, val blurb: String) {
    BARS("Bars", "Ranked rows with a proportional bar"),
    DONUT("Donut", "A ring showing each share of the total"),
    TREEMAP("Treemap", "Rectangles sized by share — the classic disk-usage view"),
    LIST("Plain list", "Just names and sizes, no graphics"),
}

/** Which animation the app shows while it waits on the filesystem. */
enum class LoaderStyle(val label: String, val blurb: String) {
    ARC("Storm Arc", "Sweeping arc with a breathing core"),
    DOTS("Bounce", "Three dots bouncing in sequence"),
    PULSE("Ripple", "Rings pulsing outward"),
    BARS("Equaliser", "Bars rising and falling"),
    ORBIT("Orbit", "A dot circling its track"),
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
    var palette by mutableStateOf(ThemePalette.DEFAULT)
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
    var secureScreen by mutableStateOf(false)
        private set
    var diagnostics by mutableStateOf(false)
        private set
    var loaderStyle by mutableStateOf(LoaderStyle.ARC)
        private set
    var chartStyle by mutableStateOf(ChartStyle.BARS)
        private set

    // Reel: a full-screen vertical feed of the photos and videos in a folder.
    var reelEnabled by mutableStateOf(true)
        private set
    var reelAutoplay by mutableStateOf(true)
        private set
    var reelStartMuted by mutableStateOf(true)
        private set
    var reelLoop by mutableStateOf(true)
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
        secureScreen = sp.getBoolean("secure_screen", false)
        diagnostics = sp.getBoolean("diagnostics", false)
        chartStyle = runCatching {
            ChartStyle.valueOf(sp.getString("chart_style", ChartStyle.BARS.name)!!)
        }.getOrDefault(ChartStyle.BARS)
        loaderStyle = runCatching {
            LoaderStyle.valueOf(sp.getString("loader_style", LoaderStyle.ARC.name)!!)
        }.getOrDefault(LoaderStyle.ARC)
        // Brightness and palette used to be one setting, so older saves hold
        // names like BLOSSOM_NIGHT. Split them rather than silently resetting.
        val storedAppearance = sp.getString("appearance", Appearance.SYSTEM.name)!!
        val legacy = legacyAppearance(storedAppearance)
        appearance = legacy?.first ?: runCatching {
            Appearance.valueOf(storedAppearance)
        }.getOrDefault(Appearance.SYSTEM)
        palette = legacy?.second ?: runCatching {
            ThemePalette.valueOf(sp.getString("palette", ThemePalette.DEFAULT.name)!!)
        }.getOrDefault(ThemePalette.DEFAULT)
        if (legacy != null) {
            sp.edit()
                .putString("appearance", appearance.name)
                .putString("palette", palette.name)
                .apply()
        }
        reelEnabled = sp.getBoolean("reel_enabled", true)
        reelAutoplay = sp.getBoolean("reel_autoplay", true)
        reelStartMuted = sp.getBoolean("reel_start_muted", true)
        reelLoop = sp.getBoolean("reel_loop", true)
        accent = runCatching {
            Accent.valueOf(sp.getString("accent", Accent.BLUE.name)!!)
        }.getOrDefault(Accent.BLUE)
    }

    fun updateReelEnabled(value: Boolean) {
        reelEnabled = value
        sp.edit().putBoolean("reel_enabled", value).apply()
        // Leaving the mode selected while hiding its switch would strand the
        // folder in a view with no way back to a list.
        if (!value && viewMode == ViewMode.REEL) updateViewMode(ViewMode.GALLERY)
    }

    fun updateReelAutoplay(value: Boolean) {
        reelAutoplay = value
        sp.edit().putBoolean("reel_autoplay", value).apply()
    }

    fun updateReelStartMuted(value: Boolean) {
        reelStartMuted = value
        sp.edit().putBoolean("reel_start_muted", value).apply()
    }

    fun updateReelLoop(value: Boolean) {
        reelLoop = value
        sp.edit().putBoolean("reel_loop", value).apply()
    }

    fun updateShowHidden(value: Boolean) {
        showHidden = value
        sp.edit().putBoolean("show_hidden", value).apply()
    }

    fun updateBiometricLock(value: Boolean) {
        biometricLock = value
        sp.edit().putBoolean("biometric_lock", value).apply()
    }

    private fun legacyAppearance(stored: String): Pair<Appearance, ThemePalette>? = when (stored) {
        "BLOSSOM" -> Appearance.LIGHT to ThemePalette.BLOSSOM
        "BLOSSOM_NIGHT" -> Appearance.DARK to ThemePalette.BLOSSOM
        "SAKURA" -> Appearance.LIGHT to ThemePalette.SAKURA
        "SAKURA_NIGHT" -> Appearance.DARK to ThemePalette.SAKURA
        else -> null
    }

    fun updatePalette(value: ThemePalette) {
        palette = value
        sp.edit().putString("palette", value.name).apply()
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

    fun updateChartStyle(value: ChartStyle) {
        chartStyle = value
        sp.edit().putString("chart_style", value.name).apply()
    }

    fun updateLoaderStyle(value: LoaderStyle) {
        loaderStyle = value
        sp.edit().putString("loader_style", value.name).apply()
    }

    fun updateDiagnostics(value: Boolean) {
        diagnostics = value
        sp.edit().putBoolean("diagnostics", value).apply()
    }

    fun updateSecureScreen(value: Boolean) {
        secureScreen = value
        sp.edit().putBoolean("secure_screen", value).apply()
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
    private val grouped = androidx.compose.runtime.mutableStateMapOf<String, Boolean>()

    fun init(context: Context) {
        sp = context.getSharedPreferences("filestorm_folder_views", Context.MODE_PRIVATE)
        sp.all.forEach { (key, value) ->
            when {
                key.startsWith("cols:") && value is Int -> columns[key.removePrefix("cols:")] = value
                key.startsWith("group:") && value is Boolean -> grouped[key.removePrefix("group:")] = value
                value is String -> runCatching { modes[key] = ViewMode.valueOf(value) }
            }
        }
    }

    /** TIMELINE is kept only for older saves; it now means "list, grouped". */
    fun viewFor(key: String): ViewMode {
        val stored = modes[key] ?: Prefs.viewMode
        return if (stored == ViewMode.TIMELINE) ViewMode.LIST else stored
    }

    /** Month grouping is independent of the layout, so it works in every view. */
    fun groupedFor(key: String): Boolean =
        grouped[key] ?: ((modes[key] ?: Prefs.viewMode) == ViewMode.TIMELINE)

    fun setGrouped(key: String, value: Boolean) {
        grouped[key] = value
        sp.edit().putBoolean("group:$key", value).apply()
        // Drop the legacy timeline mode so the layout choice is honoured.
        if (modes[key] == ViewMode.TIMELINE) setView(key, ViewMode.LIST)
    }

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
            ViewMode.MOSAIC -> 2
            else -> 3
        }
    }

    fun setColumns(key: String, mode: ViewMode, value: Int) {
        val maxColumns = if (mode == ViewMode.GALLERY || mode == ViewMode.MOSAIC) 4 else 6
        val clamped = value.coerceIn(1, maxColumns)
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

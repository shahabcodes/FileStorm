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

    fun updateSort(field: SortField, ascending: Boolean) {
        sortField = field
        sortAscending = ascending
        sp.edit().putString("sort_field", field.name).putBoolean("sort_ascending", ascending).apply()
    }
}

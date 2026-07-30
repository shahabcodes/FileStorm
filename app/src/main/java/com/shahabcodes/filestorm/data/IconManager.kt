package com.shahabcodes.filestorm.data

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import com.shahabcodes.filestorm.R

/** Switchable launcher icons backed by manifest activity-aliases. */
object IconManager {

    data class AppIcon(val key: String, val label: String, val alias: String, val mipmap: Int)

    val icons = listOf(
        AppIcon("default", "Ocean Bolt", ".IconDefault", R.mipmap.ic_launcher),
        AppIcon("opt13", "Classic Max", ".Icon13", R.mipmap.ic_launcher_opt13),
        AppIcon("opt15", "Violet Velvet", ".Icon15", R.mipmap.ic_launcher_opt15),
        AppIcon("opt16", "Split Strike", ".Icon16", R.mipmap.ic_launcher_opt16),
        AppIcon("opt17", "Neon Rim", ".Icon17", R.mipmap.ic_launcher_opt17),
        AppIcon("opt18", "Golden Folder", ".Icon18", R.mipmap.ic_launcher_opt18),
        AppIcon("opt19", "Aurora", ".Icon19", R.mipmap.ic_launcher_opt19),
        AppIcon("opt21", "Twin Stack", ".Icon21", R.mipmap.ic_launcher_opt21),
        AppIcon("opt22", "Carbon", ".Icon22", R.mipmap.ic_launcher_opt22),
        AppIcon("opt24", "Bold Outline", ".Icon24", R.mipmap.ic_launcher_opt24),
        AppIcon("opt25", "Glass", ".Icon25", R.mipmap.ic_launcher_opt25),
        AppIcon("opt27", "Open Folder", ".Icon27", R.mipmap.ic_launcher_opt27),
        AppIcon("opt28", "Pixel Storm", ".Icon28", R.mipmap.ic_launcher_opt28),
        AppIcon("opt29", "Origami", ".Icon29", R.mipmap.ic_launcher_opt29),
    )

    fun current(): AppIcon = icons.firstOrNull { it.key == Prefs.appIcon } ?: icons.first()

    /**
     * Enables the chosen alias and disables the rest. The new one is enabled
     * FIRST so a launcher entry always exists. The launcher may take a moment
     * (or a home-screen revisit) to show the new icon.
     */
    fun apply(context: Context, icon: AppIcon) {
        val pm = context.packageManager
        val pkg = context.packageName
        pm.setComponentEnabledSetting(
            ComponentName(pkg, pkg + icon.alias),
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
            PackageManager.DONT_KILL_APP,
        )
        icons.filter { it.key != icon.key }.forEach { other ->
            pm.setComponentEnabledSetting(
                ComponentName(pkg, pkg + other.alias),
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP,
            )
        }
        Prefs.updateAppIcon(icon.key)
    }
}

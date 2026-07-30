package com.shahabcodes.filestorm.data

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import com.shahabcodes.filestorm.R

/** Switchable launcher icon + name, backed by manifest activity-aliases. */
object IconManager {

    data class AppIcon(val key: String, val label: String, val mipmap: Int)
    data class AppName(val key: String, val label: String)

    val icons = listOf(
        AppIcon("opt25", "Glass", R.mipmap.ic_launcher_opt25),
        AppIcon("default", "Ocean Bolt", R.mipmap.ic_launcher),
        AppIcon("opt13", "Classic Max", R.mipmap.ic_launcher_opt13),
        AppIcon("opt15", "Violet Velvet", R.mipmap.ic_launcher_opt15),
        AppIcon("opt16", "Split Strike", R.mipmap.ic_launcher_opt16),
        AppIcon("opt17", "Neon Rim", R.mipmap.ic_launcher_opt17),
        AppIcon("opt18", "Golden Folder", R.mipmap.ic_launcher_opt18),
        AppIcon("opt19", "Aurora", R.mipmap.ic_launcher_opt19),
        AppIcon("opt21", "Twin Stack", R.mipmap.ic_launcher_opt21),
        AppIcon("opt22", "Carbon", R.mipmap.ic_launcher_opt22),
        AppIcon("opt24", "Bold Outline", R.mipmap.ic_launcher_opt24),
        AppIcon("opt27", "Open Folder", R.mipmap.ic_launcher_opt27),
        AppIcon("opt28", "Pixel Storm", R.mipmap.ic_launcher_opt28),
        AppIcon("opt29", "Origami", R.mipmap.ic_launcher_opt29),
        AppIcon("opt30", "Nothing", R.mipmap.ic_launcher_opt30),
    )

    val names = listOf(
        AppName("storm", "File Storm"),
        AppName("files", "Files"),
        AppName("myfiles", "My Files"),
        AppName("storage", "Storage"),
        AppName("explorer", "Explorer"),
        AppName("vault", "Vault"),
    )

    /** Must match the single alias marked enabled="true" in the manifest. */
    private const val MANIFEST_DEFAULT_ICON = "opt25"
    private const val MANIFEST_DEFAULT_NAME = "storm"

    private fun aliasFor(iconKey: String, nameKey: String) = ".L_${iconKey}_${nameKey}"

    private fun component(context: Context, alias: String) =
        ComponentName(context.packageName, context.packageName + alias)

    /** Applies an (icon, name) combo: enable the new alias first, then retire the old. */
    fun apply(
        context: Context,
        iconKey: String = Prefs.appIcon,
        nameKey: String = Prefs.appName,
    ) {
        val pm = context.packageManager
        val previous = aliasFor(Prefs.appIcon, Prefs.appName)
        val next = aliasFor(iconKey, nameKey)
        pm.setComponentEnabledSetting(
            component(context, next),
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
            PackageManager.DONT_KILL_APP,
        )
        if (previous != next) {
            pm.setComponentEnabledSetting(
                component(context, previous),
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP,
            )
        }
        // The manifest-default alias must never linger alongside a custom one.
        val manifestDefault = aliasFor(MANIFEST_DEFAULT_ICON, MANIFEST_DEFAULT_NAME)
        if (next != manifestDefault && previous != manifestDefault) {
            pm.setComponentEnabledSetting(
                component(context, manifestDefault),
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP,
            )
        }
        Prefs.updateAppIcon(iconKey)
        Prefs.updateAppName(nameKey)
    }

    /**
     * Called at startup: after app updates change the alias set, re-enable the
     * combo the user chose (no-op when already correct).
     */
    fun reconcile(context: Context) {
        val pm = context.packageManager
        val desired = component(context, aliasFor(Prefs.appIcon, Prefs.appName))
        val state = pm.getComponentEnabledSetting(desired)
        val enabled = state == PackageManager.COMPONENT_ENABLED_STATE_ENABLED ||
            (state == PackageManager.COMPONENT_ENABLED_STATE_DEFAULT &&
                Prefs.appIcon == MANIFEST_DEFAULT_ICON && Prefs.appName == MANIFEST_DEFAULT_NAME)
        if (!enabled) apply(context)
    }
}

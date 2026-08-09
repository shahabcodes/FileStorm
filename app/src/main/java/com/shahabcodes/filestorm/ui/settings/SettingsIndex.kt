package com.shahabcodes.filestorm.ui.settings

/**
 * One searchable setting.
 *
 * [keywords] carries the words people actually type — "dark mode", "password",
 * "spinner" — rather than only the label the app happens to use. Searching for
 * the thing you want should not require knowing what it is called here.
 */
data class SettingsEntry(
    val title: String,
    val page: SettingsPageId,
    val pageTitle: String,
    val section: String,
    val keywords: String = "",
)

/**
 * Everything settings can do, in one flat list.
 *
 * This is what stops a hub from ever making something harder to find than the
 * single long page it replaced: whatever you are after is one search away
 * regardless of which page it ended up on.
 *
 * The hidden diagnostics tools are deliberately absent. Indexing them would
 * undo the point of keeping them behind the version tap.
 */
object SettingsIndex {

    val entries: List<SettingsEntry> = listOf(
        // ── Appearance ─────────────────────────────────────────────────
        SettingsEntry(
            "Light and dark mode", SettingsPageId.APPEARANCE, "Appearance", "Appearance",
            "dark mode night light automatic system bright",
        ),
        SettingsEntry(
            "Theme", SettingsPageId.APPEARANCE, "Appearance", "Theme",
            "classic blossom sakura pink colour color palette girly",
        ),
        SettingsEntry(
            "Accent colour", SettingsPageId.APPEARANCE, "Appearance", "Accent Colour",
            "accent colour color blue rose highlight tint",
        ),
        SettingsEntry(
            "Loading indicator", SettingsPageId.APPEARANCE, "Appearance", "Loading Indicator",
            "spinner loader loading animation progress arc bounce ripple orbit",
        ),

        // ── Dashboard ──────────────────────────────────────────────────
        SettingsEntry(
            "Which dashboard cards show", SettingsPageId.DASHBOARD, "Dashboard", "Dashboard",
            "home cards storage reclaim biggest files largest folders monthly footprint " +
                "recent apps hide show",
        ),
        SettingsEntry(
            "Dashboard card order", SettingsPageId.DASHBOARD, "Dashboard", "Dashboard",
            "order arrange rearrange move up down first top",
        ),
        SettingsEntry(
            "Chart style", SettingsPageId.DASHBOARD, "Dashboard", "Chart Style",
            "chart graph bars donut treemap list pie visual",
        ),

        // ── Files ──────────────────────────────────────────────────────
        SettingsEntry(
            "Show hidden files", SettingsPageId.FILES, "Files & Folders", "Files",
            "hidden dot files folders invisible show",
        ),

        // ── Privacy & Security ─────────────────────────────────────────
        SettingsEntry(
            "App lock", SettingsPageId.PRIVACY, "Privacy & Security", "Security",
            "biometric fingerprint face lock password unlock security protect",
        ),
        SettingsEntry(
            "Hide the app in recents", SettingsPageId.PRIVACY, "Privacy & Security", "Privacy",
            "recents blur screenshot privacy preview task switcher secure",
        ),

        // ── Vault ──────────────────────────────────────────────────────
        SettingsEntry(
            "Verify after encrypting", SettingsPageId.VAULT, "Vault", "Vault",
            "verify check integrity safety encrypt speed faster",
        ),
        SettingsEntry(
            "Encrypt hidden files", SettingsPageId.VAULT, "Vault", "Vault",
            "hidden dot files vault encrypt include",
        ),
        SettingsEntry(
            "Originals to Trash", SettingsPageId.VAULT, "Vault", "Vault",
            "trash delete original recover space free encrypt",
        ),
        SettingsEntry(
            "Files encrypted at once", SettingsPageId.VAULT, "Vault", "Vault Speed",
            "speed faster concurrency parallel workers threads slow",
        ),
        SettingsEntry(
            "Vault auto-lock", SettingsPageId.VAULT, "Vault", "Vault Locking",
            "auto lock timeout close relock leave app minutes",
        ),
        SettingsEntry(
            "Vault key strength", SettingsPageId.VAULT, "Vault", "Vault Key Strength",
            "key strength iterations password security standard high maximum slow",
        ),
        SettingsEntry(
            "Vault log", SettingsPageId.VAULT, "Vault", "Vault Log",
            "log logging trace debug diagnostics share problem error",
        ),
        SettingsEntry(
            "Filenames in the vault log", SettingsPageId.VAULT, "Vault", "Vault Log",
            "log filenames names privacy redact",
        ),

        // ── App icon and name ──────────────────────────────────────────
        SettingsEntry(
            "App icon", SettingsPageId.IDENTITY, "App Icon & Name", "App Icon",
            "icon launcher glass nothing monochrome home screen change",
        ),
        SettingsEntry(
            "App name", SettingsPageId.IDENTITY, "App Icon & Name", "App Name",
            "name rename label launcher disguise vault files",
        ),

        // ── About ──────────────────────────────────────────────────────
        SettingsEntry(
            "Version", SettingsPageId.ABOUT, "About", "About",
            "version build about developer credit",
        ),
    )

    /**
     * Ranks a title match above a keyword match, so typing "lock" leads with
     * App lock rather than whichever entry merely mentions locking.
     */
    fun search(query: String): List<SettingsEntry> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return emptyList()
        val terms = q.split(" ").filter { it.isNotBlank() }

        return entries
            .mapNotNull { entry ->
                val title = entry.title.lowercase()
                val haystack = (entry.title + " " + entry.section + " " + entry.pageTitle +
                    " " + entry.keywords).lowercase()
                // Every term has to appear somewhere, so a two-word search
                // narrows rather than widening.
                if (!terms.all { haystack.contains(it) }) return@mapNotNull null
                val score = when {
                    title == q -> 0
                    title.startsWith(q) -> 1
                    title.contains(q) -> 2
                    entry.section.lowercase().contains(q) -> 3
                    else -> 4
                }
                score to entry
            }
            .sortedWith(compareBy({ it.first }, { it.second.title }))
            .map { it.second }
    }
}

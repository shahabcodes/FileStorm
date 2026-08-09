package com.shahabcodes.filestorm.ui.settings

import com.shahabcodes.filestorm.ui.components.SearchField
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Dashboard
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.shahabcodes.filestorm.BuildConfig
import com.shahabcodes.filestorm.data.Appearance
import com.shahabcodes.filestorm.data.DashboardPrefs
import com.shahabcodes.filestorm.data.IconManager
import com.shahabcodes.filestorm.data.Prefs
import com.shahabcodes.filestorm.data.vault.VaultPrefs
import com.shahabcodes.filestorm.ui.components.GroupedCard
import com.shahabcodes.filestorm.ui.components.RowSeparator
import com.shahabcodes.filestorm.ui.components.pressScale
import com.shahabcodes.filestorm.ui.theme.fsColors

/** The seven places settings can live. */
enum class SettingsPageId(val route: String) {
    APPEARANCE("appearance"),
    DASHBOARD("dashboard"),
    FILES("files"),
    PRIVACY("privacy"),
    VAULT("vault"),
    IDENTITY("identity"),
    ABOUT("about"),
}

/**
 * The settings hub.
 *
 * Every row says what it is currently set to, which is the point of a hub
 * rather than a menu: most questions get answered here without opening
 * anything. Ordered by how often each is actually touched.
 */
@Composable
fun SettingsScreen(onBack: () -> Unit, onOpen: (SettingsPageId) -> Unit) {
    var query by remember { mutableStateOf("") }
    val results = remember(query) { SettingsIndex.search(query) }
    val searching = query.isNotBlank()

    Column(
        Modifier
            .fillMaxSize()
            .background(fsColors.groupedBackground)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState()),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.pressScale(onBack).padding(horizontal = 6.dp, vertical = 6.dp),
            ) {
                Icon(
                    Icons.AutoMirrored.Rounded.KeyboardArrowLeft, null,
                    tint = fsColors.accent, modifier = Modifier.size(18.dp),
                )
                Text("Back", color = fsColors.accent, style = MaterialTheme.typography.bodyLarge)
            }
        }
        Text(
            "Settings",
            style = MaterialTheme.typography.headlineLarge,
            color = fsColors.label,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        Spacer(Modifier.height(14.dp))

        SearchField(
            value = query,
            onValueChange = { query = it },
            placeholder = "Search settings",
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        Spacer(Modifier.height(16.dp))

        if (searching) {
            SearchResults(results, onOpen)
            Spacer(Modifier.height(40.dp))
            return@Column
        }

        GroupedCard(Modifier.padding(horizontal = 16.dp)) {
            HubRow(
                Icons.Rounded.Palette, fsColors.kinds.video,
                "Appearance", appearanceSummary(),
            ) { onOpen(SettingsPageId.APPEARANCE) }
            RowSeparator(startIndent = 60.dp)
            HubRow(
                Icons.Rounded.Dashboard, fsColors.accent,
                "Dashboard", dashboardSummary(),
            ) { onOpen(SettingsPageId.DASHBOARD) }
            RowSeparator(startIndent = 60.dp)
            HubRow(
                Icons.Rounded.Folder, fsColors.kinds.folder,
                "Files & Folders", filesSummary(),
            ) { onOpen(SettingsPageId.FILES) }
        }
        Spacer(Modifier.height(18.dp))

        GroupedCard(Modifier.padding(horizontal = 16.dp)) {
            HubRow(
                Icons.Rounded.Shield, fsColors.green,
                "Privacy & Security", privacySummary(),
            ) { onOpen(SettingsPageId.PRIVACY) }
            RowSeparator(startIndent = 60.dp)
            HubRow(
                Icons.Rounded.Lock, fsColors.orange,
                "Vault", vaultSummary(),
            ) { onOpen(SettingsPageId.VAULT) }
        }
        Spacer(Modifier.height(18.dp))

        GroupedCard(Modifier.padding(horizontal = 16.dp)) {
            HubRow(
                Icons.Rounded.Widgets, fsColors.kinds.archive,
                "App Icon & Name", identitySummary(),
            ) { onOpen(SettingsPageId.IDENTITY) }
            RowSeparator(startIndent = 60.dp)
            HubRow(
                Icons.Rounded.Info, fsColors.secondaryLabel,
                "About", "Version ${BuildConfig.VERSION_NAME}",
            ) { onOpen(SettingsPageId.ABOUT) }
        }
        Spacer(Modifier.height(40.dp))
    }
}

/**
 * What a search turns up. Each result names the page it lives on, so tapping it
 * is predictable rather than a jump to somewhere unexplained.
 */
@Composable
private fun SearchResults(
    results: List<SettingsEntry>,
    onOpen: (SettingsPageId) -> Unit,
) {
    if (results.isEmpty()) {
        Text(
            "Nothing matches that.",
            style = MaterialTheme.typography.bodyMedium,
            color = fsColors.secondaryLabel,
            modifier = Modifier.padding(horizontal = 20.dp),
        )
        return
    }
    GroupedCard(Modifier.padding(horizontal = 16.dp)) {
        results.forEachIndexed { index, entry ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .pressScale { onOpen(entry.page) }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        entry.title,
                        style = MaterialTheme.typography.bodyLarge,
                        color = fsColors.label,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        entry.pageTitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = fsColors.accent,
                    )
                }
                Icon(
                    Icons.Rounded.ChevronRight, null,
                    tint = fsColors.secondaryLabel.copy(alpha = 0.55f),
                    modifier = Modifier.size(20.dp),
                )
            }
            if (index != results.lastIndex) RowSeparator(startIndent = 16.dp)
        }
    }
}

@Composable
private fun HubRow(
    icon: ImageVector,
    tint: Color,
    title: String,
    summary: String,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .pressScale(onClick)
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(30.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(tint.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(17.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = fsColors.label)
            Text(
                summary,
                style = MaterialTheme.typography.bodySmall,
                color = fsColors.secondaryLabel,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Icon(
            Icons.Rounded.ChevronRight, null,
            tint = fsColors.secondaryLabel.copy(alpha = 0.55f),
            modifier = Modifier.size(20.dp),
        )
    }
}

// ── Current-value summaries ────────────────────────────────────────────
// Read live, so a row always describes the state rather than a guess.

@Composable
private fun appearanceSummary(): String = listOf(
    Prefs.appearance.label,
    Prefs.palette.label,
    Prefs.accent.label,
).joinToString(" · ")

@Composable
private fun dashboardSummary(): String {
    val on = DashboardPrefs.enabled.size
    val all = com.shahabcodes.filestorm.data.DashboardCard.entries.size
    return "$on of $all cards · ${Prefs.chartStyle.label}"
}

@Composable
private fun filesSummary(): String = listOfNotNull(
    if (Prefs.showHidden) "Hidden files shown" else "Hidden files off",
    Prefs.viewMode.label,
    Prefs.loaderStyle.label,
).joinToString(" · ")

@Composable
private fun privacySummary(): String = when {
    Prefs.biometricLock && Prefs.secureScreen -> "App locked · recents hidden"
    Prefs.biometricLock -> "App locked"
    Prefs.secureScreen -> "Recents hidden"
    else -> "No lock set"
}

@Composable
private fun vaultSummary(): String = listOf(
    if (VaultPrefs.verifyAfterWrite) "Verify on" else "Verify off",
    "${VaultPrefs.resolvedWorkers()} at a time",
    VaultPrefs.strength.label,
).joinToString(" · ")

@Composable
private fun identitySummary(): String {
    val icon = IconManager.icons.firstOrNull { it.key == Prefs.appIcon }?.label ?: "Default"
    val name = IconManager.names.firstOrNull { it.key == Prefs.appName }?.label ?: "File Storm"
    return "$icon · $name"
}

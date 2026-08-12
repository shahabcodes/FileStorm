package com.shahabcodes.filestorm.ui.settings

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.DragIndicator
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.PlayCircleOutline
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.Slideshow
import androidx.compose.material.icons.rounded.SmartDisplay
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.VolumeOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.fragment.app.FragmentActivity
import com.shahabcodes.filestorm.BuildConfig
import com.shahabcodes.filestorm.data.Accent
import com.shahabcodes.filestorm.data.Appearance
import com.shahabcodes.filestorm.data.ChartStyle
import com.shahabcodes.filestorm.data.DashboardCard
import com.shahabcodes.filestorm.data.DashboardPrefs
import com.shahabcodes.filestorm.data.LoaderStyle
import com.shahabcodes.filestorm.data.Prefs
import com.shahabcodes.filestorm.data.ThemePalette
import com.shahabcodes.filestorm.data.vault.AutoLock
import com.shahabcodes.filestorm.data.vault.VaultCrypto
import com.shahabcodes.filestorm.data.vault.VaultLog
import com.shahabcodes.filestorm.data.vault.VaultLogLevel
import com.shahabcodes.filestorm.data.vault.VaultPrefs
import com.shahabcodes.filestorm.ui.Biometrics
import com.shahabcodes.filestorm.ui.components.FsSpinner
import com.shahabcodes.filestorm.ui.components.GroupedCard
import com.shahabcodes.filestorm.ui.components.RowSeparator
import com.shahabcodes.filestorm.ui.components.pressScale
import com.shahabcodes.filestorm.ui.theme.fsColors
import com.shahabcodes.filestorm.util.Formatters

/**
 * The individual settings pages.
 *
 * Settings used to be one page of eighteen sections, which meant scrolling past
 * a security choice to reach a colour. Each page here owns one subject; the hub
 * in SettingsScreen lists them and shows what each is currently set to.
 */
@Composable
private fun SettingsPage(
    title: String,
    onBack: () -> Unit,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .background(fsColors.groupedBackground)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState()),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
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
            title,
            style = MaterialTheme.typography.headlineLarge,
            color = fsColors.label,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        Spacer(Modifier.height(20.dp))
        content()
        Spacer(Modifier.height(40.dp))
    }
}

@Composable
fun AppearanceSettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val activity = context as? FragmentActivity
    SettingsPage("Appearance", onBack) {
        // ── Appearance ─────────────────────────────────────────────────
        SectionHeader("Appearance")
        GroupedCard(Modifier.padding(horizontal = 16.dp)) {
            Appearance.entries.forEachIndexed { i, mode ->
                ChoiceRow(
                    label = mode.label,
                    blurb = mode.blurb,
                    swatchStart = mode.swatchStart,
                    swatchEnd = mode.swatchEnd,
                    selected = Prefs.appearance == mode,
                ) { Prefs.updateAppearance(mode) }
                if (i != Appearance.entries.lastIndex) RowSeparator(startIndent = 54.dp)
            }
        }
        Spacer(Modifier.height(24.dp))

        // Palette is a separate axis from brightness: every one of these has a
        // light and a dark form, so there is no "Blossom Night" to pick.
        SectionHeader("Theme")
        GroupedCard(Modifier.padding(horizontal = 16.dp)) {
            ThemePalette.entries.forEachIndexed { i, theme ->
                ChoiceRow(
                    label = theme.label,
                    blurb = theme.blurb,
                    swatchStart = theme.swatchStart,
                    swatchEnd = theme.swatchEnd,
                    selected = Prefs.palette == theme,
                ) { Prefs.updatePalette(theme) }
                if (i != ThemePalette.entries.lastIndex) RowSeparator(startIndent = 54.dp)
            }
        }
        Spacer(Modifier.height(24.dp))

        // ── Accent color ───────────────────────────────────────────────
        SectionHeader("Accent Colour")
        GroupedCard(Modifier.padding(horizontal = 16.dp)) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Accent.entries.forEach { accent ->
                    val color = Color(if (fsColors.isDark) accent.dark else accent.light)
                    val selected = Prefs.accent == accent
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(color)
                                .pressScale { Prefs.updateAccent(accent) },
                            contentAlignment = Alignment.Center,
                        ) {
                            if (selected) {
                                Box(
                                    Modifier
                                        .size(38.dp)
                                        .clip(CircleShape)
                                        .background(Color.Transparent),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        Icons.Rounded.Check, null,
                                        tint = Color.White, modifier = Modifier.size(22.dp),
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(5.dp))
                        Text(
                            accent.label,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (selected) fsColors.accent else fsColors.secondaryLabel,
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(24.dp))

        SectionHeader("Loading Indicator")
        GroupedCard(Modifier.padding(horizontal = 16.dp)) {
            LoaderStyle.entries.forEachIndexed { i, style ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .pressScale { Prefs.updateLoaderStyle(style) }
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Every option animates live, so the choice is obvious.
                    Box(Modifier.size(38.dp), contentAlignment = Alignment.Center) {
                        FsSpinner(style = style, size = 34.dp, strokeWidth = 3.dp)
                    }
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            style.label,
                            style = MaterialTheme.typography.bodyLarge,
                            color = fsColors.label,
                        )
                        Text(
                            style.blurb,
                            style = MaterialTheme.typography.bodySmall,
                            color = fsColors.secondaryLabel,
                        )
                    }
                    if (Prefs.loaderStyle == style) {
                        Icon(
                            Icons.Rounded.Check, null,
                            tint = fsColors.accent, modifier = Modifier.size(20.dp),
                        )
                    }
                }
                if (i != LoaderStyle.entries.lastIndex) RowSeparator(startIndent = 16.dp)
            }
        }
        Spacer(Modifier.height(24.dp))

    }
}

@Composable
fun DashboardSettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val activity = context as? FragmentActivity
    SettingsPage("Dashboard", onBack) {
        // Anything switched off here is never composed and never scanned for,
        // so turning a card off actually removes its cost.
        SectionHeader("Dashboard")

        // Pick a card up, then tap where it goes.
        //
        // Nudging one step at a time meant up to eleven taps to move something
        // across twelve cards, and the row slid out from under your finger on
        // every one. Dragging is the usual answer but not here: the list is
        // taller than the screen, so a drag would have to fight the page's own
        // scroll and auto-scroll at the edges to reach the far end. Two taps
        // with a scroll in between is both simpler and shorter.
        var moving by remember { mutableStateOf<DashboardCard?>(null) }

        moving?.let { card ->
            GroupedCard(Modifier.padding(horizontal = 16.dp)) {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                    Text(
                        "Moving “${card.label}”",
                        style = MaterialTheme.typography.titleMedium,
                        color = fsColors.accent,
                    )
                    Text(
                        "Tap a row below to drop it there. Scroll first if you need to.",
                        style = MaterialTheme.typography.bodySmall,
                        color = fsColors.secondaryLabel,
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        MovePill("To top") {
                            DashboardPrefs.moveTo(card, 0)
                            moving = null
                        }
                        MovePill("To bottom") {
                            DashboardPrefs.moveTo(card, DashboardPrefs.order.lastIndex)
                            moving = null
                        }
                        MovePill("Cancel") { moving = null }
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
        }

        GroupedCard(Modifier.padding(horizontal = 16.dp)) {
            DashboardPrefs.order.forEachIndexed { i, card ->
                val on = DashboardPrefs.isEnabled(card)
                val lifted = moving == card
                val placing = moving != null && !lifted
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(
                            when {
                                lifted -> fsColors.accent.copy(alpha = 0.12f)
                                else -> Color.Transparent
                            }
                        )
                        // While something is in hand the whole row is the drop
                        // target, so placing never needs a precise tap.
                        .then(
                            if (placing) {
                                Modifier.pressScale {
                                    DashboardPrefs.moveTo(moving!!, i)
                                    moving = null
                                }
                            } else {
                                Modifier
                            }
                        )
                        .padding(start = 10.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(
                                if (lifted) fsColors.accent else fsColors.fill.copy(alpha = 0.6f)
                            )
                            .then(
                                if (moving == null) {
                                    Modifier.pressScale { moving = card }
                                } else {
                                    Modifier
                                }
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (placing) {
                            Text(
                                "${i + 1}",
                                style = MaterialTheme.typography.labelMedium,
                                color = fsColors.secondaryLabel,
                            )
                        } else {
                            Icon(
                                Icons.Rounded.DragIndicator,
                                if (lifted) "In hand" else "Move ${card.label}",
                                tint = if (lifted) Color.White else fsColors.secondaryLabel,
                                modifier = Modifier.size(19.dp),
                            )
                        }
                    }
                    Spacer(Modifier.width(10.dp))
                    Text(
                        "${i + 1}",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (on) fsColors.accent else fsColors.secondaryLabel,
                        modifier = Modifier.width(20.dp),
                    )
                    Column(Modifier.weight(1f)) {
                        Text(
                            card.label,
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (on) fsColors.label else fsColors.secondaryLabel,
                        )
                        Text(
                            if (placing) "Drop here" else card.blurb,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (placing) fsColors.accent else fsColors.secondaryLabel,
                        )
                    }
                    // Hidden mid-move: the row means "drop here" then, and a
                    // switch sitting in it would be the one thing that did not.
                    if (moving == null) {
                        Switch(
                            checked = on,
                            onCheckedChange = { DashboardPrefs.setEnabled(card, it) },
                            colors = SwitchDefaults.colors(
                                checkedTrackColor = fsColors.accent,
                                checkedThumbColor = Color.White,
                                uncheckedThumbColor = Color.White,
                                uncheckedTrackColor = fsColors.fill,
                                uncheckedBorderColor = Color.Transparent,
                            ),
                        )
                    }
                }
                if (i != DashboardPrefs.order.lastIndex) RowSeparator(startIndent = 16.dp)
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (moving == null) {
                    "Cards appear on the dashboard in this order. Tap the handle to move one."
                } else {
                    "Tap any row to drop it there."
                },
                style = MaterialTheme.typography.labelSmall,
                color = fsColors.secondaryLabel,
                modifier = Modifier.weight(1f),
            )
            Text(
                "Reset",
                style = MaterialTheme.typography.labelMedium,
                color = fsColors.accent,
                modifier = Modifier
                    .pressScale {
                        moving = null
                        DashboardPrefs.resetOrder()
                    }
                    .padding(4.dp),
            )
        }
        Spacer(Modifier.height(24.dp))

        SectionHeader("Chart Style")
        GroupedCard(Modifier.padding(horizontal = 16.dp)) {
            ChartStyle.entries.forEachIndexed { i, style ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .pressScale { Prefs.updateChartStyle(style) }
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            style.label,
                            style = MaterialTheme.typography.bodyLarge,
                            color = fsColors.label,
                        )
                        Text(
                            style.blurb,
                            style = MaterialTheme.typography.bodySmall,
                            color = fsColors.secondaryLabel,
                        )
                    }
                    if (Prefs.chartStyle == style) {
                        Icon(
                            Icons.Rounded.Check, null,
                            tint = fsColors.accent, modifier = Modifier.size(20.dp),
                        )
                    }
                }
                if (i != ChartStyle.entries.lastIndex) RowSeparator(startIndent = 16.dp)
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            "Applies to Biggest Files and Largest Folders.",
            style = MaterialTheme.typography.labelSmall,
            color = fsColors.secondaryLabel,
            modifier = Modifier.padding(horizontal = 20.dp),
        )
        Spacer(Modifier.height(24.dp))

    }
}

@Composable
fun FilesSettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val activity = context as? FragmentActivity
    SettingsPage("Files & Folders", onBack) {
        // ── Files ──────────────────────────────────────────────────────
        SectionHeader("Files")
        GroupedCard(Modifier.padding(horizontal = 16.dp)) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Rounded.Visibility, null,
                    tint = fsColors.accent, modifier = Modifier.size(26.dp),
                )
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text("Show hidden files", style = MaterialTheme.typography.bodyLarge, color = fsColors.label)
                    Text(
                        "Include files and folders starting with a dot",
                        style = MaterialTheme.typography.bodySmall,
                        color = fsColors.secondaryLabel,
                    )
                }
                Switch(
                    checked = Prefs.showHidden,
                    onCheckedChange = { Prefs.updateShowHidden(it) },
                    colors = SwitchDefaults.colors(
                        checkedTrackColor = fsColors.accent,
                        checkedThumbColor = Color.White,
                        uncheckedThumbColor = Color.White,
                        uncheckedTrackColor = fsColors.fill,
                        uncheckedBorderColor = Color.Transparent,
                    ),
                )
            }
        }
        Spacer(Modifier.height(24.dp))

        // ── Reel ───────────────────────────────────────────────────────
        SectionHeader("Reel")
        GroupedCard(Modifier.padding(horizontal = 16.dp)) {
            SettingSwitch(
                icon = Icons.Rounded.Slideshow,
                title = "Reel view",
                subtitle = "Adds a full-screen feed of a folder's photos and videos " +
                    "to the view menu",
                checked = Prefs.reelEnabled,
                onChange = { Prefs.updateReelEnabled(it) },
            )
            if (Prefs.reelEnabled) {
                RowSeparator(startIndent = 56.dp)
                SettingSwitch(
                    icon = Icons.Rounded.PlayCircleOutline,
                    title = "Play videos automatically",
                    subtitle = "Start each clip as it reaches the front",
                    checked = Prefs.reelAutoplay,
                    onChange = { Prefs.updateReelAutoplay(it) },
                )
                RowSeparator(startIndent = 56.dp)
                SettingSwitch(
                    icon = Icons.Rounded.VolumeOff,
                    title = "Start muted",
                    subtitle = "Unmuting stays on until you leave the reel",
                    checked = Prefs.reelStartMuted,
                    onChange = { Prefs.updateReelStartMuted(it) },
                )
                RowSeparator(startIndent = 56.dp)
                SettingSwitch(
                    icon = Icons.Rounded.Repeat,
                    title = "Repeat videos",
                    subtitle = if (Prefs.reelAutoAdvance) {
                        "Ignored while playing through — a looping clip never ends"
                    } else {
                        "Loop the clip instead of stopping at the end"
                    },
                    checked = Prefs.reelLoop,
                    onChange = { Prefs.updateReelLoop(it) },
                )
                RowSeparator(startIndent = 56.dp)
                SettingSwitch(
                    icon = Icons.Rounded.SmartDisplay,
                    title = "Play through automatically",
                    subtitle = "Move on by itself: a video when it finishes, " +
                        "a photo after a moment",
                    checked = Prefs.reelAutoAdvance,
                    onChange = { Prefs.updateReelAutoAdvance(it) },
                )
                if (Prefs.reelAutoAdvance) {
                    RowSeparator(startIndent = 56.dp)
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                        Text(
                            "Hold each photo for",
                            style = MaterialTheme.typography.bodyLarge,
                            color = fsColors.label,
                        )
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(3, 5, 8, 12).forEach { seconds ->
                                val active = Prefs.reelPhotoSeconds == seconds
                                Box(
                                    Modifier
                                        .clip(CircleShape)
                                        .background(
                                            if (active) fsColors.accent else fsColors.fill
                                        )
                                        .pressScale { Prefs.updateReelPhotoSeconds(seconds) }
                                        .padding(horizontal = 18.dp, vertical = 8.dp),
                                ) {
                                    Text(
                                        "${seconds}s",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = if (active) Color.White else fsColors.label,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

/** The switch row used throughout the settings pages. */
@Composable
private fun SettingSwitch(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = fsColors.accent, modifier = Modifier.size(26.dp))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = fsColors.label)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = fsColors.secondaryLabel,
            )
        }
        Spacer(Modifier.width(10.dp))
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedTrackColor = fsColors.accent,
                checkedThumbColor = Color.White,
                uncheckedThumbColor = Color.White,
                uncheckedTrackColor = fsColors.fill,
                uncheckedBorderColor = Color.Transparent,
            ),
        )
    }
}

@Composable
fun PrivacySettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val activity = context as? FragmentActivity
    SettingsPage("Privacy & Security", onBack) {
        // ── Security ───────────────────────────────────────────────────
        SectionHeader("Security")
        GroupedCard(Modifier.padding(horizontal = 16.dp)) {
            val biometricsAvailable = activity != null && Biometrics.available(activity)
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Rounded.Fingerprint, null,
                    tint = fsColors.accent, modifier = Modifier.size(26.dp),
                )
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text("Biometric lock", style = MaterialTheme.typography.bodyLarge, color = fsColors.label)
                    Text(
                        if (biometricsAvailable) "Require fingerprint, face or PIN to open File Storm"
                        else "Set up a screen lock on this device first",
                        style = MaterialTheme.typography.bodySmall,
                        color = fsColors.secondaryLabel,
                    )
                }
                Switch(
                    checked = Prefs.biometricLock,
                    enabled = biometricsAvailable,
                    onCheckedChange = { enable ->
                        val act = activity ?: return@Switch
                        // Authenticate before flipping either way, so a passer-by
                        // can't just switch the lock off.
                        Biometrics.prompt(
                            act,
                            title = if (enable) "Enable biometric lock" else "Disable biometric lock",
                            subtitle = "Confirm it's you",
                            onSuccess = { Prefs.updateBiometricLock(enable) },
                        )
                    },
                    colors = SwitchDefaults.colors(
                        checkedTrackColor = fsColors.accent,
                        checkedThumbColor = Color.White,
                        uncheckedThumbColor = Color.White,
                        uncheckedTrackColor = fsColors.fill,
                        uncheckedBorderColor = Color.Transparent,
                    ),
                )
            }
        }
        Spacer(Modifier.height(24.dp))

        // ── Privacy ────────────────────────────────────────────────────
        SectionHeader("Privacy")
        GroupedCard(Modifier.padding(horizontal = 16.dp)) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Rounded.VisibilityOff, null,
                    tint = fsColors.accent, modifier = Modifier.size(26.dp),
                )
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        "Hide preview in recents",
                        style = MaterialTheme.typography.bodyLarge,
                        color = fsColors.label,
                    )
                    Text(
                        "Blanks the app preview in the recent-apps switcher and blocks screenshots",
                        style = MaterialTheme.typography.bodySmall,
                        color = fsColors.secondaryLabel,
                    )
                }
                Switch(
                    checked = Prefs.secureScreen,
                    onCheckedChange = { Prefs.updateSecureScreen(it) },
                    colors = SwitchDefaults.colors(
                        checkedTrackColor = fsColors.accent,
                        checkedThumbColor = Color.White,
                        uncheckedThumbColor = Color.White,
                        uncheckedTrackColor = fsColors.fill,
                        uncheckedBorderColor = Color.Transparent,
                    ),
                )
            }
        }
        Spacer(Modifier.height(24.dp))

    }
}

@Composable
fun VaultSettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val activity = context as? FragmentActivity
    SettingsPage("Vault", onBack) {
        SectionHeader("Vault")
        GroupedCard(Modifier.padding(horizontal = 16.dp)) {
            VaultToggle(
                "Verify after encrypting",
                "Reads every encrypted file back and checks it against the original before " +
                    "the original is removed. Turning this off is about a third faster.",
                VaultPrefs.verifyAfterWrite,
            ) { VaultPrefs.updateVerify(it) }
            RowSeparator(startIndent = 16.dp)
            VaultToggle(
                "Include hidden files",
                "Encrypt files and folders whose names start with a dot.",
                VaultPrefs.includeHidden,
            ) { VaultPrefs.updateIncludeHidden(it) }
            RowSeparator(startIndent = 16.dp)
            VaultToggle(
                "Send originals to Trash",
                "Recoverable, but the space is not freed until the Trash is emptied - which " +
                    "matters when the folder is larger than the space left.",
                VaultPrefs.originalsToTrash,
            ) { VaultPrefs.updateToTrash(it) }
        }
        Spacer(Modifier.height(14.dp))

        SectionHeader("Vault Speed")
        GroupedCard(Modifier.padding(horizontal = 16.dp)) {
            listOf(0 to "Auto", 1 to "One at a time", 2 to "Two", 4 to "Four").forEachIndexed { i, pair ->
                ChoicePlainRow(
                    if (pair.first == 0) "Auto (" + VaultPrefs.resolvedWorkers() + " at once)" else pair.second,
                    VaultPrefs.workers == pair.first,
                ) { VaultPrefs.updateWorkers(pair.first) }
                if (i != 3) RowSeparator(startIndent = 16.dp)
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            "Files at once. More is faster on folders full of small files, where opening and " +
                "flushing each one costs more than the encryption itself. It also warms the " +
                "phone up more.",
            style = MaterialTheme.typography.labelSmall,
            color = fsColors.secondaryLabel,
            modifier = Modifier.padding(horizontal = 20.dp),
        )
        Spacer(Modifier.height(24.dp))

        SectionHeader("Vault Locking")
        GroupedCard(Modifier.padding(horizontal = 16.dp)) {
            AutoLock.entries.forEachIndexed { i, mode ->
                ChoicePlainRow(mode.label, VaultPrefs.autoLock == mode) {
                    VaultPrefs.updateAutoLock(mode)
                }
                if (i != AutoLock.entries.lastIndex) RowSeparator(startIndent = 16.dp)
            }
        }
        Spacer(Modifier.height(14.dp))

        SectionHeader("Vault Key Strength")
        GroupedCard(Modifier.padding(horizontal = 16.dp)) {
            VaultCrypto.Strength.entries.forEachIndexed { i, level ->
                ChoicePlainRow(level.label, VaultPrefs.strength == level) {
                    VaultPrefs.updateStrength(level)
                }
                if (i != VaultCrypto.Strength.entries.lastIndex) RowSeparator(startIndent = 16.dp)
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            "A stronger setting makes unlocking slower and guessing far slower. Applies to " +
                "vaults made from now on; an existing vault keeps what it was made with.",
            style = MaterialTheme.typography.labelSmall,
            color = fsColors.secondaryLabel,
            modifier = Modifier.padding(horizontal = 20.dp),
        )
        Spacer(Modifier.height(24.dp))

        SectionHeader("Vault Log")
        GroupedCard(Modifier.padding(horizontal = 16.dp)) {
            VaultLogLevel.entries.forEachIndexed { i, level ->
                ChoicePlainRow(level.label + " - " + level.blurb, VaultPrefs.logLevel == level) {
                    VaultPrefs.updateLogLevel(level)
                }
                if (i != VaultLogLevel.entries.lastIndex) RowSeparator(startIndent = 16.dp)
            }
            RowSeparator(startIndent = 16.dp)
            VaultToggle(
                "Include filenames in the log",
                "Off by default: a log naming your files gives away what the vault is hiding. " +
                    "Files are identified by a short code instead.",
                VaultPrefs.logFilenames,
            ) { VaultPrefs.updateLogFilenames(it) }
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Log is " + Formatters.bytes(VaultLog.sizeBytes()),
                style = MaterialTheme.typography.labelSmall,
                color = fsColors.secondaryLabel,
                modifier = Modifier.weight(1f),
            )
            Text(
                "Share",
                style = MaterialTheme.typography.labelMedium,
                color = fsColors.accent,
                modifier = Modifier
                    .pressScale { shareVaultLog(context) }
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            )
            Text(
                "Clear",
                style = MaterialTheme.typography.labelMedium,
                color = fsColors.red,
                modifier = Modifier.pressScale { VaultLog.clear() }.padding(4.dp),
            )
        }
        Spacer(Modifier.height(24.dp))

    }
}

@Composable
fun IdentitySettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val activity = context as? FragmentActivity
    SettingsPage("App Icon & Name", onBack) {
        // ── App icon ───────────────────────────────────────────────────
        SectionHeader("App Icon")
        GroupedCard(Modifier.padding(horizontal = 16.dp)) {
            val context = LocalContext.current
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                com.shahabcodes.filestorm.data.IconManager.icons.forEach { icon ->
                    val selectedIcon = Prefs.appIcon == icon.key
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        val bitmap = androidx.compose.runtime.remember(icon.mipmap) {
                            androidx.core.content.res.ResourcesCompat
                                .getDrawable(context.resources, icon.mipmap, context.theme)
                                ?.toBitmap(132, 132)
                                ?.asImageBitmap()
                        }
                        Box(
                            Modifier
                                .size(58.dp)
                                .clip(androidx.compose.foundation.shape.RoundedCornerShape(14.dp))
                                .background(fsColors.fill)
                                .then(
                                    if (selectedIcon) Modifier.border(
                                        2.5.dp, fsColors.accent,
                                        androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
                                    ) else Modifier
                                )
                                .pressScale {
                                    com.shahabcodes.filestorm.data.IconManager.apply(context, iconKey = icon.key)
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            bitmap?.let {
                                androidx.compose.foundation.Image(
                                    bitmap = it,
                                    contentDescription = icon.label,
                                    modifier = Modifier.size(58.dp),
                                )
                            }
                        }
                        Spacer(Modifier.height(5.dp))
                        Text(
                            icon.label,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (selectedIcon) fsColors.accent else fsColors.secondaryLabel,
                            maxLines = 1,
                        )
                    }
                }
            }
            Text(
                "Your launcher may take a moment to show the new icon.",
                style = MaterialTheme.typography.labelSmall,
                color = fsColors.secondaryLabel.copy(alpha = 0.7f),
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
            )
        }
        Spacer(Modifier.height(24.dp))

        // ── App name ───────────────────────────────────────────────────
        SectionHeader("App Name")
        GroupedCard(Modifier.padding(horizontal = 16.dp)) {
            val context = LocalContext.current
            com.shahabcodes.filestorm.data.IconManager.names.forEachIndexed { i, name ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .pressScale {
                            com.shahabcodes.filestorm.data.IconManager.apply(context, nameKey = name.key)
                        }
                        .padding(horizontal = 16.dp, vertical = 13.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        name.label,
                        style = MaterialTheme.typography.bodyLarge,
                        color = fsColors.label,
                        modifier = Modifier.weight(1f),
                    )
                    if (Prefs.appName == name.key) {
                        Icon(
                            Icons.Rounded.Check, null,
                            tint = fsColors.accent, modifier = Modifier.size(20.dp),
                        )
                    }
                }
                if (i != com.shahabcodes.filestorm.data.IconManager.names.lastIndex) {
                    RowSeparator(startIndent = 16.dp)
                }
            }
            Text(
                "Changes how the app is named on your home screen.",
                style = MaterialTheme.typography.labelSmall,
                color = fsColors.secondaryLabel.copy(alpha = 0.7f),
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp, top = 4.dp),
            )
        }
        Spacer(Modifier.height(24.dp))

    }
}

@Composable
fun AboutSettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val activity = context as? FragmentActivity
    // Session-only: closing the app puts the tools away again.
    var versionTaps by remember { mutableIntStateOf(0) }
    var devUnlocked by remember { mutableStateOf(Prefs.diagnostics) }
    SettingsPage("About", onBack) {
        // Hidden until the version number is tapped several times, the usual
        // way developer tools stay out of a shipped build without being cut
        // from it — a real trace is still one gesture away if a bug appears.
        if (devUnlocked) {
            SectionHeader("Troubleshooting")
            GroupedCard(Modifier.padding(horizontal = 16.dp)) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Rounded.BugReport, null,
                    tint = fsColors.accent, modifier = Modifier.size(26.dp),
                )
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        "Diagnostics overlay",
                        style = MaterialTheme.typography.bodyLarge,
                        color = fsColors.label,
                    )
                    Text(
                        "Shows a live trace of screens, taps and what gets opened, with " +
                            "copy and share buttons",
                        style = MaterialTheme.typography.bodySmall,
                        color = fsColors.secondaryLabel,
                    )
                }
                Switch(
                    checked = Prefs.diagnostics,
                    onCheckedChange = { Prefs.updateDiagnostics(it) },
                    colors = SwitchDefaults.colors(
                        checkedTrackColor = fsColors.accent,
                        checkedThumbColor = Color.White,
                        uncheckedThumbColor = Color.White,
                        uncheckedTrackColor = fsColors.fill,
                        uncheckedBorderColor = Color.Transparent,
                    ),
                )
                }
            }
            Spacer(Modifier.height(24.dp))
        }

        SectionHeader("About")
        GroupedCard(Modifier.padding(horizontal = 16.dp)) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    "File Storm",
                    style = MaterialTheme.typography.titleLarge,
                    color = fsColors.label,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    "Version ${BuildConfig.VERSION_NAME}",
                    style = MaterialTheme.typography.bodySmall,
                    color = fsColors.secondaryLabel,
                    modifier = Modifier.pressScale {
                        if (devUnlocked) return@pressScale
                        versionTaps++
                        if (versionTaps >= 7) {
                            devUnlocked = true
                            Toast.makeText(
                                context,
                                "Diagnostics unlocked",
                                Toast.LENGTH_SHORT,
                            ).show()
                        }
                    },
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    "Developed by",
                    style = MaterialTheme.typography.labelSmall,
                    color = fsColors.secondaryLabel,
                )
                Spacer(Modifier.height(1.dp))
                Text(
                    "shahabkodes",
                    style = MaterialTheme.typography.titleMedium,
                    color = fsColors.accent,
                )
            }
        }
        Spacer(Modifier.height(40.dp))

    }
}

/** A settings row that previews its option as a gradient dot. */
@Composable
private fun ChoiceRow(
    label: String,
    blurb: String,
    swatchStart: Long,
    swatchEnd: Long,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .pressScale(onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(26.dp)
                .clip(CircleShape)
                .background(Brush.linearGradient(listOf(Color(swatchStart), Color(swatchEnd))))
                // Pale swatches would vanish against a white card without this.
                .border(1.dp, fsColors.separator, CircleShape)
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge, color = fsColors.label)
            Text(blurb, style = MaterialTheme.typography.bodySmall, color = fsColors.secondaryLabel)
        }
        if (selected) {
            Icon(
                Icons.Rounded.Check, null,
                tint = fsColors.accent, modifier = Modifier.size(20.dp),
            )
        }
    }
}

/** A compact chevron for nudging a dashboard card up or down the list. */
@Composable
private fun MovePill(label: String, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(CircleShape)
            .background(fsColors.fill)
            .pressScale(onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = fsColors.accent,
        )
    }
}

/** A vault switch with room for the explanation each one needs. */
@Composable
private fun VaultToggle(
    label: String,
    blurb: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge, color = fsColors.label)
            Text(blurb, style = MaterialTheme.typography.bodySmall, color = fsColors.secondaryLabel)
        }
        Spacer(Modifier.width(10.dp))
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedTrackColor = fsColors.accent,
                checkedThumbColor = Color.White,
                uncheckedThumbColor = Color.White,
                uncheckedTrackColor = fsColors.fill,
                uncheckedBorderColor = Color.Transparent,
            ),
        )
    }
}

@Composable
private fun ChoicePlainRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .pressScale(onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = fsColors.label,
            modifier = Modifier.weight(1f),
        )
        if (selected) {
            Icon(
                Icons.Rounded.Check, null,
                tint = fsColors.accent, modifier = Modifier.size(20.dp),
            )
        }
    }
}

/** Shares the log file. It carries no names unless the user turned them on. */
private fun shareVaultLog(context: android.content.Context) {
    val file = VaultLog.file() ?: return
    if (!file.isFile) return
    runCatching {
        val uri = androidx.core.content.FileProvider.getUriForFile(
            context, context.packageName + ".fileprovider", file,
        )
        context.startActivity(
            android.content.Intent.createChooser(
                android.content.Intent(android.content.Intent.ACTION_SEND)
                    .setType("text/plain")
                    .putExtra(android.content.Intent.EXTRA_STREAM, uri)
                    .addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION),
                "Share vault log",
            )
        )
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = fsColors.secondaryLabel,
        modifier = Modifier.padding(horizontal = 32.dp).padding(bottom = 8.dp),
    )
}

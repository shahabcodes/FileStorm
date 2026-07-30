package com.shahabcodes.filestorm.ui.settings

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
import androidx.compose.material.icons.automirrored.rounded.ArrowBackIos
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import com.shahabcodes.filestorm.BuildConfig
import com.shahabcodes.filestorm.data.Accent
import com.shahabcodes.filestorm.data.Appearance
import com.shahabcodes.filestorm.data.Prefs
import com.shahabcodes.filestorm.ui.Biometrics
import com.shahabcodes.filestorm.ui.components.GroupedCard
import com.shahabcodes.filestorm.ui.components.RowSeparator
import com.shahabcodes.filestorm.ui.components.pressScale
import com.shahabcodes.filestorm.ui.theme.fsColors

@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val activity = LocalContext.current as? FragmentActivity

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
                    Icons.AutoMirrored.Rounded.ArrowBackIos, null,
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
        Spacer(Modifier.height(20.dp))

        // ── Appearance ─────────────────────────────────────────────────
        SectionHeader("Appearance")
        GroupedCard(Modifier.padding(horizontal = 16.dp)) {
            Appearance.entries.forEachIndexed { i, mode ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .pressScale { Prefs.updateAppearance(mode) }
                        .padding(horizontal = 16.dp, vertical = 13.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        mode.label,
                        style = MaterialTheme.typography.bodyLarge,
                        color = fsColors.label,
                        modifier = Modifier.weight(1f),
                    )
                    if (Prefs.appearance == mode) {
                        Icon(
                            Icons.Rounded.Check, null,
                            tint = fsColors.accent, modifier = Modifier.size(20.dp),
                        )
                    }
                }
                if (i != Appearance.entries.lastIndex) RowSeparator(startIndent = 16.dp)
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
                                    com.shahabcodes.filestorm.data.IconManager.apply(context, icon)
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
                        if (biometricsAvailable) "Require fingerprint, face or PIN to open FileStorm"
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
                        checkedTrackColor = fsColors.green,
                        checkedThumbColor = Color.White,
                        uncheckedThumbColor = Color.White,
                        uncheckedTrackColor = fsColors.fill,
                        uncheckedBorderColor = Color.Transparent,
                    ),
                )
            }
        }
        Spacer(Modifier.height(24.dp))

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
                        checkedTrackColor = fsColors.green,
                        checkedThumbColor = Color.White,
                        uncheckedThumbColor = Color.White,
                        uncheckedTrackColor = fsColors.fill,
                        uncheckedBorderColor = Color.Transparent,
                    ),
                )
            }
        }
        Spacer(Modifier.height(24.dp))

        Text(
            "FileStorm v${BuildConfig.VERSION_NAME}",
            style = MaterialTheme.typography.bodySmall,
            color = fsColors.secondaryLabel.copy(alpha = 0.6f),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 40.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
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

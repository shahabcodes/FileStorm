package com.shahabcodes.filestorm.ui.browser

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.FormatBold
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import com.shahabcodes.filestorm.data.FolderStyles
import com.shahabcodes.filestorm.data.FsEntry
import com.shahabcodes.filestorm.ui.components.FileIconView
import com.shahabcodes.filestorm.ui.components.GroupedCard
import com.shahabcodes.filestorm.ui.components.RowSeparator
import com.shahabcodes.filestorm.ui.components.pressScale
import com.shahabcodes.filestorm.ui.theme.fsColors

private val presets = listOf(
    0xFF007AFF, 0xFF5856D6, 0xFFAF52DE, 0xFFFF2D55, 0xFFFF3B30,
    0xFFFF9500, 0xFFFFCC00, 0xFF34C759, 0xFF30B0C7, 0xFF8E8E93,
    0xFFA2845E, 0xFF000000,
).map { it.toInt() }

/** Folder customisation: preset swatches, custom HSV colour picker, bold name. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FolderStyleSheet(entry: FsEntry, onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val currentColor = FolderStyles.colorOf(entry.path)
    var customMode by remember { mutableStateOf(false) }

    // HSV state seeded from the current colour if one is set.
    var hue by remember { mutableFloatStateOf(0f) }
    var sat by remember { mutableFloatStateOf(0.85f) }
    var value by remember { mutableFloatStateOf(0.95f) }
    remember {
        currentColor?.let {
            val hsv = FloatArray(3)
            android.graphics.Color.colorToHSV(it, hsv)
            hue = hsv[0]; sat = hsv[1]; value = hsv[2]
        }
    }
    val customColor = Color.hsv(hue, sat, value)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = fsColors.groupedBackground,
    ) {
        Column(
            Modifier
                .navigationBarsPadding()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                FileIconView(entry, size = 46.dp, cornerRadius = 12.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        entry.name,
                        style = MaterialTheme.typography.titleLarge,
                        color = fsColors.label,
                        fontWeight = com.shahabcodes.filestorm.ui.components.entryNameWeight(entry),
                        maxLines = 1,
                    )
                    Text(
                        "Folder style",
                        style = MaterialTheme.typography.bodySmall,
                        color = fsColors.secondaryLabel,
                    )
                }
                TextButton(onClick = onDismiss) { Text("Done", color = fsColors.accent) }
            }
            Spacer(Modifier.height(14.dp))

            // ── Colour ─────────────────────────────────────────────────
            GroupedCard {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        "COLOUR",
                        style = MaterialTheme.typography.labelMedium,
                        color = fsColors.secondaryLabel,
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(
                        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        // Default (no colour)
                        Box(
                            Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(fsColors.fill)
                                .pressScale {
                                    FolderStyles.setColor(entry.path, null)
                                    customMode = false
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            if (currentColor == null && !customMode) {
                                Icon(Icons.Rounded.Check, null, tint = fsColors.label, modifier = Modifier.size(20.dp))
                            } else {
                                Text("A", color = fsColors.secondaryLabel, style = MaterialTheme.typography.labelLarge)
                            }
                        }
                        presets.forEach { preset ->
                            val selected = currentColor == preset && !customMode
                            Box(
                                Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(Color(preset))
                                    .pressScale {
                                        FolderStyles.setColor(entry.path, preset)
                                        customMode = false
                                    },
                                contentAlignment = Alignment.Center,
                            ) {
                                if (selected) {
                                    Icon(Icons.Rounded.Check, null, tint = Color.White, modifier = Modifier.size(20.dp))
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(16.dp))

                    // Custom picker toggle row
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .pressScale { customMode = !customMode }
                            .padding(vertical = 6.dp),
                    ) {
                        Box(
                            Modifier
                                .size(26.dp)
                                .clip(CircleShape)
                                .background(
                                    Brush.sweepGradient(
                                        listOf(
                                            Color.Red, Color.Yellow, Color.Green,
                                            Color.Cyan, Color.Blue, Color.Magenta, Color.Red,
                                        )
                                    )
                                ),
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            "Custom colour…",
                            style = MaterialTheme.typography.bodyLarge,
                            color = fsColors.accent,
                        )
                    }

                    if (customMode) {
                        Spacer(Modifier.height(12.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier
                                    .size(44.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(customColor),
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(
                                "#%06X".format(customColor.toArgb() and 0xFFFFFF),
                                style = MaterialTheme.typography.titleMedium,
                                color = fsColors.label,
                            )
                            Spacer(Modifier.weight(1f))
                            TextButton(onClick = {
                                FolderStyles.setColor(entry.path, customColor.toArgb())
                            }) { Text("Apply", color = fsColors.accent) }
                        }
                        Spacer(Modifier.height(8.dp))

                        PickerSlider(
                            label = "Hue",
                            value = hue / 360f,
                            onChange = { hue = it * 360f },
                            gradient = listOf(
                                Color.hsv(0f, 1f, 1f), Color.hsv(60f, 1f, 1f), Color.hsv(120f, 1f, 1f),
                                Color.hsv(180f, 1f, 1f), Color.hsv(240f, 1f, 1f), Color.hsv(300f, 1f, 1f),
                                Color.hsv(360f, 1f, 1f),
                            ),
                            thumbColor = Color.hsv(hue, 1f, 1f),
                        )
                        PickerSlider(
                            label = "Saturation",
                            value = sat,
                            onChange = { sat = it },
                            gradient = listOf(Color.hsv(hue, 0f, value), Color.hsv(hue, 1f, value)),
                            thumbColor = customColor,
                        )
                        PickerSlider(
                            label = "Brightness",
                            value = value,
                            onChange = { value = it },
                            gradient = listOf(Color.hsv(hue, sat, 0f), Color.hsv(hue, sat, 1f)),
                            thumbColor = customColor,
                        )
                    }
                }
            }
            Spacer(Modifier.height(14.dp))

            // ── Bold name + lock ───────────────────────────────────────
            GroupedCard {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Rounded.FormatBold, null,
                        tint = fsColors.accent, modifier = Modifier.size(24.dp),
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        "Bold folder name",
                        style = MaterialTheme.typography.bodyLarge,
                        color = fsColors.label,
                        modifier = Modifier.weight(1f),
                    )
                    Switch(
                        checked = FolderStyles.isBold(entry.path),
                        onCheckedChange = { FolderStyles.setBold(entry.path, it) },
                        colors = SwitchDefaults.colors(
                            checkedTrackColor = fsColors.green,
                            checkedThumbColor = Color.White,
                            uncheckedThumbColor = Color.White,
                            uncheckedTrackColor = fsColors.fill,
                            uncheckedBorderColor = Color.Transparent,
                        ),
                    )
                }
                RowSeparator(startIndent = 16.dp)
                val activity = androidx.compose.ui.platform.LocalContext.current
                    as? androidx.fragment.app.FragmentActivity
                val biometricsOk = activity != null &&
                    com.shahabcodes.filestorm.ui.Biometrics.available(activity)
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Rounded.Lock, null,
                        tint = fsColors.orange, modifier = Modifier.size(24.dp),
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            "Lock this folder",
                            style = MaterialTheme.typography.bodyLarge,
                            color = fsColors.label,
                        )
                        Text(
                            if (biometricsOk) "Opening it requires fingerprint, face or PIN"
                            else "Set up a device screen lock first",
                            style = MaterialTheme.typography.bodySmall,
                            color = fsColors.secondaryLabel,
                        )
                    }
                    Switch(
                        checked = com.shahabcodes.filestorm.data.FolderLocks.isLocked(entry.path),
                        enabled = biometricsOk,
                        onCheckedChange = { enable ->
                            val act = activity ?: return@Switch
                            com.shahabcodes.filestorm.ui.Biometrics.prompt(
                                act,
                                title = if (enable) "Lock \"${entry.name}\"" else "Unlock \"${entry.name}\"",
                                subtitle = "Confirm it's you",
                                onSuccess = {
                                    com.shahabcodes.filestorm.data.FolderLocks.setLocked(entry.path, enable)
                                },
                            )
                        },
                        colors = SwitchDefaults.colors(
                            checkedTrackColor = fsColors.orange,
                            checkedThumbColor = Color.White,
                            uncheckedThumbColor = Color.White,
                            uncheckedTrackColor = fsColors.fill,
                            uncheckedBorderColor = Color.Transparent,
                        ),
                    )
                }
            }
        }
    }
}

@Composable
private fun PickerSlider(
    label: String,
    value: Float,
    onChange: (Float) -> Unit,
    gradient: List<Color>,
    thumbColor: Color,
) {
    Column {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = fsColors.secondaryLabel,
        )
        Box(contentAlignment = Alignment.Center) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(10.dp)
                    .clip(RoundedCornerShape(5.dp))
                    .background(Brush.horizontalGradient(gradient)),
            )
            Slider(
                value = value,
                onValueChange = onChange,
                colors = SliderDefaults.colors(
                    thumbColor = thumbColor,
                    activeTrackColor = Color.Transparent,
                    inactiveTrackColor = Color.Transparent,
                ),
            )
        }
    }
}

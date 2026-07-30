package com.shahabcodes.filestorm.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Android
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.AudioFile
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.InsertDriveFile
import androidx.compose.material.icons.rounded.PlayCircleFilled
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import com.shahabcodes.filestorm.data.FileKind
import com.shahabcodes.filestorm.data.FolderStyles
import com.shahabcodes.filestorm.data.FsEntry
import com.shahabcodes.filestorm.ui.theme.Ios
import com.shahabcodes.filestorm.ui.theme.fsColors
import java.io.File

/** iOS-style press feedback: element shrinks slightly while pressed. */
fun Modifier.pressScale(onClick: () -> Unit): Modifier = composed {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.965f else 1f,
        animationSpec = spring(stiffness = 700f),
        label = "pressScale",
    )
    this
        .scale(scale)
        .clickable(interactionSource = interaction, indication = null, onClick = onClick)
}

/** Inset-grouped card, the iOS Settings look. */
@Composable
fun GroupedCard(
    modifier: Modifier = Modifier,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(fsColors.card),
        content = content,
    )
}

@Composable
fun RowSeparator(startIndent: Dp = 60.dp) {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(start = startIndent)
            .height(0.7.dp)
            .background(fsColors.separator)
    )
}

fun kindColor(kind: FileKind, dark: Boolean): Color = when (kind) {
    FileKind.FOLDER -> if (dark) Ios.BlueDark else Ios.Blue
    FileKind.IMAGE -> if (dark) Ios.GreenDark else Ios.Green
    FileKind.VIDEO -> if (dark) Ios.PurpleDark else Ios.Purple
    FileKind.AUDIO -> Ios.Pink
    FileKind.DOCUMENT -> if (dark) Ios.OrangeDark else Ios.Orange
    FileKind.ARCHIVE -> Ios.Indigo
    FileKind.APK -> Ios.Teal
    FileKind.OTHER -> Color(0xFF8E8E93)
}

fun kindIcon(kind: FileKind): ImageVector = when (kind) {
    FileKind.FOLDER -> Icons.Rounded.Folder
    FileKind.IMAGE -> Icons.Rounded.Image
    FileKind.VIDEO -> Icons.Rounded.PlayCircleFilled
    FileKind.AUDIO -> Icons.Rounded.AudioFile
    FileKind.DOCUMENT -> Icons.Rounded.Description
    FileKind.ARCHIVE -> Icons.Rounded.Archive
    FileKind.APK -> Icons.Rounded.Android
    FileKind.OTHER -> Icons.Rounded.InsertDriveFile
}

/** Rounded-square colored icon, with live thumbnails for images and videos. */
@Composable
fun FileIconView(entry: FsEntry, size: Dp = 40.dp, cornerRadius: Dp = 10.dp) {
    val color = if (entry.isDirectory) {
        com.shahabcodes.filestorm.data.FolderStyles.colorOf(entry.path)?.let { Color(it) }
            ?: kindColor(entry.kind, fsColors.isDark)
    } else kindColor(entry.kind, fsColors.isDark)
    if (!entry.isDirectory && (entry.kind == FileKind.IMAGE || entry.kind == FileKind.VIDEO)) {
        Box(Modifier.size(size).clip(RoundedCornerShape(cornerRadius)).background(fsColors.fill)) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(File(entry.path))
                    .crossfade(true)
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(size),
            )
            if (entry.kind == FileKind.VIDEO) {
                Icon(
                    Icons.Rounded.PlayCircleFilled, null,
                    tint = Color.White.copy(alpha = 0.9f),
                    modifier = Modifier.align(Alignment.Center).size(size / 2.2f),
                )
            }
        }
    } else {
        Box(
            Modifier
                .size(size)
                .clip(RoundedCornerShape(cornerRadius))
                .background(
                    Brush.linearGradient(
                        listOf(color.copy(alpha = 0.95f), color.copy(alpha = 0.7f))
                    )
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                if (entry.isDirectory) FolderIcons.iconFor(FolderStyles.iconOf(entry.path))
                else kindIcon(entry.kind),
                null,
                tint = Color.White,
                modifier = Modifier.size(size * 0.55f),
            )
        }
    }
}

/** Bold weight for folders the user marked bold, normal otherwise. */
fun entryNameWeight(entry: FsEntry): FontWeight =
    if (entry.isDirectory && FolderStyles.isBold(entry.path)) FontWeight.Bold else FontWeight.Normal

/** iOS-style round selection checkmark. */
@Composable
fun SelectionCircle(selected: Boolean, size: Dp = 24.dp) {
    if (selected) {
        Box(
            Modifier.size(size).clip(CircleShape).background(fsColors.accent),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.Check, null, tint = Color.White, modifier = Modifier.size(size * 0.62f))
        }
    } else {
        Box(
            Modifier
                .size(size)
                .clip(CircleShape)
                .background(fsColors.fill.copy(alpha = 0.6f))
        ) {
            Box(
                Modifier
                    .size(size)
                    .padding(1.5.dp)
                    .clip(CircleShape)
                    .background(fsColors.card)
            )
        }
    }
}

@Composable
fun IosSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String = "Search",
    modifier: Modifier = Modifier,
) {
    TextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp)),
        placeholder = { Text(placeholder, color = fsColors.secondaryLabel) },
        leadingIcon = { Icon(Icons.Rounded.Search, null, tint = fsColors.secondaryLabel) },
        singleLine = true,
        colors = TextFieldDefaults.colors(
            focusedContainerColor = fsColors.fill,
            unfocusedContainerColor = fsColors.fill,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            cursorColor = fsColors.accent,
            focusedTextColor = fsColors.label,
            unfocusedTextColor = fsColors.label,
        ),
        textStyle = MaterialTheme.typography.bodyLarge,
    )
}

/** Pill-shaped filled action button, iOS style. */
@Composable
fun RowScope.ActionPill(
    icon: ImageVector,
    label: String,
    enabled: Boolean = true,
    tint: Color? = null,
    onClick: () -> Unit,
) {
    val color = tint ?: fsColors.accent
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp),
        modifier = Modifier
            .weight(1f)
            .then(if (enabled) Modifier.pressScale(onClick) else Modifier)
            .padding(vertical = 8.dp),
    ) {
        Icon(
            icon, null,
            tint = if (enabled) color else fsColors.secondaryLabel.copy(alpha = 0.4f),
            modifier = Modifier.size(24.dp),
        )
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = if (enabled) color else fsColors.secondaryLabel.copy(alpha = 0.4f),
        )
    }
}

package com.shahabcodes.filestorm.ui.home

import android.os.Environment
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Android
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.AudioFile
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.PlayCircleFilled
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Lock
import com.shahabcodes.filestorm.data.Favorites
import com.shahabcodes.filestorm.data.jobs.JobStore
import com.shahabcodes.filestorm.data.FileKind
import com.shahabcodes.filestorm.data.FileRepository
import com.shahabcodes.filestorm.data.TrashManager
import com.shahabcodes.filestorm.transfer.TransferManager
import com.shahabcodes.filestorm.ui.components.GroupedCard
import com.shahabcodes.filestorm.ui.components.RowSeparator
import com.shahabcodes.filestorm.ui.components.pressScale
import com.shahabcodes.filestorm.ui.theme.Ios
import com.shahabcodes.filestorm.ui.theme.fsColors
import com.shahabcodes.filestorm.util.Formatters

private data class Category(val kind: FileKind, val label: String, val icon: ImageVector, val color: Color)

private data class Shortcut(val label: String, val path: String, val icon: ImageVector)

@Composable
fun HomeScreen(
    onOpenFolder: (String) -> Unit,
    onOpenCategory: (FileKind) -> Unit,
    onOpenTransfer: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenTrash: () -> Unit,
    onOpenJobs: () -> Unit,
) {
    LaunchedEffect(Unit) { TrashManager.refresh() }
    val stats = remember { FileRepository.storageStats() }
    val transfer by TransferManager.state.collectAsState()

    val categories = listOf(
        Category(FileKind.IMAGE, "Images", Icons.Rounded.Image, Ios.Green),
        Category(FileKind.VIDEO, "Videos", Icons.Rounded.PlayCircleFilled, Ios.Purple),
        Category(FileKind.AUDIO, "Audio", Icons.Rounded.AudioFile, Ios.Pink),
        Category(FileKind.DOCUMENT, "Docs", Icons.Rounded.Description, Ios.Orange),
        Category(FileKind.ARCHIVE, "Archives", Icons.Rounded.Archive, Ios.Indigo),
        Category(FileKind.APK, "APKs", Icons.Rounded.Android, Ios.Teal),
    )

    val root = FileRepository.rootPath
    val shortcuts = listOf(
        Shortcut("Downloads", "$root/${Environment.DIRECTORY_DOWNLOADS}", Icons.Rounded.Download),
        Shortcut("DCIM", "$root/${Environment.DIRECTORY_DCIM}", Icons.Rounded.Image),
        Shortcut("Documents", "$root/${Environment.DIRECTORY_DOCUMENTS}", Icons.Rounded.Description),
        Shortcut("Internal storage", root, Icons.Rounded.PhoneAndroid),
    )

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(fsColors.groupedBackground)
            .statusBarsPadding(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = 16.dp, end = 16.dp, top = 8.dp, bottom = 40.dp
        ),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        item {
            Row(
                Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "FileStorm",
                    style = MaterialTheme.typography.headlineLarge,
                    color = fsColors.label,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    Icons.Rounded.Settings, "Settings",
                    tint = fsColors.accent,
                    modifier = Modifier
                        .pressScale(onOpenSettings)
                        .padding(6.dp)
                        .size(24.dp),
                )
            }
        }

        // Active transfer banner
        if (transfer.isActive) {
            item {
                GroupedCard(Modifier.pressScale(onOpenTransfer)) {
                    Row(
                        Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Rounded.SwapVert, null, tint = fsColors.accent)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                "Transfer in progress",
                                style = MaterialTheme.typography.titleMedium,
                                color = fsColors.label,
                            )
                            Text(
                                "${(transfer.progress * 100).toInt()}% · ${Formatters.speed(transfer.speedBps)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = fsColors.secondaryLabel,
                            )
                        }
                        Icon(Icons.Rounded.ChevronRight, null, tint = fsColors.secondaryLabel)
                    }
                }
            }
        }

        // Storage dashboard
        item { DashboardCard() }

        // Favorites
        if (Favorites.paths.isNotEmpty()) {
            item {
                Column {
                    Text(
                        "Favourites",
                        style = MaterialTheme.typography.titleLarge,
                        color = fsColors.label,
                        modifier = Modifier.padding(bottom = 12.dp, start = 4.dp),
                    )
                    GroupedCard {
                        val favs = Favorites.paths
                        favs.forEachIndexed { i, favPath ->
                            Row(
                                Modifier
                                    .pressScale { onOpenFolder(favPath) }
                                    .padding(horizontal = 16.dp, vertical = 13.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(
                                    Modifier
                                        .size(32.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(
                                            (com.shahabcodes.filestorm.data.FolderStyles
                                                .colorOf(favPath)?.let { Color(it) } ?: fsColors.accent)
                                                .copy(alpha = 0.18f)
                                        ),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        Icons.Rounded.Folder, null,
                                        tint = com.shahabcodes.filestorm.data.FolderStyles
                                            .colorOf(favPath)?.let { Color(it) } ?: fsColors.accent,
                                        modifier = Modifier.size(18.dp),
                                    )
                                }
                                Spacer(Modifier.width(14.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        java.io.File(favPath).name.ifEmpty { "Storage" },
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = fsColors.label,
                                        maxLines = 1,
                                    )
                                    Text(
                                        favPath.replace(root, "Internal storage"),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = fsColors.secondaryLabel,
                                        maxLines = 1,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                    )
                                }
                                if (com.shahabcodes.filestorm.data.FolderLocks.isLocked(favPath)) {
                                    Icon(
                                        Icons.Rounded.Lock, null,
                                        tint = fsColors.orange, modifier = Modifier.size(16.dp),
                                    )
                                    Spacer(Modifier.width(6.dp))
                                }
                                Icon(
                                    Icons.Rounded.ChevronRight, null,
                                    tint = fsColors.secondaryLabel.copy(alpha = 0.6f),
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                            if (i != favs.lastIndex) RowSeparator(startIndent = 62.dp)
                        }
                    }
                }
            }
        }

        // Categories grid
        item {
            Column {
                Text(
                    "Categories",
                    style = MaterialTheme.typography.titleLarge,
                    color = fsColors.label,
                    modifier = Modifier.padding(bottom = 12.dp, start = 4.dp),
                )
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier.height(190.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    userScrollEnabled = false,
                ) {
                    items(categories) { cat ->
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .background(fsColors.card)
                                .pressScale { onOpenCategory(cat.kind) }
                                .padding(vertical = 14.dp),
                        ) {
                            Box(
                                Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(
                                        Brush.linearGradient(
                                            listOf(cat.color, cat.color.copy(alpha = 0.72f))
                                        )
                                    ),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(cat.icon, null, tint = Color.White, modifier = Modifier.size(22.dp))
                            }
                            Text(
                                cat.label,
                                style = MaterialTheme.typography.labelMedium,
                                color = fsColors.label,
                            )
                        }
                    }
                }
            }
        }

        // Shortcuts
        item {
            Column {
                Text(
                    "Browse",
                    style = MaterialTheme.typography.titleLarge,
                    color = fsColors.label,
                    modifier = Modifier.padding(bottom = 12.dp, start = 4.dp),
                )
                GroupedCard {
                    shortcuts.forEachIndexed { i, s ->
                        Row(
                            Modifier
                                .pressScale { onOpenFolder(s.path) }
                                .padding(horizontal = 16.dp, vertical = 13.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                Modifier
                                    .size(32.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(fsColors.accent.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(s.icon, null, tint = fsColors.accent, modifier = Modifier.size(18.dp))
                            }
                            Spacer(Modifier.width(14.dp))
                            Text(
                                s.label,
                                style = MaterialTheme.typography.bodyLarge,
                                color = fsColors.label,
                                modifier = Modifier.weight(1f),
                            )
                            Icon(
                                Icons.Rounded.ChevronRight, null,
                                tint = fsColors.secondaryLabel.copy(alpha = 0.6f),
                                modifier = Modifier.size(20.dp),
                            )
                        }
                        if (i != shortcuts.lastIndex) RowSeparator(startIndent = 62.dp)
                    }
                }
            }
        }

        // Jobs
        item {
            GroupedCard(Modifier.pressScale(onOpenJobs)) {
                Row(
                    Modifier.padding(horizontal = 16.dp, vertical = 13.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier
                            .size(32.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Ios.Indigo.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Rounded.CalendarMonth, null,
                            tint = Ios.Indigo, modifier = Modifier.size(18.dp),
                        )
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            "Jobs",
                            style = MaterialTheme.typography.bodyLarge,
                            color = fsColors.label,
                        )
                        val jobCount = JobStore.jobs.size
                        Text(
                            if (jobCount == 0) "Organize files into month folders"
                            else "$jobCount saved job${if (jobCount == 1) "" else "s"}",
                            style = MaterialTheme.typography.bodySmall,
                            color = fsColors.secondaryLabel,
                        )
                    }
                    Icon(
                        Icons.Rounded.ChevronRight, null,
                        tint = fsColors.secondaryLabel.copy(alpha = 0.6f),
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }

        // Trash
        item {
            GroupedCard(Modifier.pressScale(onOpenTrash)) {
                Row(
                    Modifier.padding(horizontal = 16.dp, vertical = 13.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier
                            .size(32.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(fsColors.red.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Rounded.DeleteOutline, null,
                            tint = fsColors.red, modifier = Modifier.size(18.dp),
                        )
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            "Trash",
                            style = MaterialTheme.typography.bodyLarge,
                            color = fsColors.label,
                        )
                        val trashCount = TrashManager.items.size
                        Text(
                            if (trashCount == 0) "Empty"
                            else "$trashCount item${if (trashCount == 1) "" else "s"} · recoverable",
                            style = MaterialTheme.typography.bodySmall,
                            color = fsColors.secondaryLabel,
                        )
                    }
                    Icon(
                        Icons.Rounded.ChevronRight, null,
                        tint = fsColors.secondaryLabel.copy(alpha = 0.6f),
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun StorageRing(fraction: Float, size: androidx.compose.ui.unit.Dp) {
    val track = fsColors.fill
    val accent = fsColors.accent
    val green = fsColors.green
    Box(contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            val stroke = Stroke(width = 9.dp.toPx(), cap = StrokeCap.Round)
            drawArc(
                color = track,
                startAngle = -90f, sweepAngle = 360f, useCenter = false, style = stroke,
            )
            drawArc(
                brush = Brush.sweepGradient(listOf(accent, green, accent)),
                startAngle = -90f, sweepAngle = 360f * fraction.coerceIn(0.02f, 1f),
                useCenter = false, style = stroke,
            )
        }
        Text(
            "${(fraction * 100).toInt()}%",
            style = MaterialTheme.typography.titleMedium,
            color = fsColors.label,
        )
    }
}

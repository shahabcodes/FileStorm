package com.shahabcodes.filestorm.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.shahabcodes.filestorm.data.DashboardCard
import com.shahabcodes.filestorm.data.FileKind
import com.shahabcodes.filestorm.data.FsEntry
import com.shahabcodes.filestorm.data.StorageInsights
import com.shahabcodes.filestorm.ui.browser.openFile
import com.shahabcodes.filestorm.ui.components.FileIconView
import com.shahabcodes.filestorm.ui.components.GroupedCard
import com.shahabcodes.filestorm.ui.components.RowSeparator
import com.shahabcodes.filestorm.ui.components.pressScale
import com.shahabcodes.filestorm.ui.theme.fsColors
import com.shahabcodes.filestorm.util.Formatters
import java.io.File

/**
 * The full story behind a dashboard card. The card itself stays glanceable at
 * three rows; everything the scan found lives here, drawn at full size in the
 * chart style chosen in Settings and then listed in rank order.
 */
@Composable
fun InsightDetailScreen(
    card: DashboardCard,
    onBack: () -> Unit,
    onOpenFolder: (String) -> Unit,
    onOpenViewer: (List<String>, Int) -> Unit,
) {
    val context = LocalContext.current
    val snapshot = StorageInsights.snapshot

    val title = when (card) {
        DashboardCard.BIGGEST_FILES -> "Biggest Files"
        DashboardCard.LARGEST_FOLDERS -> "Largest Folders"
        DashboardCard.GROWTH -> "Monthly Footprint"
        DashboardCard.RECENT -> "Recent Files"
        else -> card.label
    }

    val slices: List<Slice> = when (card) {
        DashboardCard.BIGGEST_FILES -> snapshot?.biggestFiles.orEmpty().map { file ->
            Slice(
                title = file.name,
                subtitle = prettyPath(file.folder),
                bytes = file.size,
                onClick = { onOpenFolder(file.folder) },
            )
        }
        DashboardCard.LARGEST_FOLDERS -> snapshot?.largestFolders.orEmpty()
            .map { folderSlice(it, onOpenFolder) }
        else -> emptyList()
    }

    val totalBytes = when (card) {
        DashboardCard.GROWTH -> snapshot?.months.orEmpty().sumOf { it.bytes }
        DashboardCard.RECENT -> snapshot?.recent.orEmpty().sumOf { it.size }
        else -> slices.sumOf { it.bytes }
    }
    val count = when (card) {
        DashboardCard.GROWTH -> snapshot?.months.orEmpty().size
        DashboardCard.RECENT -> snapshot?.recent.orEmpty().size
        else -> slices.size
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(fsColors.groupedBackground)
            .statusBarsPadding(),
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

        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 16.dp, end = 16.dp, bottom = 40.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                Column {
                    Text(
                        title,
                        style = MaterialTheme.typography.headlineLarge,
                        color = fsColors.label,
                        modifier = Modifier.padding(start = 4.dp),
                    )
                    Text(
                        "$count entries" + hiddenNote(snapshot?.includedHidden),
                        style = MaterialTheme.typography.bodySmall,
                        color = fsColors.secondaryLabel,
                        modifier = Modifier.padding(start = 4.dp, top = 2.dp),
                    )
                }
            }

            // Headline number, so the screen opens with the answer.
            item {
                GroupedCard {
                    Row(
                        Modifier.fillMaxWidth().padding(18.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                Formatters.bytes(totalBytes),
                                style = MaterialTheme.typography.headlineMedium,
                                color = fsColors.label,
                            )
                            Text(
                                when (card) {
                                    DashboardCard.GROWTH -> "dated across $count months"
                                    DashboardCard.RECENT -> "changed in the last 7 days"
                                    DashboardCard.LARGEST_FOLDERS -> "held by the top $count folders"
                                    else -> "held by the top $count files"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = fsColors.secondaryLabel,
                            )
                        }
                        if (snapshot != null && snapshot.scannedAt > 0) {
                            Text(
                                "Scanned\n${Formatters.fileDate(snapshot.scannedAt)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = fsColors.secondaryLabel,
                            )
                        }
                    }
                }
            }

            if (slices.isNotEmpty()) {
                item {
                    GroupedCard {
                        Column(Modifier.fillMaxWidth().padding(18.dp)) {
                            RankedChart(slices)
                        }
                    }
                }
                item {
                    Text(
                        "Every entry",
                        style = MaterialTheme.typography.titleLarge,
                        color = fsColors.label,
                        modifier = Modifier.padding(start = 4.dp),
                    )
                }
                itemsIndexedSlices(slices)
            }

            if (card == DashboardCard.GROWTH) {
                items(snapshot?.months.orEmpty(), key = { it.key }) { month ->
                    val peak = snapshot?.months.orEmpty()
                        .maxOfOrNull { it.bytes }?.coerceAtLeast(1L) ?: 1L
                    GroupedCard {
                        Row(
                            Modifier.fillMaxWidth().padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    month.label,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = fsColors.label,
                                )
                                Text(
                                    "${Formatters.compactCount(month.count)} files",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = fsColors.secondaryLabel,
                                )
                                Spacer(Modifier.height(6.dp))
                                Box(
                                    Modifier
                                        .fillMaxWidth()
                                        .height(6.dp)
                                        .clip(RoundedCornerShape(3.dp))
                                        .background(fsColors.fill),
                                ) {
                                    Box(
                                        Modifier
                                            .fillMaxWidth(
                                                (month.bytes.toFloat() / peak).coerceIn(0.02f, 1f)
                                            )
                                            .fillMaxHeight()
                                            .clip(RoundedCornerShape(3.dp))
                                            .background(
                                                Brush.horizontalGradient(
                                                    listOf(fsColors.accent, fsColors.green)
                                                )
                                            ),
                                    )
                                }
                            }
                            Spacer(Modifier.width(12.dp))
                            Text(
                                Formatters.bytes(month.bytes),
                                style = MaterialTheme.typography.titleMedium,
                                color = fsColors.label,
                            )
                        }
                    }
                }
            }

            if (card == DashboardCard.RECENT) {
                val files = snapshot?.recent.orEmpty()
                items(files, key = { it.path }) { file ->
                    GroupedCard {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .pressScale {
                                    val media = files.filter {
                                        val kind = FsEntry.kindOf(it.name, false)
                                        kind == FileKind.IMAGE || kind == FileKind.VIDEO
                                    }
                                    val at = media.indexOfFirst { it.path == file.path }
                                    if (at >= 0) onOpenViewer(media.map { it.path }, at)
                                    else openFile(context, FsEntry.from(File(file.path)))
                                }
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            FileIconView(FsEntry.from(File(file.path)), size = 42.dp)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    file.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = fsColors.label,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    prettyPath(file.folder),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = fsColors.secondaryLabel,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            Spacer(Modifier.width(10.dp))
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    Formatters.bytes(file.size),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = fsColors.label,
                                )
                                Text(
                                    Formatters.fileDate(file.modified),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = fsColors.secondaryLabel,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Ranked rows, numbered, so position is readable without counting. */
private fun androidx.compose.foundation.lazy.LazyListScope.itemsIndexedSlices(slices: List<Slice>) {
    val biggest = slices.firstOrNull()?.bytes?.coerceAtLeast(1L) ?: 1L
    items(slices.size, key = { "${it}_${slices[it].title}" }) { index ->
        val slice = slices[index]
        GroupedCard {
            Row(
                Modifier.fillMaxWidth().pressScale(slice.onClick).padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(30.dp)
                        .clip(RoundedCornerShape(9.dp))
                        .background(sliceColorFor(index)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "${index + 1}",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = androidx.compose.ui.graphics.Color.White,
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        slice.title,
                        style = MaterialTheme.typography.bodyMedium,
                        color = fsColors.label,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        slice.subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = fsColors.secondaryLabel,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(6.dp))
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(5.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(fsColors.fill),
                    ) {
                        Box(
                            Modifier
                                .fillMaxWidth((slice.bytes.toFloat() / biggest).coerceIn(0.02f, 1f))
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(3.dp))
                                .background(
                                    Brush.horizontalGradient(
                                        listOf(fsColors.accent, fsColors.green)
                                    )
                                ),
                        )
                    }
                }
                Spacer(Modifier.width(12.dp))
                Text(
                    Formatters.bytes(slice.bytes),
                    style = MaterialTheme.typography.titleMedium,
                    color = fsColors.label,
                )
            }
        }
    }
}

/**
 * Rank badge colour. Deliberately not [sliceColor], which is @Composable and so
 * cannot be called from a LazyListScope lambda.
 */
private fun sliceColorFor(index: Int): androidx.compose.ui.graphics.Color {
    val wheel = listOf(
        androidx.compose.ui.graphics.Color(0xFF007AFF),
        androidx.compose.ui.graphics.Color(0xFF34C759),
        androidx.compose.ui.graphics.Color(0xFFAF52DE),
        androidx.compose.ui.graphics.Color(0xFFFF9500),
        androidx.compose.ui.graphics.Color(0xFF5856D6),
        androidx.compose.ui.graphics.Color(0xFF30B0C7),
        androidx.compose.ui.graphics.Color(0xFFFF2D55),
    )
    return wheel[index % wheel.size]
}

package com.shahabcodes.filestorm.ui.home

import com.shahabcodes.filestorm.ui.components.FsSpinner
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.shahabcodes.filestorm.data.FileKind
import com.shahabcodes.filestorm.data.FileRepository
import com.shahabcodes.filestorm.data.AppStorageAnalyzer
import com.shahabcodes.filestorm.data.StorageAnalyzer
import com.shahabcodes.filestorm.ui.components.GroupedCard
import com.shahabcodes.filestorm.ui.components.kindColor
import com.shahabcodes.filestorm.ui.components.pressScale
import com.shahabcodes.filestorm.ui.theme.fsColors
import com.shahabcodes.filestorm.util.Formatters

private val kindLabels = mapOf(
    FileKind.IMAGE to "Images",
    FileKind.VIDEO to "Videos",
    FileKind.AUDIO to "Audio",
    FileKind.DOCUMENT to "Documents",
    FileKind.ARCHIVE to "Archives",
    FileKind.APK to "APKs",
    FileKind.OTHER to "Other",
)

/** Colourful storage dashboard: donut of used/free, category bar, per-type stats. */
@Composable
fun DashboardCard() {
    val stats = FileRepository.storageStats()
    val snapshot = StorageAnalyzer.snapshot
    val scanning = StorageAnalyzer.scanning

    // First composition with no cached data → kick off a scan automatically.
    LaunchedEffect(Unit) {
        if (snapshot == null || System.currentTimeMillis() - snapshot.scannedAt > 6 * 60 * 60 * 1000) {
            StorageAnalyzer.refresh()
        }
    }

    GroupedCard {
        Column(Modifier.fillMaxWidth().padding(18.dp)) {
            // Header
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Storage",
                    style = MaterialTheme.typography.titleLarge,
                    color = fsColors.label,
                    modifier = Modifier.weight(1f),
                )
                if (scanning) {
                    com.shahabcodes.filestorm.ui.components.FsSpinner(
                        size = 20.dp,
                        strokeWidth = 2.5.dp,
                    )
                } else {
                    Icon(
                        Icons.Rounded.Refresh, "Rescan",
                        tint = fsColors.accent,
                        modifier = Modifier
                            .pressScale { StorageAnalyzer.refresh() }
                            .padding(4.dp)
                            .size(18.dp),
                    )
                }
            }
            Spacer(Modifier.height(14.dp))

            // Donut + used/free
            Row(verticalAlignment = Alignment.CenterVertically) {
                val usedFraction by animateFloatAsState(
                    stats.usedFraction.coerceIn(0.02f, 1f), tween(700), label = "df",
                )
                val track = fsColors.fill
                val accent = fsColors.accent
                val green = fsColors.green
                Box(contentAlignment = Alignment.Center) {
                    Canvas(Modifier.size(96.dp)) {
                        val stroke = Stroke(width = 11.dp.toPx(), cap = StrokeCap.Round)
                        drawArc(color = track, startAngle = -90f, sweepAngle = 360f, useCenter = false, style = stroke)
                        drawArc(
                            brush = androidx.compose.ui.graphics.Brush.sweepGradient(
                                listOf(accent, green, accent)
                            ),
                            startAngle = -90f,
                            sweepAngle = 360f * usedFraction,
                            useCenter = false,
                            style = stroke,
                        )
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "${(stats.usedFraction * 100).toInt()}%",
                            style = MaterialTheme.typography.titleLarge,
                            color = fsColors.label,
                        )
                        Text("used", style = MaterialTheme.typography.labelSmall, color = fsColors.secondaryLabel)
                    }
                }
                Spacer(Modifier.width(18.dp))
                Column {
                    LegendRow(fsColors.accent, "Used", Formatters.bytes(stats.usedBytes))
                    Spacer(Modifier.height(6.dp))
                    LegendRow(fsColors.green, "Free", Formatters.bytes(stats.freeBytes))
                    Spacer(Modifier.height(6.dp))
                    LegendRow(fsColors.secondaryLabel, "Total", Formatters.bytes(stats.totalBytes))
                    snapshot?.let {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "${it.totalFiles} files scanned · ${Formatters.fileDate(it.scannedAt)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = fsColors.secondaryLabel.copy(alpha = 0.7f),
                        )
                    }
                }
            }

            if (snapshot != null && snapshot.totalCategorizedBytes > 0) {
                Spacer(Modifier.height(16.dp))

                // Stacked category bar
                val total = snapshot.totalCategorizedBytes.toFloat()
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(14.dp)
                        .clip(RoundedCornerShape(7.dp)),
                ) {
                    snapshot.stats.filter { it.bytes > 0 }.forEach { stat ->
                        Box(
                            Modifier
                                .weight((stat.bytes / total).coerceAtLeast(0.015f))
                                .height(14.dp)
                                .background(kindColor(stat.kind, fsColors)),
                        )
                    }
                }
                Spacer(Modifier.height(14.dp))

                // Per-category stats, two columns
                val rows = snapshot.stats.filter { it.count > 0 }.chunked(2)
                rows.forEach { pair ->
                    Row(Modifier.fillMaxWidth()) {
                        pair.forEach { stat ->
                            Row(
                                Modifier.weight(1f).padding(vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(
                                    Modifier
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(kindColor(stat.kind, fsColors)),
                                )
                                Spacer(Modifier.width(8.dp))
                                Column {
                                    Text(
                                        kindLabels[stat.kind] ?: stat.kind.name,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = fsColors.label,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        "${stat.count} files · ${Formatters.bytes(stat.bytes)}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = fsColors.secondaryLabel,
                                        maxLines = 1,
                                    )
                                }
                            }
                        }
                        if (pair.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            } else if (scanning) {
                Spacer(Modifier.height(14.dp))
                Text(
                    "Analyzing your files…",
                    style = MaterialTheme.typography.bodySmall,
                    color = fsColors.secondaryLabel,
                )
            }
        }
    }
}

@Composable
private fun LegendRow(color: Color, label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(9.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(8.dp))
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = fsColors.secondaryLabel,
            modifier = Modifier.width(44.dp),
        )
        Text(value, style = MaterialTheme.typography.bodySmall, color = fsColors.label)
    }
}

/**
 * What the installed apps are using. Android gates these numbers behind Usage
 * Access, so when that has not been granted the card explains why it is empty
 * and offers the one tap that fixes it.
 */
@Composable
fun AppStorageCard(onSeeAll: () -> Unit = {}) {
    val context = LocalContext.current
    val snapshot = AppStorageAnalyzer.snapshot
    val scanning = AppStorageAnalyzer.scanning
    // Recheck on every return to the screen, since the grant happens in Settings.
    var granted by remember { mutableStateOf(AppStorageAnalyzer.hasPermission(context)) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                granted = AppStorageAnalyzer.hasPermission(context)
                if (granted && AppStorageAnalyzer.snapshot == null) {
                    AppStorageAnalyzer.refresh(context)
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(granted) {
        val stale = snapshot == null ||
            System.currentTimeMillis() - snapshot.scannedAt > 6 * 60 * 60 * 1000
        if (granted && stale) AppStorageAnalyzer.refresh(context)
    }

    GroupedCard {
        Column(Modifier.fillMaxWidth().padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Apps",
                    style = MaterialTheme.typography.titleLarge,
                    color = fsColors.label,
                    modifier = Modifier.weight(1f),
                )
                if (scanning) {
                    com.shahabcodes.filestorm.ui.components.FsSpinner(size = 20.dp, strokeWidth = 2.5.dp)
                } else if (granted) {
                    Icon(
                        Icons.Rounded.Refresh, "Rescan apps",
                        tint = fsColors.accent,
                        modifier = Modifier
                            .pressScale { AppStorageAnalyzer.refresh(context) }
                            .padding(4.dp)
                            .size(18.dp),
                    )
                }
            }
            Spacer(Modifier.height(6.dp))

            if (!granted) {
                Text(
                    "Android keeps per-app storage behind a separate permission. Grant File " +
                        "Storm usage access and this card will show what every app, its data " +
                        "and its cache are taking up.",
                    style = MaterialTheme.typography.bodySmall,
                    color = fsColors.secondaryLabel,
                )
                Spacer(Modifier.height(12.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(fsColors.accent)
                        .pressScale { AppStorageAnalyzer.requestPermission(context) }
                        .padding(vertical = 11.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "Allow Usage Access",
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    "Find File Storm in the list that opens and turn it on.",
                    style = MaterialTheme.typography.labelSmall,
                    color = fsColors.secondaryLabel,
                )
            } else if (snapshot == null) {
                Text(
                    if (scanning) "Measuring installed apps…" else "No app data yet — tap refresh.",
                    style = MaterialTheme.typography.bodySmall,
                    color = fsColors.secondaryLabel,
                )
            } else {
                Text(
                    Formatters.bytes(snapshot.totalBytes),
                    style = MaterialTheme.typography.headlineMedium,
                    color = fsColors.label,
                )
                Text(
                    "${snapshot.appCount} apps · app, data and cache combined",
                    style = MaterialTheme.typography.bodySmall,
                    color = fsColors.secondaryLabel,
                )
                Spacer(Modifier.height(14.dp))

                // Where the app storage actually goes.
                val segments = listOf(
                    Triple("App", snapshot.codeBytes, fsColors.accent),
                    Triple("Data", snapshot.dataBytes, fsColors.orange),
                    Triple("Cache", snapshot.cacheBytes, fsColors.green),
                )
                val total = snapshot.totalBytes.coerceAtLeast(1L)
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(12.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(fsColors.fill),
                ) {
                    segments.forEach { (_, bytes, color) ->
                        val weight = (bytes.toFloat() / total).coerceAtLeast(0.0001f)
                        Box(Modifier.weight(weight).fillMaxHeight().background(color))
                    }
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    segments.forEach { (label, bytes, color) ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier
                                    .size(8.dp)
                                    .clip(RoundedCornerShape(50))
                                    .background(color)
                            )
                            Spacer(Modifier.width(5.dp))
                            Text(
                                "$label ${Formatters.bytes(bytes)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = fsColors.secondaryLabel,
                            )
                        }
                    }
                }

                val top = snapshot.apps.take(6)
                if (top.isNotEmpty()) {
                    Spacer(Modifier.height(16.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "Biggest apps",
                            style = MaterialTheme.typography.labelMedium,
                            color = fsColors.secondaryLabel,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            "See all ${snapshot.appCount}",
                            style = MaterialTheme.typography.labelMedium,
                            color = fsColors.accent,
                            modifier = Modifier.pressScale(onSeeAll).padding(4.dp),
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    val biggest = top.first().totalBytes.coerceAtLeast(1L)
                    top.forEachIndexed { index, app ->
                        if (index > 0) Spacer(Modifier.height(9.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    app.label,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = fsColors.label,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Spacer(Modifier.height(4.dp))
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
                                                (app.totalBytes.toFloat() / biggest)
                                                    .coerceIn(0.02f, 1f)
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
                                Formatters.bytes(app.totalBytes),
                                style = MaterialTheme.typography.labelMedium,
                                color = fsColors.secondaryLabel,
                            )
                        }
                    }
                }
            }
        }
    }
}

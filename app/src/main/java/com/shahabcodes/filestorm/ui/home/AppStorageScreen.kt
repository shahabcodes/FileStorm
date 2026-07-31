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
import androidx.compose.material.icons.automirrored.rounded.ArrowBackIos
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.shahabcodes.filestorm.data.AppStorageAnalyzer
import com.shahabcodes.filestorm.ui.components.GroupedCard
import com.shahabcodes.filestorm.ui.components.IosSearchField
import com.shahabcodes.filestorm.ui.components.pressScale
import com.shahabcodes.filestorm.ui.theme.fsColors
import com.shahabcodes.filestorm.util.Formatters

private enum class AppSort(val label: String) {
    SIZE("Size"),
    NAME("Name"),
    CACHE("Cache"),
}

/** The full per-app storage list: every app, searchable and sortable. */
@Composable
fun AppStorageScreen(onBack: () -> Unit) {
    val snapshot = AppStorageAnalyzer.snapshot
    var query by remember { mutableStateOf("") }
    var sort by remember { mutableStateOf(AppSort.SIZE) }
    var showSystem by remember { mutableStateOf(true) }

    val all = snapshot?.apps.orEmpty()
    val visible = remember(all, query, sort, showSystem) {
        val trimmed = query.trim()
        all
            .filter { showSystem || !it.isSystem }
            .filter { trimmed.isBlank() || it.label.contains(trimmed, ignoreCase = true) }
            .let { list ->
                when (sort) {
                    AppSort.SIZE -> list.sortedByDescending { it.totalBytes }
                    AppSort.NAME -> list.sortedBy { it.label.lowercase() }
                    AppSort.CACHE -> list.sortedByDescending { it.cacheBytes }
                }
            }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(fsColors.groupedBackground)
            .statusBarsPadding(),
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
            "All Apps",
            style = MaterialTheme.typography.headlineLarge,
            color = fsColors.label,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        Text(
            "${visible.size} of ${all.size} apps · " +
                Formatters.bytes(visible.sumOf { it.totalBytes }),
            style = MaterialTheme.typography.bodySmall,
            color = fsColors.secondaryLabel,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )
        Spacer(Modifier.height(8.dp))
        IosSearchField(
            value = query,
            onValueChange = { query = it },
            placeholder = "Search apps",
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        Spacer(Modifier.height(10.dp))
        Row(
            Modifier.padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppSort.entries.forEach { option ->
                Chip(option.label, sort == option) { sort = option }
            }
            Spacer(Modifier.weight(1f))
            Chip("System", showSystem) { showSystem = !showSystem }
        }
        Spacer(Modifier.height(12.dp))

        if (all.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "No app data yet. Open the dashboard and grant usage access.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = fsColors.secondaryLabel,
                    modifier = Modifier.padding(32.dp),
                )
            }
        } else {
            val biggest = visible.maxOfOrNull { it.totalBytes }?.coerceAtLeast(1L) ?: 1L
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    start = 16.dp, end = 16.dp, bottom = 40.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(visible, key = { it.packageName }) { app ->
                    GroupedCard {
                        Column(Modifier.fillMaxWidth().padding(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        app.label,
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = fsColors.label,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        app.packageName +
                                            if (app.isSystem) " · system" else "",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = fsColors.secondaryLabel,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    Formatters.bytes(app.totalBytes),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = fsColors.label,
                                )
                            }
                            Spacer(Modifier.height(8.dp))
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
                                            (app.totalBytes.toFloat() / biggest).coerceIn(0.02f, 1f)
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
                            Spacer(Modifier.height(7.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Legend("App", app.appBytes, fsColors.accent)
                                Legend("Data", app.dataBytes, fsColors.orange)
                                Legend("Cache", app.cacheBytes, fsColors.green)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Chip(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) fsColors.accent else fsColors.fill)
            .pressScale(onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (selected) {
            Icon(Icons.Rounded.Check, null, tint = Color.White, modifier = Modifier.size(13.dp))
            Spacer(Modifier.width(4.dp))
        }
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) Color.White else fsColors.secondaryLabel,
        )
    }
}

@Composable
private fun Legend(label: String, bytes: Long, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(7.dp).clip(RoundedCornerShape(50)).background(color))
        Spacer(Modifier.width(4.dp))
        Text(
            "$label ${Formatters.bytes(bytes)}",
            style = MaterialTheme.typography.labelSmall,
            color = fsColors.secondaryLabel,
        )
    }
}

package com.shahabcodes.filestorm.ui.meta

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.shahabcodes.filestorm.data.FsEntry
import com.shahabcodes.filestorm.data.meta.FilenameDate
import com.shahabcodes.filestorm.data.meta.MetadataEditor
import com.shahabcodes.filestorm.ui.components.FileIconView
import com.shahabcodes.filestorm.ui.components.GroupedCard
import com.shahabcodes.filestorm.ui.components.RowSeparator
import com.shahabcodes.filestorm.ui.components.pressScale
import com.shahabcodes.filestorm.ui.theme.fsColors
import com.shahabcodes.filestorm.util.Formatters
import kotlinx.coroutines.launch
import java.util.Calendar

/** Single-file date editor: shows what is there now, what the filename says, and what will be written. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MetadataSheet(entry: FsEntry, onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val file = remember(entry.path) { entry.toFile() }
    val current = remember(entry.path) { MetadataEditor.read(file) }
    val detected = remember(entry.name) { FilenameDate.parse(entry.name) }

    var chosen by remember {
        mutableStateOf(detected?.millis ?: current.exifDate ?: current.fileDate)
    }
    var writeExif by remember { mutableStateOf(current.exifWritable) }
    var writeVideo by remember { mutableStateOf(current.videoWritable) }
    var writeFileDate by remember { mutableStateOf(true) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    var confirming by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var outcome by remember { mutableStateOf<MetadataEditor.Outcome?>(null) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = fsColors.groupedBackground,
    ) {
        Column(
            Modifier
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                FileIconView(entry, size = 46.dp, cornerRadius = 12.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        "Edit date",
                        style = MaterialTheme.typography.titleLarge,
                        color = fsColors.label,
                    )
                    Text(
                        entry.name,
                        style = MaterialTheme.typography.bodySmall,
                        color = fsColors.secondaryLabel,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(Modifier.height(16.dp))

            // Current values
            GroupedCard {
                if (current.isVideo) {
                    InfoRow(
                        "Video created",
                        current.videoDate?.let { Formatters.fullDate(it) } ?: "Missing",
                        valueColor = if (current.videoDate == null) fsColors.orange else fsColors.label,
                    )
                } else {
                    InfoRow(
                        "Photo taken (EXIF)",
                        current.exifDate?.let { Formatters.fullDate(it) }
                            ?: if (current.isImage) "Missing" else "Not applicable",
                        valueColor = if (current.exifDate == null && current.isImage) fsColors.orange else fsColors.label,
                    )
                }
                RowSeparator(startIndent = 16.dp)
                InfoRow("File modified", Formatters.fullDate(current.fileDate))
            }
            Spacer(Modifier.height(14.dp))

            // Detected from filename
            if (detected != null) {
                GroupedCard {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .pressScale { chosen = detected.millis }
                            .padding(horizontal = 16.dp, vertical = 13.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Rounded.AutoAwesome, null,
                            tint = fsColors.green, modifier = Modifier.size(20.dp),
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                "Found in filename",
                                style = MaterialTheme.typography.labelSmall,
                                color = fsColors.secondaryLabel,
                            )
                            Text(
                                Formatters.fullDate(detected.millis),
                                style = MaterialTheme.typography.bodyMedium,
                                color = fsColors.label,
                            )
                            Text(
                                detected.source + if (!detected.hasTime) " · no time in name, noon assumed" else "",
                                style = MaterialTheme.typography.labelSmall,
                                color = fsColors.secondaryLabel,
                            )
                        }
                        if (chosen == detected.millis) {
                            Icon(
                                Icons.Rounded.CheckCircle, null,
                                tint = fsColors.green, modifier = Modifier.size(20.dp),
                            )
                        } else {
                            Text("Use", color = fsColors.accent, style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
                Spacer(Modifier.height(14.dp))
            } else {
                GroupedCard {
                    Row(
                        Modifier.padding(horizontal = 16.dp, vertical = 13.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Rounded.Error, null,
                            tint = fsColors.orange, modifier = Modifier.size(20.dp),
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            "No date found in the filename — set one manually below.",
                            style = MaterialTheme.typography.bodySmall,
                            color = fsColors.secondaryLabel,
                        )
                    }
                }
                Spacer(Modifier.height(14.dp))
            }

            // New value
            Text(
                "NEW DATE",
                style = MaterialTheme.typography.labelMedium,
                color = fsColors.secondaryLabel,
                modifier = Modifier.padding(start = 16.dp, bottom = 8.dp),
            )
            GroupedCard {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .pressScale { showDatePicker = true }
                        .padding(horizontal = 16.dp, vertical = 13.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.CalendarMonth, null, tint = fsColors.accent, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(12.dp))
                    Text("Date", style = MaterialTheme.typography.bodyLarge, color = fsColors.label, modifier = Modifier.weight(1f))
                    Text(Formatters.shortDate(chosen), color = fsColors.accent, style = MaterialTheme.typography.bodyMedium)
                }
                RowSeparator(startIndent = 16.dp)
                Row(
                    Modifier
                        .fillMaxWidth()
                        .pressScale { showTimePicker = true }
                        .padding(horizontal = 16.dp, vertical = 13.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.Schedule, null, tint = fsColors.accent, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(12.dp))
                    Text("Time", style = MaterialTheme.typography.bodyLarge, color = fsColors.label, modifier = Modifier.weight(1f))
                    Text(timeLabel(chosen), color = fsColors.accent, style = MaterialTheme.typography.bodyMedium)
                }
            }
            Spacer(Modifier.height(14.dp))

            WriteOptions(
                isVideo = current.isVideo,
                writeExif = writeExif,
                onWriteExifChange = { writeExif = it },
                exifSupported = current.exifWritable,
                writeVideo = writeVideo,
                onWriteVideoChange = { writeVideo = it },
                videoSupported = current.videoWritable,
                writeFileDate = writeFileDate,
                onWriteFileDateChange = { writeFileDate = it },
            )
            Spacer(Modifier.height(18.dp))

            val enabled = (writeExif && current.exifWritable) ||
                (writeVideo && current.videoWritable) || writeFileDate
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (enabled) fsColors.accent else fsColors.fill)
                    .pressScale { if (enabled) confirming = true }
                    .padding(vertical = 14.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "Review & Save",
                    color = if (enabled) Color.White else fsColors.secondaryLabel,
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        }
    }

    if (showDatePicker) {
        DatePickerSheet(
            initial = chosen,
            onDismiss = { showDatePicker = false },
            onPicked = { millis ->
                showDatePicker = false
                chosen = mergeDate(chosen, millis)
            },
        )
    }

    if (showTimePicker) {
        TimePickerDialog(
            initial = chosen,
            onDismiss = { showTimePicker = false },
            onPicked = { hour, minute ->
                showTimePicker = false
                chosen = mergeTime(chosen, hour, minute)
            },
        )
    }

    if (confirming) {
        AlertDialog(
            onDismissRequest = { confirming = false },
            containerColor = fsColors.card,
            title = { Text("Save these changes?", color = fsColors.label) },
            text = {
                Column {
                    Text(entry.name, color = fsColors.label, style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(10.dp))
                    if (writeExif && current.exifWritable) {
                        ChangeLine(
                            "Photo taken",
                            current.exifDate?.let { Formatters.fullDate(it) } ?: "Missing",
                            Formatters.fullDate(chosen),
                        )
                    }
                    if (writeVideo && current.videoWritable) {
                        ChangeLine(
                            "Video created",
                            current.videoDate?.let { Formatters.fullDate(it) } ?: "Missing",
                            Formatters.fullDate(chosen),
                        )
                    }
                    if (writeFileDate) {
                        ChangeLine(
                            "File modified",
                            Formatters.fullDate(current.fileDate),
                            Formatters.fullDate(chosen),
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "The file is rewritten in place. This cannot be undone.",
                        style = MaterialTheme.typography.bodySmall,
                        color = fsColors.secondaryLabel,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    confirming = false
                    scope.launch {
                        busy = true
                        outcome = MetadataEditor.apply(
                            context,
                            listOf(MetadataEditor.Change(file, chosen, detected?.source ?: "manual")),
                            writeExif = writeExif,
                            writeFileDate = writeFileDate,
                            writeVideoMeta = writeVideo,
                        )
                        busy = false
                    }
                }) { Text("Save", color = fsColors.accent) }
            },
            dismissButton = {
                TextButton(onClick = { confirming = false }) { Text("Cancel", color = fsColors.secondaryLabel) }
            },
        )
    }

    if (busy) BusyDialog("Saving…")

    outcome?.let { result ->
        AlertDialog(
            onDismissRequest = {
                outcome = null
                onDismiss()
            },
            containerColor = fsColors.card,
            title = {
                Text(
                    if (result.failed == 0) "Date updated" else "Could not update",
                    color = fsColors.label,
                )
            },
            text = {
                Text(
                    if (result.failed == 0)
                        "Saved successfully." +
                            (if (result.exifWritten > 0) " EXIF metadata was rewritten." else "") +
                            (if (result.videoWritten > 0) " Video creation time was rewritten." else "")
                    else result.errors.firstOrNull() ?: "Unknown error",
                    color = fsColors.secondaryLabel,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    outcome = null
                    onDismiss()
                }) { Text("Done", color = fsColors.accent) }
            },
        )
    }
}

/** Batch editor: recovers dates from filenames for every selected file. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BatchDateSheet(entries: List<FsEntry>, onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val files = remember(entries) { entries.filter { !it.isDirectory } }
    val matched = remember(files) {
        files.mapNotNull { entry ->
            FilenameDate.parse(entry.name)?.let { parsed ->
                MetadataEditor.Change(entry.toFile(), parsed.millis, parsed.source) to parsed.hasTime
            }
        }
    }
    val unmatched = files.size - matched.size

    val photoCount = remember(matched) {
        matched.count { !com.shahabcodes.filestorm.data.meta.Mp4Meta.isSupported(it.first.file) }
    }
    val videoCount = matched.size - photoCount

    var writeExif by remember { mutableStateOf(true) }
    var writeVideo by remember { mutableStateOf(true) }
    var writeFileDate by remember { mutableStateOf(true) }
    var confirming by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf<MetadataEditor.Progress?>(null) }
    var outcome by remember { mutableStateOf<MetadataEditor.Outcome?>(null) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = fsColors.groupedBackground,
    ) {
        Column(
            Modifier
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
        ) {
            Text(
                "Recover dates from filenames",
                style = MaterialTheme.typography.titleLarge,
                color = fsColors.label,
            )
            Text(
                "${matched.size} of ${files.size} selected file(s) have a date in their name" +
                    if (unmatched > 0) " · $unmatched will be skipped" else "",
                style = MaterialTheme.typography.bodySmall,
                color = fsColors.secondaryLabel,
            )
            if (matched.isNotEmpty()) {
                Text(
                    "$photoCount photo(s) · $videoCount video(s)",
                    style = MaterialTheme.typography.labelSmall,
                    color = fsColors.secondaryLabel.copy(alpha = 0.8f),
                )
            }
            Spacer(Modifier.height(14.dp))

            if (matched.isNotEmpty()) {
                GroupedCard {
                    matched.take(40).forEachIndexed { index, (change, hasTime) ->
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    change.file.name,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = fsColors.label,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    Formatters.fullDate(change.newMillis) +
                                        if (!hasTime) " (noon)" else "",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = fsColors.green,
                                )
                            }
                        }
                        if (index != matched.take(40).lastIndex) RowSeparator(startIndent = 14.dp)
                    }
                    if (matched.size > 40) {
                        Text(
                            "…and ${matched.size - 40} more",
                            style = MaterialTheme.typography.labelSmall,
                            color = fsColors.secondaryLabel,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
                        )
                    }
                }
                Spacer(Modifier.height(14.dp))

                WriteOptions(
                    writeExif = writeExif,
                    onWriteExifChange = { writeExif = it },
                    exifSupported = photoCount > 0,
                    writeVideo = writeVideo,
                    onWriteVideoChange = { writeVideo = it },
                    videoSupported = videoCount > 0,
                    writeFileDate = writeFileDate,
                    onWriteFileDateChange = { writeFileDate = it },
                    mixed = true,
                )
                Spacer(Modifier.height(18.dp))

                val enabled = writeExif || writeVideo || writeFileDate
                Box(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (enabled) fsColors.accent else fsColors.fill)
                        .pressScale { if (enabled) confirming = true }
                        .padding(vertical = 14.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "Review & Update ${matched.size} File(s)",
                        color = if (enabled) Color.White else fsColors.secondaryLabel,
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            } else {
                GroupedCard {
                    Row(
                        Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Rounded.Error, null, tint = fsColors.orange)
                        Spacer(Modifier.width(12.dp))
                        Text(
                            "None of the selected files have a recognisable date in their filename.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = fsColors.label,
                        )
                    }
                }
            }
        }
    }

    if (confirming) {
        AlertDialog(
            onDismissRequest = { confirming = false },
            containerColor = fsColors.card,
            title = { Text("Update ${matched.size} file(s)?", color = fsColors.label) },
            text = {
                Column {
                    Text(
                        buildString {
                            if (writeExif && photoCount > 0) {
                                append("• EXIF taken-date written for $photoCount photo(s)\n")
                            }
                            if (writeVideo && videoCount > 0) {
                                append("• Creation time written for $videoCount video(s)\n")
                            }
                            if (writeFileDate) append("• File modified date set for all ${matched.size}\n")
                            if (unmatched > 0) append("• $unmatched file(s) without a date in the name stay untouched\n")
                        }.trim(),
                        color = fsColors.secondaryLabel,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "Files are rewritten in place. This cannot be undone.",
                        style = MaterialTheme.typography.bodySmall,
                        color = fsColors.secondaryLabel,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    confirming = false
                    scope.launch {
                        outcome = MetadataEditor.apply(
                            context,
                            matched.map { it.first },
                            writeExif = writeExif,
                            writeFileDate = writeFileDate,
                            writeVideoMeta = writeVideo,
                            onProgress = { p -> progress = p },
                        )
                        progress = null
                    }
                }) { Text("Update", color = fsColors.accent) }
            },
            dismissButton = {
                TextButton(onClick = { confirming = false }) { Text("Cancel", color = fsColors.secondaryLabel) }
            },
        )
    }

    progress?.let { DetailedProgressDialog(it) }

    outcome?.let { result ->
        AlertDialog(
            onDismissRequest = {
                outcome = null
                onDismiss()
            },
            containerColor = fsColors.card,
            title = { Text("Dates updated", color = fsColors.label) },
            text = {
                Column {
                    Text(
                        "${result.succeeded} file(s) updated" +
                            (if (result.exifWritten > 0) " · ${result.exifWritten} EXIF rewritten" else "") +
                            (if (result.videoWritten > 0) " · ${result.videoWritten} video header(s) rewritten" else "") +
                            (if (result.failed > 0) " · ${result.failed} failed" else ""),
                        color = fsColors.label,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    if (result.errors.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        result.errors.take(5).forEach {
                            Text(it, color = fsColors.red, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    outcome = null
                    onDismiss()
                }) { Text("Done", color = fsColors.accent) }
            },
        )
    }
}

@Composable
private fun WriteOptions(
    writeExif: Boolean,
    onWriteExifChange: (Boolean) -> Unit,
    exifSupported: Boolean,
    writeFileDate: Boolean,
    onWriteFileDateChange: (Boolean) -> Unit,
    isVideo: Boolean = false,
    writeVideo: Boolean = false,
    onWriteVideoChange: (Boolean) -> Unit = {},
    videoSupported: Boolean = false,
    mixed: Boolean = false,
) {
    GroupedCard {
        if (!isVideo || mixed) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Write EXIF date", style = MaterialTheme.typography.bodyLarge, color = fsColors.label)
                    Text(
                        if (exifSupported) "Photos: sets the taken date galleries read"
                        else "Not supported for this file type",
                        style = MaterialTheme.typography.bodySmall,
                        color = fsColors.secondaryLabel,
                    )
                }
                Switch(
                    checked = writeExif && exifSupported,
                    enabled = exifSupported,
                    onCheckedChange = onWriteExifChange,
                    colors = switchColors(),
                )
            }
            RowSeparator(startIndent = 16.dp)
        }
        if (isVideo || mixed) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Write video creation time", style = MaterialTheme.typography.bodyLarge, color = fsColors.label)
                    Text(
                        if (videoSupported) "Videos: patches MP4/MOV headers, no re-encoding"
                        else "Not supported for this file type",
                        style = MaterialTheme.typography.bodySmall,
                        color = fsColors.secondaryLabel,
                    )
                }
                Switch(
                    checked = writeVideo && videoSupported,
                    enabled = videoSupported,
                    onCheckedChange = onWriteVideoChange,
                    colors = switchColors(),
                )
            }
            RowSeparator(startIndent = 16.dp)
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("Update file date", style = MaterialTheme.typography.bodyLarge, color = fsColors.label)
                Text(
                    "Used by sorting, monthly jobs and most file managers",
                    style = MaterialTheme.typography.bodySmall,
                    color = fsColors.secondaryLabel,
                )
            }
            Switch(
                checked = writeFileDate,
                onCheckedChange = onWriteFileDateChange,
                colors = switchColors(),
            )
        }
    }
}

@Composable
private fun switchColors() = SwitchDefaults.colors(
    checkedTrackColor = fsColors.green,
    checkedThumbColor = Color.White,
    uncheckedThumbColor = Color.White,
    uncheckedTrackColor = fsColors.fill,
    uncheckedBorderColor = Color.Transparent,
)

@Composable
private fun InfoRow(label: String, value: String, valueColor: Color = fsColors.label) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = fsColors.secondaryLabel,
            modifier = Modifier.weight(1f),
        )
        Text(value, style = MaterialTheme.typography.bodyMedium, color = valueColor)
    }
}

@Composable
private fun ChangeLine(label: String, from: String, to: String) {
    Column(Modifier.padding(vertical = 4.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = fsColors.secondaryLabel)
        Text(from, style = MaterialTheme.typography.bodySmall, color = fsColors.secondaryLabel)
        Text("→ $to", style = MaterialTheme.typography.bodySmall, color = fsColors.green)
    }
}

/** Rich progress: percent, bar, current file, per-type counters, elapsed and ETA. */
@Composable
private fun DetailedProgressDialog(p: MetadataEditor.Progress) {
    Dialog(onDismissRequest = {}) {
        Column(
            Modifier
                .clip(RoundedCornerShape(20.dp))
                .background(fsColors.card)
                .padding(20.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Updating dates",
                    style = MaterialTheme.typography.titleMedium,
                    color = fsColors.label,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    "${(p.fraction * 100).toInt()}%",
                    style = MaterialTheme.typography.titleMedium,
                    color = fsColors.accent,
                )
            }
            Spacer(Modifier.height(10.dp))
            androidx.compose.material3.LinearProgressIndicator(
                progress = { p.fraction },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = fsColors.accent,
                trackColor = fsColors.fill,
                strokeCap = androidx.compose.ui.graphics.StrokeCap.Round,
            )
            Spacer(Modifier.height(12.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (p.currentIsVideo) Icons.Rounded.Movie else Icons.Rounded.Image,
                    null,
                    tint = fsColors.secondaryLabel,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    p.currentName,
                    style = MaterialTheme.typography.bodySmall,
                    color = fsColors.label,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(14.dp))

            ProgressStat("Files", "${p.done} of ${p.total}")
            ProgressStat("Data processed", "${Formatters.bytes(p.bytesDone)} of ${Formatters.bytes(p.bytesTotal)}")
            if (p.exifWritten > 0) ProgressStat("EXIF written", "${p.exifWritten}", fsColors.green)
            if (p.videoWritten > 0) ProgressStat("Video headers written", "${p.videoWritten}", fsColors.green)
            if (p.fileDatesSet > 0) ProgressStat("File dates set", "${p.fileDatesSet}", fsColors.green)
            if (p.failed > 0) ProgressStat("Failed", "${p.failed}", fsColors.red)
            ProgressStat("Elapsed", Formatters.eta(p.elapsedSeconds))
            ProgressStat(
                "Time left",
                if (p.etaSeconds >= 0) Formatters.eta(p.etaSeconds) else "calculating…",
            )
        }
    }
}

@Composable
private fun ProgressStat(label: String, value: String, valueColor: Color = fsColors.label) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = fsColors.secondaryLabel,
            modifier = Modifier.weight(1f),
        )
        Text(value, style = MaterialTheme.typography.labelSmall, color = valueColor)
    }
}

@Composable
private fun BusyDialog(label: String) {
    Dialog(onDismissRequest = {}) {
        Column(
            Modifier
                .clip(RoundedCornerShape(18.dp))
                .background(fsColors.card)
                .padding(horizontal = 28.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            CircularProgressIndicator(color = fsColors.accent)
            Spacer(Modifier.height(14.dp))
            Text(label, color = fsColors.label, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DatePickerSheet(initial: Long, onDismiss: () -> Unit, onPicked: (Long) -> Unit) {
    val state = rememberDatePickerState(initialSelectedDateMillis = initial)
    DatePickerDialog(
        onDismissRequest = onDismiss,
        colors = DatePickerDefaults.colors(containerColor = fsColors.card),
        confirmButton = {
            TextButton(onClick = { state.selectedDateMillis?.let(onPicked) }) {
                Text("Set", color = fsColors.accent)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = fsColors.secondaryLabel) }
        },
    ) {
        DatePicker(
            state = state,
            showModeToggle = false,
            colors = DatePickerDefaults.colors(
                containerColor = fsColors.card,
                selectedDayContainerColor = fsColors.accent,
                selectedDayContentColor = Color.White,
            ),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimePickerDialog(
    initial: Long,
    onDismiss: () -> Unit,
    onPicked: (Int, Int) -> Unit,
) {
    val cal = remember(initial) { Calendar.getInstance().apply { timeInMillis = initial } }
    val state = rememberTimePickerState(
        initialHour = cal.get(Calendar.HOUR_OF_DAY),
        initialMinute = cal.get(Calendar.MINUTE),
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = fsColors.card,
        title = { Text("Set time", color = fsColors.label) },
        text = {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                TimePicker(state = state)
            }
        },
        confirmButton = {
            TextButton(onClick = { onPicked(state.hour, state.minute) }) {
                Text("Set", color = fsColors.accent)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = fsColors.secondaryLabel) }
        },
    )
}

/** Keeps the time-of-day from [base] but takes the calendar day from [dayMillis]. */
private fun mergeDate(base: Long, dayMillis: Long): Long {
    val src = Calendar.getInstance().apply { timeInMillis = base }
    // The date picker reports UTC midnight; read the day in UTC to avoid drift.
    val picked = Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC")).apply {
        timeInMillis = dayMillis
    }
    return Calendar.getInstance().apply {
        clear()
        set(
            picked.get(Calendar.YEAR),
            picked.get(Calendar.MONTH),
            picked.get(Calendar.DAY_OF_MONTH),
            src.get(Calendar.HOUR_OF_DAY),
            src.get(Calendar.MINUTE),
            src.get(Calendar.SECOND),
        )
    }.timeInMillis
}

private fun mergeTime(base: Long, hour: Int, minute: Int): Long =
    Calendar.getInstance().apply {
        timeInMillis = base
        set(Calendar.HOUR_OF_DAY, hour)
        set(Calendar.MINUTE, minute)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

private fun timeLabel(millis: Long): String =
    java.text.SimpleDateFormat("h:mm a", java.util.Locale.US).format(java.util.Date(millis))

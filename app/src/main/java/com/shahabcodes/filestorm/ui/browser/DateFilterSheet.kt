package com.shahabcodes.filestorm.ui.browser

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.DateRange
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.shahabcodes.filestorm.data.FsEntry
import com.shahabcodes.filestorm.ui.components.GroupedCard
import com.shahabcodes.filestorm.ui.components.RowSeparator
import com.shahabcodes.filestorm.ui.components.pressScale
import com.shahabcodes.filestorm.ui.theme.fsColors
import com.shahabcodes.filestorm.util.Formatters
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private data class MonthBucket(
    val label: String,
    val count: Int,
    val start: Long,
    val end: Long,
)

/** Lets the folder be narrowed to a month present in it, or to any custom range. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateFilterSheet(
    entries: List<FsEntry>,
    onDismiss: () -> Unit,
    onPick: (ClosedRange<Long>, String) -> Unit,
    onClear: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var customOpen by remember { mutableStateOf(false) }

    val months = remember(entries) {
        val fmt = SimpleDateFormat("MMMM yyyy", Locale.getDefault())
        entries
            .filter { !it.isDirectory && it.lastModified > 0 }
            .groupBy { fmt.format(Date(it.lastModified)) }
            .map { (label, group) ->
                val cal = Calendar.getInstance().apply { timeInMillis = group.first().lastModified }
                val start = Calendar.getInstance().apply {
                    clear()
                    set(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), 1)
                }
                val end = (start.clone() as Calendar).apply { add(Calendar.MONTH, 1) }
                MonthBucket(label, group.size, start.timeInMillis, end.timeInMillis - 1)
            }
            .sortedByDescending { it.start }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = fsColors.groupedBackground,
    ) {
        Column(
            Modifier
                .navigationBarsPadding()
                .padding(horizontal = 16.dp)
                .padding(bottom = 20.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Filter by date",
                    style = MaterialTheme.typography.titleLarge,
                    color = fsColors.label,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onClear) { Text("Show all", color = fsColors.accent) }
            }
            Text(
                "Only files from the chosen period stay visible.",
                style = MaterialTheme.typography.bodySmall,
                color = fsColors.secondaryLabel,
            )
            Spacer(Modifier.height(14.dp))

            GroupedCard {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .pressScale { customOpen = true }
                        .padding(horizontal = 16.dp, vertical = 13.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.DateRange, null, tint = fsColors.accent, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(12.dp))
                    Text(
                        "Custom date range…",
                        style = MaterialTheme.typography.bodyLarge,
                        color = fsColors.accent,
                    )
                }
            }
            Spacer(Modifier.height(14.dp))

            if (months.isNotEmpty()) {
                Text(
                    "MONTHS IN THIS FOLDER",
                    style = MaterialTheme.typography.labelMedium,
                    color = fsColors.secondaryLabel,
                    modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
                )
                LazyColumn(Modifier.height(320.dp)) {
                    itemsIndexed(months) { index, month ->
                        val shape = when {
                            months.size == 1 -> RoundedCornerShape(16.dp)
                            index == 0 -> RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
                            index == months.lastIndex ->
                                RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp)
                            else -> RoundedCornerShape(0.dp)
                        }
                        Column(Modifier.clip(shape).background(fsColors.card)) {
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .pressScale { onPick(month.start..month.end, month.label) }
                                    .padding(horizontal = 16.dp, vertical = 13.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    Icons.Rounded.CalendarMonth, null,
                                    tint = fsColors.accent, modifier = Modifier.size(18.dp),
                                )
                                Spacer(Modifier.width(12.dp))
                                Text(
                                    month.label,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = fsColors.label,
                                    modifier = Modifier.weight(1f),
                                )
                                Text(
                                    "${month.count}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = fsColors.secondaryLabel,
                                )
                            }
                            if (index != months.lastIndex) RowSeparator(startIndent = 46.dp)
                        }
                    }
                }
            }
        }
    }

    if (customOpen) {
        DateRangeSheet(
            onDismiss = { customOpen = false },
            onConfirm = { start, end ->
                customOpen = false
                onPick(
                    start..end,
                    "${Formatters.shortDate(start)} – ${Formatters.shortDate(end)}",
                )
            },
        )
    }
}

package com.shahaabapps.filestorm.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shahaabapps.filestorm.data.Diagnostics

/** Live event trace, shown over everything while diagnostics are enabled. */
@Composable
fun BoxScope.DiagnosticsOverlay(onDisable: () -> Unit) {
    val context = LocalContext.current
    var expanded by remember { mutableStateOf(true) }
    val listState = rememberLazyListState()
    val events = Diagnostics.events

    LaunchedEffect(events.size) {
        if (events.isNotEmpty()) listState.scrollToItem(events.lastIndex)
    }

    Column(
        Modifier
            .align(Alignment.BottomCenter)
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
            .navigationBarsPadding()
            .padding(bottom = 6.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Color.Black.copy(alpha = 0.86f))
            .padding(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Diagnostics · ${events.size}",
                color = Color(0xFF7BEBFF),
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.weight(1f),
            )
            OverlayButton(if (expanded) "Hide" else "Show") { expanded = !expanded }
            Spacer(Modifier.width(6.dp))
            OverlayButton("Copy") {
                copyTrace(context)
            }
            Spacer(Modifier.width(6.dp))
            OverlayButton("Share") { shareTrace(context) }
            Spacer(Modifier.width(6.dp))
            OverlayButton("Clear") { Diagnostics.clear() }
            Spacer(Modifier.width(6.dp))
            OverlayButton("Off", Color(0xFFFF6B6B)) { onDisable() }
        }
        if (expanded) {
            Spacer(Modifier.height(6.dp))
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxWidth().height(190.dp),
            ) {
                items(events) { line ->
                    Text(
                        line,
                        color = when {
                            line.contains("TAP") -> Color(0xFFFFD60A)
                            line.contains("VIEWER") -> Color(0xFF30D158)
                            line.contains("REUSE") || line.contains("MISMATCH") -> Color(0xFFFF6B6B)
                            else -> Color(0xFFDDDDDD)
                        },
                        fontFamily = FontFamily.Monospace,
                        fontSize = 9.sp,
                        lineHeight = 12.sp,
                    )
                }
            }
        }
    }
}

@Composable
private fun OverlayButton(label: String, tint: Color = Color(0xFF7BEBFF), onClick: () -> Unit) {
    Text(
        label,
        color = tint,
        style = MaterialTheme.typography.labelSmall,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(Color.White.copy(alpha = 0.10f))
            .pressScale(onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp),
    )
}

private fun copyTrace(context: Context) {
    val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    manager.setPrimaryClip(ClipData.newPlainText("File Storm diagnostics", Diagnostics.dump()))
    Toast.makeText(context, "Diagnostics copied", Toast.LENGTH_SHORT).show()
}

private fun shareTrace(context: Context) {
    runCatching {
        context.startActivity(
            Intent.createChooser(
                Intent(Intent.ACTION_SEND)
                    .setType("text/plain")
                    .putExtra(Intent.EXTRA_TEXT, Diagnostics.dump()),
                "Share diagnostics",
            )
        )
    }
}

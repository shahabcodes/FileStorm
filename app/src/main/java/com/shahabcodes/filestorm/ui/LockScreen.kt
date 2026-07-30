package com.shahabcodes.filestorm.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.shahabcodes.filestorm.ui.components.pressScale
import com.shahabcodes.filestorm.ui.theme.fsColors

@Composable
fun LockScreen(onRequestUnlock: () -> Unit) {
    // Fire the biometric prompt as soon as the lock screen appears.
    LaunchedEffect(Unit) { onRequestUnlock() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(fsColors.groupedBackground)
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            Modifier
                .size(96.dp)
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(listOf(fsColors.accent, fsColors.accent.copy(alpha = 0.7f)))
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.Lock, null, tint = Color.White, modifier = Modifier.size(44.dp))
        }
        Spacer(Modifier.height(28.dp))
        Text(
            "FileStorm is Locked",
            style = MaterialTheme.typography.headlineMedium,
            color = fsColors.label,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(10.dp))
        Text(
            "Unlock with your fingerprint, face or device PIN to continue.",
            style = MaterialTheme.typography.bodyMedium,
            color = fsColors.secondaryLabel,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(36.dp))
        Box(
            Modifier
                .clip(RoundedCornerShape(14.dp))
                .background(fsColors.accent)
                .pressScale(onRequestUnlock)
                .padding(horizontal = 40.dp, vertical = 14.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text("Unlock", style = MaterialTheme.typography.titleMedium, color = Color.White)
        }
    }
}

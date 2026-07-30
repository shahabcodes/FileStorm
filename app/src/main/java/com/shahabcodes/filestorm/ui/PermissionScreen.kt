package com.shahabcodes.filestorm.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
fun PermissionScreen(onGrantClick: () -> Unit) {
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
            Icon(
                Icons.Rounded.FolderOpen, null,
                tint = Color.White, modifier = Modifier.size(48.dp),
            )
        }
        Spacer(Modifier.height(28.dp))
        Text(
            "Welcome to FileStorm",
            style = MaterialTheme.typography.headlineMedium,
            color = fsColors.label,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            "To browse, organize and move your files, FileStorm needs access to your device storage. Nothing ever leaves your phone.",
            style = MaterialTheme.typography.bodyMedium,
            color = fsColors.secondaryLabel,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(36.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(fsColors.accent)
                .pressScale(onGrantClick)
                .padding(vertical = 15.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "Allow All Files Access",
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
            )
        }
    }
}

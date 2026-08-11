package com.shahabcodes.filestorm.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.shahabcodes.filestorm.ui.theme.fsColors

/**
 * The app's dialog.
 *
 * Material's stock AlertDialog drops a pale slab with a stray text button in
 * the corner, which matches nothing else here and reads as an unfinished
 * screen. This one is built from the same pieces as the rest of the app —
 * grouped-card surface, the accent colour, a full-width primary button — and
 * it settles in rather than snapping into place.
 *
 * [content] is for the rare dialog that needs a control rather than a
 * sentence, such as renaming. Everything else should just pass [message].
 */
@Composable
fun FsDialog(
    title: String,
    onDismiss: () -> Unit,
    confirmText: String,
    onConfirm: () -> Unit,
    message: String? = null,
    icon: ImageVector? = null,
    /** Tints the icon and the confirm button red, for anything that erases. */
    destructive: Boolean = false,
    confirmEnabled: Boolean = true,
    /** Null leaves a single button, for dialogs that only report something. */
    dismissText: String? = "Cancel",
    dismissOnTapOutside: Boolean = true,
    content: (@Composable ColumnScope.() -> Unit)? = null,
) {
    val tint = if (destructive) fsColors.red else fsColors.accent

    // Growing in from slightly small is the difference between a dialog that
    // appears and one that arrives.
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    val scale by animateFloatAsState(
        if (shown) 1f else 0.9f,
        spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMedium),
        label = "dialogScale",
    )
    val fade by animateFloatAsState(if (shown) 1f else 0f, tween(150), label = "dialogFade")

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = dismissOnTapOutside,
            dismissOnClickOutside = dismissOnTapOutside,
            usePlatformDefaultWidth = false,
        ),
    ) {
        Box(
            Modifier.fillMaxWidth().padding(horizontal = 28.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                Modifier
                    .widthIn(max = 400.dp)
                    .fillMaxWidth()
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        alpha = fade
                    }
                    .clip(RoundedCornerShape(28.dp))
                    .background(fsColors.card)
                    .padding(horizontal = 24.dp, vertical = 26.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (icon != null) {
                    Box(
                        Modifier
                            .size(58.dp)
                            .clip(CircleShape)
                            .background(tint.copy(alpha = 0.14f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(icon, null, tint = tint, modifier = Modifier.size(28.dp))
                    }
                    Spacer(Modifier.height(16.dp))
                }

                Text(
                    title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = fsColors.label,
                    textAlign = TextAlign.Center,
                )

                if (message != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = fsColors.secondaryLabel,
                        textAlign = TextAlign.Center,
                        lineHeight = MaterialTheme.typography.bodyMedium.fontSize * 1.45f,
                    )
                }

                if (content != null) {
                    Spacer(Modifier.height(18.dp))
                    Column(
                        Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        content = content,
                    )
                }

                Spacer(Modifier.height(24.dp))

                // Full width and stacked: the same shape as every other primary
                // action in the app, and a target you can hit without aiming.
                Box(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (confirmEnabled) tint else fsColors.fill)
                        .pressScale { if (confirmEnabled) onConfirm() }
                        .padding(vertical = 14.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        confirmText,
                        style = MaterialTheme.typography.titleMedium,
                        color = if (confirmEnabled) Color.White else fsColors.secondaryLabel,
                    )
                }

                if (dismissText != null) {
                    Spacer(Modifier.height(6.dp))
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .pressScale(onDismiss)
                            .padding(vertical = 13.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            dismissText,
                            style = MaterialTheme.typography.titleMedium,
                            color = fsColors.secondaryLabel,
                        )
                    }
                }
            }
        }
    }
}

/**
 * A dialog that only reports something and needs one button to go away.
 */
@Composable
fun FsInfoDialog(
    title: String,
    onDismiss: () -> Unit,
    message: String? = null,
    icon: ImageVector? = null,
    buttonText: String = "OK",
    content: (@Composable ColumnScope.() -> Unit)? = null,
) = FsDialog(
    title = title,
    onDismiss = onDismiss,
    confirmText = buttonText,
    onConfirm = onDismiss,
    message = message,
    icon = icon,
    dismissText = null,
    content = content,
)

/** Vertical rhythm for dialogs that stack their own rows inside [content]. */
@Composable
fun DialogSpacer() = Spacer(Modifier.height(10.dp))

/** A quiet strip inside a dialog, for a warning that must not be missed. */
@Composable
fun DialogNote(text: String, tint: Color = fsColors.orange) {
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(tint.copy(alpha = 0.12f))
            .padding(horizontal = 14.dp, vertical = 11.dp),
    ) {
        Text(
            text,
            style = MaterialTheme.typography.bodySmall,
            color = fsColors.label,
            textAlign = TextAlign.Center,
        )
    }
}

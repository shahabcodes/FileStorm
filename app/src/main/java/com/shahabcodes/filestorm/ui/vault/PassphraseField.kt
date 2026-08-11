package com.shahabcodes.filestorm.ui.vault

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.shahabcodes.filestorm.ui.components.pressScale
import com.shahabcodes.filestorm.ui.theme.fsColors

/**
 * A passphrase box you can actually paste into.
 *
 * Compose hides copy and paste from the selection toolbar on password fields,
 * and most keyboards drop their clipboard strip once the field asks for
 * [KeyboardType.Password] — between them a password manager becomes unusable
 * and people end up choosing something short enough to retype. An explicit
 * paste button sidesteps both, and the reveal toggle lets you confirm what
 * actually landed in the box.
 */
@Composable
fun PassphraseField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    masked: Boolean = true,
    isError: Boolean = false,
) {
    var reveal by remember { mutableStateOf(false) }
    val clipboard = LocalClipboardManager.current

    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        label = label?.let { { Text(it, color = fsColors.secondaryLabel) } },
        isError = isError,
        // Revealing turns off the masking, not the field's password type, so
        // the keyboard still keeps it out of its learned-word dictionary.
        visualTransformation =
            if (!masked || reveal) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        trailingIcon = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                if (clipboard.hasText()) {
                    Icon(
                        Icons.Rounded.ContentPaste,
                        "Paste",
                        tint = fsColors.accent,
                        modifier = Modifier
                            .pressScale {
                                // Appending rather than replacing means tapping
                                // paste can never wipe out something already
                                // half-typed.
                                //
                                // Line breaks are stripped because a single-line
                                // field cannot contain them anyway, so a
                                // passphrase can never legitimately hold one and
                                // a manager that copies a trailing newline would
                                // otherwise cause a baffling wrong-passphrase.
                                // Spaces are left alone — a passphrase is allowed
                                // to end in one, and silently trimming it here
                                // would lock someone out of their own vault.
                                val pasted = clipboard.getText()?.text.orEmpty()
                                    .replace("\r", "")
                                    .replace("\n", "")
                                if (pasted.isNotEmpty()) onValueChange(value + pasted)
                            }
                            .padding(8.dp)
                            .size(20.dp),
                    )
                }
                if (masked) {
                    Icon(
                        if (reveal) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                        if (reveal) "Hide passphrase" else "Show passphrase",
                        tint = fsColors.secondaryLabel,
                        modifier = Modifier
                            .pressScale { reveal = !reveal }
                            .padding(8.dp)
                            .size(20.dp),
                    )
                }
                Spacer(Modifier.width(4.dp))
            }
        },
        modifier = modifier,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = fsColors.accent,
            unfocusedBorderColor = fsColors.separator,
            focusedTextColor = fsColors.label,
            unfocusedTextColor = fsColors.label,
            cursorColor = fsColors.accent,
        ),
    )
}

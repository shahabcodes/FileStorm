package com.shahaabapps.filestorm.ui.vault

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.shahaabapps.filestorm.data.vault.VaultCrypto
import com.shahaabapps.filestorm.data.vault.VaultFolder
import com.shahaabapps.filestorm.data.vault.VaultPrefs
import com.shahaabapps.filestorm.data.vault.VaultSession
import com.shahaabapps.filestorm.ui.components.FsSpinner
import com.shahaabapps.filestorm.ui.components.GroupedCard
import com.shahaabapps.filestorm.ui.theme.fsColors
import com.shahaabapps.filestorm.ui.components.pressScale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Setting up a vault: choose a passphrase, then be shown the recovery code once
 * and made to confirm it has been saved. The confirmation step is deliberate —
 * this is the only moment where losing the code is still recoverable.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VaultSetupSheet(root: File, onDismiss: () -> Unit, onCreated: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var passphrase by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var stage by remember { mutableStateOf(0) } // 0 passphrase, 1 recovery code
    var busy by remember { mutableStateOf(false) }
    var saved by remember { mutableStateOf(false) }
    val recoveryCode = remember { VaultCrypto.newRecoveryCode() }

    val strength = rate(passphrase)
    val matches = passphrase.isNotEmpty() && passphrase == confirm

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = fsColors.groupedBackground,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .navigationBarsPadding(),
        ) {
            if (stage == 0) {
                Text(
                    "Choose a passphrase",
                    style = MaterialTheme.typography.headlineMedium,
                    color = fsColors.label,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "This is the only thing protecting the folder. A few random words are both " +
                        "stronger and easier to remember than one complicated word.",
                    style = MaterialTheme.typography.bodySmall,
                    color = fsColors.secondaryLabel,
                )
                Spacer(Modifier.height(16.dp))

                Field(passphrase, "Passphrase") { passphrase = it }
                Spacer(Modifier.height(6.dp))
                Text(
                    strength.second,
                    style = MaterialTheme.typography.labelSmall,
                    color = strength.first,
                )
                Spacer(Modifier.height(12.dp))
                Field(confirm, "Type it again") { confirm = it }
                if (confirm.isNotEmpty() && !matches) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "These do not match.",
                        style = MaterialTheme.typography.labelSmall,
                        color = fsColors.red,
                    )
                }

                Spacer(Modifier.height(16.dp))
                GroupedCard {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.Top) {
                        Icon(
                            Icons.Rounded.Warning, null,
                            tint = fsColors.orange, modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            "There is no way to reset this. If the passphrase and the recovery " +
                                "code are both lost, the files cannot be opened by anyone, " +
                                "including me.",
                            style = MaterialTheme.typography.bodySmall,
                            color = fsColors.label,
                        )
                    }
                }
                Spacer(Modifier.height(18.dp))
                PrimaryButton("Continue", enabled = matches && passphrase.length >= 8) { stage = 1 }
                Spacer(Modifier.height(20.dp))
            } else {
                Text(
                    "Save your recovery code",
                    style = MaterialTheme.typography.headlineMedium,
                    color = fsColors.label,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "This opens the vault if you ever forget the passphrase. It is shown once " +
                        "and never again. Keep it somewhere that is not this phone.",
                    style = MaterialTheme.typography.bodySmall,
                    color = fsColors.secondaryLabel,
                )
                Spacer(Modifier.height(16.dp))
                GroupedCard {
                    Column(
                        Modifier.fillMaxWidth().padding(18.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            recoveryCode,
                            style = MaterialTheme.typography.titleLarge,
                            fontFamily = FontFamily.Monospace,
                            color = fsColors.label,
                            textAlign = TextAlign.Center,
                        )
                        Spacer(Modifier.height(12.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(fsColors.fill)
                                .pressScale {
                                    val manager = context
                                        .getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    manager.setPrimaryClip(
                                        ClipData.newPlainText("File Storm recovery code", recoveryCode)
                                    )
                                    Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
                                }
                                .padding(horizontal = 14.dp, vertical = 8.dp),
                        ) {
                            Icon(
                                Icons.Rounded.ContentCopy, null,
                                tint = fsColors.accent, modifier = Modifier.size(15.dp),
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                "Copy",
                                style = MaterialTheme.typography.labelMedium,
                                color = fsColors.accent,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(14.dp))
                Row(
                    Modifier
                        .fillMaxWidth()
                        .pressScale { saved = !saved }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    com.shahaabapps.filestorm.ui.components.SelectionCircle(selected = saved)
                    Spacer(Modifier.width(12.dp))
                    Text(
                        "I have saved this somewhere safe",
                        style = MaterialTheme.typography.bodyMedium,
                        color = fsColors.label,
                    )
                }
                Spacer(Modifier.height(14.dp))
                if (busy) {
                    Column(
                        Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        FsSpinner()
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Preparing the vault…",
                            style = MaterialTheme.typography.bodySmall,
                            color = fsColors.secondaryLabel,
                        )
                    }
                } else {
                    PrimaryButton("Create Vault", enabled = saved) {
                        busy = true
                        scope.launch {
                            // Deriving the key is deliberately slow, so it runs
                            // off the main thread rather than freezing the sheet.
                            val ok = withContext(Dispatchers.Default) {
                                runCatching {
                                    val (folder, master) = VaultFolder.create(
                                        root, passphrase.toCharArray(), recoveryCode,
                                        VaultPrefs.strength,
                                    )
                                    VaultSession.unlock(root, passphrase.toCharArray())
                                    folder.isVault() && master.isNotEmpty()
                                }.getOrDefault(false)
                            }
                            busy = false
                            if (ok) onCreated()
                        }
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun Field(value: String, label: String, onChange: (String) -> Unit) {
    PassphraseField(
        value = value,
        onValueChange = onChange,
        label = label,
        modifier = Modifier.fillMaxWidth(),
    )
}

/**
 * A blunt but honest read on the passphrase. Length and variety are what
 * actually decide whether a copied vault survives an offline attack, so the
 * advice says so rather than showing a meaningless bar.
 */
@Composable
private fun rate(passphrase: String): Pair<androidx.compose.ui.graphics.Color, String> {
    val words = passphrase.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }.size
    val classes = listOf(
        passphrase.any { it.isLowerCase() },
        passphrase.any { it.isUpperCase() },
        passphrase.any { it.isDigit() },
        passphrase.any { !it.isLetterOrDigit() },
    ).count { it }
    return when {
        passphrase.isEmpty() -> fsColors.secondaryLabel to "At least 8 characters."
        passphrase.length < 8 -> fsColors.red to "Too short — at least 8 characters."
        words >= 4 && passphrase.length >= 16 -> fsColors.green to "Strong. Four or more words is hard to guess."
        passphrase.length >= 16 && classes >= 3 -> fsColors.green to "Strong."
        passphrase.length >= 12 -> fsColors.orange to "Reasonable. Longer or more words would be better."
        else -> fsColors.orange to "Weak against a determined attacker. Try a few random words."
    }
}

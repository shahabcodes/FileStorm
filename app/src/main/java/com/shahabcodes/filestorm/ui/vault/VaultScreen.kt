package com.shahabcodes.filestorm.ui.vault

import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.GridCells
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import com.shahabcodes.filestorm.data.vault.VaultMedia
import com.shahabcodes.filestorm.data.FsEntry
import com.shahabcodes.filestorm.data.FileKind
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.rounded.CheckCircleOutline
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.PauseCircleOutline
import androidx.compose.material.icons.rounded.Warning
import com.shahabcodes.filestorm.ui.components.FsDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.shahabcodes.filestorm.data.FileRepository
import com.shahabcodes.filestorm.data.vault.VaultCrypto
import com.shahabcodes.filestorm.data.vault.VaultEngine
import com.shahabcodes.filestorm.data.vault.VaultFileInfo
import com.shahabcodes.filestorm.data.vault.VaultFolder
import com.shahabcodes.filestorm.data.vault.VaultPhase
import com.shahabcodes.filestorm.data.vault.VaultPrefs
import com.shahabcodes.filestorm.data.vault.VaultSession
import com.shahabcodes.filestorm.ui.components.FsSpinner
import com.shahabcodes.filestorm.ui.components.GroupedCard
import com.shahabcodes.filestorm.ui.components.RowSeparator
import com.shahabcodes.filestorm.ui.components.pressScale
import com.shahabcodes.filestorm.ui.theme.fsColors
import com.shahabcodes.filestorm.util.Formatters
import kotlinx.coroutines.launch
import java.io.File

/**
 * The vault, in whichever state the folder happens to be: not yet a vault,
 * locked, unlocked and browsable, or mid-run.
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun VaultScreen(
    path: String,
    onBack: () -> Unit,
    onOpenFolder: (String) -> Unit,
    onOpenViewer: (List<String>, Int) -> Unit = { _, _ -> },
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val root = remember(path) { File(path) }
    val folder = remember(path) { VaultFolder(root) }
    val run by VaultSession.run.collectAsState()

    // Recomposes when a vault opens or closes.
    val revision = VaultSession.revision
    val unlocked = remember(revision, path) { VaultSession.isUnlocked(root) }
    val isVault = remember(revision, path, run.summary) { folder.isVault() }

    var creating by remember { mutableStateOf(false) }
    var confirmLock by remember { mutableStateOf(false) }
    var confirmUnlockAll by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .background(fsColors.groupedBackground)
            .statusBarsPadding(),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.pressScale(onBack).padding(horizontal = 6.dp, vertical = 6.dp),
            ) {
                Icon(
                    Icons.AutoMirrored.Rounded.KeyboardArrowLeft, null,
                    tint = fsColors.accent, modifier = Modifier.size(18.dp),
                )
                Text("Back", color = fsColors.accent, style = MaterialTheme.typography.bodyLarge)
            }
            Spacer(Modifier.weight(1f))
            if (unlocked && !run.active) {
                Text(
                    "Lock",
                    color = fsColors.accent,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier
                        .pressScale { VaultSession.lock(root) }
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                )
            }
        }

        Row(
            Modifier.padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                if (unlocked) Icons.Rounded.LockOpen else Icons.Rounded.Lock,
                null,
                tint = if (unlocked) fsColors.green else fsColors.accent,
                modifier = Modifier.size(26.dp),
            )
            Spacer(Modifier.width(10.dp))
            Column {
                Text(
                    root.name.ifEmpty { "Internal storage" },
                    style = MaterialTheme.typography.headlineMedium,
                    color = fsColors.label,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    when {
                        !isVault -> "Not encrypted"
                        unlocked -> "Unlocked"
                        else -> "Locked"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = fsColors.secondaryLabel,
                )
            }
        }
        Spacer(Modifier.height(14.dp))

        when {
            run.active -> VaultProgressView(run.locking)
            !isVault -> NotAVaultView(onCreate = { creating = true })
            !unlocked -> UnlockView(root)
            else -> UnlockedView(
                folder = folder,
                onLockRemaining = { confirmLock = true },
                onUnlockAll = { confirmUnlockAll = true },
                onOpenViewer = onOpenViewer,
            )
        }
    }

    if (creating) {
        VaultSetupSheet(
            root = root,
            onDismiss = { creating = false },
            onCreated = {
                creating = false
                VaultSession.startLock(context, root)
            },
        )
    }

    run.summary?.let { summary ->
        val close = {
            VaultSession.clearSummary()
            FileRepository.invalidate(root.absolutePath)
        }
        FsDialog(
            title = when {
                summary.cancelled -> "Stopped"
                summary.failed > 0 -> "Finished with problems"
                else -> "Done"
            },
            message = "${summary.succeeded} file(s) · ${Formatters.bytes(summary.bytes)}" +
                if (summary.failed > 0) " · ${summary.failed} failed" else "",
            icon = when {
                summary.failed > 0 -> Icons.Rounded.ErrorOutline
                summary.cancelled -> Icons.Rounded.PauseCircleOutline
                else -> Icons.Rounded.CheckCircleOutline
            },
            destructive = summary.failed > 0,
            // Failed files kept their originals, so running again simply picks
            // them up — the engine only ever takes what is still unencrypted.
            confirmText = if (summary.failed > 0) "Retry failed" else "OK",
            dismissText = if (summary.failed > 0) "Not now" else null,
            onDismiss = close,
            onConfirm = {
                if (summary.failed > 0) {
                    val wasLocking = run.locking
                    VaultSession.clearSummary()
                    if (wasLocking) VaultSession.startLock(context, root)
                    else VaultSession.startUnlock(context, root)
                } else close()
            },
            content = if (summary.failures.isEmpty()) null else {
                {
                    summary.failures.take(6).forEach {
                        Text(
                            "${it.path}: ${it.reason}",
                            style = MaterialTheme.typography.labelSmall,
                            color = fsColors.red,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center,
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Originals of failed files were left exactly where they were.",
                        style = MaterialTheme.typography.labelSmall,
                        color = fsColors.secondaryLabel,
                        textAlign = TextAlign.Center,
                    )
                }
            },
        )
    }

    if (confirmLock) {
        ConfirmDialog(
            title = "Encrypt everything here?",
            body = "Every file in this folder and its subfolders will be encrypted. Each " +
                "original is removed only after its encrypted copy has been verified.",
            confirmLabel = "Encrypt",
            onConfirm = {
                confirmLock = false
                VaultSession.startLock(context, root)
            },
            onDismiss = { confirmLock = false },
        )
    }

    if (confirmUnlockAll) {
        ConfirmDialog(
            title = "Decrypt the whole folder?",
            body = "Every file will be restored to its original name, place and date, and the " +
                "folder stops being a vault.",
            confirmLabel = "Decrypt All",
            onConfirm = {
                confirmUnlockAll = false
                VaultSession.startUnlock(context, root)
            },
            onDismiss = { confirmUnlockAll = false },
        )
    }
}

@Composable
private fun NotAVaultView(onCreate: () -> Unit) {
    Column(Modifier.padding(horizontal = 16.dp).verticalScroll(rememberScrollState())) {
        GroupedCard {
            Column(Modifier.padding(18.dp)) {
                Text(
                    "Encrypt this folder",
                    style = MaterialTheme.typography.titleLarge,
                    color = fsColors.label,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "Everything inside, including subfolders and hidden files, is encrypted " +
                        "with a passphrase only you know. Names and dates are hidden too, and " +
                        "come back exactly as they were when you decrypt.",
                    style = MaterialTheme.typography.bodySmall,
                    color = fsColors.secondaryLabel,
                )
                Spacer(Modifier.height(14.dp))
                Row(verticalAlignment = Alignment.Top) {
                    Icon(
                        Icons.Rounded.Warning, null,
                        tint = fsColors.orange, modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        "Lose the passphrase and the recovery code and the files are gone for " +
                            "good. There is no way to reset it.",
                        style = MaterialTheme.typography.bodySmall,
                        color = fsColors.label,
                    )
                }
            }
        }
        Spacer(Modifier.height(18.dp))
        PrimaryButton("Set Up Encryption", onClick = onCreate)
    }
}

@Composable
private fun UnlockView(root: File) {
    val scope = rememberCoroutineScope()
    var passphrase by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var wrong by remember { mutableStateOf(false) }
    var usingRecovery by remember { mutableStateOf(false) }

    Column(Modifier.padding(horizontal = 16.dp).verticalScroll(rememberScrollState())) {
        GroupedCard {
            Column(Modifier.padding(18.dp)) {
                Text(
                    if (usingRecovery) "Enter your recovery code" else "Enter your passphrase",
                    style = MaterialTheme.typography.titleMedium,
                    color = fsColors.label,
                )
                Spacer(Modifier.height(12.dp))
                PassphraseField(
                    value = passphrase,
                    onValueChange = { passphrase = it; wrong = false },
                    // The recovery code is shown as issued — it is written down
                    // rather than memorised, so hiding it helps nobody.
                    masked = !usingRecovery,
                    isError = wrong,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (wrong) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        if (usingRecovery) "That code did not open this vault."
                        else "That passphrase did not open this vault.",
                        style = MaterialTheme.typography.labelSmall,
                        color = fsColors.red,
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    if (usingRecovery) "Use passphrase instead" else "Use recovery code instead",
                    style = MaterialTheme.typography.labelMedium,
                    color = fsColors.accent,
                    modifier = Modifier
                        .pressScale { usingRecovery = !usingRecovery; passphrase = ""; wrong = false }
                        .padding(vertical = 4.dp),
                )
            }
        }
        Spacer(Modifier.height(18.dp))
        if (busy) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    FsSpinner()
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Checking…",
                        style = MaterialTheme.typography.bodySmall,
                        color = fsColors.secondaryLabel,
                    )
                }
            }
        } else {
            PrimaryButton("Unlock", enabled = passphrase.isNotBlank()) {
                busy = true
                scope.launch {
                    val ok = if (usingRecovery) {
                        VaultSession.unlockWithRecoveryCode(root, passphrase)
                    } else {
                        VaultSession.unlock(root, passphrase.toCharArray())
                    }
                    busy = false
                    wrong = !ok
                    if (ok) passphrase = ""
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "Unlocking takes a moment on purpose — it is what makes guessing expensive.",
                style = MaterialTheme.typography.labelSmall,
                color = fsColors.secondaryLabel,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }
    }
}

@Composable
private fun UnlockedView(
    folder: VaultFolder,
    onLockRemaining: () -> Unit,
    onUnlockAll: () -> Unit,
    onOpenViewer: (List<String>, Int) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val key = VaultSession.keyFor(folder.root)
    var entries by remember { mutableStateOf<List<Pair<File, VaultFileInfo>>?>(null) }
    var busyWith by remember { mutableStateOf<String?>(null) }
    var stragglers by remember { mutableStateOf(0) }
    var gallery by remember { mutableStateOf(true) }
    var sheetFor by remember { mutableStateOf<Pair<File, VaultFileInfo>?>(null) }

    androidx.compose.runtime.LaunchedEffect(folder.root.path, key) {
        if (key == null) return@LaunchedEffect
        entries = withContext(Dispatchers.IO) {
            VaultEngine.listContents(folder, key).sortedBy { it.second.relativePath.lowercase() }
        }
        stragglers = withContext(Dispatchers.IO) {
            folder.plaintextStragglers(VaultPrefs.includeHidden).size
        }
    }

    /** Media opens in the app's viewer; anything else is restored instead. */
    fun open(entry: Pair<File, VaultFileInfo>) {
        val master = key ?: return
        val all = entries.orEmpty()
        val media = all.filter {
            val kind = FsEntry.kindOf(it.second.name, false)
            kind == FileKind.IMAGE || kind == FileKind.VIDEO
        }
        val at = media.indexOfFirst { it.first == entry.first }
        if (at < 0) {
            sheetFor = entry
            return
        }
        busyWith = entry.second.name
        scope.launch {
            // The whole run of media is decrypted so swiping works, which is
            // also why the cache is wiped the moment the vault locks.
            val paths = ArrayList<String>()
            var index = 0
            media.forEachIndexed { i, item ->
                val copy = VaultMedia.openCopy(item.first, master, item.second)
                if (copy != null) {
                    if (i == at) index = paths.size
                    paths.add(copy.absolutePath)
                }
            }
            busyWith = null
            if (paths.isNotEmpty()) onOpenViewer(paths, index)
        }
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "${entries?.size ?: 0} encrypted file(s)",
                        style = MaterialTheme.typography.titleMedium,
                        color = fsColors.label,
                    )
                    Text(
                        if (busyWith != null) "Opening $busyWith…"
                        else "Tap to view · hold to bring one back",
                        style = MaterialTheme.typography.bodySmall,
                        color = fsColors.secondaryLabel,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    if (gallery) "List" else "Gallery",
                    style = MaterialTheme.typography.labelMedium,
                    color = fsColors.accent,
                    modifier = Modifier.pressScale { gallery = !gallery }.padding(6.dp),
                )
            }

            if (stragglers > 0) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "$stragglers file(s) here are not encrypted yet.",
                        style = MaterialTheme.typography.bodySmall,
                        color = fsColors.orange,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        "Encrypt",
                        style = MaterialTheme.typography.labelMedium,
                        color = fsColors.accent,
                        modifier = Modifier.pressScale(onLockRemaining).padding(6.dp),
                    )
                }
            }

            when {
                entries == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    FsSpinner()
                }
                gallery -> LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        start = 12.dp, end = 12.dp, top = 8.dp, bottom = 150.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    items(entries!!, key = { it.first.absolutePath }) { entry ->
                        VaultTile(entry, onOpen = { open(entry) }, onHold = { sheetFor = entry })
                    }
                }
                else -> LazyColumn(
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        start = 16.dp, end = 16.dp, bottom = 150.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(entries!!, key = { it.first.absolutePath }) { entry ->
                        VaultRow(entry, onOpen = { open(entry) }, onHold = { sheetFor = entry })
                    }
                }
            }
        }

        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                .background(fsColors.card)
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            PrimaryButton("Decrypt Whole Folder", onClick = onUnlockAll)
        }
    }

    sheetFor?.let { entry ->
        FsDialog(
            title = entry.second.name,
            message = "Bringing it back decrypts it to its original name, place and date, " +
                "and removes it from the vault. Everything else stays locked.",
            icon = Icons.Rounded.LockOpen,
            confirmText = "Bring It Back",
            onDismiss = { sheetFor = null },
            onConfirm = {
                val master = key
                val file = entry.first
                sheetFor = null
                if (master != null) {
                    busyWith = entry.second.name
                    scope.launch {
                        val result = withContext(Dispatchers.IO) {
                            VaultEngine.restore(folder, file, master)
                        }
                        busyWith = null
                        if (result.ok) {
                            FileRepository.invalidate(folder.root.absolutePath)
                            entries = entries?.filterNot { it.first == file }
                        }
                    }
                }
            },
            content = {
                Text(
                    entry.second.relativePath,
                    style = MaterialTheme.typography.labelSmall,
                    color = fsColors.secondaryLabel,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    Formatters.bytes(entry.second.size) + " · " +
                        Formatters.fileDate(entry.second.modified),
                    style = MaterialTheme.typography.bodySmall,
                    color = fsColors.secondaryLabel,
                    textAlign = TextAlign.Center,
                )
            },
        )
    }
}

/** Grid tile, showing the encrypted thumbnail once the vault is open. */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun VaultTile(
    entry: Pair<File, VaultFileInfo>,
    onOpen: () -> Unit,
    onHold: () -> Unit,
) {
    val info = entry.second
    val bitmap = remember(entry.first.path) {
        VaultMedia.thumbnailBitmap(info.thumbnail)?.asImageBitmap()
    }
    Box(
        Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(10.dp))
            .background(fsColors.fill)
            .combinedClickable(onClick = onOpen, onLongClick = onHold),
    ) {
        if (bitmap != null) {
            androidx.compose.foundation.Image(
                bitmap = bitmap,
                contentDescription = info.name,
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Column(
                Modifier.fillMaxSize().padding(8.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(
                    Icons.Rounded.Lock, null,
                    tint = fsColors.secondaryLabel, modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    info.name,
                    style = MaterialTheme.typography.labelSmall,
                    color = fsColors.secondaryLabel,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                )
            }
        }
        Text(
            Formatters.bytes(info.size),
            style = MaterialTheme.typography.labelSmall,
            color = Color.White,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(4.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(Color.Black.copy(alpha = 0.45f))
                .padding(horizontal = 5.dp, vertical = 1.dp),
        )
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun VaultRow(
    entry: Pair<File, VaultFileInfo>,
    onOpen: () -> Unit,
    onHold: () -> Unit,
) {
    val info = entry.second
    val bitmap = remember(entry.first.path) {
        VaultMedia.thumbnailBitmap(info.thumbnail)?.asImageBitmap()
    }
    GroupedCard {
        Row(
            Modifier
                .fillMaxWidth()
                .combinedClickable(onClick = onOpen, onLongClick = onHold)
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(44.dp).clip(RoundedCornerShape(9.dp)).background(fsColors.fill),
                contentAlignment = Alignment.Center,
            ) {
                if (bitmap != null) {
                    androidx.compose.foundation.Image(
                        bitmap = bitmap,
                        contentDescription = null,
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Icon(
                        Icons.Rounded.Lock, null,
                        tint = fsColors.secondaryLabel, modifier = Modifier.size(18.dp),
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    info.name,
                    style = MaterialTheme.typography.bodyMedium,
                    color = fsColors.label,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    info.relativePath.substringBeforeLast('/', "").ifEmpty { "in this folder" },
                    style = MaterialTheme.typography.labelSmall,
                    color = fsColors.secondaryLabel,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(10.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    Formatters.bytes(info.size),
                    style = MaterialTheme.typography.labelMedium,
                    color = fsColors.label,
                )
                Text(
                    Formatters.fileDate(info.modified),
                    style = MaterialTheme.typography.labelSmall,
                    color = fsColors.secondaryLabel,
                )
            }
        }
    }
}

/** Live detail of what the run is doing right now. */
@Composable
private fun VaultProgressView(locking: Boolean) {
    val run by VaultSession.run.collectAsState()
    val p = run.progress
    val many = p.workers > 1 && p.activeFiles.size > 1

    Column(
        Modifier.fillMaxSize().padding(horizontal = 16.dp).verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(10.dp))
        FsSpinner(size = 64.dp, strokeWidth = 6.dp)
        Spacer(Modifier.height(14.dp))
        Text(
            when (p.phase) {
                VaultPhase.SCANNING -> "Looking through the folder"
                VaultPhase.RESUMING -> "Finishing interrupted work"
                VaultPhase.CLEANING -> "Tidying up"
                VaultPhase.DECRYPTING -> "Decrypting"
                else -> if (locking) "Encrypting" else "Decrypting"
            },
            style = MaterialTheme.typography.titleLarge,
            color = fsColors.label,
        )
        // With several files at once there is no single "current file", so say
        // how many are running rather than naming one and looking wrong.
        Text(
            when {
                many -> "${p.activeFiles.size} files at once"
                p.currentName.isNotEmpty() -> p.currentName
                else -> ""
            },
            style = MaterialTheme.typography.bodySmall,
            color = fsColors.secondaryLabel,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(16.dp))

        ProgressBar(p.fraction)
        Spacer(Modifier.height(6.dp))
        Text(
            "${Formatters.bytes(p.bytesDone)} of ${Formatters.bytes(p.bytesTotal)}",
            style = MaterialTheme.typography.labelMedium,
            color = fsColors.label,
        )
        Spacer(Modifier.height(2.dp))
        // Speed is what tells you whether a long run is healthy or has hit
        // something slow; elapsed gives the ETA something to be judged against.
        Text(
            listOfNotNull(
                if (p.speedBps > 1.0) Formatters.speed(p.speedBps) else null,
                if (p.etaSeconds > 0) "${Formatters.eta(p.etaSeconds)} left" else null,
                "${Formatters.eta(p.elapsedSeconds)} so far",
            ).joinToString(" · "),
            style = MaterialTheme.typography.labelSmall,
            color = fsColors.secondaryLabel,
        )

        if (p.activeFiles.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            GroupedCard {
                Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                    p.activeFiles.forEachIndexed { index, file ->
                        if (index > 0) Spacer(Modifier.height(10.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                file.name,
                                style = MaterialTheme.typography.labelMedium,
                                color = fsColors.label,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "${(file.fraction * 100).toInt()}%",
                                style = MaterialTheme.typography.labelSmall,
                                color = fsColors.secondaryLabel,
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        ProgressBar(file.fraction, thin = true)
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        GroupedCard {
            Column {
                Tally("File", "${p.fileIndex} of ${p.fileCount}")
                RowSeparator(startIndent = 16.dp)
                Tally("Done", "${p.succeeded}")
                RowSeparator(startIndent = 16.dp)
                Tally("Failed", "${p.failed}", if (p.failed > 0) fsColors.red else null)
                RowSeparator(startIndent = 16.dp)
                Tally("Skipped", "${p.skipped}")
            }
        }

        // Failures named while the run is still going, rather than only in the
        // summary at the end — a run of thousands should not hide what broke.
        if (p.recentFailures.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            GroupedCard {
                Column(Modifier.fillMaxWidth().padding(14.dp)) {
                    Text(
                        "Recent problems",
                        style = MaterialTheme.typography.labelMedium,
                        color = fsColors.red,
                    )
                    Spacer(Modifier.height(6.dp))
                    p.recentFailures.forEach { failure ->
                        Text(
                            "${failure.path} — ${failure.reason}",
                            style = MaterialTheme.typography.labelSmall,
                            color = fsColors.secondaryLabel,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Their originals were left exactly where they were.",
                        style = MaterialTheme.typography.labelSmall,
                        color = fsColors.secondaryLabel,
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        Text(
            "Stop",
            style = MaterialTheme.typography.titleMedium,
            color = fsColors.red,
            modifier = Modifier.pressScale { VaultSession.cancel() }.padding(12.dp),
        )
        Text(
            "Stopping is safe — anything already encrypted stays encrypted, and anything not " +
                "yet started is untouched.",
            style = MaterialTheme.typography.labelSmall,
            color = fsColors.secondaryLabel,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(30.dp))
    }
}

@Composable
private fun ProgressBar(fraction: Float, thin: Boolean = false) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(if (thin) 5.dp else 9.dp)
            .clip(RoundedCornerShape(50))
            .background(fsColors.fill),
    ) {
        Box(
            Modifier
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .fillMaxHeight()
                .clip(RoundedCornerShape(50))
                .background(
                    androidx.compose.ui.graphics.Brush.horizontalGradient(
                        listOf(fsColors.accent, fsColors.green)
                    )
                ),
        )
    }
}

@Composable
private fun Tally(label: String, value: String, tint: Color? = null) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = fsColors.secondaryLabel,
            modifier = Modifier.weight(1f),
        )
        Text(value, style = MaterialTheme.typography.bodyMedium, color = tint ?: fsColors.label)
    }
}

@Composable
internal fun PrimaryButton(label: String, enabled: Boolean = true, onClick: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(if (enabled) fsColors.accent else fsColors.fill)
            .pressScale { if (enabled) onClick() }
            .padding(vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = if (enabled) Color.White else fsColors.secondaryLabel,
            style = MaterialTheme.typography.titleMedium,
        )
    }
}

@Composable
private fun ConfirmDialog(
    title: String,
    body: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    FsDialog(
        title = title,
        message = body,
        icon = Icons.Rounded.Lock,
        confirmText = confirmLabel,
        onConfirm = onConfirm,
        onDismiss = onDismiss,
    )
}

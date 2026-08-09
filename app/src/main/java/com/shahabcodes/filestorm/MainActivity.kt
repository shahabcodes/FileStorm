package com.shahabcodes.filestorm

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.fragment.app.FragmentActivity
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.shahabcodes.filestorm.data.FileKind
import com.shahabcodes.filestorm.data.FileRepository
import com.shahabcodes.filestorm.data.Prefs
import androidx.navigation.NavBackStackEntry
import com.shahabcodes.filestorm.ui.audio.MiniPlayer
import com.shahabcodes.filestorm.ui.viewer.VideoController
import com.shahabcodes.filestorm.ui.components.DiagnosticsOverlay
import com.shahabcodes.filestorm.ui.Biometrics
import com.shahabcodes.filestorm.ui.LockScreen
import com.shahabcodes.filestorm.ui.PermissionScreen
import com.shahabcodes.filestorm.ui.browser.BrowserScreen
import com.shahabcodes.filestorm.ui.browser.CategoryScreen
import com.shahabcodes.filestorm.ui.home.HomeScreen
import com.shahabcodes.filestorm.ui.settings.SettingsScreen
import com.shahabcodes.filestorm.ui.theme.FileStormTheme
import com.shahabcodes.filestorm.ui.theme.fsColors
import com.shahabcodes.filestorm.ui.transfer.TransferScreen
import com.shahabcodes.filestorm.ui.transfer.TransferSheet
import java.io.File

private const val ACTION_PIP_TOGGLE = "com.shahabcodes.filestorm.PIP_TOGGLE"
private const val VIDEO_CHANNEL_ID = "filestorm_video"
private const val VIDEO_NOTIFICATION_ID = 4412

class MainActivity : FragmentActivity() {

    private var hasAccess by mutableStateOf(false)
    private var locked by mutableStateOf(false)

    /** Play/pause taps coming back from the floating window's controls. */
    private val pipReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: android.content.Context?, intent: android.content.Intent?) {
            if (intent?.action == ACTION_PIP_TOGGLE) VideoController.toggle()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        hasAccess = checkAccess()
        locked = Prefs.biometricLock
        com.shahabcodes.filestorm.data.Diagnostics.log("APP", "activity created")

        androidx.core.content.ContextCompat.registerReceiver(
            this,
            pipReceiver,
            android.content.IntentFilter(ACTION_PIP_TOGGLE),
            androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        // Rebuild the floating controls whenever playback flips, otherwise the
        // button keeps showing the action that has already happened.
        VideoController.onStateChanged = {
            if (VideoController.inPip) runCatching { setPictureInPictureParams(pipParams()) }
            updateVideoNotification()
        }

        if (Build.VERSION.SDK_INT >= 33) {
            registerForActivityResult(ActivityResultContracts.RequestPermission()) {}
                .launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        setContent {
            // FLAG_SECURE blanks the recent-apps preview (and blocks screenshots).
            // Locking the app but leaving its contents legible in the recents
            // switcher would defeat the point, so the lock implies it.
            val hideContents = Prefs.secureScreen || Prefs.biometricLock
            androidx.compose.runtime.LaunchedEffect(hideContents) {
                if (hideContents) {
                    window.setFlags(
                        android.view.WindowManager.LayoutParams.FLAG_SECURE,
                        android.view.WindowManager.LayoutParams.FLAG_SECURE,
                    )
                } else {
                    window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
                }
            }
            FileStormTheme {
                Surface(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(fsColors.groupedBackground),
                    color = fsColors.groupedBackground,
                ) {
                    // The lock screen OVERLAYS the app instead of replacing it, so
                    // navigation, tabs, sheets and progress views all survive a
                    // lock/unlock cycle (transfers/jobs never stop — they run in
                    // services — but now the UI comes back exactly where it was).
                    androidx.compose.foundation.layout.Box(Modifier.fillMaxSize()) {
                        if (hasAccess) AppNav() else PermissionScreen(onGrantClick = { requestAccess() })
                        if (locked) LockScreen(onRequestUnlock = { promptUnlock() })
                        com.shahabcodes.filestorm.ui.components.TrashProgressDialog()
                        if (Prefs.diagnostics) {
                            DiagnosticsOverlay(onDisable = { Prefs.updateDiagnostics(false) })
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        hasAccess = checkAccess()
    }

    override fun onStop() {
        super.onStop()
        // Closing the floating window stops the activity but leaves the
        // composition — and therefore the player — alive, so the video carried
        // on as audio with nothing on screen. Only music is meant to outlive
        // the app; video pauses whenever the screen goes away. Entering PiP
        // does not call this, so floating playback is unaffected.
        VideoController.pause()
        updateVideoNotification()
        com.shahabcodes.filestorm.data.vault.VaultSession.lockAllIfLeaving()
        // Re-lock whenever the app leaves the foreground.
        if (Prefs.biometricLock) locked = true
        com.shahabcodes.filestorm.data.FolderLocks.clearSession()
    }

    private fun promptUnlock() {
        if (!Biometrics.available(this)) {
            // Screen lock was removed since enabling; don't lock the user out forever.
            locked = false
            Prefs.updateBiometricLock(false)
            return
        }
        Biometrics.prompt(
            this,
            title = "Unlock File Storm",
            subtitle = "Use your fingerprint, face or device PIN",
            onSuccess = { locked = false },
        )
    }

    /**
     * Shape and controls for the floating window. The aspect ratio has to sit
     * inside the range Android accepts or the request is rejected outright, so
     * an unusually tall or wide video is clamped rather than refused.
     */
    private fun pipParams(): android.app.PictureInPictureParams {
        val width = VideoController.videoWidth.takeIf { it > 0 } ?: 16
        val height = VideoController.videoHeight.takeIf { it > 0 } ?: 9
        var ratio = width.toFloat() / height.toFloat()
        ratio = ratio.coerceIn(0.42f, 2.38f)
        val numerator = (ratio * 1000).toInt()

        val icon = if (VideoController.playing) android.R.drawable.ic_media_pause
        else android.R.drawable.ic_media_play
        val label = if (VideoController.playing) "Pause" else "Play"
        val action = android.app.RemoteAction(
            android.graphics.drawable.Icon.createWithResource(this, icon),
            label,
            label,
            android.app.PendingIntent.getBroadcast(
                this,
                0,
                android.content.Intent(ACTION_PIP_TOGGLE).setPackage(packageName),
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or
                    android.app.PendingIntent.FLAG_IMMUTABLE,
            ),
        )
        return android.app.PictureInPictureParams.Builder()
            .setAspectRatio(android.util.Rational(numerator, 1000))
            .setActions(listOf(action))
            .build()
    }

    /**
     * Video playing in the floating window had nothing in the status bar, so
     * there was no sign it was running and no way to control it once the window
     * was tucked away. This posts the usual media entry while that is the case
     * and takes it down as soon as the video comes back into the app.
     */
    private fun updateVideoNotification() {
        val manager = getSystemService(android.content.Context.NOTIFICATION_SERVICE)
            as android.app.NotificationManager
        if (!VideoController.inPip || !VideoController.available) {
            runCatching { manager.cancel(VIDEO_NOTIFICATION_ID) }
            return
        }
        if (manager.getNotificationChannel(VIDEO_CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                android.app.NotificationChannel(
                    VIDEO_CHANNEL_ID,
                    "Video playback",
                    android.app.NotificationManager.IMPORTANCE_LOW,
                ).apply { setShowBadge(false) }
            )
        }
        val name = VideoController.activePath?.let { java.io.File(it).name } ?: "Video"
        val playing = VideoController.playing
        val toggle = android.app.PendingIntent.getBroadcast(
            this,
            1,
            android.content.Intent(ACTION_PIP_TOGGLE).setPackage(packageName),
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or
                android.app.PendingIntent.FLAG_IMMUTABLE,
        )
        val open = android.app.PendingIntent.getActivity(
            this,
            1,
            android.content.Intent(this, MainActivity::class.java)
                .setFlags(
                    android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP or
                        android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP
                ),
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or
                android.app.PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = android.app.Notification.Builder(this, VIDEO_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle(name)
            .setContentText(if (playing) "Playing" else "Paused")
            .setContentIntent(open)
            .setOnlyAlertOnce(true)
            .setOngoing(playing)
            .setVisibility(android.app.Notification.VISIBILITY_PUBLIC)
            .addAction(
                android.app.Notification.Action.Builder(
                    android.graphics.drawable.Icon.createWithResource(
                        this,
                        if (playing) android.R.drawable.ic_media_pause
                        else android.R.drawable.ic_media_play,
                    ),
                    if (playing) "Pause" else "Play",
                    toggle,
                ).build()
            )
            .build()
        runCatching { manager.notify(VIDEO_NOTIFICATION_ID, notification) }
    }

    fun enterPipIfPlaying() {
        if (!VideoController.available) return
        if (locked) return
        runCatching { enterPictureInPictureMode(pipParams()) }
    }

    /** Pressing home while a video is up floats it instead of just leaving. */
    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (VideoController.playing) enterPipIfPlaying()
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: android.content.res.Configuration,
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        VideoController.inPip = isInPictureInPictureMode
        updateVideoNotification()
    }

    override fun onDestroy() {
        runCatching {
            (getSystemService(android.content.Context.NOTIFICATION_SERVICE)
                as android.app.NotificationManager).cancel(VIDEO_NOTIFICATION_ID)
        }
        runCatching { unregisterReceiver(pipReceiver) }
        VideoController.onStateChanged = null
        super.onDestroy()
    }

    private fun checkAccess(): Boolean =
        if (Build.VERSION.SDK_INT >= 30) Environment.isExternalStorageManager()
        else true

    private fun requestAccess() {
        if (Build.VERSION.SDK_INT >= 30) {
            try {
                startActivity(
                    Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                        .setData(Uri.parse("package:$packageName"))
                )
            } catch (e: Exception) {
                startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
            }
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun AppNav() {
    val nav = rememberNavController()
    val context = androidx.compose.ui.platform.LocalContext.current
    var showTransferSheet by remember { mutableStateOf(false) }
    // The viewer is an overlay, not a destination. As a route it could be left on
    // the back stack and re-shown later with whatever the shared state happened to
    // hold, which is how tapping one category could surface a file from another.
    var showPlayer by remember { mutableStateOf(false) }
    var viewerItems by remember { mutableStateOf<List<String>>(emptyList()) }
    var viewerStart by remember { mutableStateOf("") }

    /** Never pops the start destination, which would leave an empty NavHost. */
    fun goBack() {
        val from = nav.currentBackStackEntry?.destination?.route
        val to = nav.previousBackStackEntry?.destination?.route
        com.shahabcodes.filestorm.data.Diagnostics.log("NAV", "back from=$from to=${to ?: "NONE"}")
        if (nav.previousBackStackEntry != null) nav.popBackStack()
    }

    fun openViewer(paths: List<String>, index: Int) {
        val start = paths.getOrNull(index)
        com.shahabcodes.filestorm.data.Diagnostics.log(
            "VIEWER",
            "open request index=$index of ${paths.size} start=${start ?: "NULL"} " +
                "first=${paths.firstOrNull()}",
        )
        if (start == null) return
        viewerStart = start
        viewerItems = paths
    }

    /**
     * A screen that is being navigated away from is still composed and still
     * hit-testable while the transition runs, so a tap aimed at the incoming
     * screen can be delivered to the outgoing one instead. That is how tapping
     * Videos on the home screen could open a file from the Images list: the
     * touch landed on the Images grid that had not been torn down yet.
     *
     * The test is top-of-back-stack, not RESUMED. A screen animating *in* is
     * already the top entry but is not RESUMED until the transition ends, so a
     * lifecycle check would swallow real taps for the length of that animation.
     * The back stack updates the instant we navigate, so the incoming screen
     * accepts taps immediately and the outgoing one stops accepting them
     * immediately, which is exactly the split we want.
     */
    fun NavBackStackEntry.ifCurrent(action: () -> Unit) {
        if (nav.currentBackStackEntry?.id == id) {
            action()
        } else {
            com.shahabcodes.filestorm.data.Diagnostics.log(
                "BLOCKED",
                "stale tap on ${destination.route}, top is " +
                    "${nav.currentBackStackEntry?.destination?.route}",
            )
        }
    }

    fun openBrowser(path: String) {
        // A locked folder holds nothing but opaque containers, so browsing it
        // as an ordinary folder would only ever show scrambled names.
        if (com.shahabcodes.filestorm.data.vault.VaultFolder.isVault(File(path))) {
            nav.navigate("vault?path=" + Uri.encode(path))
            return
        }
        val safe = if (File(path).exists()) path else FileRepository.rootPath
        com.shahabcodes.filestorm.ui.browser.openFolderGated(context, safe, File(safe).name) {
            com.shahabcodes.filestorm.data.BrowserTabs.open(safe)
            nav.navigate("browse") { launchSingleTop = true }
        }
    }

    // Any screen that opens a file can now hand an archive to its own screen
    // without every one of them needing a navigation callback threaded in.
    androidx.compose.runtime.DisposableEffect(nav) {
        com.shahabcodes.filestorm.ui.browser.onOpenArchive = { path ->
            nav.navigate("archive?path=" + Uri.encode(path))
        }
        com.shahabcodes.filestorm.ui.browser.onOpenVault = { path ->
            nav.navigate("vault?path=" + Uri.encode(path))
        }
        onDispose {
            com.shahabcodes.filestorm.ui.browser.onOpenArchive = null
            com.shahabcodes.filestorm.ui.browser.onOpenVault = null
        }
    }

    NavHost(navController = nav, startDestination = "home") {
        composable("home") { entry ->
            HomeScreen(
                onOpenFolder = { path -> entry.ifCurrent { openBrowser(path) } },
                onOpenCategory = { kind ->
                    entry.ifCurrent {
                        com.shahabcodes.filestorm.data.Diagnostics.log("NAV", "open category ${kind.name}")
                        nav.navigate("category/${kind.name}")
                    }
                },
                onOpenTransfer = { entry.ifCurrent { showTransferSheet = true } },
                onOpenSettings = { entry.ifCurrent { nav.navigate("settings") } },
                onOpenTrash = { entry.ifCurrent { nav.navigate("trash") } },
                onOpenJobs = { entry.ifCurrent { nav.navigate("jobs") } },
                onOpenDuplicates = { entry.ifCurrent { nav.navigate("duplicates") } },
                onOpenViewer = { paths, index -> entry.ifCurrent { openViewer(paths, index) } },
                onOpenInsight = { card ->
                    entry.ifCurrent { nav.navigate("insight/${card.name}") }
                },
            )
        }
        composable("browse") { entry ->
            BrowserScreen(
                onExit = { entry.ifCurrent { goBack() } },
                onOpenTransfer = { entry.ifCurrent { showTransferSheet = true } },
                onArrange = { p, mode ->
                    entry.ifCurrent {
                        nav.navigate("arrange?path=" + Uri.encode(p) + "&mode=" + mode.name)
                    }
                },
                onOpenViewer = { paths, index -> entry.ifCurrent { openViewer(paths, index) } },
            )
        }
        composable("category/{kind}") { backStack ->
            val kind = runCatching {
                FileKind.valueOf(backStack.arguments?.getString("kind") ?: "IMAGE")
            }.getOrDefault(FileKind.IMAGE)
            // Keying on the entry id guarantees a fresh screen per visit, so one
            // category can never inherit another's list.
            com.shahabcodes.filestorm.data.Diagnostics.log(
                "NAV",
                "compose category route kind=$kind entry=${backStack.id.take(8)} " +
                    "arg=${backStack.arguments?.getString("kind")}",
            )
            androidx.compose.runtime.key(backStack.id, kind) {
                CategoryScreen(
                    kind = kind,
                    onBack = { backStack.ifCurrent { goBack() } },
                    onOpenTransfer = { backStack.ifCurrent { showTransferSheet = true } },
                    onOpenViewer = { paths, index ->
                        backStack.ifCurrent { openViewer(paths, index) }
                    },
                )
            }
        }
        composable("transfer") {
            TransferScreen(onBack = { goBack() })
        }
        composable("settings") { entry ->
            SettingsScreen(
                onBack = { entry.ifCurrent { goBack() } },
                onOpen = { page ->
                    entry.ifCurrent { nav.navigate("settings/" + page.route) }
                },
            )
        }
        composable("settings/{page}") { entry ->
            val page = runCatching {
                com.shahabcodes.filestorm.ui.settings.SettingsPageId.entries
                    .first { it.route == entry.arguments?.getString("page") }
            }.getOrDefault(com.shahabcodes.filestorm.ui.settings.SettingsPageId.APPEARANCE)
            val back = { entry.ifCurrent { goBack() } }
            when (page) {
                com.shahabcodes.filestorm.ui.settings.SettingsPageId.APPEARANCE ->
                    com.shahabcodes.filestorm.ui.settings.AppearanceSettingsScreen(back)
                com.shahabcodes.filestorm.ui.settings.SettingsPageId.DASHBOARD ->
                    com.shahabcodes.filestorm.ui.settings.DashboardSettingsScreen(back)
                com.shahabcodes.filestorm.ui.settings.SettingsPageId.FILES ->
                    com.shahabcodes.filestorm.ui.settings.FilesSettingsScreen(back)
                com.shahabcodes.filestorm.ui.settings.SettingsPageId.PRIVACY ->
                    com.shahabcodes.filestorm.ui.settings.PrivacySettingsScreen(back)
                com.shahabcodes.filestorm.ui.settings.SettingsPageId.VAULT ->
                    com.shahabcodes.filestorm.ui.settings.VaultSettingsScreen(back)
                com.shahabcodes.filestorm.ui.settings.SettingsPageId.IDENTITY ->
                    com.shahabcodes.filestorm.ui.settings.IdentitySettingsScreen(back)
                com.shahabcodes.filestorm.ui.settings.SettingsPageId.ABOUT ->
                    com.shahabcodes.filestorm.ui.settings.AboutSettingsScreen(back)
            }
        }
        composable("trash") {
            com.shahabcodes.filestorm.ui.trash.TrashScreen(onBack = { goBack() })
        }
        composable("jobs") { entry ->
            com.shahabcodes.filestorm.ui.jobs.JobsScreen(
                onBack = { entry.ifCurrent { goBack() } },
                onOpenProgress = { entry.ifCurrent { nav.navigate("jobprogress") } },
                onOpenVerify = { entry.ifCurrent { nav.navigate("verify") } },
            )
        }
        composable("jobprogress") {
            com.shahabcodes.filestorm.ui.jobs.JobProgressScreen(onBack = { goBack() })
        }
        composable("verify") {
            com.shahabcodes.filestorm.ui.jobs.VerifyScreen(onBack = { goBack() })
        }
        composable("arrange?path={path}&mode={mode}") { backStack ->
            val target = Uri.decode(backStack.arguments?.getString("path") ?: FileRepository.rootPath)
            val mode = runCatching {
                com.shahabcodes.filestorm.data.arrange.ArrangeMode.valueOf(
                    backStack.arguments?.getString("mode") ?: "MONTHLY"
                )
            }.getOrDefault(com.shahabcodes.filestorm.data.arrange.ArrangeMode.MONTHLY)
            com.shahabcodes.filestorm.ui.arrange.ArrangeScreen(
                path = target,
                mode = mode,
                onBack = { goBack() },
            )
        }
        composable("insight/{card}") { entry ->
            val card = runCatching {
                com.shahabcodes.filestorm.data.DashboardCard.valueOf(
                    entry.arguments?.getString("card") ?: "BIGGEST_FILES"
                )
            }.getOrDefault(com.shahabcodes.filestorm.data.DashboardCard.BIGGEST_FILES)
            com.shahabcodes.filestorm.ui.home.InsightDetailScreen(
                card = card,
                onBack = { entry.ifCurrent { goBack() } },
                onOpenFolder = { path -> entry.ifCurrent { openBrowser(path) } },
                onOpenViewer = { paths, index -> entry.ifCurrent { openViewer(paths, index) } },
            )
        }
        composable("vault?path={path}") { entry ->
            val target = Uri.decode(entry.arguments?.getString("path").orEmpty())
            com.shahabcodes.filestorm.ui.vault.VaultScreen(
                path = target,
                onBack = { entry.ifCurrent { goBack() } },
                onOpenFolder = { path -> entry.ifCurrent { openBrowser(path) } },
                onOpenViewer = { paths, index -> entry.ifCurrent { openViewer(paths, index) } },
            )
        }
        composable("archive?path={path}") { entry ->
            val target = Uri.decode(entry.arguments?.getString("path").orEmpty())
            com.shahabcodes.filestorm.ui.browser.ArchiveScreen(
                path = target,
                onBack = { entry.ifCurrent { goBack() } },
                onOpenFolder = { path -> entry.ifCurrent { openBrowser(path) } },
            )
        }
        composable("duplicates") {
            com.shahabcodes.filestorm.ui.dup.DuplicatesScreen(onBack = { goBack() })
        }
    }

    // The bar rides above every screen so playback is always reachable, and
    // steps aside while the image/video viewer or the full player is up.
    if (viewerItems.isEmpty() && !showPlayer && !VideoController.inPip) {
        androidx.compose.foundation.layout.Box(Modifier.fillMaxSize()) {
            MiniPlayer(onExpand = { showPlayer = true })
        }
    }
    if (showPlayer) {
        androidx.activity.compose.BackHandler { showPlayer = false }
        com.shahabcodes.filestorm.ui.audio.AudioPlayerScreen(onCollapse = { showPlayer = false })
    }

    if (viewerItems.isNotEmpty()) {
        com.shahabcodes.filestorm.data.Diagnostics.log(
            "VIEWER",
            "showing ${viewerItems.size} item(s) start=$viewerStart",
        )
        com.shahabcodes.filestorm.ui.viewer.ViewerScreen(
            items = viewerItems,
            startPath = viewerStart,
            onBack = {
                viewerItems = emptyList()
                viewerStart = ""
            },
        )
    }

    if (showTransferSheet) {
        TransferSheet(
            onDismiss = { showTransferSheet = false },
            onExpand = {
                showTransferSheet = false
                nav.navigate("transfer")
            },
        )
    }
}

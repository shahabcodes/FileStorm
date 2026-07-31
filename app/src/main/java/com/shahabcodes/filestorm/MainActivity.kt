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

class MainActivity : FragmentActivity() {

    private var hasAccess by mutableStateOf(false)
    private var locked by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        hasAccess = checkAccess()
        locked = Prefs.biometricLock
        com.shahabcodes.filestorm.data.Diagnostics.log("APP", "activity created")

        if (Build.VERSION.SDK_INT >= 33) {
            registerForActivityResult(ActivityResultContracts.RequestPermission()) {}
                .launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        setContent {
            // FLAG_SECURE blanks the recent-apps preview (and blocks screenshots).
            androidx.compose.runtime.LaunchedEffect(Prefs.secureScreen) {
                if (Prefs.secureScreen) {
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

@Composable
private fun AppNav() {
    val nav = rememberNavController()
    val context = androidx.compose.ui.platform.LocalContext.current
    var showTransferSheet by remember { mutableStateOf(false) }
    // The viewer is an overlay, not a destination. As a route it could be left on
    // the back stack and re-shown later with whatever the shared state happened to
    // hold, which is how tapping one category could surface a file from another.
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
        val safe = if (File(path).exists()) path else FileRepository.rootPath
        com.shahabcodes.filestorm.ui.browser.openFolderGated(context, safe, File(safe).name) {
            com.shahabcodes.filestorm.data.BrowserTabs.open(safe)
            nav.navigate("browse") { launchSingleTop = true }
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
                onOpenApps = { entry.ifCurrent { nav.navigate("apps") } },
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
        composable("settings") {
            SettingsScreen(onBack = { goBack() })
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
        composable("apps") { entry ->
            com.shahabcodes.filestorm.ui.home.AppStorageScreen(
                onBack = { entry.ifCurrent { goBack() } },
            )
        }
        composable("duplicates") {
            com.shahabcodes.filestorm.ui.dup.DuplicatesScreen(onBack = { goBack() })
        }
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

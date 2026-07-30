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

        if (Build.VERSION.SDK_INT >= 33) {
            registerForActivityResult(ActivityResultContracts.RequestPermission()) {}
                .launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        setContent {
            FileStormTheme {
                Surface(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(fsColors.groupedBackground),
                    color = fsColors.groupedBackground,
                ) {
                    when {
                        locked -> LockScreen(onRequestUnlock = { promptUnlock() })
                        hasAccess -> AppNav()
                        else -> PermissionScreen(onGrantClick = { requestAccess() })
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
            title = "Unlock FileStorm",
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
    var showTransferSheet by remember { mutableStateOf(false) }

    NavHost(navController = nav, startDestination = "home") {
        composable("home") {
            HomeScreen(
                onOpenFolder = { path -> nav.navigate("browse?path=${Uri.encode(path)}") },
                onOpenCategory = { kind -> nav.navigate("category/${kind.name}") },
                onOpenTransfer = { showTransferSheet = true },
                onOpenSettings = { nav.navigate("settings") },
            )
        }
        composable("browse?path={path}") { backStack ->
            val path = Uri.decode(backStack.arguments?.getString("path") ?: FileRepository.rootPath)
            BrowserScreen(
                path = if (File(path).exists()) path else FileRepository.rootPath,
                onOpenFolder = { p -> nav.navigate("browse?path=${Uri.encode(p)}") },
                onBack = { nav.popBackStack() },
                onOpenTransfer = { showTransferSheet = true },
            )
        }
        composable("category/{kind}") { backStack ->
            val kind = FileKind.valueOf(backStack.arguments?.getString("kind") ?: "IMAGE")
            CategoryScreen(
                kind = kind,
                onBack = { nav.popBackStack() },
                onOpenTransfer = { showTransferSheet = true },
            )
        }
        composable("transfer") {
            TransferScreen(onBack = { nav.popBackStack() })
        }
        composable("settings") {
            SettingsScreen(onBack = { nav.popBackStack() })
        }
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

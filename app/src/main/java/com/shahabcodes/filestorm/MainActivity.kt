package com.shahabcodes.filestorm

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.shahabcodes.filestorm.data.FileKind
import com.shahabcodes.filestorm.data.FileRepository
import com.shahabcodes.filestorm.ui.PermissionScreen
import com.shahabcodes.filestorm.ui.browser.BrowserScreen
import com.shahabcodes.filestorm.ui.browser.CategoryScreen
import com.shahabcodes.filestorm.ui.home.HomeScreen
import com.shahabcodes.filestorm.ui.theme.FileStormTheme
import com.shahabcodes.filestorm.ui.theme.fsColors
import com.shahabcodes.filestorm.ui.transfer.TransferScreen
import java.io.File

class MainActivity : ComponentActivity() {

    private var hasAccess by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        hasAccess = checkAccess()

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
                    if (hasAccess) AppNav() else PermissionScreen(onGrantClick = { requestAccess() })
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        hasAccess = checkAccess()
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
    NavHost(navController = nav, startDestination = "home") {
        composable("home") {
            HomeScreen(
                onOpenFolder = { path -> nav.navigate("browse?path=${Uri.encode(path)}") },
                onOpenCategory = { kind -> nav.navigate("category/${kind.name}") },
                onOpenTransfer = { nav.navigate("transfer") },
            )
        }
        composable("browse?path={path}") { backStack ->
            val path = Uri.decode(backStack.arguments?.getString("path") ?: FileRepository.rootPath)
            BrowserScreen(
                path = if (File(path).exists()) path else FileRepository.rootPath,
                onOpenFolder = { p -> nav.navigate("browse?path=${Uri.encode(p)}") },
                onBack = { nav.popBackStack() },
                onOpenTransfer = { nav.navigate("transfer") },
            )
        }
        composable("category/{kind}") { backStack ->
            val kind = FileKind.valueOf(backStack.arguments?.getString("kind") ?: "IMAGE")
            CategoryScreen(
                kind = kind,
                onBack = { nav.popBackStack() },
                onOpenTransfer = { nav.navigate("transfer") },
            )
        }
        composable("transfer") {
            TransferScreen(onBack = { nav.popBackStack() })
        }
    }
}

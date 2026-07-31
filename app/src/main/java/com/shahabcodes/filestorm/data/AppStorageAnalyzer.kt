package com.shahabcodes.filestorm.data

import android.app.AppOpsManager
import android.app.usage.StorageStats
import android.app.usage.StorageStatsManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Process
import android.os.storage.StorageManager
import android.provider.Settings
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

/**
 * How much room each installed app takes: its own code, its data and its cache.
 *
 * Android will not hand this over to an ordinary permission — it needs Usage
 * Access, which the user grants in system Settings. Everything here degrades
 * gracefully when that has not been granted, so the dashboard can ask for it
 * rather than simply showing nothing.
 */
object AppStorageAnalyzer {

    data class AppStat(
        val packageName: String,
        val label: String,
        val appBytes: Long,
        val dataBytes: Long,
        val cacheBytes: Long,
        val isSystem: Boolean,
    ) {
        val totalBytes: Long get() = appBytes + dataBytes + cacheBytes
    }

    /**
     * [apps] is only the heaviest apps, so the totals are carried explicitly
     * rather than summed from the list — otherwise a restored snapshot would
     * under-report everything that did not make the cut.
     */
    data class Snapshot(
        val apps: List<AppStat>,
        val appCount: Int,
        val totalBytes: Long,
        val codeBytes: Long,
        val dataBytes: Long,
        val cacheBytes: Long,
        val scannedAt: Long,
    )

    private lateinit var sp: SharedPreferences
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    var snapshot by mutableStateOf<Snapshot?>(null)
        private set
    var scanning by mutableStateOf(false)
        private set

    fun init(context: Context) {
        sp = context.getSharedPreferences("filestorm_appstorage", Context.MODE_PRIVATE)
        snapshot = runCatching {
            val json = sp.getString("snapshot", null) ?: return@runCatching null
            val o = JSONObject(json)
            val array = o.getJSONArray("apps")
            Snapshot(
                apps = buildList {
                    for (i in 0 until array.length()) {
                        val a = array.getJSONObject(i)
                        add(
                            AppStat(
                                packageName = a.getString("pkg"),
                                label = a.getString("label"),
                                appBytes = a.getLong("app"),
                                dataBytes = a.getLong("data"),
                                cacheBytes = a.getLong("cache"),
                                isSystem = a.optBoolean("system"),
                            )
                        )
                    }
                },
                appCount = o.optInt("appCount"),
                totalBytes = o.optLong("total"),
                codeBytes = o.optLong("code"),
                dataBytes = o.optLong("data"),
                cacheBytes = o.optLong("cache"),
                scannedAt = o.optLong("scannedAt"),
            )
        }.getOrNull()
    }

    /** True once the user has granted Usage Access to File Storm. */
    fun hasPermission(context: Context): Boolean = runCatching {
        val ops = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = ops.unsafeCheckOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName,
        )
        mode == AppOpsManager.MODE_ALLOWED
    }.getOrDefault(false)

    /** Opens the system screen where Usage Access is granted. */
    fun requestPermission(context: Context) {
        runCatching {
            context.startActivity(
                Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }.onFailure {
            runCatching {
                context.startActivity(
                    Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
        }
    }

    fun refresh(context: Context) {
        if (scanning || !hasPermission(context)) return
        scanning = true
        val appContext = context.applicationContext
        scope.launch {
            val result = runCatching { measure(appContext) }.getOrNull()
            if (result != null) {
                snapshot = result
                persist(result)
            }
            scanning = false
        }
    }

    private fun measure(context: Context): Snapshot {
        val pm = context.packageManager
        val ssm = context.getSystemService(Context.STORAGE_STATS_SERVICE) as StorageStatsManager
        val user = Process.myUserHandle()
        val installed = pm.getInstalledApplications(PackageManager.GET_META_DATA)
        val out = mutableListOf<AppStat>()
        for (info in installed) {
            // A package can vanish mid-scan, and some are simply not queryable;
            // one failure must not lose the whole report.
            val stats: StorageStats = runCatching {
                ssm.queryStatsForPackage(StorageManager.UUID_DEFAULT, info.packageName, user)
            }.getOrNull() ?: continue
            out.add(
                AppStat(
                    packageName = info.packageName,
                    label = runCatching { pm.getApplicationLabel(info).toString() }
                        .getOrDefault(info.packageName),
                    appBytes = stats.appBytes,
                    dataBytes = stats.dataBytes,
                    cacheBytes = stats.cacheBytes,
                    isSystem = (info.flags and ApplicationInfo.FLAG_SYSTEM) != 0,
                )
            )
        }
        return Snapshot(
            apps = out.sortedByDescending { it.totalBytes },
            appCount = out.size,
            totalBytes = out.sumOf { it.totalBytes },
            codeBytes = out.sumOf { it.appBytes },
            dataBytes = out.sumOf { it.dataBytes },
            cacheBytes = out.sumOf { it.cacheBytes },
            scannedAt = System.currentTimeMillis(),
        )
    }

    private fun persist(s: Snapshot) {
        runCatching {
            val array = JSONArray()
            // The All Apps screen needs every entry, so the whole list is kept.
            s.apps.forEach { app ->
                array.put(
                    JSONObject()
                        .put("pkg", app.packageName)
                        .put("label", app.label)
                        .put("app", app.appBytes)
                        .put("data", app.dataBytes)
                        .put("cache", app.cacheBytes)
                        .put("system", app.isSystem)
                )
            }
            sp.edit()
                .putString(
                    "snapshot",
                    JSONObject()
                        .put("scannedAt", s.scannedAt)
                        .put("appCount", s.appCount)
                        .put("total", s.totalBytes)
                        .put("code", s.codeBytes)
                        .put("data", s.dataBytes)
                        .put("cache", s.cacheBytes)
                        .put("apps", array)
                        .toString(),
                )
                .apply()
        }
    }
}

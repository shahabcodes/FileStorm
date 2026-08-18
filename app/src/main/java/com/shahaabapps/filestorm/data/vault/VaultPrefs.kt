package com.shahaabapps.filestorm.data.vault

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

enum class AutoLock(val label: String, val millis: Long) {
    ON_LEAVE("When I leave the app", 0),
    MIN_1("After 1 minute", 60_000),
    MIN_5("After 5 minutes", 300_000),
    MIN_15("After 15 minutes", 900_000),
    NEVER("Never, until I lock it", Long.MAX_VALUE),
}

/** Every vault setting, all of them adjustable. */
object VaultPrefs {
    private lateinit var sp: SharedPreferences

    var verifyAfterWrite by mutableStateOf(true)
        private set
    var includeHidden by mutableStateOf(true)
        private set
    var chunkSizeKb by mutableStateOf(1024)
        private set
    var originalsToTrash by mutableStateOf(false)
        private set
    var autoLock by mutableStateOf(AutoLock.ON_LEAVE)
        private set
    var strength by mutableStateOf(VaultCrypto.Strength.STANDARD)
        private set
    var logLevel by mutableStateOf(VaultLogLevel.ERRORS)
        private set
    var logFilenames by mutableStateOf(false)
        private set
    var logSizeMb by mutableStateOf(5)
        private set
    var chargingOnly by mutableStateOf(false)
        private set
    /** 0 means auto: a sensible share of the cores, capped so the phone stays usable. */
    var workers by mutableStateOf(0)
        private set
    var pauseBelowBattery by mutableStateOf(15)
        private set

    fun init(context: Context) {
        sp = context.getSharedPreferences("filestorm_vault", Context.MODE_PRIVATE)
        verifyAfterWrite = sp.getBoolean("verify", true)
        includeHidden = sp.getBoolean("hidden", true)
        chunkSizeKb = sp.getInt("chunk_kb", 1024)
        originalsToTrash = sp.getBoolean("to_trash", false)
        chargingOnly = sp.getBoolean("charging_only", false)
        workers = sp.getInt("workers", 0)
        pauseBelowBattery = sp.getInt("battery_floor", 15)
        logFilenames = sp.getBoolean("log_names", false)
        logSizeMb = sp.getInt("log_mb", 5)
        autoLock = runCatching { AutoLock.valueOf(sp.getString("auto_lock", AutoLock.ON_LEAVE.name)!!) }
            .getOrDefault(AutoLock.ON_LEAVE)
        strength = runCatching {
            VaultCrypto.Strength.valueOf(sp.getString("strength", VaultCrypto.Strength.STANDARD.name)!!)
        }.getOrDefault(VaultCrypto.Strength.STANDARD)
        logLevel = runCatching { VaultLogLevel.valueOf(sp.getString("log_level", VaultLogLevel.ERRORS.name)!!) }
            .getOrDefault(VaultLogLevel.ERRORS)
    }

    fun updateVerify(value: Boolean) { verifyAfterWrite = value; sp.edit().putBoolean("verify", value).apply() }
    fun updateIncludeHidden(value: Boolean) { includeHidden = value; sp.edit().putBoolean("hidden", value).apply() }
    fun updateChunkKb(value: Int) { chunkSizeKb = value; sp.edit().putInt("chunk_kb", value).apply() }
    fun updateToTrash(value: Boolean) { originalsToTrash = value; sp.edit().putBoolean("to_trash", value).apply() }
    fun updateWorkers(value: Int) { workers = value; sp.edit().putInt("workers", value).apply() }
    fun updateChargingOnly(value: Boolean) { chargingOnly = value; sp.edit().putBoolean("charging_only", value).apply() }
    fun updateBatteryFloor(value: Int) { pauseBelowBattery = value; sp.edit().putInt("battery_floor", value).apply() }
    fun updateLogFilenames(value: Boolean) { logFilenames = value; sp.edit().putBoolean("log_names", value).apply() }
    fun updateLogSizeMb(value: Int) { logSizeMb = value; sp.edit().putInt("log_mb", value).apply() }
    fun updateAutoLock(value: AutoLock) { autoLock = value; sp.edit().putString("auto_lock", value.name).apply() }
    fun updateStrength(value: VaultCrypto.Strength) {
        strength = value
        sp.edit().putString("strength", value.name).apply()
    }
    fun updateLogLevel(value: VaultLogLevel) { logLevel = value; sp.edit().putString("log_level", value.name).apply() }

    fun options(removeOriginal: (java.io.File) -> Boolean): VaultOptions = VaultOptions(
        verifyAfterWrite = verifyAfterWrite,
        includeHidden = includeHidden,
        chunkSize = chunkSizeKb * 1024,
        removeOriginal = removeOriginal,
        workers = resolvedWorkers(),
    )

    /** Leaves a core free so the phone does not become unusable mid-run. */
    fun resolvedWorkers(): Int {
        if (workers > 0) return workers
        val cores = Runtime.getRuntime().availableProcessors()
        return (cores - 2).coerceIn(1, 4)
    }
}

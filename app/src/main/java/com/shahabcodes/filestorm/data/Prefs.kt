package com.shahabcodes.filestorm.data

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** App settings, Compose-observable and persisted. */
object Prefs {
    private lateinit var sp: SharedPreferences

    var showHidden by mutableStateOf(false)
        private set

    fun init(context: Context) {
        sp = context.getSharedPreferences("filestorm", Context.MODE_PRIVATE)
        showHidden = sp.getBoolean("show_hidden", false)
    }

    fun updateShowHidden(value: Boolean) {
        showHidden = value
        sp.edit().putBoolean("show_hidden", value).apply()
    }
}

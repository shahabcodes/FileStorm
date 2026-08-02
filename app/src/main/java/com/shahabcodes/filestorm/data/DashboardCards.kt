package com.shahabcodes.filestorm.data

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * The cards the dashboard can show. Each one is opt-in and, crucially, nothing
 * is computed for a card that is switched off — [StorageInsights] is told which
 * cards are enabled and skips the work for the rest, so a disabled card costs
 * exactly nothing rather than being calculated and then hidden.
 *
 * Declaration order means nothing here; the dashboard follows [DashboardPrefs.order].
 */
enum class DashboardCard(
    val key: String,
    val label: String,
    val blurb: String,
    /** True when this card's data comes from the whole-storage walk. */
    val needsScan: Boolean,
) {
    STORAGE("storage", "Storage", "Used and free space, with a breakdown by file type", true),
    RECLAIM("reclaim", "Reclaim Space", "Trash, duplicates, empty folders and zero-byte files", true),
    BIGGEST_FILES("biggest", "Biggest Files", "The largest files on the device, newest scan", true),
    LARGEST_FOLDERS("folders", "Largest Folders", "Which folders are holding the most", true),
    GROWTH("growth", "Monthly Footprint", "How much of what you still have is dated to each month", true),
    RECENT("recent", "Recent Files", "Everything added or changed in the last week", true),
    APPS("apps", "Apps", "Per-app storage — needs Usage Access from system settings", false),
}

/**
 * Which dashboard cards are switched on, and the order they appear in.
 *
 * The default order follows the questions in the order people ask them: how
 * full am I, what has been piling up, what can I get back, and only then where
 * exactly it all sits. Every part of that is rearrangeable in Settings.
 */
object DashboardPrefs {
    private lateinit var sp: SharedPreferences

    private val defaultOrder = listOf(
        DashboardCard.STORAGE,
        DashboardCard.GROWTH,
        DashboardCard.RECLAIM,
        DashboardCard.BIGGEST_FILES,
        DashboardCard.LARGEST_FOLDERS,
        DashboardCard.RECENT,
        DashboardCard.APPS,
    )

    private val defaultEnabled = setOf(
        DashboardCard.STORAGE,
        DashboardCard.GROWTH,
        DashboardCard.RECLAIM,
        DashboardCard.BIGGEST_FILES,
        DashboardCard.LARGEST_FOLDERS,
    )

    var enabled by mutableStateOf(defaultEnabled)
        private set
    var order by mutableStateOf(defaultOrder)
        private set

    fun init(context: Context) {
        sp = context.getSharedPreferences("filestorm_dashboard_cards", Context.MODE_PRIVATE)

        val storedEnabled = sp.getStringSet("enabled", null)
        enabled = if (storedEnabled == null) defaultEnabled
        else DashboardCard.entries.filter { it.key in storedEnabled }.toSet()

        val storedOrder = sp.getString("order", null)
            ?.split(",")
            ?.mapNotNull { key -> DashboardCard.entries.firstOrNull { it.key == key } }
            .orEmpty()
        // A card added in a later version is unknown to a stored order, so the
        // leftovers are appended rather than dropped off the dashboard.
        order = storedOrder + defaultOrder.filterNot { it in storedOrder }
    }

    fun isEnabled(card: DashboardCard): Boolean = card in enabled

    fun setEnabled(card: DashboardCard, value: Boolean) {
        enabled = if (value) enabled + card else enabled - card
        sp.edit().putStringSet("enabled", enabled.map { it.key }.toSet()).apply()
    }

    /** Moves a card one place up or down the dashboard. */
    fun move(card: DashboardCard, up: Boolean) {
        val current = order.indexOf(card)
        val target = if (up) current - 1 else current + 1
        if (current < 0 || target !in order.indices) return
        order = order.toMutableList().apply {
            removeAt(current)
            add(target, card)
        }
        persistOrder()
    }

    fun resetOrder() {
        order = defaultOrder
        persistOrder()
    }

    private fun persistOrder() {
        sp.edit().putString("order", order.joinToString(",") { it.key }).apply()
    }

    /** The cards the dashboard actually renders, in the chosen order. */
    fun visibleInOrder(): List<DashboardCard> = order.filter { it in enabled }

    /** Cards needing the storage walk. Empty means the walk never runs. */
    fun scanningCards(): Set<DashboardCard> = enabled.filter { it.needsScan }.toSet()
}

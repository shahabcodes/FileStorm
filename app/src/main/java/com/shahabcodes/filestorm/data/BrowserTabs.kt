package com.shahabcodes.filestorm.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** MiX-style browser tabs: each tab keeps its own folder history. */
object BrowserTabs {

    data class Tab(val id: Long, val stack: List<String>) {
        val current: String get() = stack.last()
    }

    private var nextId = 1L

    var tabs by mutableStateOf<List<Tab>>(emptyList())
        private set
    var activeIndex by mutableIntStateOf(0)
        private set

    val active: Tab get() = tabs[activeIndex.coerceIn(0, tabs.lastIndex)]

    /** Entry from home/favorites: reuse the active tab, or create the first one. */
    fun open(path: String) {
        if (tabs.isEmpty()) {
            tabs = listOf(Tab(nextId++, listOf(path)))
            activeIndex = 0
        } else {
            val i = activeIndex.coerceIn(0, tabs.lastIndex)
            tabs = tabs.toMutableList().also { it[i] = it[i].copy(stack = listOf(path)) }
            activeIndex = i
        }
    }

    fun push(path: String) {
        if (tabs.isEmpty()) return open(path)
        val i = activeIndex.coerceIn(0, tabs.lastIndex)
        tabs = tabs.toMutableList().also { it[i] = it[i].copy(stack = it[i].stack + path) }
    }

    /** Pops within the active tab. Returns false when there is nothing left to pop. */
    fun pop(): Boolean {
        if (tabs.isEmpty()) return false
        val i = activeIndex.coerceIn(0, tabs.lastIndex)
        val tab = tabs[i]
        if (tab.stack.size <= 1) return false
        tabs = tabs.toMutableList().also { it[i] = tab.copy(stack = tab.stack.dropLast(1)) }
        return true
    }

    fun newTab(root: String) {
        tabs = tabs + Tab(nextId++, listOf(root))
        activeIndex = tabs.lastIndex
    }

    fun select(index: Int) {
        if (index in tabs.indices) activeIndex = index
    }

    fun close(index: Int) {
        if (index !in tabs.indices || tabs.size <= 1) return
        tabs = tabs.filterIndexed { i, _ -> i != index }
        activeIndex = activeIndex.coerceIn(0, tabs.lastIndex)
    }
}

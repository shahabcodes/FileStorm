package com.shahabcodes.filestorm.ui.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Search is the thing that stops a hub of seven pages from ever being worse
 * than the one long page it replaced, so it is worth pinning down: the words
 * people actually type have to find the right setting, and the hidden developer
 * tools have to stay hidden.
 */
class SettingsIndexTest {

    private fun titlesFor(query: String) = SettingsIndex.search(query).map { it.title }

    @Test
    fun `everyday words find the right setting`() {
        val expectations = mapOf(
            "dark" to "Light and dark mode",
            "night" to "Light and dark mode",
            "fingerprint" to "App lock",
            "password" to "App lock",
            "spinner" to "Loading indicator",
            "treemap" to "Chart style",
            "pink" to "Theme",
            "rename" to "App name",
            "blur" to "Hide the app in recents",
            "concurrency" to "Files encrypted at once",
            "faster" to "Files encrypted at once",
            "trash" to "Originals to Trash",
        )
        expectations.forEach { (query, expected) ->
            val results = titlesFor(query)
            assertTrue(
                "'$query' should find '$expected' but found $results",
                results.contains(expected),
            )
        }
    }

    @Test
    fun `an exact title ranks first`() {
        assertEquals("App lock", titlesFor("app lock").first())
        assertEquals("Chart style", titlesFor("chart style").first())
    }

    @Test
    fun `a title match beats a keyword mention`() {
        // Several vault entries mention locking; the one named for it wins.
        assertEquals("App lock", titlesFor("lock").first())
    }

    @Test
    fun `two words narrow rather than widen`() {
        val one = SettingsIndex.search("vault").size
        val two = SettingsIndex.search("vault log").size
        assertTrue("adding a word should not return more", two <= one)
        assertTrue(titlesFor("vault log").contains("Vault log"))
    }

    @Test
    fun `search is case and space insensitive`() {
        assertEquals(titlesFor("dark"), titlesFor("  DARK "))
    }

    @Test
    fun `an empty query returns nothing rather than everything`() {
        assertTrue(SettingsIndex.search("").isEmpty())
        assertTrue(SettingsIndex.search("   ").isEmpty())
    }

    @Test
    fun `nonsense finds nothing`() {
        assertTrue(SettingsIndex.search("zzzzqqq").isEmpty())
    }

    @Test
    fun `the hidden developer tools are not searchable`() {
        // Indexing these would undo the point of hiding them behind the
        // version tap.
        listOf("diagnostics", "troubleshooting", "overlay", "debug overlay").forEach { query ->
            val hits = titlesFor(query)
            assertFalse(
                "'$query' exposed hidden tools: $hits",
                hits.any { it.contains("Diagnostic", ignoreCase = true) },
            )
        }
    }

    @Test
    fun `every page is reachable through search`() {
        val reachable = SettingsIndex.entries.map { it.page }.toSet()
        SettingsPageId.entries.forEach {
            assertTrue("no way to search into $it", reachable.contains(it))
        }
    }

    @Test
    fun `every entry names a page title that matches its page`() {
        val expected = mapOf(
            SettingsPageId.APPEARANCE to "Appearance",
            SettingsPageId.DASHBOARD to "Dashboard",
            SettingsPageId.FILES to "Files & Folders",
            SettingsPageId.PRIVACY to "Privacy & Security",
            SettingsPageId.VAULT to "Vault",
            SettingsPageId.IDENTITY to "App Icon & Name",
            SettingsPageId.ABOUT to "About",
        )
        SettingsIndex.entries.forEach { entry ->
            assertEquals(
                "${entry.title} points at the wrong page name",
                expected[entry.page],
                entry.pageTitle,
            )
        }
    }
}

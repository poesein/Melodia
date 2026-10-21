package com.lin0721.linmusic.feature.personallibrary

import org.junit.Assert.*
import org.junit.Test
import com.lin0721.linmusic.feature.library.ui.LibraryItem
import com.lin0721.linmusic.feature.library.ui.LibraryItemType

class LibraryPageLayoutTest {
    @Test fun firstUsePreservesTheOriginalLocalSectionOrderAndShowsAllSections() {
        assertEquals(listOf("local_playlists", "recent", "artists", "albums"), LibraryPageLayout().normalized(LibraryPage.LOCAL).sections.map { it.key })
        assertTrue(LibraryPageLayout().normalized(LibraryPage.LOCAL).sections.all { it.visible })
        val personal = LibraryPageLayout().normalized(LibraryPage.PERSONAL)
        assertEquals(3, personal.sections.count { it.visible })
        assertTrue(personal.sections.filter { it.key.startsWith("local_") }.none { it.visible })
    }
    @Test fun normalizationKeepsSavedOrderAndHiddenChoicesWhileRemovingUnknownAndDuplicateSections() {
        val layout = LibraryPageLayout(sections = listOf(LibrarySectionLayout("albums", false), LibrarySectionLayout("unknown"), LibrarySectionLayout("albums")))
            .normalized(LibraryPage.PERSONAL)
        assertEquals(listOf("albums", "playlists", "artists"), layout.sections.take(3).map { it.key })
        assertFalse(layout.sections.first().visible)
    }
    @Test fun moveRespectsEdgesAndDoesNotShowAHiddenSection() {
        val layout = LibraryPageLayout(sections = listOf(LibrarySectionLayout("albums", false))).normalized(LibraryPage.PERSONAL)
        assertEquals(layout, layout.moved(LibraryPage.PERSONAL, "albums", -1))
        val moved = layout.moved(LibraryPage.PERSONAL, "albums", 1)
        assertEquals(listOf("playlists", "albums", "artists"), moved.sections.take(3).map { it.key })
        assertFalse(moved.sections[1].visible)
    }
    @Test fun listAndCoverColumnsAreIndependentAndBounded() {
        val list = LibraryPageLayout(listColumns = 2, coverColumns = 5).normalized(LibraryPage.PERSONAL)
        assertEquals(2, list.playlistColumns)
        assertEquals(5, list.copy(playlistMode = "covers").playlistColumns)
        assertEquals(1, list.copy(listColumns = -1).normalized(LibraryPage.PERSONAL).listColumns)
        assertEquals(5, list.copy(coverColumns = 100).normalized(LibraryPage.PERSONAL).coverColumns)
    }
    @Test fun previewUsesAtMostFiveActualRowsAndDoesNotPadAPartialRow() {
        fun records(n: Int) = (1..n).map { LibraryItem("$it", "Playlist $it", "", "", LibraryItemType.PLAYLIST) }
        assertTrue(playlistPreviewRows(emptyList(), 5).isEmpty())
        assertEquals(listOf(3), playlistPreviewRows(records(3), 5).map { it.size })
        assertEquals(listOf(5, 2), playlistPreviewRows(records(7), 5).map { it.size })
        assertEquals(listOf(5, 5, 5, 5, 5), playlistPreviewRows(records(30), 5).map { it.size })
        assertEquals((1..5).map { "$it" }, playlistPreviewRows(records(30), 1).flatten().map { it.id })
    }
    @Test fun addingLocalChoicesToAnExistingThreeSectionLayoutDoesNotChangeItsVisibilityOrOrder() {
        val old = LibraryPageLayout(sections = listOf(LibrarySectionLayout("albums"), LibrarySectionLayout("artists", false), LibrarySectionLayout("playlists")))
        val upgraded = old.normalized(LibraryPage.PERSONAL)
        assertEquals(old.sections, upgraded.sections.take(3))
        assertEquals(4, upgraded.sections.drop(3).size)
        assertTrue(upgraded.sections.drop(3).none { it.visible })
    }
    @Test fun manualOrderUsesStableIdsAppendsNewRecordsAndRetainsMissingRecords() {
        val original = LibraryPageLayout(itemOrders = mapOf("artists" to listOf("b", "absent", "a")))
        assertEquals(listOf("b", "a", "c"), original.orderedItems("artists", listOf("a", "b", "c")) { it })
        val moved = original.moveItem("artists", "c", 0, listOf("a", "b", "c"))
        assertEquals(listOf("c", "b", "a"), moved.orderedItems("artists", listOf("a", "b", "c")) { it })
        assertTrue("absent" in moved.itemOrders.getValue("artists"))
        assertEquals(moved, moved.moveItem("artists", "nonexistent", 0, listOf("a", "b", "c")))
    }
    @Test fun shelfCountIsAppliedAfterSortingAndZeroDisplaysEverything() {
        val records = (1..25).map { "$it" }
        val limited = LibraryPageLayout(itemLimits = mapOf("albums" to 2), itemOrders = mapOf("albums" to listOf("25", "24")))
        assertEquals(listOf("25", "24"), limited.shelfItems("albums", records) { it })
        assertEquals(25, limited.copy(itemLimits = mapOf("albums" to 0)).shelfItems("albums", records) { it }.size)
        assertEquals(10, LibraryPageLayout().shelfItems("albums", records) { it }.size)
    }
    @Test fun localOrderingIsSharedBetweenEntryPointsWhileCloudAndPageSectionLayoutsRemainSeparate() {
        val personal = LibraryPageLayout(sections = listOf(LibrarySectionLayout("local_artists")), itemOrders = mapOf("artists" to listOf("cloud2", "cloud1")))
        val local = LibraryPageLayout(itemLimits = mapOf("artists" to 0), itemOrders = mapOf("artists" to listOf("local2", "local1")))
        val effective = personal.withLocalShelfSettings(local)
        assertEquals(personal.sections, effective.sections)
        assertEquals(listOf("cloud2", "cloud1"), effective.itemOrders["artists"])
        assertEquals(listOf("local2", "local1"), effective.itemOrders["local_artists"])
        assertEquals(0, effective.itemLimit("local_artists"))
    }
    @Test fun normalizedLimitsAndOrdersRejectUnknownKeysAndDuplicateIdsWithoutChangingVisibility() {
        val layout = LibraryPageLayout(itemLimits = mapOf("artists" to -10, "albums" to 9999, "invalid" to 2),
            itemOrders = mapOf("artists" to listOf("b", "", "b", "a"), "invalid" to listOf("x"))).normalized(LibraryPage.LOCAL)
        assertEquals(mapOf("artists" to 0, "albums" to 5000), layout.itemLimits)
        assertEquals(mapOf("artists" to listOf("b", "a")), layout.itemOrders)
        assertTrue(layout.sections.all { it.visible })
    }
}

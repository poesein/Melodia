package com.lin0721.linmusic.feature.personallibrary

import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class PersonalPlaylistSelectionTest {
    @Test fun oldBeta23SettingsShowEveryPlaylistWithoutResettingOrderColumnsOrLimits() {
        val old = Json.decodeFromString<LibraryPageLayout>("""{"version":1,"playlistMode":"covers","listColumns":2,"coverColumns":5,"itemOrders":{"playlists":["-2","10"]},"itemLimits":{"artists":0}}""")
        assertNull(old.visiblePlaylistIds)
        assertTrue(listOf("-2", "10", "new").all(old::playlistIsVisible))
        assertEquals(5, old.coverColumns); assertEquals(2, old.listColumns)
        assertEquals(listOf("-2", "10"), old.itemOrders["playlists"])
        assertEquals(0, old.itemLimit("artists"))
    }
    @Test fun firstExplicitChoiceIncludesOnlySelectedPlaylistsAndDoesNotAddFuturePlaylistsSilently() {
        val selected = LibraryPageLayout().setPlaylistVisible("-2", false, setOf("-2", "10", "20"))
        assertEquals(setOf("10", "20"), selected.visiblePlaylistIds)
        assertFalse(selected.playlistIsVisible("-2")); assertFalse(selected.playlistIsVisible("new"))
        assertTrue(selected.setPlaylistVisible("-2", true, setOf("-2", "10", "20")).playlistIsVisible("-2"))
    }
    @Test fun editingAFetchedSubsetDoesNotDiscardSelectionsForTemporarilyMissingPlaylists() {
        val saved = LibraryPageLayout(visiblePlaylistIds = setOf("10", "temporarily_missing"))
        val edited = saved.setPlaylistVisible("10", false, setOf("10", "20"))
        assertEquals(setOf("temporarily_missing"), edited.visiblePlaylistIds)
        assertFalse(edited.playlistIsVisible("10"))
        assertEquals(emptySet<String>(), LibraryPageLayout(visiblePlaylistIds = emptySet()).visiblePlaylistIds)
    }
    @Test fun normalizationAndOtherLayoutChangesRetainSelection() {
        val selected = LibraryPageLayout(visiblePlaylistIds = setOf("-2", "10", "")).normalized(LibraryPage.PERSONAL)
        assertEquals(setOf("-2", "10"), selected.visiblePlaylistIds)
        assertEquals(selected.visiblePlaylistIds, selected.moved(LibraryPage.PERSONAL, "artists", -1).visiblePlaylistIds)
        assertEquals(selected.visiblePlaylistIds, selected.withLocalShelfSettings(LibraryPageLayout()).visiblePlaylistIds)
    }
    @Test fun newCreationIsVisibleWhilePreviouslyHiddenPlaylistsRemainHidden() {
        val selected = LibraryPageLayout().setPlaylistVisible("20", false, setOf("10", "20"))
        val created = selected.withCreatedPlaylistVisible(30)
        assertTrue(created.playlistIsVisible("30"))
        assertTrue(created.playlistIsVisible("10")); assertFalse(created.playlistIsVisible("20"))
        assertFalse(created.setPlaylistVisible("30", false, setOf("10", "20", "30")).playlistIsVisible("30"))
    }
}

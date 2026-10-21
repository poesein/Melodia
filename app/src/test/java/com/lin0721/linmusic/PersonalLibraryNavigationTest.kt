package com.lin0721.linmusic

import androidx.compose.runtime.snapshots.Snapshot
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class PersonalLibraryNavigationTest {
    private fun snapshot(block: () -> Unit) {
        val state = Snapshot.takeMutableSnapshot()
        try { state.enter(block); state.apply() } finally { state.dispose() }
    }
    @Test fun searchExitRestoresThePersonalLibraryFrameAndDetailBackStack() = snapshot {
        val nav = MelodiaNavigationState()
        nav.openTab(Screen.PersonalLibrary)
        val rootId = nav.currentEntry.id
        nav.openSearch(true)
        nav.openPlaylist(123, false)
        nav.navigateBack()
        assertEquals(Screen.Search, nav.currentScreen)
        assertTrue(nav.returnFromSearch())
        assertEquals(Screen.PersonalLibrary, nav.currentScreen)
        assertEquals(rootId, nav.currentEntry.id)
    }
    @Test fun personalAndOriginalLibrariesRetainIndependentStacksThroughSnapshotRestore() = snapshot {
        val nav = MelodiaNavigationState()
        nav.navigateTo(Screen.Library); nav.openArtist(1)
        nav.navigateTo(Screen.PersonalLibrary); nav.openPlaylist(2, true)
        val saved = nav.toSnapshot()
        val restored = restoreNavigationSnapshot(Json.decodeFromString<NavigationSnapshot>(Json.encodeToString(NavigationSnapshot.serializer(), saved)))
        assertEquals(Screen.Playlist(2, true), restored.currentScreen)
        restored.navigateBack()
        assertEquals(Screen.PersonalLibrary, restored.currentScreen)
        restored.navigateTo(Screen.Library)
        assertEquals(Screen.Artist(1), restored.currentScreen)
        assertEquals(restored.liveEntryIds.size, restored.toSnapshot().let { listOf(it.homeIds, it.musicIds, it.searchIds, it.libraryIds, it.personalLibraryIds, it.localMusicIds, it.downloadsIds, it.cloudIds, it.myProfileIds).flatten().size })
    }
    @Test fun oldSnapshotGetsANewPersonalLibraryRootWithoutChangingItsActiveTab() = snapshot {
        val old = Json.decodeFromString<NavigationSnapshot>("{\"activeTabIndex\":2}")
        val restored = restoreNavigationSnapshot(old)
        assertEquals(Screen.Library, restored.currentScreen)
        restored.openTab(Screen.PersonalLibrary)
        assertEquals(Screen.PersonalLibrary, restored.currentScreen)
        assertFalse(restored.canNavigateBack)
    }
    @Test fun personalDefaultRootAndSavedSearchOriginAreSupported() = snapshot {
        val nav = MelodiaNavigationState()
        nav.updateDefaultRootTab(Screen.PersonalLibrary)
        nav.openTab(Screen.PersonalLibrary); nav.openSearch(true)
        val restored = restoreNavigationSnapshot(nav.toSnapshot())
        assertTrue(restored.returnFromSearch())
        assertEquals(Screen.PersonalLibrary, restored.currentScreen)
        nav.navigateBack()
        assertEquals(Screen.PersonalLibrary, nav.currentScreen)
    }
}

package com.lin0721.linmusic

import androidx.compose.runtime.snapshots.Snapshot
import com.lin0721.linmusic.core.preferences.BottomBarItem
import com.lin0721.linmusic.core.preferences.BottomBarLayout
import com.lin0721.linmusic.feature.home.ui.TAB_MUSIC
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class CollectedAlbumsNavigationTest {
    private fun inSnapshot(block: () -> Unit) = Snapshot.withMutableSnapshot(block)

    @Test fun retiredMusicNavigationRestoresAsAlbumsWithoutLosingOtherStacks() = inSnapshot {
        val raw = """{"homeStack":[{"type":"com.lin0721.linmusic.Screen.Home"},{"type":"com.lin0721.linmusic.Screen.Artist","id":12}],"homeIds":[40,41],"musicStack":[{"type":"com.lin0721.linmusic.Screen.Music"},{"type":"com.lin0721.linmusic.Screen.Playlist","id":42,"isAlbum":true}],"musicIds":[50,51],"activeTabScreen":{"type":"com.lin0721.linmusic.Screen.Music"},"activeTabIndex":7,"showMusicNewWorks":true}"""
        val snapshot = Json { ignoreUnknownKeys = true }.decodeFromString(NavigationSnapshot.serializer(), raw)
        val restored = restoreNavigationSnapshot(snapshot)
        assertEquals(Screen.CollectedAlbums, restored.activeTab)
        assertEquals(Screen.Playlist(42, true), restored.currentScreen)
        assertEquals(51L, restored.currentEntry.id)
        restored.navigateBack()
        assertEquals(Screen.CollectedAlbums, restored.currentScreen)
        restored.navigateTo(Screen.Home)
        assertEquals(Screen.Artist(12), restored.currentScreen)
        assertEquals(41L, restored.currentEntry.id)
        assertEquals(restored.liveEntryIds.size, restored.toSnapshot().let {
            listOf(it.homeIds,it.collectedAlbumsIds,it.personalLibraryIds,it.libraryIds,it.searchIds,it.localMusicIds,it.cloudIds,it.downloadsIds,it.myProfileIds).flatten().size
        })
    }

    @Test fun albumTabKeepsIndependentDetailsAndSearchReturnsToTheOrigin() = inSnapshot {
        val nav = MelodiaNavigationState()
        nav.selectHomeTab(TAB_MUSIC)
        nav.updateShowHomeMusicNewWorks(true)
        nav.openTab(Screen.CollectedAlbums)
        nav.openPlaylist(100, true)
        val frame = nav.currentEntry
        nav.openSearch(true)
        assertTrue(nav.canReturnFromSearch)
        assertTrue(nav.returnFromSearch())
        assertEquals(frame, nav.currentEntry)
        nav.navigateBack()
        assertEquals(Screen.CollectedAlbums, nav.currentScreen)
        nav.navigateTo(Screen.Home)
        assertEquals(TAB_MUSIC, nav.homeTab)
        assertTrue(nav.showHomeMusicNewWorks)
    }

    @Test fun albumEntryCanBeDefaultRootAndSurvivesSnapshotRestoration() = inSnapshot {
        val nav = MelodiaNavigationState()
        nav.updateDefaultRootTab(Screen.CollectedAlbums)
        nav.openTab(Screen.PersonalLibrary)
        nav.navigateBack()
        assertEquals(Screen.CollectedAlbums, nav.currentScreen)
        nav.openPlaylist(70,true)
        val encoded = Json.encodeToString(NavigationSnapshot.serializer(), nav.toSnapshot())
        val restored = restoreNavigationSnapshot(Json.decodeFromString(NavigationSnapshot.serializer(), encoded))
        assertEquals(nav.currentEntry,restored.currentEntry)
        restored.navigateBack()
        assertEquals(Screen.CollectedAlbums,restored.currentScreen)
    }

    @Test fun retiredBottomEntryPreservesItsPositionAndHiddenChoice() {
        val old = "search:1,music:0,home:0,library:1,personal_library:1,create:0,local_music:0,downloads:0,cloud:0,profile:0"
        val layout = BottomBarLayout.decode(old)
        assertEquals(BottomBarItem.ALBUMS,layout[1].item)
        assertFalse(layout[1].visible)
        val encoded = BottomBarLayout.encode(layout)
        assertFalse(encoded.split(',').any { it.startsWith("music:") })
        assertTrue(encoded.contains("albums:0"))
        assertEquals(layout,BottomBarLayout.decode(encoded))
    }
}

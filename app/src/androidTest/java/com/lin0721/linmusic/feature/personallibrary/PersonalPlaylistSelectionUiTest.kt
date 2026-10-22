package com.lin0721.linmusic.feature.personallibrary

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.lin0721.linmusic.core.ui.theme.MelodiaTheme
import com.lin0721.linmusic.feature.library.ui.LibraryItem
import com.lin0721.linmusic.feature.library.ui.LibraryItemType
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalMaterial3Api::class)
class PersonalPlaylistSelectionUiTest {
    @get:Rule val compose = createComposeRule()
    private val playlists = listOf(
        LibraryItem("-2", "听歌排行的歌单", "听歌排行统计", "", LibraryItemType.PLAYLIST),
        LibraryItem("1", "Playlist One", "", "", LibraryItemType.PLAYLIST),
        LibraryItem("2", "Playlist Two", "", "", LibraryItemType.PLAYLIST))
    @Composable private fun Theme(content: @Composable () -> Unit) {
        MelodiaTheme { Surface(Modifier.fillMaxSize(), content = content) }
    }
    @Test fun oneTapSelectionHidesOnlyUncheckedPlaylistsInBothPreviewAndAll() {
        var layout by mutableStateOf(LibraryPageLayout())
        var choosing by mutableStateOf(true)
        var all by mutableStateOf(false)
        compose.setContent { Theme {
            when {
                choosing -> PersonalPlaylistSelectionContent(playlists, LibraryPageLayoutState(layout, loaded = true),
                    { layout = it(layout) }, { choosing = false })
                all -> PersonalLibraryCollectionSheet(LibraryItemType.PLAYLIST, playlists, layout, {}, { all = false })
                else -> PersonalLibraryContent(playlists, layout, {}, { all = true })
            }
        } }
        compose.onNodeWithContentDescription("显示歌单：Playlist Two").performClick()
        compose.runOnIdle { assertFalse(layout.playlistIsVisible("2")); assertTrue(layout.playlistIsVisible("-2")) }
        compose.onNodeWithText("完成").performClick()
        compose.onNodeWithText("Playlist Two").assertDoesNotExist()
        compose.onNodeWithText("Playlist One").assertExists()
        compose.onNodeWithTag("listening_rank_cover", useUnmergedTree = true).assertExists()
        compose.runOnIdle { all = true }
        compose.onNodeWithText("Playlist Two").assertDoesNotExist()
        compose.onNodeWithText("歌单 · 2").assertExists()
    }
    @Test fun searchAndBulkChoicesKeepTemporarilyMissingSavedIds() {
        var layout by mutableStateOf(LibraryPageLayout(visiblePlaylistIds = setOf("1", "offline_saved")))
        compose.setContent { Theme { PersonalPlaylistSelectionContent(playlists, LibraryPageLayoutState(layout, loaded = true),
            { layout = it(layout) }, {}) } }
        compose.onNode(hasSetTextAction()).performTextReplacement("Playlist One")
        compose.onNodeWithContentDescription("显示歌单：Playlist Two").assertDoesNotExist()
        compose.onNodeWithText("全选").performClick()
        compose.runOnIdle { assertEquals(setOf("1", "2", "-2", "offline_saved"), layout.visiblePlaylistIds) }
        compose.onNodeWithText("清除选择").performClick()
        compose.runOnIdle { assertEquals(setOf("offline_saved"), layout.visiblePlaylistIds) }
    }
    @Test fun rankingCoverIsAvailableOfflineInListAndGridModes() {
        var mode by mutableStateOf("list")
        compose.setContent { Theme { PersonalLibraryContent(playlists.take(1), LibraryPageLayout(playlistMode = mode), {}, {}) } }
        compose.onNodeWithContentDescription("听歌排行歌单封面").assertIsDisplayed()
        compose.runOnIdle { mode = "covers" }
        compose.onNodeWithContentDescription("听歌排行歌单封面").assertIsDisplayed()
    }

    @Test fun newlyCreatedPlaylistAppearsInPreviewAndAllWithoutRevealingHiddenPlaylists() {
        var items by mutableStateOf(playlists)
        var layout by mutableStateOf(LibraryPageLayout(visiblePlaylistIds = setOf("1")))
        var all by mutableStateOf(false)
        compose.setContent { Theme {
            if (all) PersonalLibraryCollectionSheet(LibraryItemType.PLAYLIST, items, layout, {}, {})
            else PersonalLibraryContent(items, layout, {}, { all = true })
        } }
        compose.runOnIdle {
            layout = layout.withCreatedPlaylistVisible(3)
            items = items + LibraryItem("3", "Newly Created", "", "", LibraryItemType.PLAYLIST)
        }
        compose.onNodeWithText("Newly Created").assertIsDisplayed()
        compose.onNodeWithText("Playlist Two").assertDoesNotExist()
        compose.runOnIdle { all = true }
        compose.onNodeWithText("Newly Created").assertIsDisplayed()
        compose.onNodeWithText("Playlist Two").assertDoesNotExist()
        compose.onNodeWithText("歌单 · 2").assertExists()
    }
}

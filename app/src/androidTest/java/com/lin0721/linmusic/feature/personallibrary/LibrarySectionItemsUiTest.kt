package com.lin0721.linmusic.feature.personallibrary

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class LibrarySectionItemsUiTest {
    @get:Rule val compose = createComposeRule()
    private val items = listOf(LibrarySortItem("a", "Album A"), LibrarySortItem("b", "Album B"), LibrarySortItem("c", "Album C"))
    @Test fun customCountAndUnlimitedAreExplicitlySavedAndFilteredTopActionChangesTheFullOrder() {
        var layout by mutableStateOf(LibraryPageLayout())
        compose.setContent { MaterialTheme { LibrarySectionItemsContent("专辑", "albums", LibraryPageLayoutState(layout, loaded = true),
            items, { layout = it(layout) }, {}) } }
        compose.onAllNodes(hasSetTextAction())[0].performTextReplacement("0")
        compose.onNodeWithText("保存数量").performClick()
        compose.runOnIdle { assertEquals(0, layout.itemLimit("albums")) }
        compose.onAllNodes(hasSetTextAction())[1].performTextReplacement("Album C")
        compose.onNodeWithContentDescription("置顶：Album C").performClick()
        compose.runOnIdle { assertEquals(listOf("c", "a", "b"), layout.orderedItems("albums", items) { it.id }.map { it.id }) }
        compose.onAllNodes(hasSetTextAction())[1].performTextReplacement("")
        compose.onNodeWithText("1. Album C").assertExists()
        compose.onNodeWithContentDescription("下移：Album C").performClick()
        compose.runOnIdle { assertEquals(listOf("a", "c", "b"), layout.itemOrders["albums"]) }
    }
    @Test fun playlistOrderIsOneTapAndDoesNotExposeAConflictingRowCountLimit() {
        var layout by mutableStateOf(LibraryPageLayout())
        compose.setContent { MaterialTheme { LibrarySectionItemsContent("歌单", "playlists", LibraryPageLayoutState(layout, loaded = true),
            items, { layout = it(layout) }, {}) } }
        compose.onNodeWithText("横栏显示数量（0 为全部）").assertDoesNotExist()
        compose.onNodeWithContentDescription("上移：Album B").performClick()
        compose.runOnIdle { assertEquals(listOf("b", "a", "c"), layout.itemOrders["playlists"]) }
        compose.onNodeWithContentDescription("置顶：Album B").assertIsNotEnabled()
    }
}

package com.lin0721.linmusic.feature.albumlibrary

import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.lin0721.linmusic.core.model.Artist
import com.lin0721.linmusic.feature.library.data.AlbumSubItem
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class CollectedAlbumsUiTest {
    @get:Rule val compose = createComposeRule()

    private fun album(id: Long, name: String, vararg artists: String, collected: Long = id) = AlbumSubItem(
        id = id, name = name, artists = artists.map { Artist(name = it) }, size = 10, subTime = collected
    )

    private fun state(albums: List<AlbumSubItem>) = CollectedAlbumsState(albums = albums, loading = false, loggedIn = true)

    @Test fun toolbarHasOnlyTheThreeCompactActionsAndNoPlaybackChoicePanel() {
        compose.setContent { MaterialTheme {
            CollectedAlbumsPage(state(listOf(album(1, "A Record", "Muse"))), AlbumPageLayout(), {}, { _, _ -> }, {}, {}, {})
        } }
        val actions = listOf("全部播放专辑", "随机播放专辑（专辑内顺序）", "搜索收藏专辑")
        actions.forEach { compose.onNodeWithContentDescription(it).assertIsDisplayed().assertHasClickAction() }
        val bounds = actions.map { compose.onNodeWithContentDescription(it).fetchSemanticsNode().boundsInRoot }
        assertTrue(bounds[0].right <= bounds[1].left)
        assertTrue(bounds[1].right <= bounds[2].left)
        assertEquals(bounds[0].top, bounds[2].top, 1f)
        compose.onNodeWithText("选择一种播放方式").assertDoesNotExist()
        compose.onNodeWithText("全部播放").assertDoesNotExist()
        compose.onNodeWithText("随机播放").assertDoesNotExist()
        assertTrue(bounds[2].bottom <= compose.onNodeWithText("A Record").fetchSemanticsNode().boundsInRoot.top)
    }

    @Test fun bothPlaybackActionsUseTheCurrentSearchScopeAndClosingSearchRestoresAllAlbums() {
        val records = listOf(album(1, "First Record", "Muse", collected = 200),
            album(2, "Another Record", "U2", collected = 300), album(3, "Third Record", "Muse", collected = 100))
        val calls = mutableListOf<Pair<List<Long>, Boolean>>()
        compose.setContent { MaterialTheme {
            CollectedAlbumsPage(state(records), AlbumPageLayout(), {}, { items, random -> calls += items.map { it.id } to random }, {}, {}, {})
        } }
        compose.onNodeWithContentDescription("全部播放专辑").performClick()
        compose.runOnIdle { assertEquals(listOf(2L, 1L, 3L) to false, calls.last()) }
        compose.onNodeWithContentDescription("搜索收藏专辑").performClick()
        compose.onNode(hasSetTextAction()).performTextInput("Muse")
        compose.onNodeWithText("Another Record").assertDoesNotExist()
        compose.onNodeWithContentDescription("随机播放专辑（专辑内顺序）").performClick()
        compose.runOnIdle { assertEquals(listOf(1L, 3L) to true, calls.last()) }
        compose.onNode(hasSetTextAction()).performTextReplacement("Muse First")
        compose.onNodeWithContentDescription("全部播放专辑").performClick()
        compose.runOnIdle { assertEquals(listOf(1L) to false, calls.last()) }
        compose.onNodeWithContentDescription("关闭专辑搜索").performClick()
        compose.onNode(hasSetTextAction()).assertDoesNotExist()
        compose.onNodeWithContentDescription("随机播放专辑（专辑内顺序）").performClick()
        compose.runOnIdle { assertEquals(listOf(2L, 1L, 3L) to true, calls.last()) }
    }

    @Test fun oneAndFiveColumnLayoutsUseActualGridColumnsWithoutDuplicatingAlbums() {
        val records = (1L..15L).map { album(it, "Record $it", "Artist") }
        var layout by mutableStateOf(AlbumPageLayout(columns = 1))
        lateinit var grid: LazyGridState
        compose.setContent { MaterialTheme {
            grid = rememberLazyGridState()
            CollectedAlbumsPage(state(records), layout, {}, { _, _ -> }, {}, {}, {}, gridState = grid)
        } }
        compose.runOnIdle {
            assertEquals(15, grid.layoutInfo.totalItemsCount)
            assertEquals(setOf(0), grid.layoutInfo.visibleItemsInfo.map { it.column }.toSet())
            layout = layout.copy(columns = 5)
        }
        compose.runOnIdle {
            val firstRow = grid.layoutInfo.visibleItemsInfo.take(5)
            assertEquals(5, firstRow.size)
            assertEquals(setOf(0, 1, 2, 3, 4), firstRow.map { it.column }.toSet())
            assertEquals(1, firstRow.map { it.row }.toSet().size)
            assertEquals(15, grid.layoutInfo.totalItemsCount)
            assertEquals(grid.layoutInfo.visibleItemsInfo.size, grid.layoutInfo.visibleItemsInfo.map { it.key }.toSet().size)
        }
    }

    @Test fun artistGroupingKeepsACompilationWholeAndItsCoverStillOpensTheAlbum() {
        val compilation = album(10, "Shared Compilation", "Muse", "U2")
        val records = listOf(compilation, album(20, "Solo Record", "Muse"), album(30, "Another Solo", "U2"))
        var opened: Long? = null
        var played = emptyList<Long>()
        compose.setContent { MaterialTheme {
            CollectedAlbumsPage(state(records), AlbumPageLayout(groupByArtist = true), { opened = it },
                { items, _ -> played = items.map { it.id } }, {}, {}, {})
        } }
        compose.onNodeWithText("Muse • U2 · 1").assertExists()
        compose.onAllNodesWithText("Shared Compilation").assertCountEquals(1)
        compose.onNodeWithText("Shared Compilation").performClick()
        compose.runOnIdle { assertEquals(10L, opened) }
        compose.onNodeWithContentDescription("全部播放专辑").performClick()
        compose.runOnIdle { assertEquals(3, played.size); assertEquals(setOf(10L, 20L, 30L), played.toSet()) }
    }

    @Test fun preparationAndEmptySearchDisablePlaybackWhileCancellationRemainsAvailable() {
        var current by mutableStateOf(state(listOf(album(1, "A Record", "Muse"))))
        var cancelled = false
        compose.setContent { MaterialTheme {
            CollectedAlbumsPage(current, AlbumPageLayout(), {}, { _, _ -> }, { cancelled = true }, {}, {})
        } }
        compose.runOnIdle { current = current.copy(preparing = true, preparedAlbums = 1, totalAlbums = 2) }
        compose.onNodeWithContentDescription("全部播放专辑").assertIsNotEnabled()
        compose.onNodeWithContentDescription("随机播放专辑（专辑内顺序）").assertIsNotEnabled()
        compose.onNodeWithText("取消").performClick()
        compose.runOnIdle { assertTrue(cancelled); current = current.copy(preparing = false) }
        compose.onNodeWithContentDescription("搜索收藏专辑").performClick()
        compose.onNode(hasSetTextAction()).performTextInput("not-in-collection")
        compose.onNodeWithText("没有找到匹配的专辑").assertExists()
        compose.onNodeWithContentDescription("全部播放专辑").assertIsNotEnabled()
        compose.onNodeWithContentDescription("随机播放专辑（专辑内顺序）").assertIsNotEnabled()
    }
    @Test fun coversOnlyHidesAllVisibleNamesAndGroupHeadingsWhileCoversRemainAccessibleAndClickable() {
        var opened: Long? = null
        var coversOnly by mutableStateOf(true)
        compose.setContent { MaterialTheme {
            CollectedAlbumsPage(state(listOf(album(1, "Cover Only Record", "Muse"))),
                AlbumPageLayout(groupByArtist = true, coversOnly = coversOnly), { opened = it }, { _, _ -> }, {}, {}, {})
        } }
        compose.onNodeWithText("Cover Only Record").assertDoesNotExist()
        compose.onNodeWithText("Muse · 1").assertDoesNotExist()
        compose.onNodeWithContentDescription("Cover Only Record · 专辑 · Muse").performClick()
        compose.runOnIdle { assertEquals(1L, opened); coversOnly = false }
        compose.onNodeWithText("Cover Only Record").assertExists()
        compose.onNodeWithText("Muse · 1").assertExists()
    }

}

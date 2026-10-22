package com.lin0721.linmusic.feature.personallibrary

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.lin0721.linmusic.feature.library.ui.LibraryItem
import com.lin0721.linmusic.feature.library.ui.LibraryItemType
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Exercise production sheet functions, including transient frames after repeated edge flings. */
@OptIn(ExperimentalMaterial3Api::class)
class PersonalLibrarySheetScrollTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var sheet: SheetState
    private lateinit var grid: LazyGridState
    private lateinit var list: LazyListState
    private var dismissed = false
    private var recording = false
    private val offsets = mutableListOf<Float>()
    private var tag = "personal_library_all"

    private fun show(kind: String, type: LibraryItemType = LibraryItemType.ARTIST, count: Int = 120) {
        val records = (1..count).map { LibraryItem("$it", "Item $it", "Test", "", type) }
        tag = when (kind) { "sort" -> "library_section_items"; "visibility" -> "playlist_visibility_list"; else -> "personal_library_all" }
        compose.mainClock.autoAdvance = false
        compose.setContent { MaterialTheme {
            sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            grid = rememberLazyGridState()
            list = rememberLazyListState()
            LaunchedEffect(sheet) {
                while (true) {
                    withFrameNanos { }
                    if (recording && sheet.currentValue == SheetValue.Expanded) offsets.add(sheet.requireOffset())
                }
            }
            val state = LibraryPageLayoutState(LibraryPageLayout(playlistMode = if (kind == "list") "list" else "covers"), loaded = true)
            when (kind) {
                "sort" -> LibrarySectionItemsSheet(LibraryPage.PERSONAL, "artists", state,
                    records.map { LibrarySortItem(it.id, it.title) }, {}, { dismissed = true }, sheet, list)
                "visibility" -> PersonalPlaylistSelectionSheet(records, state, {}, { dismissed = true }, sheet, list)
                else -> PersonalLibraryCollectionSheet(type, records, state.layout, {}, { dismissed = true }, sheet, grid)
            }
        } }
        compose.mainClock.advanceTimeBy(1000)
        compose.runOnIdle { assertEquals(SheetValue.Expanded, sheet.currentValue) }
    }

    private fun assertBottomStable(kind: String, type: LibraryItemType = LibraryItemType.ARTIST, count: Int = 120) {
        show(kind, type, count)
        compose.onNodeWithTag(tag).performScrollToIndex(count - 1)
        compose.mainClock.advanceTimeBy(200)
        val expandedOffset = sheet.requireOffset()
        compose.runOnIdle { recording = true }
        repeat(8) {
            compose.onNodeWithTag(tag).performTouchInput { swipeUp(durationMillis = 120) }
            compose.mainClock.advanceTimeBy(600)
        }
        compose.mainClock.advanceTimeBy(2000)
        compose.runOnIdle {
            assertFalse(dismissed)
            assertFalse(if (kind in listOf("sort", "visibility")) list.canScrollForward else grid.canScrollForward)
            assertTrue(offsets.size >= 60)
            assertTrue("$kind kept moving after upward edge gestures: ${offsets.min()}..${offsets.max()}", offsets.max() - offsets.min() < 1f)
            assertEquals(expandedOffset, sheet.requireOffset(), 1f)
            assertEquals(SheetValue.Expanded, sheet.targetValue)
        }
    }
    @Test fun allArtistsStayStableAtBottom() = assertBottomStable("covers", LibraryItemType.ARTIST)
    @Test fun allAlbumsStayStableAtBottom() = assertBottomStable("covers", LibraryItemType.ALBUM)
    @Test fun allPlaylistCoversStayStableAtBottom() = assertBottomStable("covers", LibraryItemType.PLAYLIST)
    @Test fun allPlaylistRowsStayStableAtBottom() = assertBottomStable("list", LibraryItemType.PLAYLIST)
    @Test fun sortingPanelStaysStableAtBottom() = assertBottomStable("sort")
    @Test fun playlistVisibilityPanelStaysStableAtBottom() = assertBottomStable("visibility", LibraryItemType.PLAYLIST)
    @Test fun shortAlbumCollectionAlsoStaysStable() = assertBottomStable("covers", LibraryItemType.ALBUM, 2)

    @Test fun upwardFlingStillScrollsTheActualCollection() {
        show("covers")
        compose.onNodeWithTag(tag).performTouchInput { swipeUp(durationMillis = 120) }
        compose.mainClock.advanceTimeBy(1000)
        compose.runOnIdle { assertTrue(grid.firstVisibleItemIndex > 0); assertTrue(grid.canScrollForward); assertFalse(dismissed) }
    }
    @Test fun downwardSwipeAtTopStillDismissesTheActualCollection() {
        show("covers")
        compose.onNodeWithTag(tag).performTouchInput { swipeDown(durationMillis = 120) }
        compose.mainClock.advanceTimeBy(1500)
        compose.runOnIdle { assertTrue(dismissed) }
    }
}

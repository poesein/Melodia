package com.lin0721.linmusic.feature.albumlibrary

import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.lin0721.linmusic.core.model.Artist
import com.lin0721.linmusic.feature.library.data.AlbumSubItem
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Test the production standalone page, rather than a synthetic grid or the old bottom sheet. */
class CollectedAlbumsScrollTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var grid: LazyGridState
    private var recording = false
    private val positions = mutableListOf<Pair<Int, Int>>()

    private fun show(count: Int, columns: Int) {
        val records = (1..count).map { AlbumSubItem(id = it.toLong(), name = "Record $it",
            artists = listOf(Artist(name = "Artist")), subTime = (count - it).toLong()) }
        compose.mainClock.autoAdvance = false
        compose.setContent { MaterialTheme {
            grid = rememberLazyGridState()
            LaunchedEffect(grid) {
                while (true) {
                    withFrameNanos { }
                    if (recording) positions += grid.firstVisibleItemIndex to grid.firstVisibleItemScrollOffset
                }
            }
            CollectedAlbumsPage(CollectedAlbumsState(albums = records, loading = false, loggedIn = true),
                AlbumPageLayout(columns = columns), {}, { _, _ -> }, {}, {}, {}, gridState = grid)
        } }
        compose.mainClock.advanceTimeBy(1_000)
    }

    private fun assertBottomStable(count: Int, columns: Int) {
        show(count, columns)
        compose.onNodeWithTag("collected_albums_grid").performScrollToIndex(count - 1)
        compose.mainClock.advanceTimeBy(500)
        lateinit var bottom: Pair<Int, Int>
        compose.runOnIdle {
            assertFalse(grid.canScrollForward)
            bottom = grid.firstVisibleItemIndex to grid.firstVisibleItemScrollOffset
        }
        repeat(8) {
            compose.onNodeWithTag("collected_albums_grid").performTouchInput { swipeUp(durationMillis = 120) }
            compose.mainClock.advanceTimeBy(800)
            compose.runOnIdle { assertFalse(grid.canScrollForward) }
        }
        compose.runOnIdle { recording = true }
        compose.mainClock.advanceTimeBy(2_000)
        compose.runOnIdle {
            assertTrue("Expected frame samples after all edge gestures", positions.size >= 60)
            assertEquals("The grid kept oscillating after upward edge gestures", setOf(bottom), positions.toSet())
            assertEquals(bottom, grid.firstVisibleItemIndex to grid.firstVisibleItemScrollOffset)
            assertFalse(grid.canScrollForward)
            if (count > columns) assertTrue("Repeated edge gestures returned to the top", grid.firstVisibleItemIndex > 0)
        }
        compose.onNodeWithContentDescription("搜索收藏专辑").assertIsDisplayed()
        compose.onNodeWithText("选择一种播放方式").assertDoesNotExist()
    }

    @Test fun longThreeColumnCollectionStaysAtTheBottomAfterRepeatedUpwardFlings() = assertBottomStable(180, 3)
    @Test fun longFiveColumnCollectionStaysAtTheBottomAfterRepeatedUpwardFlings() = assertBottomStable(180, 5)
    @Test fun shortCollectionAlsoStaysStableWhenThereIsNothingMoreToScroll() = assertBottomStable(2, 3)

    @Test fun normalUpwardFlingStillMovesThroughTheCollection() {
        show(180, 3)
        compose.onNodeWithTag("collected_albums_grid").performTouchInput { swipeUp(durationMillis = 120) }
        compose.mainClock.advanceTimeBy(1_000)
        compose.runOnIdle {
            assertTrue(grid.firstVisibleItemIndex > 0)
            assertTrue(grid.canScrollForward)
        }
    }
}

package com.lin0721.linmusic.feature.player.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.dp
import com.lin0721.linmusic.core.ui.components.SwipeToSkipCover
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Real touch arbitration: cover taps open lyrics, drags remain navigation gestures. */
class CoverLyricsGestureTest {
    @get:Rule val compose = createComposeRule()
    private var lyricsOpens = 0
    private var next = 0
    private var previous = 0

    @Composable
    private fun Cover() {
        SwipeToSkipCover(coverUrl = "", previousCoverUrl = "", nextCoverUrl = "",
            currentKey = "current", previousKey = "previous", nextKey = "next",
            onConfirmPrevious = { previous++ }, onConfirmNext = { next++ },
            contentPadding = 24.dp, modifier = Modifier.width(260.dp).testTag("cover"),
            onClick = { lyricsOpens++ }, onClickLabel = "显示歌词")
    }

    private fun showCover() { compose.setContent { MaterialTheme { Box(Modifier.fillMaxSize()) { Cover() } } } }

    @Test fun tapOpensLyricsWithoutSkipping() {
        showCover()
        compose.onNodeWithTag("cover").performTouchInput { click(center) }
        compose.runOnIdle {
            assertEquals(1, lyricsOpens); assertEquals(0, next); assertEquals(0, previous)
        }
    }

    @Test fun swipeLeftSkipsWithoutOpeningLyrics() {
        showCover()
        compose.onNodeWithTag("cover").performTouchInput { swipeLeft(durationMillis = 400) }
        compose.waitForIdle()
        compose.runOnIdle {
            assertEquals(0, lyricsOpens); assertEquals(1, next); assertEquals(0, previous)
        }
    }

    @Test fun swipeRightSkipsWithoutOpeningLyrics() {
        showCover()
        compose.onNodeWithTag("cover").performTouchInput { swipeRight(durationMillis = 400) }
        compose.waitForIdle()
        compose.runOnIdle {
            assertEquals(0, lyricsOpens); assertEquals(0, next); assertEquals(1, previous)
        }
    }

    @Test fun coverPaddingDoesNotOpenLyrics() {
        showCover()
        compose.onNodeWithTag("cover").performTouchInput { click(Offset(4f, centerY)) }
        compose.runOnIdle { assertEquals(0, lyricsOpens) }
    }

    @Test fun verticalScrollDoesNotOpenLyricsOrSkip() {
        lateinit var list: LazyListState
        compose.setContent {
            MaterialTheme {
                list = rememberLazyListState()
                LazyColumn(state = list, modifier = Modifier.fillMaxSize()) {
                    item { Cover() }
                    items(20) { Box(Modifier.height(80.dp)) }
                }
            }
        }
        compose.onNodeWithTag("cover").performTouchInput { swipeUp(durationMillis = 400) }
        compose.waitForIdle()
        compose.runOnIdle {
            assertTrue(list.firstVisibleItemIndex > 0 || list.firstVisibleItemScrollOffset > 0)
            assertEquals(0, lyricsOpens); assertEquals(0, next); assertEquals(0, previous)
        }
    }
}

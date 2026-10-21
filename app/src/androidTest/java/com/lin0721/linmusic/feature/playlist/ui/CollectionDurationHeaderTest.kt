package com.lin0721.linmusic.feature.playlist.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import com.lin0721.linmusic.core.model.PlaylistDetail
import com.lin0721.linmusic.core.model.PlaylistTrackId
import org.junit.Rule
import org.junit.Test

class CollectionDurationHeaderTest {
    @get:Rule val compose = createComposeRule()
    private fun show(album: Boolean, duration: Long?) {
        compose.setContent {
            MaterialTheme {
                PlaylistHeaderItem(
                    playlist = PlaylistDetail(name = "Duration test", playCount = 123,
                        trackIds = (1L..26L).map { PlaylistTrackId(it) }),
                    totalDurationMs = duration, showPlayCount = !album,
                    coverSize = 80.dp, coverAlpha = 1f, progress = 0f, statusBarHeight = 0.dp,
                    dominantColor = Color.DarkGray, onColorCalculated = {}, onPlayAll = {}, onShuffleToggle = {},
                    isShuffleActive = false, isCurrentlyPlayingThis = false, isSubscribed = false,
                    onSubscribeClick = {}, onCommentsClick = {}, onMoreClick = {}
                )
            }
        }
    }
    @Test fun albumNeverAddsPlayCountEvenWhenModelContainsOne() {
        show(album = true, duration = 5_535_000)
        compose.onNodeWithText("26 首歌曲 · 1:32:15 时长").assertExists()
        compose.onNodeWithText("播放 123 次", substring = true).assertDoesNotExist()
    }
    @Test fun playlistPreservesPlayCountAfterDuration() {
        show(album = false, duration = 5_535_000)
        compose.onNodeWithText("26 首歌曲 · 1:32:15 时长 · 播放 123 次").assertExists()
    }
    @Test fun underAnHourOmitsHours() {
        show(album = true, duration = 1_935_000)
        compose.onNodeWithText("26 首歌曲 · 32:15 时长").assertExists()
    }
}

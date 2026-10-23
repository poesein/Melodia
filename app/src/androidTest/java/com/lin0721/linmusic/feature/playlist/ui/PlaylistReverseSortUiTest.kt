package com.lin0721.linmusic.feature.playlist.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.lin0721.linmusic.core.model.PlaylistDetail
import com.lin0721.linmusic.core.model.Track
import com.lin0721.linmusic.core.ui.components.PlaylistCollectState
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class PlaylistReverseSortUiTest {
    @get:Rule val compose = createComposeRule()
    private var songs by mutableStateOf((3L downTo 1L).map { Track(it, "Track $it") })
    private var order by mutableStateOf(PlaylistTrackSort())
    private var hasMore by mutableStateOf(false)
    private var played = emptyList<Long>()
    private var selected: Long? = null
    private var edits = 0
    private var loadAll: () -> Unit = {}

    private fun show() {
        compose.setContent { MaterialTheme { Surface(Modifier.fillMaxSize()) {
            PlaylistContent(playlist = PlaylistDetail(id = 42, name = "Reverse test", tracks = songs),
                currentTrackId = null, isPlaying = false, likedSongIds = emptySet(),
                collectState = PlaylistCollectState(), isLoggedIn = false, recommendedSongs = emptyList(),
                onBack = {}, onArtistClick = {}, onAlbumClick = {}, onToggleLike = { _, _ -> },
                onPlaySong = { selected = it.id; played = order.sort(songs).map { track -> track.id } },
                onPlayRecommendedSong = {}, onAddToPlayNext = {}, onPlayAll = { played = order.sort(songs).map { it.id } },
                onShuffleToggle = {}, isShuffleActive = false, isCurrentlyPlayingThis = false,
                onLikeClick = {}, onSaveCollection = { _, _ -> }, onSaveNewCollection = { _, _ -> },
                onRequireLogin = {}, onRefreshRecommendations = {}, onAddRecommendSong = {},
                isSubscribed = false, onSubscribeClick = {}, onCommentsClick = {}, onMoreClick = {},
                canRemoveFromPlaylist = true, onEditOrderClick = { edits++ },
                hasMoreTracks = hasMore, sortOrder = order, onSortOrderChange = { order = it },
                onCompleteTrackOrder = { loadAll() })
        } } }
    }
    private fun scrollToSongs() = compose.onNodeWithTag("playlist_track_list").performScrollToIndex(2)

    @Test fun sheetReversesTheCurrentKeyAndCanRestoreNormalDirection() {
        compose.setContent { MaterialTheme { PlaylistSortSheet(order, { order = it }, {}) } }
        compose.onNodeWithContentDescription("倒序排列歌曲").performClick()
        compose.runOnIdle { assertTrue(order.reversed) }
        compose.onNodeWithText("歌曲名").performClick()
        compose.runOnIdle { assertEquals(PlaylistSortOption.TITLE, order.option); assertTrue(order.reversed) }
        compose.onNodeWithContentDescription("倒序排列歌曲").performClick()
        compose.runOnIdle { assertFalse(order.reversed) }
    }

    @Test fun actualPlaylistReflectsReverseAndNewSongsWithoutChangingTheSourceOrder() {
        show()
        compose.onNodeWithText("排序").performClick()
        compose.onNodeWithContentDescription("倒序排列歌曲").performClick()
        scrollToSongs()
        val first = compose.onNodeWithText("Track 1").getUnclippedBoundsInRoot().top
        val last = compose.onNodeWithText("Track 3").getUnclippedBoundsInRoot().top
        assertTrue(first < last)
        compose.onNodeWithText("Track 2").performClick()
        compose.runOnIdle { assertEquals(2L, selected); assertEquals(listOf(1L, 2L, 3L), played) }
        compose.runOnIdle { songs = listOf(Track(5, "Track 5"), Track(4, "Track 4")) + songs }
        scrollToSongs()
        compose.onNodeWithText("Track 4").performClick()
        compose.runOnIdle {
            assertEquals(4L, selected); assertEquals(listOf(1L, 2L, 3L, 4L, 5L), played)
            assertEquals(listOf(5L, 4L, 3L, 2L, 1L), songs.map { it.id })
        }
        compose.onAllNodesWithContentDescription("播放").onLast().performClick()
        compose.runOnIdle { assertEquals(listOf(1L, 2L, 3L, 4L, 5L), played) }
    }

    @Test fun enteringManualEditingRestoresTheRealPlaylistOrder() {
        order = PlaylistTrackSort(reversed = true)
        show()
        compose.onNodeWithText("调整顺序").performClick()
        compose.runOnIdle {
            assertEquals(1, edits); assertEquals(PlaylistTrackSort(), order)
            assertEquals(listOf(3L, 2L, 1L), songs.map { it.id })
        }
        compose.onNodeWithText("排序").assertExists()
    }

    @Test fun refreshedPartialPlaylistDoesNotExposeAnIncorrectReversedPrefixAndCanRetry() {
        order = PlaylistTrackSort(reversed = true); hasMore = true
        var retries = 0
        loadAll = { retries++; songs = (5L downTo 1L).map { Track(it, "Track $it") }; hasMore = false }
        show(); scrollToSongs()
        compose.onNodeWithText("Track 1").assertDoesNotExist()
        compose.onNodeWithText("倒序需要完整歌单").assertIsDisplayed()
        compose.onNodeWithText("加载完整歌单").performClick()
        scrollToSongs()
        compose.onNodeWithText("Track 1").assertIsDisplayed()
        compose.runOnIdle { assertEquals(1, retries); assertFalse(hasMore) }
    }
}

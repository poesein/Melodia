package com.lin0721.linmusic.feature.albumlibrary

import com.lin0721.linmusic.core.model.PlaylistDetail
import com.lin0721.linmusic.core.model.PlaylistTrackId
import com.lin0721.linmusic.core.model.Track
import com.lin0721.linmusic.feature.library.data.AlbumSubItem
import kotlinx.coroutines.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class CollectedAlbumsPlaybackTest {
    private val albums = (1L..7L).map { AlbumSubItem(it, "Album $it") }
    private fun tracks(id: Long) = listOf(Track(id * 10 + 1), Track(id * 10 + 2))

    @Test fun sequentialPlaybackPreservesBothAlbumAndTrackOrderDespiteReverseCompletion() = runTest {
        val queue = CollectedAlbumsPlayback.prepare(albums, false, { album ->
            delay((8 - album.id) * 10); tracks(album.id)
        })
        assertEquals(albums.flatMap { tracks(it.id) }.map { it.id }, queue.map { it.songId })
    }

    @Test fun randomPlaybackOnlyPermutesCompleteAlbumBlocks() = runTest {
        val expected = CollectedAlbumsPlayback.orderedAlbums(albums, true, Random(42))
        assertNotEquals(albums, expected)
        val queue = CollectedAlbumsPlayback.prepare(albums, true, { tracks(it.id) }, Random(42))
        assertEquals(expected.flatMap { tracks(it.id) }.map { it.id }, queue.map { it.songId })
    }

    @Test fun repeatedSongIdsInDifferentAlbumsRemainInBothAlbums() = runTest {
        val queue = CollectedAlbumsPlayback.prepare(albums.take(2), false, {
            if (it.id == 1L) listOf(Track(1), Track(9)) else listOf(Track(9), Track(3))
        })
        assertEquals(listOf(1L, 9L, 9L, 3L), queue.map { it.songId })
    }

    @Test fun albumRequestsNeverExceedThreeAndProgressReachesTheFullCollection() = runTest {
        var active = 0; var maximum = 0
        val progress = mutableListOf<Int>()
        CollectedAlbumsPlayback.prepare(albums, false, {
            active++; maximum = maxOf(maximum, active)
            try { delay(100); tracks(it.id) } finally { active-- }
        }, onPrepared = { count, total -> assertEquals(7, total); progress += count })
        assertEquals(3, maximum)
        assertEquals((1..7).toList(), progress)
    }

    @Test fun oneBrokenAlbumFailsTheWholePreparationAndCancelsItsSiblings() = runTest {
        var cancelled = false
        val result = runCatching {
            CollectedAlbumsPlayback.prepare(albums.take(3), false, {
                when (it.id) {
                    1L -> tracks(it.id)
                    2L -> { delay(10); error("broken album") }
                    else -> try { awaitCancellation() } finally { cancelled = true }
                }
            })
        }
        assertEquals("broken album", result.exceptionOrNull()?.message)
        assertTrue(cancelled)
    }

    @Test fun emptyOrInvalidAlbumsCannotProduceAPartialQueue() = runTest {
        assertTrue(runCatching { CollectedAlbumsPlayback.prepare(emptyList(), false, { tracks(it.id) }) }.isFailure)
        assertTrue(runCatching { CollectedAlbumsPlayback.prepare(listOf(AlbumSubItem(0)), false, { tracks(it.id) }) }.isFailure)
        assertTrue(runCatching { CollectedAlbumsPlayback.prepare(albums.take(1), false, { emptyList() }) }.isFailure)
        assertTrue(runCatching { CollectedAlbumsPlayback.prepare(albums.take(1), false, { listOf(Track(0)) }) }.isFailure)
    }

    @Test fun declaredTrackIdsRestoreOriginalOrderIncludingRepeatedSongsWithinAnAlbum() {
        val detail = PlaylistDetail(trackIds = listOf(PlaylistTrackId(2), PlaylistTrackId(1), PlaylistTrackId(2)))
        assertEquals(listOf(2L, 1L, 2L), CollectedAlbumsPlayback.originalTrackOrder(detail, listOf(Track(1), Track(2))).map { it.id })
    }

    @Test fun incompleteDeclaredTrackIdsFailInsteadOfSilentlySkippingSongs() {
        val detail = PlaylistDetail(trackIds = listOf(PlaylistTrackId(1), PlaylistTrackId(2)))
        assertTrue(runCatching { CollectedAlbumsPlayback.originalTrackOrder(detail, listOf(Track(1))) }.isFailure)
    }
}

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
    @Test fun randomPlaybackPermutesEachAlbumOnceAndSequentialOrderUsesTheGivenSnapshot() {
        val albums = (1L..20L).map { AlbumSubItem(it) }
        assertEquals(albums, CollectedAlbumsPlayback.orderedAlbums(albums, false))
        val random = CollectedAlbumsPlayback.orderedAlbums(albums + albums.first(), true, Random(42))
        assertEquals(albums.toSet(), random.toSet())
        assertEquals(20, random.size)
        assertNotEquals(albums, random)
    }

    @Test fun queueCreationKeepsRepeatedTracksAcrossAlbumsAndNeverRequestsPlaybackUrls() {
        val album = AlbumSubItem(1, "Record", picUrl = "cover")
        val tracks = listOf(Track(9), Track(9), Track(10))
        assertEquals(listOf(9L, 9L, 10L), CollectedAlbumsPlayback.queueItems(album, tracks).map { it.songId })
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

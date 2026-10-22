package com.lin0721.linmusic.feature.playlist.ui

import com.lin0721.linmusic.core.model.Artist
import com.lin0721.linmusic.core.model.Track
import org.junit.Assert.*
import org.junit.Test

class PlaylistTrackSortTest {
    private val albumInPlaylist = listOf(
        Track(3, "C", ar = listOf(Artist(3, "B")), dt = 100),
        Track(2, "A", ar = listOf(Artist(2, "C")), dt = 300),
        Track(1, "B", ar = listOf(Artist(1, "A")), dt = 200))

    @Test fun reverseRestoresAlbumSequenceWithoutChangingTheSource() {
        val order = PlaylistTrackSort(reversed = true)
        assertEquals(listOf(1L, 2L, 3L), order.sort(albumInPlaylist).map { it.id })
        assertEquals(listOf(3L, 2L, 1L), albumInPlaylist.map { it.id })
        assertEquals(albumInPlaylist, order.copy(reversed = false).sort(albumInPlaylist))
    }
    @Test fun directionCanReverseEveryExistingSortKeyAndBeTurnedOff() {
        PlaylistSortOption.entries.forEach { key ->
            val normal = PlaylistTrackSort(key)
            assertEquals(normal.sort(albumInPlaylist).reversed(), normal.copy(reversed = true).sort(albumInPlaylist))
            assertEquals(key.sort(albumInPlaylist), normal.sort(albumInPlaylist))
        }
    }
    @Test fun completeLongPlaylistIsReversedFromItsRealEndWithoutDroppingAnySongs() {
        val source = (1L..1205L).map { Track(it, "Song $it") }
        val result = PlaylistTrackSort(reversed = true).sort(source)
        assertEquals(1205L, result.first().id); assertEquals(1L, result.last().id)
        assertEquals(source.map { it.id }.toSet(), result.map { it.id }.toSet())
        assertEquals(source.size, result.size)
    }
    @Test fun emptyAndSingleSongPlaylistsKeepTheirContent() {
        val reverse = PlaylistTrackSort(reversed = true)
        assertTrue(reverse.sort(emptyList()).isEmpty())
        assertEquals(albumInPlaylist.take(1), reverse.sort(albumInPlaylist.take(1)))
    }
    @Test fun labelCommunicatesTheSelectedDirectionAndKey() {
        assertEquals("排序", PlaylistTrackSort().label)
        assertEquals("倒序", PlaylistTrackSort(reversed = true).label)
        assertEquals("歌曲名 · 倒序", PlaylistTrackSort(PlaylistSortOption.TITLE, true).label)
    }
    @Test fun addingAndRemovingSongsRecomputesTheViewWithoutReversingTheOldSourceAgain() {
        val order = PlaylistTrackSort(reversed = true)
        val afterAdding = listOf(Track(5, "New 5"), Track(4, "New 4")) + albumInPlaylist
        assertEquals(listOf(1L, 2L, 3L, 4L, 5L), order.sort(afterAdding).map { it.id })
        assertEquals(listOf(1L, 3L, 4L, 5L), order.sort(afterAdding.filterNot { it.id == 2L }).map { it.id })
        assertEquals(listOf(3L, 2L, 1L), albumInPlaylist.map { it.id })
        assertEquals(listOf(5L, 4L, 3L, 2L, 1L), afterAdding.map { it.id })
    }
}

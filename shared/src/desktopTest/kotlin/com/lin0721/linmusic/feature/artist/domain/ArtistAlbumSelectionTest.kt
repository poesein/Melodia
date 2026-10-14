package com.lin0721.linmusic.feature.artist.domain

import com.lin0721.linmusic.core.model.ArtistAlbum
import com.lin0721.linmusic.core.preferences.ArtistAlbumFilterSettings
import org.junit.Assert.*
import org.junit.Test

class ArtistAlbumSelectionTest {
    private val albums = listOf(
        ArtistAlbum(id = 1, size = 1),
        ArtistAlbum(id = 2, size = 7),
        ArtistAlbum(id = 3, size = 8),
        ArtistAlbum(id = 4, size = 12),
        ArtistAlbum(id = 5, size = 0)
    )

    @Test
    fun zeroIsTheDefaultAndDisplaysTheCompleteOriginalList() {
        assertEquals(0, ArtistAlbumFilterSettings.DEFAULT_MIN_TRACKS)
        assertSame(albums, ArtistAlbumSelection.visibleAlbums(albums, 0))
    }

    @Test
    fun minimumIsInclusiveAndUnknownCountsDoNotPassAPositiveMinimum() {
        assertEquals(listOf(3L, 4L), ArtistAlbumSelection.visibleAlbums(albums, 8).map { it.id })
    }

    @Test
    fun returningToZeroRestoresSinglesAndUnknownCountsWithoutRefetching() {
        ArtistAlbumSelection.visibleAlbums(albums, 8)
        assertEquals(listOf(1L, 2L, 3L, 4L, 5L), ArtistAlbumSelection.visibleAlbums(albums, 0).map { it.id })
    }

    @Test
    fun anEmptyFilteredPageContinuesUntilALaterPageContainsAlbums() {
        val first = ArtistAlbumPage(List(20) { ArtistAlbum(id = (it + 10).toLong(), size = 1) }, true)
        val second = ArtistAlbumPage(listOf(ArtistAlbum(id = 30, size = 10)), false)
        val firstVisible = ArtistAlbumSelection.visibleAlbums(first.albums, 8)
        assertTrue(firstVisible.isEmpty())
        assertTrue(ArtistAlbumSelection.shouldLoadMore(false, firstVisible.size, ArtistAlbumSelection.hasNextPage(first), false, null))
        val combined = first.albums + second.albums
        assertEquals(listOf(30L), ArtistAlbumSelection.visibleAlbums(combined, 8).map { it.id })
        assertEquals(21, combined.size) // Offset is based on original rows, never filtered rows.
        assertFalse(ArtistAlbumSelection.hasNextPage(second))
    }

    @Test
    fun existingFilteredRowsLoadWhenNearTheEnd() {
        assertFalse(ArtistAlbumSelection.shouldLoadMore(false, 2, true, false, null))
        assertTrue(ArtistAlbumSelection.shouldLoadMore(true, 2, true, false, null))
    }

    @Test
    fun errorsAndInFlightRequestsPreventRepeatedAutomaticRequests() {
        assertFalse(ArtistAlbumSelection.shouldLoadMore(true, 0, true, true, null))
        assertFalse(ArtistAlbumSelection.shouldLoadMore(true, 0, true, false, "网络异常"))
        assertTrue(ArtistAlbumSelection.shouldLoadMore(true, 0, true, false, null))
    }

    @Test
    fun anEmptyServerPageOrNoMorePagesStopsPagination() {
        assertFalse(ArtistAlbumSelection.hasNextPage(ArtistAlbumPage(emptyList(), true)))
        assertFalse(ArtistAlbumSelection.shouldLoadMore(true, 0, false, false, null))
    }

    @Test
    fun invalidSavedThresholdsAreBoundedAndNegativeThresholdsRestoreAll() {
        assertEquals(0, ArtistAlbumFilterSettings.normalizeMinTracks(-5))
        assertEquals(100, ArtistAlbumFilterSettings.normalizeMinTracks(999))
        assertSame(albums, ArtistAlbumSelection.visibleAlbums(albums, -5))
    }
}

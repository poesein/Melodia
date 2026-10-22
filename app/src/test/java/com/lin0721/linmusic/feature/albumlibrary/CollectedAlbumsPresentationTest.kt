package com.lin0721.linmusic.feature.albumlibrary

import com.lin0721.linmusic.core.model.Artist
import com.lin0721.linmusic.feature.library.data.AlbumSubItem
import com.lin0721.linmusic.feature.library.ui.LibraryItemType
import org.junit.Assert.*
import org.junit.Test

class CollectedAlbumsPresentationTest {
    @Test fun releaseSortUsesPublicationDateAndKeepsUnknownDatesLastInBothDirections() {
        val albums = listOf(AlbumSubItem(1, publishTime = 200), AlbumSubItem(2), AlbumSubItem(3, publishTime = 100))
        assertEquals(listOf(1L, 3L, 2L), displayedAlbums(albums, "", AlbumPageLayout(sort = AlbumPageSort.RELEASE_NEWEST)).map { it.id })
        assertEquals(listOf(3L, 1L, 2L), displayedAlbums(albums, "", AlbumPageLayout(sort = AlbumPageSort.RELEASE_OLDEST)).map { it.id })
    }
    private val source = listOf(
        AlbumSubItem(1,"B",artists=listOf(Artist(1,"Muse")),subTime=20),
        AlbumSubItem(2,"A",artists=listOf(Artist(2,"U2")),subTime=30),
        AlbumSubItem(3,"Compilation",artists=listOf(Artist(1,"Muse"),Artist(2,"U2")),subTime=10)
    )
    @Test fun savedColumnsAreBoundedAndTheDefaultIsCompact() {
        assertEquals(3,AlbumPageLayout().columns)
        assertEquals(1,AlbumPageLayout(-2).normalized().columns)
        assertEquals(5,AlbumPageLayout(99).normalized().columns)
    }
    @Test fun recentAndNameOrderAreSharedWithThePlaybackScope() {
        assertEquals(listOf(2L,1L,3L),displayedAlbums(source,"",AlbumPageLayout()).map { it.id })
        assertEquals(listOf(2L,1L,3L),displayedAlbums(source,"",AlbumPageLayout(sort=AlbumPageSort.NAME)).map { it.id })
        assertEquals(listOf(1L,3L),displayedAlbums(source,"muse",AlbumPageLayout()).map { it.id })
        assertEquals(listOf(3L),displayedAlbums(source,"compilation u2",AlbumPageLayout()).map { it.id })
        assertTrue(displayedAlbums(source,"missing",AlbumPageLayout()).isEmpty())
    }
    @Test fun artistGroupingDoesNotDuplicateAMultiArtistCompilation() {
        val result=displayedAlbums(source,"",AlbumPageLayout(groupByArtist=true))
        assertEquals(3,result.size)
        assertEquals(3,result.map { it.id }.toSet().size)
        assertEquals("Muse • U2",source[2].artistLabel())
    }
    @Test fun cardsUseTheExistingLibraryModelWithoutChangingTrackArtistMetadata() {
        val item=source[2].libraryItem()
        assertEquals(LibraryItemType.ALBUM,item.type)
        assertEquals("专辑 · Muse • U2",item.subtitle)
        assertEquals(2,source[2].artists.size)
    }
}

package com.lin0721.linmusic.feature.artist.domain.catalog

import com.lin0721.linmusic.core.model.Artist
import com.lin0721.linmusic.core.model.PlaylistDetail
import com.lin0721.linmusic.core.model.Track
import org.junit.Assert.*
import org.junit.Test

class AlbumArtistCorrectionShortcutTest {
    @Test fun onlyValidOfficialAlbumCreditsAreOfferedAndCollaboratorsRemainSeparate() {
        val first = Artist(1, "First")
        val second = Artist(2, "Second")
        val album = PlaylistDetail(artists = listOf(first, second, first, Artist(0, "Unknown"), Artist(3, "")))
        assertEquals(listOf(first, second), AlbumArtistCorrectionShortcut.artists(album))
    }

    @Test fun missingAlbumCreditsDoNotSilentlyUseASongsGuestArtist() {
        val album = PlaylistDetail(tracks = listOf(Track(id = 7, ar = listOf(Artist(8, "Guest")))))
        assertTrue(AlbumArtistCorrectionShortcut.artists(album).isEmpty())
    }

    @Test fun snapshotUsesAlbumIdentityAndDoesNotInventDurationOrCountFromFilteredTracks() {
        val album = PlaylistDetail(id = 4, name = "Important", coverImgUrl = "cover", tracks = listOf(Track(id = 7)))
        val snapshot = AlbumArtistCorrectionShortcut.snapshot(album)
        assertEquals(4L, snapshot.id)
        assertEquals("Important", snapshot.name)
        assertEquals("cover", snapshot.picUrl)
        assertEquals(0, snapshot.size)
        assertEquals(0L, snapshot.publishTime)
    }
}

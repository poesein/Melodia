package com.lin0721.linmusic.feature.artist.domain.catalog

import com.lin0721.linmusic.core.model.Artist
import com.lin0721.linmusic.core.model.ArtistAlbum
import com.lin0721.linmusic.core.model.PlaylistDetail

object AlbumArtistCorrectionShortcut {
    /** A song's guest credit is not evidence that the album belongs to that artist. */
    fun artists(album: PlaylistDetail): List<Artist> = album.artists
        .filter { it.id > 0 && it.name.isNotBlank() }.distinctBy { it.id }

    fun snapshot(album: PlaylistDetail) = ArtistAlbum(id = album.id, name = album.name, picUrl = album.coverImgUrl)
    // The existing detail model does not carry album release date/total count. Leave them unknown;
    // the artist's loaded source metadata will replace this snapshot rather than using filtered songs.
}

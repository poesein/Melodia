package com.lin0721.linmusic.feature.artist.domain

import com.lin0721.linmusic.core.model.ArtistAlbum
import com.lin0721.linmusic.core.preferences.ArtistAlbumFilterSettings

/** Filtering never removes the source rows or changes the server pagination offset. */
object ArtistAlbumSelection {
    fun visibleAlbums(albums: List<ArtistAlbum>, minTracks: Int): List<ArtistAlbum> {
        val minimum = ArtistAlbumFilterSettings.normalizeMinTracks(minTracks)
        if (minimum == 0) return albums
        return albums.filter { it.size >= minimum }
    }

    fun shouldLoadMore(
        nearListEnd: Boolean,
        visibleCount: Int,
        hasMore: Boolean,
        loading: Boolean,
        error: String?
    ): Boolean = hasMore && !loading && error == null &&
        (nearListEnd || visibleCount == 0)

    // A server claiming more pages while returning no rows cannot advance the offset.
    fun hasNextPage(page: ArtistAlbumPage): Boolean = page.hasMore && page.albums.isNotEmpty()
}

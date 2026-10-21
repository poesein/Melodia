package com.lin0721.linmusic.feature.artist.domain.catalog

import com.lin0721.linmusic.core.model.ArtistAlbum
import kotlinx.serialization.Serializable

/** A NetEase release ID is corrected explicitly; similarly named editions remain independent. */
@Serializable
data class ArtistAlbumCorrection(val album: ArtistAlbum, val show: Boolean, val active: Boolean = true)

@Serializable
data class ArtistAlbumCorrections(
    val version: Int = 1,
    val enabled: Boolean = true,
    val entries: List<ArtistAlbumCorrection> = emptyList()
) {
    /** Disabling application never removes the saved choices or their album snapshots. */
    fun apply(automatic: List<ArtistAlbum>, source: List<ArtistAlbum>, catalogEnabled: Boolean): List<ArtistAlbum> {
        if (!enabled || !catalogEnabled) return automatic
        val decisions = entries.filter { it.active }.associateBy { it.album.id }
        val sourceById = source.associateBy { it.id }
        val retained = automatic.filter { decisions[it.id]?.show != false }
        val present = retained.mapTo(hashSetOf()) { it.id }
        // Keep the automatic chronology intact. Explicit additions follow it, using fresh source
        // metadata when available and a saved snapshot while paging/offline, never title guessing.
        val added = decisions.values.filter { it.show && it.album.id !in present }
            .map { sourceById[it.album.id] ?: it.album }
            .sortedWith(compareByDescending<ArtistAlbum> { it.publishTime }.thenBy { it.id })
        return (retained + added).distinctBy { it.id }
    }

    fun editableAlbums(source: List<ArtistAlbum>): List<ArtistAlbum> =
        (source + entries.map { it.album }).distinctBy { it.id }
}

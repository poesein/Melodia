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
    fun apply(automatic: List<ArtistAlbum>, source: List<ArtistAlbum>, catalogEnabled: Boolean,
        catalogMatches: List<ArtistCatalogAlbumMatch> = emptyList()): List<ArtistAlbum> {
        if (!enabled || !catalogEnabled) return automatic
        val decisions = entries.filter { it.active }.associateBy { it.album.id }
        val sourceById = source.associateBy { it.id }
        val retained = automatic.filter { decisions[it.id]?.show != false }
        val present = retained.mapTo(hashSetOf()) { it.id }
        // Prefer fresh metadata, retaining snapshots while paging/offline. Merge additions into
        // the same chronology, preserving the catalogue's original date for remastered editions.
        val added = decisions.values.filter { it.show && it.album.id !in present }
            .map { sourceById[it.album.id] ?: it.album }
        if (added.isEmpty()) return retained
        val dates = catalogMatches.associate { it.album.id to ArtistCatalogMatcher.releaseDate(it.group.firstReleaseDate)?.toEpochDay() }
        return (retained + added).distinctBy { it.id }.sortedByDescending {
            dates[it.id] ?: it.publishTime.takeIf { time -> time != 0L }?.let { time -> Math.floorDiv(time, 86_400_000L) } ?: Long.MIN_VALUE
        }
    }

    fun editableAlbums(source: List<ArtistAlbum>): List<ArtistAlbum> =
        (source + entries.map { it.album }).distinctBy { it.id }.sortedByDescending { it.publishTime.takeIf { time -> time != 0L } ?: Long.MIN_VALUE }
}

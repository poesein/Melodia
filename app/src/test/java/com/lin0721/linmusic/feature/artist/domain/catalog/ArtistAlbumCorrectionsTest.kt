package com.lin0721.linmusic.feature.artist.domain.catalog

import com.lin0721.linmusic.core.model.ArtistAlbum
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

class ArtistAlbumCorrectionsTest {
    private val automatic = ArtistAlbum(1, "Automatic", size = 10)
    private val missed = ArtistAlbum(2, "Important mini album", size = 3, type = "EP")
    private fun dated(id: Long, year: Int) = ArtistAlbum(id, "Album $id", publishTime =
        LocalDate.of(year, 1, 1).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())

    @Test fun additionsAreInterleavedWithAutomaticAlbumsByReleaseDate() {
        val recent = dated(10, 2020); val old = dated(11, 2000)
        val newer = dated(12, 2024); val middle = dated(13, 2010); val oldest = dated(14, 1960)
        val unknown = ArtistAlbum(15, "Unknown date")
        val records = ArtistAlbumCorrections(entries = listOf(unknown, oldest, middle, newer).map { ArtistAlbumCorrection(it, true) })
        assertEquals(listOf(newer, recent, middle, old, oldest, unknown),
            records.apply(listOf(recent, old), emptyList(), true))
        assertEquals(listOf(recent, old, middle, newer, oldest).sortedByDescending { it.publishTime } + unknown,
            records.editableAlbums(listOf(recent, old)))
    }

    @Test fun catalogueOriginalReleaseDatesRemainAuthoritativeForRemasteredAlbums() {
        val remaster = dated(10, 2024); val recent = dated(11, 1990); val added = dated(12, 1985)
        val matches = listOf(
            ArtistCatalogAlbumMatch(remaster, ArtistCatalogReleaseGroup("original", "Original", "1980"), ArtistCatalogTitleMatch.EDITION),
            ArtistCatalogAlbumMatch(recent, ArtistCatalogReleaseGroup("recent", "Recent", "1990-01"), ArtistCatalogTitleMatch.TITLE))
        val records = ArtistAlbumCorrections(entries = listOf(ArtistAlbumCorrection(added, true)))
        assertEquals(listOf(recent, added, remaster), records.apply(listOf(recent, remaster), emptyList(), true, matches))
    }

    @Test fun freshDatesAndSavedSnapshotsAreSortedTogetherInTheCorrectionEditorAndFinalList() {
        val snapshot = dated(12, 1980); val fresh = dated(12, 2010); val auto = dated(11, 2000)
        val records = ArtistAlbumCorrections(entries = listOf(ArtistAlbumCorrection(snapshot, true)))
        assertEquals(listOf(fresh, auto), records.editableAlbums(listOf(auto, fresh)))
        assertEquals(listOf(fresh, auto), records.apply(listOf(auto), listOf(fresh), true))
        assertEquals(listOf(auto, snapshot), records.apply(listOf(auto), emptyList(), true))
    }

    @Test fun explicitAdditionBypassesClassificationAndMinimumAndHidingRemovesOnlyChosenId() {
        val source = listOf(automatic, missed, missed.copy(id = 3))
        val records = ArtistAlbumCorrections(entries = listOf(ArtistAlbumCorrection(missed, true),
            ArtistAlbumCorrection(automatic, false)))
        assertEquals(listOf(missed), records.apply(listOf(automatic), source, true))
        assertEquals(3, source.size)
    }

    @Test fun pausingCorrectionsOrCatalogueUsesAutomaticListWithoutChangingSavedChoices() {
        val records = ArtistAlbumCorrections(entries = listOf(ArtistAlbumCorrection(missed, true)))
        assertEquals(listOf(automatic), records.copy(enabled = false).apply(listOf(automatic), emptyList(), true))
        assertEquals(listOf(automatic), records.apply(listOf(automatic), emptyList(), false))
        assertEquals(listOf(automatic, missed), records.apply(listOf(automatic), emptyList(), true))
        assertEquals(1, records.entries.size)
    }

    @Test fun temporarilyFollowingAutomaticRetainsTheLastDecisionForRecovery() {
        val paused = ArtistAlbumCorrection(missed, true, active = false)
        val records = ArtistAlbumCorrections(entries = listOf(paused))
        assertEquals(listOf(automatic), records.apply(listOf(automatic), listOf(missed), true))
        assertEquals(listOf(automatic, missed), records.copy(entries = listOf(paused.copy(active = true)))
            .apply(listOf(automatic), listOf(missed), true))
    }

    @Test fun freshSourceMetadataWinsButSavedSnapshotKeepsAddedAlbumDuringPartialOrOfflineLoad() {
        val records = ArtistAlbumCorrections(entries = listOf(ArtistAlbumCorrection(missed, true)))
        val fresh = missed.copy(name = "Fresh title", size = 4)
        assertEquals(fresh, records.apply(emptyList(), listOf(fresh), true).single())
        assertEquals(missed, records.apply(emptyList(), emptyList(), true).single())
        assertEquals(listOf(fresh), records.editableAlbums(listOf(fresh)))
    }

    @Test fun sameNamedEditionsRemainIndependentAndAutomaticChronologyStaysIntact() {
        val second = automatic.copy(id = 5, name = automatic.name)
        val base = listOf(automatic, second)
        val records = ArtistAlbumCorrections(entries = listOf(ArtistAlbumCorrection(automatic, false)))
        assertEquals(listOf(second), records.apply(base, base, true))
        assertEquals(base, ArtistAlbumCorrections().apply(base, base, true))
    }

    @Test fun savedHiddenAlbumsAreStillAvailableForReviewWhenSourcePageIsUnavailable() {
        val records = ArtistAlbumCorrections(entries = listOf(ArtistAlbumCorrection(missed, false)))
        assertEquals(listOf(automatic, missed), records.editableAlbums(listOf(automatic)))
        assertEquals(emptyList<ArtistAlbum>(), records.apply(listOf(missed), emptyList(), true))
    }
}

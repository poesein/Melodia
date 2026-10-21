package com.lin0721.linmusic.feature.artist.domain.catalog

import com.lin0721.linmusic.core.model.ArtistAlbum
import org.junit.Assert.*
import org.junit.Test

class ArtistAlbumCorrectionsTest {
    private val automatic = ArtistAlbum(1, "Automatic", size = 10)
    private val missed = ArtistAlbum(2, "Important mini album", size = 3, type = "EP")

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

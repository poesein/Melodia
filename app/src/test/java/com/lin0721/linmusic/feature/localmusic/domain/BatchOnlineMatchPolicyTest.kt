package com.lin0721.linmusic.feature.localmusic.domain

import com.lin0721.linmusic.core.model.Album
import com.lin0721.linmusic.core.model.Artist
import com.lin0721.linmusic.core.model.Track
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class BatchOnlineMatchPolicyTest {
    private val local = LocalAudioCandidate("content://music/1", "01 五味瓶", "Artist", 180_000L, album = "Album", fileName = "01 五味瓶.flac")
    private val online = Track(id = 100L, name = "五味瓶", ar = listOf(Artist(id = 10L, name = "Artist")), al = Album(id = 20L, name = "Album"), dt = 180_200L)

    @Test fun aUniqueCompleteMatchKeepsTheOriginalOnlineMetadata() {
        val decision = BatchOnlineMatchPolicy.evaluate(local, listOf(online))
        assertEquals(BatchOnlineMatchStatus.MATCHED, decision.status)
        assertSame(online, decision.matchedTrack)
        assertEquals(listOf(online), decision.candidates)
        assertEquals("01 五味瓶", local.title)
    }

    @Test fun incompleteLocalMetadataCannotCreateAnAutomaticAssociation() {
        listOf(
            local.copy(title = ""), local.copy(artist = ""), local.copy(artist = "未知歌手"),
            local.copy(artist = "Artist, <unknown>"), local.copy(album = null),
            local.copy(album = "Unknown Album"), local.copy(durationMs = 0L)
        ).forEach { incomplete ->
            val decision = BatchOnlineMatchPolicy.evaluate(incomplete, listOf(online))
            assertEquals(BatchOnlineMatchStatus.NO_MATCH, decision.status)
            assertNull(decision.matchedTrack)
            assertTrue(decision.reason.isNotBlank())
        }
    }

    @Test fun sameTitleByAnotherArtistOrOnAnotherAlbumIsNotAccepted() {
        listOf(
            online.copy(ar = listOf(Artist(name = "Other Artist"))),
            online.copy(al = Album(name = "Other Album"))
        ).forEach {
            assertEquals(BatchOnlineMatchStatus.NO_MATCH, BatchOnlineMatchPolicy.evaluate(local, listOf(it)).status)
        }
    }

    @Test fun knownAlbumDurationToleranceIsBounded() {
        val longLocal = local.copy(durationMs = 304_838L)
        assertEquals(BatchOnlineMatchStatus.MATCHED, BatchOnlineMatchPolicy.evaluate(longLocal, listOf(online.copy(dt = 300_000L))).status)
        assertEquals(BatchOnlineMatchStatus.NO_MATCH, BatchOnlineMatchPolicy.evaluate(longLocal.copy(durationMs = 305_001L), listOf(online.copy(dt = 300_000L))).status)
    }

    @Test fun visibleAndFilenameOnlyVersionChangesRequireConfirmation() {
        assertEquals(BatchOnlineMatchStatus.NO_MATCH, BatchOnlineMatchPolicy.evaluate(local.copy(title = "01 五味瓶 (Live)"), listOf(online)).status)
        assertEquals(BatchOnlineMatchStatus.NO_MATCH, BatchOnlineMatchPolicy.evaluate(local.copy(fileName = "Artist - 五味瓶 (Live).flac"), listOf(online)).status)
        assertEquals(BatchOnlineMatchStatus.MATCHED,
            BatchOnlineMatchPolicy.evaluate(local.copy(title = "01 五味瓶 (Live)", fileName = "01 五味瓶 (Live).flac"), listOf(online.copy(name = "五味瓶 (Live)"))).status)
    }

    @Test fun multiArtistSlashTagsRequireAllVerifiedArtistsAndTheSameAlbum() {
        val duet = local.copy(artist = "A/B")
        val duetOnline = online.copy(ar = listOf(Artist(name = "B"), Artist(name = "A")))
        assertEquals(BatchOnlineMatchStatus.MATCHED, BatchOnlineMatchPolicy.evaluate(duet, listOf(duetOnline)).status)
        assertEquals(BatchOnlineMatchStatus.NO_MATCH, BatchOnlineMatchPolicy.evaluate(duet, listOf(duetOnline.copy(ar = listOf(Artist(name = "A"))))).status)
        assertEquals(BatchOnlineMatchStatus.NO_MATCH, BatchOnlineMatchPolicy.evaluate(duet, listOf(duetOnline.copy(al = Album(name = "Other Album")))).status)
    }

    @Test fun slashInsideOneBandNameIsPreserved() {
        val band = local.copy(artist = "AC/DC")
        assertEquals(BatchOnlineMatchStatus.MATCHED, BatchOnlineMatchPolicy.evaluate(band, listOf(online.copy(ar = listOf(Artist(name = "AC/DC"))))).status)
        assertEquals(BatchOnlineMatchStatus.NO_MATCH, BatchOnlineMatchPolicy.evaluate(band, listOf(online.copy(ar = listOf(Artist(name = "AC"))))).status)
    }

    @Test fun severalStrongOnlineIdsRemainAmbiguous() {
        val alternate = online.copy(id = 101L)
        val decision = BatchOnlineMatchPolicy.evaluate(local, listOf(online, alternate))
        assertEquals(BatchOnlineMatchStatus.NEEDS_CONFIRMATION, decision.status)
        assertEquals(listOf(online, alternate), decision.candidates)
        assertNull(decision.matchedTrack)
    }

    @Test fun repeatedSearchPagesForTheSameSongAreNotAmbiguous() {
        val decision = BatchOnlineMatchPolicy.evaluate(local, listOf(online, online.copy()))
        assertEquals(BatchOnlineMatchStatus.MATCHED, decision.status)
        assertEquals(1, decision.candidates.size)
    }

    @Test fun incompleteOnlineMetadataDoesNotOutvoteACompleteUniqueMatch() {
        val incomplete = listOf(
            online.copy(id = 101L, ar = emptyList()),
            online.copy(id = 102L, ar = listOf(Artist(name = "Artist"), Artist(name = ""))),
            online.copy(id = 103L, al = Album(name = "")),
            online.copy(id = 104L, dt = 0L)
        )
        assertEquals(BatchOnlineMatchStatus.NO_MATCH, BatchOnlineMatchPolicy.evaluate(local, incomplete).status)
        assertSame(online, BatchOnlineMatchPolicy.evaluate(local, incomplete + online).matchedTrack)
    }

    @Test fun emptyOrInvalidSearchResultsNeverProduceAConfirmedMatch() {
        assertEquals(BatchOnlineMatchStatus.NO_MATCH, BatchOnlineMatchPolicy.evaluate(local, emptyList()).status)
        assertEquals(BatchOnlineMatchStatus.NO_MATCH, BatchOnlineMatchPolicy.evaluate(local, listOf(online.copy(id = 0L))).status)
    }

    @Test fun numericCatalogNamesAreKeptAndNoArtistAliasIsGuessed() {
        assertEquals(BatchOnlineMatchStatus.MATCHED,
            BatchOnlineMatchPolicy.evaluate(local.copy(title = "01 7 rings"), listOf(online.copy(name = "7 rings"))).status)
        assertEquals(BatchOnlineMatchStatus.NO_MATCH,
            BatchOnlineMatchPolicy.evaluate(local.copy(artist = "GAI"), listOf(online.copy(ar = listOf(Artist(name = "GAI周延"))))).status)
        assertEquals(BatchOnlineMatchStatus.NO_MATCH,
            BatchOnlineMatchPolicy.evaluate(local.copy(title = "五味瓶"), listOf(online.copy(name = "01 五味瓶"))).status)
    }
}

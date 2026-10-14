package com.lin0721.linmusic.feature.artist.domain.catalog

import com.lin0721.linmusic.core.model.ArtistAlbum
import java.time.LocalDate
import java.time.ZoneOffset
import org.junit.Assert.*
import org.junit.Test

class ArtistCatalogMatcherTest {
    private fun group(id: String, title: String, primary: String? = "Album", secondary: List<String>? = emptyList(),
                      date: String? = null, aliases: List<String> = emptyList(), artist: String = "correct-artist") =
        ArtistCatalogReleaseGroup(id, title, date, primary, secondary, aliases, listOf(ArtistCatalogCredit(artist, "Artist")))

    private fun album(id: Long, title: String, date: String? = null, size: Int = 10) = ArtistAlbum(
        id = id, name = title, size = size,
        publishTime = date?.let { LocalDate.parse(it).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli() } ?: 0
    )

    @Test
    fun albumNeedsAnExplicitEmptySecondaryTypeList() {
        assertEquals(ArtistCatalogClassification.ORDINARY_ALBUM, ArtistCatalogMatcher.classify(group("a", "A")))
        assertEquals(ArtistCatalogClassification.UNKNOWN, ArtistCatalogMatcher.classify(group("a", "A", secondary = null)))
        assertEquals(ArtistCatalogClassification.UNKNOWN, ArtistCatalogMatcher.classify(group("a", "A", primary = null)))
        assertEquals(ArtistCatalogClassification.UNKNOWN, ArtistCatalogMatcher.classify(group("a", "A", primary = "Unrecognized")))
    }

    @Test
    fun knownPrimaryAndAllSecondaryTypesAreExcluded() {
        listOf("Single", "EP", "Broadcast", "Other").forEach {
            assertEquals(ArtistCatalogClassification.EXCLUDED, ArtistCatalogMatcher.classify(group("a", "A", primary = it)))
        }
        listOf("Live", "Compilation", "Remix", "Soundtrack", "DJ-mix", "Demo").forEach {
            assertEquals(ArtistCatalogClassification.EXCLUDED, ArtistCatalogMatcher.classify(group("a", "A", secondary = listOf(it))))
        }
    }

    @Test
    fun liveForTodayIsKeptBecauseItsCatalogTypeIsAnOrdinaryAlbum() {
        val groups = listOf(group("studio", "Live for Today"), group("live", "THE LIVE", secondary = listOf("Live")))
        val result = ArtistCatalogMatcher.select(listOf(album(1, "Live for Today"), album(2, "THE LIVE")), groups)
        assertEquals(listOf(1L), result.albums.map { it.id })
        assertEquals(1, result.excludedCount)
    }

    @Test
    fun traditionalChineseCatalogTitlesMatchSimplifiedOnlineTitlesAndNames() {
        val result = ArtistCatalogMatcher.select(listOf(album(1, "认了吧")), listOf(group("album", "認了吧")))
        assertEquals(listOf(1L), result.albums.map { it.id })
        val artist = ArtistCatalogArtist("id", "陳奕迅")
        assertEquals(listOf(artist), ArtistCatalogMatcher.exactNameCandidates(listOf(artist), listOf("陈奕迅")))
    }

    @Test
    fun aliasesCanIdentifyAnAlbumWithoutGuessingAnUnrecognizedTitle() {
        val groups = listOf(group("album", "認了吧", aliases = listOf("Admit It")))
        val result = ArtistCatalogMatcher.select(listOf(album(1, "Admit It"), album(2, "A Similar Name")), groups)
        assertEquals(listOf(1L), result.albums.map { it.id })
        assertEquals(ArtistCatalogTitleMatch.ALIAS, result.matches.single().titleMatch)
        assertEquals(listOf(2L), result.unmatchedAlbums.map { it.id })
    }

    @Test
    fun remasterRegionalAndDeluxeEditionsCollapseToOneOriginalGroup() {
        val groups = listOf(group("album", "The Key", date = "2013-07-22"))
        val albums = listOf(
            album(1, "The Key (2018 Remastered)", "2018-01-01"),
            album(2, "The Key (Japanese Edition)", "2013-07-25"),
            album(3, "The Key Deluxe Edition", "2013-07-22", 20),
            album(4, "The Key", "2013-07-22", 10)
        )
        val result = ArtistCatalogMatcher.select(albums, groups)
        assertEquals(listOf(4L), result.albums.map { it.id })
        assertEquals(3, result.duplicateCount)
        assertEquals(1, result.matchedCount)
    }

    @Test
    fun regionalEditionIsStillUsableWhenThereIsNoOriginalOnlineVersion() {
        val result = ArtistCatalogMatcher.select(listOf(album(1, "The Key（日本版）")), listOf(group("album", "The Key")))
        assertEquals(listOf(1L), result.albums.map { it.id })
        assertEquals(ArtistCatalogTitleMatch.EDITION, result.matches.single().titleMatch)
    }

    @Test
    fun recordingVersionsAreNotRemovedAsHarmlessEditionSuffixes() {
        val result = ArtistCatalogMatcher.select(
            listOf(album(1, "The Key (Live)"), album(2, "The Key (Remix)"), album(3, "The Key (Acoustic)")),
            listOf(group("album", "The Key"))
        )
        assertTrue(result.albums.isEmpty())
        assertEquals(3, result.unmatchedCount)
    }

    @Test
    fun exactCatalogTitleWinsOverAHeuristicallyStrippedEditionSuffix() {
        val result = ArtistCatalogMatcher.select(
            listOf(album(1, "The Key (Deluxe Edition)")),
            listOf(group("original", "The Key"), group("separate", "The Key (Deluxe Edition)"))
        )
        assertEquals("separate", result.matches.single().group.id)
    }

    @Test
    fun identicalTitlesInDifferentGroupsAreReportedAsAmbiguous() {
        val result = ArtistCatalogMatcher.select(listOf(album(1, "Same")), listOf(group("a", "Same"), group("b", "Same")))
        assertTrue(result.albums.isEmpty())
        assertEquals(1, result.ambiguousAlbums.size)
        assertEquals(1, result.unmatchedCount)
        assertEquals(2, result.unmatchedReleaseGroups.size)
    }

    @Test
    fun unknownClassificationIsNotPromotedToAnOrdinaryAlbum() {
        val result = ArtistCatalogMatcher.select(listOf(album(1, "Same")), listOf(group("a", "Same", secondary = null)))
        assertTrue(result.albums.isEmpty())
        assertEquals(1, result.unmatchedCount)
    }

    @Test
    fun boundArtistMustBePresentInCatalogCredits() {
        val result = ArtistCatalogMatcher.select(listOf(album(1, "A")), listOf(group("a", "A", artist = "someone-else")), boundArtistMbid = "correct-artist")
        assertTrue(result.albums.isEmpty())
        assertEquals(1, result.unmatchedCount)
    }

    @Test
    fun earliestCompatibleCanonicalEditionIsPreferredAndDateFailuresAreTolerated() {
        val result = ArtistCatalogMatcher.select(
            listOf(album(1, "A", "2020-01-01"), album(2, "A", "2008-07-01"), album(3, "A")),
            listOf(group("a", "A", date = "2008"))
        )
        assertEquals(2L, result.albums.single().id)
        val unknownDate = ArtistCatalogMatcher.select(listOf(album(1, "A"), album(2, "A", "2010-01-01")), listOf(group("a", "A", date = "bad date")))
        assertEquals(2L, unknownDate.albums.single().id)
    }

    @Test
    fun punctuationOnlyTitleRemainsDistinctFromAnEmptyTitle() {
        val result = ArtistCatalogMatcher.select(listOf(album(1, "?"), album(2, "   ")), listOf(group("a", "?")))
        assertEquals(listOf(1L), result.albums.map { it.id })
        assertEquals(listOf(2L), result.unmatchedAlbums.map { it.id })
    }

    @Test
    fun eachFetchedOnlineAlbumIsAccountedForAndMissingCatalogGroupsAreCounted() {
        val groups = listOf(group("a", "A"), group("b", "B"), group("c", "C"), group("live", "Concert", secondary = listOf("Live")), group("ep", "Short", primary = "EP"))
        val albums = listOf(album(1, "A"), album(2, "A (Remastered)"), album(3, "B"), album(4, "Concert"), album(5, "Short"), album(6, "Unknown"))
        val result = ArtistCatalogMatcher.select(albums, groups)
        assertEquals(2, result.matchedCount)
        assertEquals(1, result.duplicateCount)
        assertEquals(2, result.excludedCount)
        assertEquals(1, result.unmatchedCount)
        assertEquals(6, result.matchedCount + result.duplicateCount + result.excludedCount + result.unmatchedCount)
        assertEquals(listOf("c"), result.unmatchedReleaseGroups.map { it.id })
    }

    @Test
    fun twoDistinctKnownAlbumsSupportIdentityButTwoEditionsOfOneAlbumDoNot() {
        val groups = listOf(group("a", "A"), group("b", "B"))
        val one = ArtistCatalogMatcher.identityEvidence(listOf(album(1, "A"), album(2, "A (Deluxe Edition)")), groups)
        assertEquals(1, one.matchedCount)
        assertFalse(one.canAutoBind)
        val two = ArtistCatalogMatcher.identityEvidence(listOf(album(1, "A"), album(2, "B")), groups, boundArtistMbid = "correct-artist")
        assertEquals(2, two.matchedCount)
        assertTrue(two.canAutoBind)
    }

    @Test
    fun searchScoreAloneIsNotIdentityEvidenceAndAllExactNameDuplicatesRemainVisible() {
        val artists = listOf(ArtistCatalogArtist("a", "Different", score = 100), ArtistCatalogArtist("b", "Artist", score = 90), ArtistCatalogArtist("c", "Artist", score = 85))
        assertEquals(listOf("b", "c"), ArtistCatalogMatcher.exactNameCandidates(artists, listOf("Artist")).map { it.id })
        assertFalse(ArtistCatalogMatcher.identityEvidence(emptyList(), emptyList()).canAutoBind)
    }
}

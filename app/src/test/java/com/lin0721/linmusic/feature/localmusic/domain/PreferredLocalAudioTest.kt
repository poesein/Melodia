package com.lin0721.linmusic.feature.localmusic.domain

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class PreferredLocalAudioTest {
    private val studio = LocalAudioCandidate("content://studio", "Song", "Artist", 180_000L)

    @Test fun uniqueExactMetadataAndDurationMatch() {
        assertEquals(studio.uri, PreferredLocalAudioMatcher.uniqueMatch(" SONG ", "artist", 181_000L, listOf(studio)))
    }

    @Test fun ambiguousCopiesAreNotChosen() {
        assertNull(PreferredLocalAudioMatcher.uniqueMatch("Song", "Artist", 180_000L, listOf(studio, studio.copy(uri = "content://copy"))))
    }

    @Test fun repeatedDatabaseReferenceIsOneCandidate() {
        assertEquals(studio.uri, PreferredLocalAudioMatcher.uniqueMatch("Song", "Artist", 180_000L, listOf(studio, studio)))
    }

    @Test fun scannedAndImportedUrisForTheSamePhysicalFileAreOneCandidate() {
        val scanned = studio.copy(identity = "file:1:2:0:500")
        val imported = studio.copy(uri = "content://documents/same-file", identity = scanned.identity)
        assertEquals(scanned.uri, PreferredLocalAudioMatcher.uniqueMatch("Song", "Artist", 180_000L, listOf(scanned, imported)))
    }

    @Test fun genuinelyDifferentFilesStayAmbiguousEvenWithEqualMetadata() {
        val scanned = studio.copy(identity = "file:1:2:0:500")
        val other = studio.copy(uri = "content://documents/other-file", identity = "file:1:3:0:500")
        assertNull(PreferredLocalAudioMatcher.uniqueMatch("Song", "Artist", 180_000L, listOf(scanned, other)))
    }

    @Test fun liveRemasterAndAcousticSuffixesArePreserved() {
        listOf("Song (Live)", "Song - Remastered", "Song (Acoustic)", "Song (伴奏)").forEach { title ->
            assertNull(PreferredLocalAudioMatcher.uniqueMatch("Song", "Artist", 180_000L, listOf(studio.copy(title = title))))
        }
    }

    @Test fun missingOrDifferentDurationIsNotAccepted() {
        assertNull(PreferredLocalAudioMatcher.uniqueMatch("Song", "Artist", 0L, listOf(studio)))
        assertNull(PreferredLocalAudioMatcher.uniqueMatch("Song", "Artist", 182_001L, listOf(studio)))
        assertNull(PreferredLocalAudioMatcher.uniqueMatch("Song", "Artist", 180_000L, listOf(studio.copy(durationMs = 0L))))
    }

    @Test fun sameTitleByAnotherSingerIsNotAccepted() {
        assertNull(PreferredLocalAudioMatcher.uniqueMatch("Song", "Another Artist", 180_000L, listOf(studio)))
        assertNull(PreferredLocalAudioMatcher.uniqueMatch("Song", "未知艺术家", 180_000L, listOf(studio.copy(artist = "未知艺术家"))))
    }

    @Test fun completeArtistSetIsRequiredAndOrderCanDiffer() {
        val duet = studio.copy(artist = "A, B")
        assertEquals(duet.uri, PreferredLocalAudioMatcher.uniqueMatch("Song", "B • A", 180_000L, listOf(duet)))
        assertNull(PreferredLocalAudioMatcher.uniqueMatch("Song", "A", 180_000L, listOf(duet)))
    }

    @Test fun slashInsideArtistNameIsPreserved() {
        val band = studio.copy(artist = "AC/DC")
        assertEquals(band.uri, PreferredLocalAudioMatcher.uniqueMatch("Song", "AC/DC", 180_000L, listOf(band)))
        assertNull(PreferredLocalAudioMatcher.uniqueMatch("Song", "AC, DC", 180_000L, listOf(band)))
    }

    @Test fun realSameAlbumEncodingCopiesPreferFlacWithinVerifiedDurationTolerance() {
        val mp3 = LocalAudioCandidate("content://mp3", "STAY TUNE", "Suchmos", 298_747L, album = "LOVE&VICE", fileName = "Suchmos - STAY TUNE.mp3")
        val flac = mp3.copy(uri = "content://flac", identity = "other-file", fileName = "Suchmos - STAY TUNE.flac")
        assertEquals(flac.uri, PreferredLocalAudioMatcher.uniqueMatch("STAY TUNE", "Suchmos", 295_858L, listOf(mp3, flac), "LOVE&VICE"))
    }

    @Test fun extendedDurationToleranceRequiresMatchingAlbumAndHasFiveSecondCap() {
        val candidate = studio.copy(durationMs = 304_838L, album = "Confirmed Album")
        assertEquals(candidate.uri, PreferredLocalAudioMatcher.uniqueMatch("Song", "Artist", 300_000L, listOf(candidate), "Confirmed Album"))
        assertNull(PreferredLocalAudioMatcher.uniqueMatch("Song", "Artist", 300_000L, listOf(candidate)))
        assertNull(PreferredLocalAudioMatcher.uniqueMatch("Song", "Artist", 300_000L, listOf(candidate), "Another Album"))
        assertNull(PreferredLocalAudioMatcher.uniqueMatch("Song", "Artist", 300_000L, listOf(candidate.copy(durationMs = 305_001L)), "Confirmed Album"))
    }

    @Test fun differentAlbumCopiesStillRequireManualChoice() {
        val one = studio.copy(album = "First", fileName = "song.flac")
        val two = studio.copy(uri = "content://second", identity = "second-file", album = "Second", fileName = "song.mp3")
        assertNull(PreferredLocalAudioMatcher.uniqueMatch("Song", "Artist", 180_000L, listOf(one, two), "Third"))
    }

    @Test fun filenameLiveEvidenceRejectsMislabelledStudioTag() {
        val live = studio.copy(album = "Album", fileName = "Artist - Song (Live at Tokyo).flac")
        assertNull(PreferredLocalAudioMatcher.uniqueMatch("Song", "Artist", 180_000L, listOf(live), "Album"))
    }

    @Test fun verifiedMultiArtistSlashCreditMatchesOnlyWithSameAlbum() {
        val duet = studio.copy(artist = "陈奕迅/eason and the duo band", album = "L.O.V.E.")
        assertEquals(duet.uri, PreferredLocalAudioMatcher.uniqueMatch("Song", "陈奕迅 • eason and the duo band", 180_000L, listOf(duet), "L.O.V.E."))
        assertNull(PreferredLocalAudioMatcher.uniqueMatch("Song", "陈奕迅 • eason and the duo band", 180_000L, listOf(duet)))
    }

    @Test fun manualChoiceHasPriorityAndDoesNotRunAutoMatching() = runBlocking {
        assertEquals("content://manual", resolvePreferredLocalAudio(true, "content://manual", true,
            automaticUri = { error("Manual choice must win") }, isReadable = { true }))
    }

    @Test fun unreadableManualChoiceFallsBackWithoutChoosingAnotherLocalVersion() = runBlocking {
        assertNull(resolvePreferredLocalAudio(true, "content://missing", true,
            automaticUri = { error("Must fall back to normal downloaded/online resolution") }, isReadable = { false }))
    }

    @Test fun unreadableAutomaticCandidateFallsBack() = runBlocking {
        assertNull(resolvePreferredLocalAudio(true, null, true, automaticUri = { studio.uri }, isReadable = { false }))
    }

    @Test fun disablingPreferenceOrUnlinkExclusionAvoidsLookup() = runBlocking {
        var checked = false
        assertNull(resolvePreferredLocalAudio(false, "content://manual", true,
            automaticUri = { error("Disabled") }, isReadable = { checked = true; true }))
        assertFalse(checked)
        assertNull(resolvePreferredLocalAudio(true, null, false,
            automaticUri = { error("Unlinked songs cannot auto-rematch") }, isReadable = { true }))
    }
}

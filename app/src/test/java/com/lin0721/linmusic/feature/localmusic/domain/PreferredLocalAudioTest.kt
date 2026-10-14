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

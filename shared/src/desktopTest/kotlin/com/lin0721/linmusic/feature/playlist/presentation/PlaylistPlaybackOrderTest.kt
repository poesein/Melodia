package com.lin0721.linmusic.feature.playlist.presentation

import org.junit.Assert.*
import org.junit.Test

class PlaylistPlaybackOrderTest {
    @Test fun changingDirectionRequiresANewQueueInsteadOfTogglingTheOldQueue() {
        val original = listOf(3L, 2L, 1L)
        assertFalse(playlistQueueMatches(original, original.reversed(), false))
        assertTrue(playlistQueueMatches(original.reversed(), original.reversed(), false))
        assertFalse(playlistQueueMatches(original.reversed(), original, false))
    }
    @Test fun shufflePreservesItsExistingMembershipAndMultiplicity() {
        assertTrue(playlistQueueMatches(listOf(2L, 1L, 3L), listOf(1L, 2L, 3L), true))
        assertFalse(playlistQueueMatches(listOf(1L, 1L, 2L), listOf(1L, 2L, 2L), true))
        assertFalse(playlistQueueMatches(listOf(1L, 2L), listOf(1L, 2L, 3L), true))
    }
    @Test fun partialDefaultPagesCanResumeTheSameQueueWithoutWaitingForTheRest() {
        assertTrue(playlistQueueMatches(listOf(1L, 2L, 3L), listOf(1L, 2L), false, true))
        assertFalse(playlistQueueMatches(listOf(3L, 2L, 1L), listOf(1L, 2L), false, true))
        assertFalse(playlistQueueMatches(listOf(1L), listOf(1L, 2L), false, true))
        assertTrue(playlistQueueMatches(listOf(2L, 3L, 1L), listOf(1L, 2L), true, true))
    }
    @Test fun anEmptyQueueOrPlaylistIsNeverReusedAsAPlaybackContext() {
        assertFalse(playlistQueueMatches(emptyList(), emptyList(), false))
        assertFalse(playlistQueueMatches(emptyList(), listOf(1L), true))
    }
    @Test fun addingOrRemovingSongsRequiresRefreshingTheQueueOnTheNextPlaybackAction() {
        val current = listOf(1L, 2L, 3L)
        assertFalse(playlistQueueMatches(current, current + 4L, false))
        assertFalse(playlistQueueMatches(current, listOf(1L, 3L), false))
        assertFalse(playlistQueueMatches(current, current + 4L, true))
    }
}

package com.lin0721.linmusic.feature.library.textimport

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class PlaylistTextWriterTest {
    @Test fun firstWriteCreatesOnceAndRetainsOrder() = runTest {
        val writer = PlaylistTextWriter()
        var creates = 0
        val writes = mutableListOf<List<Long>>()
        val create: suspend () -> Long = { creates++; 50L }
        val add: suspend (Long, List<Long>) -> Unit = { id, ids -> assertEquals(50L, id); writes.add(ids); Unit }
        assertEquals(50L, writer.write(listOf(3, 2, 3, 1, -1), create, add, { error("New playlist needs no read") }))
        assertEquals(50L, writer.write(listOf(3, 2, 1), create, add, { error("Completed session needs no read") }))
        assertEquals(1, creates)
        assertEquals(listOf(listOf(3L, 2L, 1L)), writes)
    }

    @Test fun retryReusesCreatedPlaylistAndAddsOnlyMissingIds() = runTest {
        val writer = PlaylistTextWriter()
        var creates = 0
        val writes = mutableListOf<List<Long>>()
        val create: suspend () -> Long = { creates++; 50L }
        try { writer.write(listOf(3, 2, 1), create, { _, _ -> error("partial write") }, { setOf(3) }); fail() }
        catch (_: IllegalStateException) { }
        assertEquals(50L, writer.playlistId)
        writer.write(listOf(3, 2, 1), create, { _, ids -> writes.add(ids); Unit }, { setOf(3) })
        assertEquals(1, creates)
        assertEquals(listOf(listOf(2L, 1L)), writes)
    }

    @Test fun lostAcknowledgementIsReconciledWithoutRepeatingWrite() = runTest {
        val writer = PlaylistTextWriter()
        var writes = 0
        assertEquals(50L, writer.write(listOf(1, 2), { 50L }, { _, _ -> writes++; error("reply lost") }, { setOf(1, 2) }))
        writer.write(listOf(1, 2), { error("must reuse") }, { _, _ -> writes++ }, { error("must reuse") })
        assertEquals(1, writes)
    }

    @Test fun failedRetryReadStopsInsteadOfGuessingThePlaylistIsEmpty() = runTest {
        val writer = PlaylistTextWriter()
        try { writer.write(listOf(1), { 50L }, { _, _ -> error("write failed") }, { emptySet() }); fail() }
        catch (_: IllegalStateException) { }
        var writes = 0
        try { writer.write(listOf(1), { error("must reuse") }, { _, _ -> writes++ }, { error("read failed") }); fail() }
        catch (_: IllegalStateException) { }
        assertEquals(0, writes)
    }

    @Test fun createdSessionRejectsChangingSelection() = runTest {
        val writer = PlaylistTextWriter()
        writer.write(listOf(1), { 50L }, { _, _ -> }, { emptySet() })
        try { writer.write(listOf(2), { error("must not create") }, { _, _ -> fail() }, { emptySet() }); fail() }
        catch (_: IllegalArgumentException) { }
    }

    @Test fun emptyImportDoesNotCreateAnything() = runTest {
        try { PlaylistTextWriter().write(emptyList(), { fail(); 1L }, { _, _ -> fail() }, { emptySet() }); fail() }
        catch (_: IllegalArgumentException) { }
    }

    @Test fun existingPlaylistReadsBeforeFirstWriteAndOnlyAddsMissingInOrder() = runTest {
        val writes = mutableListOf<List<Long>>()
        val writer = PlaylistTextWriter()
        assertEquals(80L, writer.write(listOf(3, 2, 1), { error("must not create") },
            { id, ids -> assertEquals(80L, id); writes.add(ids); Unit }, { setOf(2) }, existingPlaylistId = 80))
        assertEquals(listOf(listOf(3L, 1L)), writes)
    }

    @Test fun existingReadFailurePreventsWritesAndSessionCannotSwitchTarget() = runTest {
        val writer = PlaylistTextWriter()
        try { writer.write(listOf(1), { error("must not create") }, { _, _ -> fail() },
            { error("read failed") }, existingPlaylistId = 80); fail() } catch (_: IllegalStateException) { }
        assertEquals(80L, writer.playlistId)
        try { writer.write(listOf(1), { error("must not create") }, { _, _ -> fail() },
            { emptySet() }, existingPlaylistId = 90); fail() } catch (_: IllegalArgumentException) { }
        writer.write(listOf(1), { error("must not create") }, { _, ids -> assertEquals(listOf(1L), ids) },
            { emptySet() }, existingPlaylistId = 80)
    }

    @Test fun cancellationIsNotConvertedToSuccessOrReadRetry() = runTest {
        val writer = PlaylistTextWriter()
        var reads = 0
        try { writer.write(listOf(1), { 50L }, { _, _ -> throw CancellationException() }, { reads++; emptySet() }); fail() }
        catch (_: CancellationException) { }
        assertEquals(0, reads)
        assertEquals(50L, writer.playlistId)
    }
}

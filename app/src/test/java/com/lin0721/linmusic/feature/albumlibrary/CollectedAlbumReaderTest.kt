package com.lin0721.linmusic.feature.albumlibrary

import com.lin0721.linmusic.core.model.PlaylistDetail
import com.lin0721.linmusic.core.model.Track
import com.lin0721.linmusic.core.model.PlaylistTrackId
import com.lin0721.linmusic.core.network.AppError
import kotlinx.coroutines.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class CollectedAlbumReaderTest {
    private fun detail(id: Long, date: Long = 0) = PlaylistDetail(id = id, tracks = listOf(Track(id * 10)), publishTime = date)

    @Test fun networkRequestsAreSerialAndPacedEvenWhenPreparationSubmitsManyAlbums() = runTest {
        var active = 0; var maximum = 0
        val waits = mutableListOf<Long>()
        val reader = CollectedAlbumReader(fetch = { id ->
            active++; maximum = maxOf(maximum, active)
            try { delay(10); detail(id) } finally { active-- }
        }, pause = { waits += it; delay(it) })
        coroutineScope { (1L..20L).map { async { reader.read(it) } }.awaitAll() }
        assertEquals(1, maximum); assertEquals(List(19) { 450L }, waits)
    }

    @Test fun throttlingAndTransientFailuresRetryBeforeGivingUp() = runTest {
        var calls = 0; val waits = mutableListOf<Long>()
        val reader = CollectedAlbumReader(fetch = {
            calls++
            when (calls) { 1 -> throw AppError.RiskControl; 2 -> throw AppError.BizError(429, null); else -> detail(it) }
        }, pause = { waits += it })
        assertEquals(1L, reader.read(1).id)
        assertEquals(1L, reader.read(1).id)
        assertEquals(3, calls); assertEquals(listOf(1_000L, 2_000L), waits)
    }

    @Test fun individualAlbumPageCacheAvoidsNewNetworkRequestsAndBatchPacing() = runTest {
        var calls = 0; val waits = mutableListOf<Long>()
        val reader = CollectedAlbumReader(fetch = { calls++; detail(it) }, peek = { detail(it) }, pause = { waits += it })
        (1L..20L).forEach { reader.read(it) }
        assertEquals(0, calls); assertTrue(waits.isEmpty())
    }

    @Test fun wrongIdAndIncompleteDiskCacheAreNotUsed() = runTest {
        var calls = 0
        val reader = CollectedAlbumReader(fetch = { calls++; detail(it) }, peek = { detail(999) }, pause = {})
        assertEquals(1L, reader.read(1).id); assertEquals(1, calls)
        val incomplete = detail(2).copy(trackIds = listOf(PlaylistTrackId(20), PlaylistTrackId(21)))
        val second = CollectedAlbumReader(fetch = { calls++; detail(it) }, peek = { incomplete }, pause = {})
        assertEquals(2L, second.read(2).id); assertEquals(2, calls)
    }

    @Test fun failedAlbumDoesNotEraseOtherSuccessfulReadsWhenTheUserRetries() = runTest {
        val calls = mutableMapOf<Long, Int>(); var broken = true
        val reader = CollectedAlbumReader(fetch = { id ->
            calls[id] = (calls[id] ?: 0) + 1
            if (id == 2L && broken) throw AppError.BizError(404, null)
            detail(id)
        }, pause = {})
        reader.read(1)
        assertTrue(runCatching { reader.read(2) }.isFailure)
        broken = false
        reader.read(1); reader.read(2); reader.read(3)
        assertEquals(mapOf(1L to 1, 2L to 2, 3L to 1), calls)
    }

    @Test fun unauthorizedErrorsDoNotGetRetried() = runTest {
        var calls = 0
        val reader = CollectedAlbumReader(fetch = { calls++; throw AppError.Unauthorized })
        assertSame(AppError.Unauthorized, runCatching { reader.read(1) }.exceptionOrNull())
        assertEquals(1, calls)
    }

    @Test fun cancellationStopsTheReaderWithoutRetries() = runTest {
        var calls = 0
        val reader = CollectedAlbumReader(fetch = { calls++; awaitCancellation() })
        val job = launch { reader.read(1) }
        delay(100); job.cancelAndJoin(); assertEquals(1, calls)
    }

    @Test fun missingPublicationDateCanRefreshOnceAndNegativeDatesAreValid() = runTest {
        var calls = 0
        val reader = CollectedAlbumReader(fetch = { calls++; detail(it, -315619200000L) }, peek = { detail(it) }, pause = {})
        assertEquals(0L, reader.read(1).publishTime)
        assertEquals(-315619200000L, reader.read(1, requireDate = true).publishTime)
        reader.read(1, requireDate = true); assertEquals(1, calls)
    }
}

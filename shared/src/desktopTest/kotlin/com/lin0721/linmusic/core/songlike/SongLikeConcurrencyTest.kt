package com.lin0721.linmusic.core.songlike

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SongLikeConcurrencyTest {
    private class Api : SongLikeApi {
        var read: suspend () -> List<Long> = { emptyList() }
        var write: suspend (LikeSongRequest) -> Int = { 200 }
        override suspend fun likeSong(body: LikeSongRequest) = LikeSongResponse(code = write(body))
        override suspend fun getLikedSongIds(body: LikeSongListRequest) = LikeSongListResponse(200, read())
    }

    @Test fun fiftyRapidUnlikesAreSerializedAndRecoverFromATransientFailureAfterThirty() = runTest {
        val api = Api(); val starts = mutableListOf<Long>(); var active = 0; var maximum = 0; var failedOnce = false
        val repo = SongLikeRepositoryImpl(api, now = { currentTime })
        repo.syncLikedSongIds((1L..50L).toSet())
        api.write = { body ->
            starts += currentTime; active++; maximum = maxOf(maximum, active)
            delay(20); active--
            if (body.trackId == 31L && !failedOnce) { failedOnce = true; 429 } else 200
        }
        val results = (1L..50L).map { async { repo.likeSong(it, false).first() } }.awaitAll()
        assertTrue(results.all { it.isSuccess }); assertEquals(1, maximum)
        assertTrue(starts.zipWithNext().all { (a, b) -> b - a >= 800 })
        assertEquals(51, starts.size); assertTrue(repo.likedSongIds.value.isEmpty())
    }

    @Test fun aFailedWriteNeverRestoresEarlierSuccessfulUnlikes() = runTest {
        val api = Api(); val repo = SongLikeRepositoryImpl(api, now = { currentTime })
        repo.syncLikedSongIds((1L..40L).toSet())
        api.write = { if (it.trackId == 31L) { delay(50); 400 } else { delay(10); 200 } }
        val results = (1L..40L).map { async { repo.likeSong(it, false).first() } }.awaitAll()
        assertEquals(1, results.count { it.isFailure }); assertEquals(setOf(31L), repo.likedSongIds.value)
    }

    @Test fun aStaleRefreshCannotRestoreAJustRemovedHeartAndReturnsTheMergedState() = runTest {
        val api = Api(); api.read = { listOf(1, 2) }
        val repo = SongLikeRepositoryImpl(api, now = { currentTime }); repo.getLikedSongIds(99).first()
        val release = CompletableDeferred<Unit>(); var oldRead = true
        api.read = { if (oldRead) { oldRead = false; release.await(); listOf(1, 2) } else listOf(2) }
        val refresh = async { repo.getLikedSongIds(99).first() }; runCurrent()
        repo.likeSong(1, false).first()
        assertEquals(listOf(2L), repo.getLikedSongIds(99).first().getOrThrow())
        release.complete(Unit)
        assertEquals(listOf(2L), refresh.await().getOrThrow()); assertEquals(setOf(2L), repo.likedSongIds.value)
    }

    @Test fun persistentRateLimitStopsTheBurstWithoutChangingTheConfirmedState() = runTest {
        val api = Api(); var requests = 0; api.write = { requests++; 429 }
        val repo = SongLikeRepositoryImpl(api, now = { currentTime }); repo.syncLikedSongIds(setOf(1, 2))
        assertTrue(repo.likeSong(1, false).first().isFailure)
        assertTrue(repo.likeSong(2, false).first().isFailure)
        assertEquals(3, requests); assertEquals(setOf(1L, 2L), repo.likedSongIds.value)
    }

    @Test fun cancelledWriteDoesNotLeaveAnOptimisticHeartAndNewAccountDropsOldOverlays() = runTest {
        val api = Api(); val repo = SongLikeRepositoryImpl(api, now = { currentTime })
        api.write = { awaitCancellation() }
        val request = launch { repo.likeSong(1, true).first() }; runCurrent(); request.cancelAndJoin()
        assertTrue(repo.likedSongIds.value.isEmpty())
        api.read = { listOf(1) }; repo.getLikedSongIds(99).first()
        api.write = { 200 }; repo.likeSong(1, false).first()
        api.read = { listOf(10) }; repo.getLikedSongIds(100).first()
        assertEquals(setOf(10L), repo.likedSongIds.value)
    }
}

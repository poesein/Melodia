package com.lin0721.linmusic.feature.albumlibrary

import com.lin0721.linmusic.core.cache.MetadataCache
import com.lin0721.linmusic.core.cache.OfflineFallback
import com.lin0721.linmusic.core.model.EmptyBody
import com.lin0721.linmusic.core.network.AppError
import com.lin0721.linmusic.core.network.OnlineStateProvider
import com.lin0721.linmusic.feature.library.data.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files

class CollectedAlbumsRepositoryTest {
    private class Api(private val page: suspend (AlbumSublistRequest) -> AlbumSublistResponse) : LibraryApi {
        val requests = mutableListOf<AlbumSublistRequest>()
        override suspend fun getAlbumSublist(body: AlbumSublistRequest): AlbumSublistResponse {
            requests += body
            return page(body)
        }
        override suspend fun getUserRecord(body: UserRecordRequest): UserRecordResponse = error("unused")
        override suspend fun getUserSubcount(body: EmptyBody): UserSubcountResponse = error("unused")
    }

    private val firstPage get() = (1L..200L).map { AlbumSubItem(id = it, name = "Album $it") }

    private suspend fun fixture(block: suspend (java.io.File, MutableStateFlow<Boolean>, (LibraryApi) -> CollectedAlbumsRepository) -> Unit) {
        val root = Files.createTempDirectory("melodia-collected-albums").toFile()
        val onlineFlag = MutableStateFlow(true)
        val fallback = OfflineFallback(MetadataCache(root) { 99L }, object : OnlineStateProvider { override val online = onlineFlag })
        try { block(root, onlineFlag) { CollectedAlbumsRepository(it, fallback) } }
        finally { root.deleteRecursively() }
    }

    @Test fun fetchesPastFirstPageAndDeduplicatesIdsWithoutReordering() = runBlocking {
        fixture { _, _, repository ->
            val api = Api { if (it.offset == 0) AlbumSublistResponse(200, firstPage)
                else AlbumSublistResponse(200, listOf(AlbumSubItem(200), AlbumSubItem(201))) }
            val albums = repository(api).load(1).first().getOrThrow()
            assertEquals((1L..201L).toList(), albums.map { it.id })
            assertEquals(listOf(0, 200), api.requests.map { it.offset })
            assertTrue(api.requests.all { it.limit == 200 })
        }
    }

    @Test fun exactPageMultipleRequiresTheTerminalEmptyPage() = runBlocking {
        fixture { _, _, repository ->
            val api = Api { AlbumSublistResponse(200, if (it.offset == 0) firstPage else emptyList()) }
            assertEquals(200, repository(api).load(1).first().getOrThrow().size)
            assertEquals(listOf(0, 200), api.requests.map { it.offset })
        }
    }

    @Test fun repeatedContinuationFailsWithoutPublishingOrCachingAPartialCollection() = runBlocking {
        fixture { root, _, repository ->
            val api = Api { AlbumSublistResponse(200, firstPage) }
            val result = repository(api).load(1).first()
            assertTrue(result.isFailure)
            assertEquals(2, api.requests.size)
            assertTrue(root.walk().none { it.extension == "json" })
        }
    }

    @Test fun laterBusinessFailureDoesNotClaimThatTheFirstPageIsComplete() = runBlocking {
        fixture { root, _, repository ->
            val api = Api { AlbumSublistResponse(if (it.offset == 0) 200 else 500, if (it.offset == 0) firstPage else emptyList()) }
            assertTrue(repository(api).load(1).first().exceptionOrNull() is AppError.BizError)
            assertTrue(root.walk().none { it.extension == "json" })
        }
    }

    @Test fun offlineCachesAreAccountScopedEvenInsideTheSameMetadataDirectory() = runBlocking {
        fixture { _, online, repository ->
            val api = Api { AlbumSublistResponse(200, listOf(AlbumSubItem(10))) }
            val reader = repository(api)
            assertEquals(10L, reader.load(1).first().getOrThrow().single().id)
            online.value = false
            assertTrue(reader.load(2).first().isFailure)
            assertEquals(10L, reader.load(1).first().getOrThrow().single().id)
            assertEquals(1, api.requests.size)
        }
    }

    @Test fun cancellingContinuationNeverWritesTheFirstPageToCache() = runBlocking {
        fixture { root, _, repository ->
            val entered = CompletableDeferred<Unit>()
            val api = Api {
                if (it.offset == 0) AlbumSublistResponse(200, firstPage)
                else { entered.complete(Unit); awaitCancellation() }
            }
            var published = false
            val job = launch { repository(api).load(1).first(); published = true }
            entered.await()
            job.cancelAndJoin()
            assertFalse(published)
            assertTrue(root.walk().none { it.extension == "json" })
        }
    }

    @Test fun invalidAccountNeverUsesTheAuthenticatedEndpoint() = runBlocking {
        fixture { _, _, repository ->
            val api = Api { error("must not request") }
            assertEquals(AppError.Unauthorized, repository(api).load(0).first().exceptionOrNull())
            assertTrue(api.requests.isEmpty())
        }
    }
}

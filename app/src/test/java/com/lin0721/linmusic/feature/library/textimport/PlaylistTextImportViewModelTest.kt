package com.lin0721.linmusic.feature.library.textimport

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import com.lin0721.linmusic.core.auth.UserPreferences
import com.lin0721.linmusic.core.auth.UserProfile
import com.lin0721.linmusic.core.model.Artist
import com.lin0721.linmusic.core.model.PlaylistDetail
import com.lin0721.linmusic.core.model.PlaylistTrackId
import com.lin0721.linmusic.core.model.Track
import com.lin0721.linmusic.core.playlistmutation.PlaylistMutationBus
import com.lin0721.linmusic.feature.create.data.CreateRepository
import com.lin0721.linmusic.feature.playlist.data.PlaylistRepository
import com.lin0721.linmusic.feature.playlist.data.PlaylistApi
import com.lin0721.linmusic.feature.playlist.data.PlaylistDetailResponse
import com.lin0721.linmusic.feature.search.data.SearchRepository
import com.lin0721.linmusic.feature.search.domain.SearchPageResult
import com.lin0721.linmusic.feature.search.domain.SearchResultItem
import java.lang.reflect.Proxy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PlaylistTextImportViewModelTest {
    private class MemoryPreferences : DataStore<Preferences> {
        override val data = MutableStateFlow(emptyPreferences())
        override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences = transform(data.value).also { data.value = it }
    }

    private class Fixture {
        val preferences = UserPreferences(MemoryPreferences())
        val searchQueries = mutableListOf<String>()
        var searchTracks = listOf(Track(10, "Starlight", listOf(Artist(name = "Muse"))))
        var creates = 0
        var privacy = -1
        var additions = mutableListOf<List<Long>>()
        var failAdd = false
        var existing = emptySet<Long>()
        var failRead = false
        var timeoutRead = false
        var freshReads = 0

        private val search = Proxy.newProxyInstance(SearchRepository::class.java.classLoader, arrayOf(SearchRepository::class.java)) { _, method, args ->
            check(method.name == "search")
            searchQueries += args[0] as String
            flowOf(Result.success(SearchPageResult(searchTracks.map { SearchResultItem.SongItem(it) }, searchTracks.size, false, searchTracks.size)))
        } as SearchRepository
        private val create = object : CreateRepository {
            override fun createPlaylist(name: String, privacy: Int) = flowOf(Result.success(PlaylistDetail(id = 50))).also {
                creates++; this@Fixture.privacy = privacy
            }
        }
        private val playlists = Proxy.newProxyInstance(PlaylistRepository::class.java.classLoader, arrayOf(PlaylistRepository::class.java)) { _, method, args ->
            when (method.name) {
                "manipulatePlaylistTracks" -> {
                    @Suppress("UNCHECKED_CAST") val ids = args[2] as List<Long>
                    additions.add(ids)
                    if (failAdd) flowOf(Result.failure<Unit>(IllegalStateException("add failed"))) else flowOf(Result.success(Unit))
                }
                // A stale/offline repository value must never satisfy mutation reconciliation.
                "getPlaylistDetail" -> flowOf(Result.success(PlaylistDetail(id = 50, trackIds = listOf(PlaylistTrackId(10)))))
                else -> error("Unexpected repository call: ${method.name}")
            }
        } as PlaylistRepository
        private val api = Proxy.newProxyInstance(PlaylistApi::class.java.classLoader, arrayOf(PlaylistApi::class.java)) { _, method, _ ->
            check(method.name == "getPlaylistDetail")
            freshReads++
            if (timeoutRead) runBlocking { withTimeout(1) { delay(10_000) } }
            if (failRead) error("network unavailable")
            PlaylistDetailResponse(200, PlaylistDetail(id = 50, trackIds = existing.map { PlaylistTrackId(it) }))
        } as PlaylistApi
        val vm = PlaylistTextImportViewModel(search, create, playlists, preferences, PlaylistMutationBus(), api)
        suspend fun load(text: String = "Starlight - Muse") {
            preferences.saveUserProfile(UserProfile(1, "fixture", ""))
            vm.loadDocument(text.toByteArray(), "test.txt")
            vm.state.first { it.rows.isNotEmpty() && !it.matching }
        }
    }

    @Test fun previewHasNoWritesAndDoubleSaveCreatesOnlyOnePrivatePlaylist() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val f = Fixture(); f.load("Starlight - Muse\nStarlight - Muse\nbad line")
            assertEquals(0, f.creates)
            assertEquals(1, f.searchQueries.size)
            assertEquals(1, f.vm.state.value.selectedCount)
            f.vm.save(); f.vm.save()
            f.vm.state.first { it.finishedPlaylistId != null && !it.saving }
            assertEquals(1, f.creates)
            assertEquals(10, f.privacy)
            assertEquals(listOf(listOf(10L)), f.additions)
            f.vm.consumeFinished(50)
            assertNull(f.vm.state.value.finishedPlaylistId)
            f.vm.reset()
        } finally { Dispatchers.resetMain() }
    }

    @Test fun manualCandidateClickImmediatelyEnablesImport() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val f = Fixture()
            f.searchTracks = listOf(Track(20, "Starlight (Live)", listOf(Artist(name = "Muse"))))
            f.load()
            assertEquals(0, f.vm.state.value.selectedCount)
            f.vm.select(1, f.searchTracks.single())
            assertEquals(1, f.vm.state.value.selectedCount)
            f.vm.include(1, false)
            assertEquals(0, f.vm.state.value.selectedCount)
            f.vm.include(1, true)
            f.vm.save()
            f.vm.state.first { it.finishedPlaylistId != null && !it.saving }
            assertEquals(listOf(listOf(20L)), f.additions)
            f.vm.reset()
        } finally { Dispatchers.resetMain() }
    }

    @Test fun failedAddRetriesSameTargetWithoutReaddingExistingTracks() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val f = Fixture(); f.load(); f.failAdd = true
            f.vm.save()
            f.vm.state.first { it.error != null && !it.saving }
            assertEquals(50L, f.vm.state.value.createdPlaylistId)
            f.vm.setName("changed")
            assertEquals("test", f.vm.state.value.name)
            f.failAdd = false; f.existing = setOf(10)
            f.vm.save()
            f.vm.state.first { it.finishedPlaylistId != null && !it.saving }
            assertEquals(1, f.creates)
            assertEquals(1, f.additions.size)
            f.vm.reset()
        } finally { Dispatchers.resetMain() }
    }

    @Test fun changingAccountAfterPreviewPreventsAllWrites() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val f = Fixture(); f.load()
            f.preferences.saveUserProfile(UserProfile(2, "other account", ""))
            f.vm.save()
            val state = f.vm.state.first { it.error != null && !it.saving }
            assertTrue(state.error!!.contains("账户已改变"))
            assertEquals(0, f.creates)
            assertTrue(f.additions.isEmpty())
            f.vm.reset()
        } finally { Dispatchers.resetMain() }
    }

    @Test fun retryNeverTrustsOfflineCachedPlaylistWhenFreshReadFails() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val f = Fixture(); f.load(); f.failAdd = true; f.failRead = true
            f.vm.save()
            f.vm.state.first { it.error != null && !it.saving }
            f.failAdd = false
            f.vm.save()
            f.vm.state.first { it.error != null && !it.saving }
            assertNull(f.vm.state.value.finishedPlaylistId)
            assertEquals(2, f.freshReads)
            assertEquals(1, f.creates)
            assertEquals(1, f.additions.size)
            f.vm.reset()
        } finally { Dispatchers.resetMain() }
    }

    @Test fun readTimeoutShowsRetryMessageAndRetainsCreatedTarget() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val f = Fixture(); f.load(); f.failAdd = true; f.timeoutRead = true
            f.vm.save()
            val failed = f.vm.state.first { it.error != null && !it.saving }
            assertTrue(failed.error!!.contains("超时"))
            assertEquals(50L, failed.createdPlaylistId)
            f.timeoutRead = false; f.failAdd = false; f.existing = setOf(10)
            f.vm.save()
            f.vm.state.first { it.finishedPlaylistId != null && !it.saving }
            assertEquals(1, f.creates)
            assertEquals(1, f.additions.size)
            f.vm.reset()
        } finally { Dispatchers.resetMain() }
    }
}

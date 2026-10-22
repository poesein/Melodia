package com.lin0721.linmusic.feature.library.textimport

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.lifecycle.viewModelScope
import com.lin0721.linmusic.core.auth.UserPreferences
import com.lin0721.linmusic.core.auth.UserProfile
import com.lin0721.linmusic.core.model.Artist
import com.lin0721.linmusic.core.model.PlaylistDetail
import com.lin0721.linmusic.core.model.PlaylistTrackId
import com.lin0721.linmusic.core.model.PlaylistCreator
import com.lin0721.linmusic.core.userplaylist.UserPlaylist
import com.lin0721.linmusic.core.userplaylist.UserPlaylistRepository
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
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
        var searchTracksByQuery = emptyMap<String, List<Track>>()
        var failedQueries = emptySet<String>()
        var creates = 0
        var privacy = -1
        var additions = mutableListOf<List<Long>>()
        var failAdd = false
        var existing = emptySet<Long>()
        var failRead = false
        var timeoutRead = false
        var freshReads = 0
        var targetOwner = 1L
        var availablePlaylists = listOf(UserPlaylist(id = 50, name = "已有歌单", userId = 1))
        var targetDelayMs = 0L
        var targetReadCompleted = false
        var createdName = ""

        private val search = Proxy.newProxyInstance(SearchRepository::class.java.classLoader, arrayOf(SearchRepository::class.java)) { _, method, args ->
            check(method.name == "search")
            val query = args[0] as String
            searchQueries += query
            val tracks = searchTracksByQuery[query] ?: searchTracks
            if (query in failedQueries) flowOf(Result.failure<SearchPageResult>(IllegalStateException("search failed")))
            else flowOf(Result.success(SearchPageResult(tracks.map { SearchResultItem.SongItem(it) }, tracks.size, false, tracks.size)))
        } as SearchRepository
        private val create = object : CreateRepository {
            override fun createPlaylist(name: String, privacy: Int) = flowOf(Result.success(PlaylistDetail(id = 50))).also {
                creates++; this@Fixture.privacy = privacy; createdName = name
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
            PlaylistDetailResponse(200, PlaylistDetail(id = 50, creator = PlaylistCreator(userId = targetOwner),
                trackIds = existing.map { PlaylistTrackId(it) }))
        } as PlaylistApi
        private val userPlaylists = object : UserPlaylistRepository {
            override fun getUserPlaylists(uid: Long, limit: Int) = flow {
                val response = availablePlaylists
                delay(targetDelayMs)
                targetReadCompleted = true
                emit(Result.success(response))
            }
            override fun updatePlaylistOrder(playlistIds: List<Long>) = flowOf(Result.failure<Unit>(IllegalStateException("Not used")))
            override fun setPlaylistTop(playlistId: Long, top: Boolean) = flowOf(Result.failure<Unit>(IllegalStateException("Not used")))
        }
        val vm = PlaylistTextImportViewModel(search, create, playlists, preferences, PlaylistMutationBus(), api, userPlaylists)
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

    @Test fun pastedArtistFirstTextUsesPreviewWithoutCreatingPlaylist() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val f = Fixture()
            f.preferences.saveUserProfile(UserProfile(1, "fixture", ""))
            f.vm.loadText("Muse-Starlight\nStarlight - Muse")
            val state = f.vm.state.first { it.rows.isNotEmpty() && !it.matching }
            assertEquals("粘贴的文本", state.fileName)
            assertEquals("导入歌单", state.name)
            assertTrue(state.isPrivate)
            assertTrue(state.rows.all { it.title == "Starlight" && it.selected?.id == 10L })
            assertEquals(1, state.selectedCount)
            assertEquals(0, f.creates)
            f.vm.save()
            f.vm.state.first { it.finishedPlaylistId != null && !it.saving }
            assertEquals(listOf(listOf(10L)), f.additions)
            f.vm.reset()
        } finally { Dispatchers.resetMain() }
    }

    @Test fun invalidPastedTextShowsErrorBeforeSearching() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val f = Fixture()
            f.vm.loadText("\u0000")
            val state = f.vm.state.first { it.error != null && !it.loading }
            assertTrue(state.rows.isEmpty())
            assertTrue(f.searchQueries.isEmpty())
            assertEquals(0, f.creates)
            f.vm.reset()
        } finally { Dispatchers.resetMain() }
    }

    @Test fun pastedTextCanReplaceAFilePreviewAndUncertainResultCanBeSelected() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val f = Fixture(); f.load()
            f.searchTracks = listOf(Track(20, "Invincible (Live)", listOf(Artist(name = "Muse"))))
            f.vm.loadText("Muse-Invincible")
            val state = f.vm.state.first { it.fileName == "粘贴的文本" && it.rows.isNotEmpty() && !it.matching }
            assertEquals(TextPlaylistStatus.REVIEW, state.rows.single().status)
            f.vm.select(1, f.searchTracks.single())
            assertEquals("Invincible (Live)", f.vm.state.value.rows.single().title)
            assertEquals(1, f.vm.state.value.selectedCount)
            assertEquals(0, f.creates)
            f.vm.reset()
        } finally { Dispatchers.resetMain() }
    }

    @Test fun replacingPreviewCancelsItsOutstandingDestinationRead() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val f = Fixture()
        try {
            f.load()
            f.targetDelayMs = 1_000L
            f.vm.loadTargets()
            runCurrent()
            assertTrue(f.vm.state.value.loadingTargets)
            f.vm.loadText("Muse-Starlight")
            // Parsing runs on Dispatchers.Default, outside TestCoroutineScheduler's virtual clock.
            val state = f.vm.state.first { it.fileName == "粘贴的文本" && it.rows.isNotEmpty() && !it.loading && !it.matching }
            advanceUntilIdle()
            assertEquals("粘贴的文本", state.fileName)
            assertEquals(10L, state.rows.single().selected?.id)
            assertFalse(f.targetReadCompleted)
            assertTrue(state.targetPlaylists.isEmpty())
            assertFalse(state.loadingTargets)
            assertNull(state.targetError)
        } finally {
            f.vm.viewModelScope.coroutineContext[Job]!!.cancelAndJoin()
            Dispatchers.resetMain()
        }
    }

    @Test fun newAccountPreviewCannotReceiveOldAccountsDestinations() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val f = Fixture()
        try {
            f.load()
            f.targetDelayMs = 1_000L
            f.vm.loadTargets()
            runCurrent()
            f.preferences.saveUserProfile(UserProfile(2, "other account", ""))
            f.vm.loadText("Starlight-Muse")
            f.vm.state.first { it.fileName == "粘贴的文本" && it.rows.isNotEmpty() && !it.loading && !it.matching }
            advanceUntilIdle()
            assertTrue(f.vm.state.value.targetPlaylists.isEmpty())
            assertNull(f.vm.state.value.targetError)
            f.availablePlaylists = listOf(UserPlaylist(id = 70, name = "新账户歌单", userId = 2))
            f.targetDelayMs = 0L
            f.vm.loadTargets()
            advanceUntilIdle()
            assertEquals(listOf(70L), f.vm.state.value.targetPlaylists.map { it.id })
            assertEquals(0, f.creates)
            assertTrue(f.additions.isEmpty())
        } finally {
            f.vm.viewModelScope.coroutineContext[Job]!!.cancelAndJoin()
            Dispatchers.resetMain()
        }
    }

    @Test fun unresolvedArtistFirstQueryRetriesOppositeOrderAndMergesCandidates() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val f = Fixture()
            val wrong = Track(11, "Honey Whiskey", listOf(Artist(name = "Nothing But Thieves")))
            val correct = Track(12, "Particles", listOf(Artist(name = "Nothing But Thieves")))
            f.searchTracksByQuery = mapOf("Nothing But Thieves Particles" to listOf(wrong),
                "Particles Nothing But Thieves" to listOf(correct))
            f.load("Nothing But Thieves-Particles")
            assertEquals(listOf("Nothing But Thieves Particles", "Particles Nothing But Thieves"), f.searchQueries)
            assertEquals(12L, f.vm.state.value.rows.single().selected?.id)
            assertEquals(listOf(12L, 11L), f.vm.state.value.rows.single().candidates.map { it.id })
            assertEquals(0, f.creates)
            f.vm.reset()
        } finally { Dispatchers.resetMain() }
    }

    @Test fun alternateQueryFailureKeepsFirstCandidatesAndCanRetry() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val f = Fixture()
            f.searchTracks = listOf(Track(20, "Starlight (Live)", listOf(Artist(name = "Muse"))))
            f.failedQueries = setOf("Starlight Muse")
            f.load("Muse-Starlight")
            assertEquals(TextPlaylistStatus.ERROR, f.vm.state.value.rows.single().status)
            assertEquals(20L, f.vm.state.value.rows.single().candidates.single().id)
            f.failedQueries = emptySet()
            f.searchTracksByQuery = mapOf("Starlight Muse" to listOf(Track(10, "Starlight", listOf(Artist(name = "Muse")))))
            f.vm.retryUnresolved()
            f.vm.state.first { it.rows.single().selected?.id == 10L && !it.matching }
            assertEquals(0, f.creates)
            f.vm.reset()
        } finally { Dispatchers.resetMain() }
    }

    @Test fun existingTargetSkipsPresentTracksAndDoesNotCreateOrRequireNewName() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val f = Fixture(); f.load(); f.existing = setOf(10)
            f.availablePlaylists += UserPlaylist(id = 60, name = "别人的歌单", userId = 2)
            f.vm.loadTargets()
            val state = f.vm.state.first { it.targetPlaylists.isNotEmpty() && !it.loadingTargets }
            assertEquals(listOf(50L), state.targetPlaylists.map { it.id })
            f.vm.setTarget(state.targetPlaylists.single())
            f.vm.setName("")
            f.vm.save()
            f.vm.state.first { it.finishedPlaylistId == 50L && !it.saving }
            assertEquals(0, f.creates)
            assertTrue(f.additions.isEmpty())
            assertEquals(1, f.freshReads)
            f.vm.reset()
        } finally { Dispatchers.resetMain() }
    }

    @Test fun existingTargetRetryPinsDestinationAndAddsOnlyMissingSongs() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val f = Fixture(); f.load(); f.failAdd = true
            f.vm.loadTargets()
            val targets = f.vm.state.first { it.targetPlaylists.isNotEmpty() && !it.loadingTargets }.targetPlaylists
            f.vm.setTarget(targets.single())
            f.vm.save()
            f.vm.state.first { it.error != null && !it.saving }
            assertEquals(50L, f.vm.state.value.activePlaylistId)
            f.vm.setTarget(null)
            assertEquals(50L, f.vm.state.value.targetPlaylist?.id)
            f.failAdd = false; f.existing = setOf(10)
            f.vm.save()
            f.vm.state.first { it.finishedPlaylistId != null && !it.saving }
            assertEquals(0, f.creates)
            assertEquals(listOf(listOf(10L)), f.additions)
            f.vm.reset()
        } finally { Dispatchers.resetMain() }
    }

    @Test fun existingTargetOwnershipIsRecheckedBeforeAdding() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val f = Fixture(); f.load()
            f.vm.loadTargets()
            val targets = f.vm.state.first { it.targetPlaylists.isNotEmpty() && !it.loadingTargets }.targetPlaylists
            f.vm.setTarget(targets.single()); f.targetOwner = 2
            f.vm.save()
            val state = f.vm.state.first { it.error != null && !it.saving }
            assertTrue(state.error!!.contains("不属于当前账户"))
            assertEquals(0, f.creates)
            assertTrue(f.additions.isEmpty())
            f.vm.reset()
        } finally { Dispatchers.resetMain() }
    }

    @Test fun returningToNewTargetUsesEnteredPlaylistName() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val f = Fixture(); f.load()
            f.vm.loadTargets()
            val targets = f.vm.state.first { it.targetPlaylists.isNotEmpty() && !it.loadingTargets }.targetPlaylists
            f.vm.setTarget(targets.single()); f.vm.setTarget(null)
            f.vm.setName("  自选歌单  ")
            f.vm.save()
            f.vm.state.first { it.finishedPlaylistId != null && !it.saving }
            assertEquals(1, f.creates)
            assertEquals("自选歌单", f.createdName)
            f.vm.reset()
        } finally { Dispatchers.resetMain() }
    }

    @Test fun failedAddRetriesSameTargetWithoutReaddingExistingTracks() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val f = Fixture(); f.load(); f.failAdd = true
            f.vm.save()
            f.vm.state.first { it.error != null && !it.saving }
            assertEquals(50L, f.vm.state.value.activePlaylistId)
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
            assertEquals(50L, failed.activePlaylistId)
            f.timeoutRead = false; f.failAdd = false; f.existing = setOf(10)
            f.vm.save()
            f.vm.state.first { it.finishedPlaylistId != null && !it.saving }
            assertEquals(1, f.creates)
            assertEquals(1, f.additions.size)
            f.vm.reset()
        } finally { Dispatchers.resetMain() }
    }
}

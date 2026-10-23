package com.lin0721.linmusic.feature.playlist.ui

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.lifecycle.ViewModelStore
import com.lin0721.linmusic.core.auth.*
import com.lin0721.linmusic.core.model.*
import com.lin0721.linmusic.core.songlike.*
import com.lin0721.linmusic.core.playlistmutation.PlaylistMutationBus
import com.lin0721.linmusic.core.offline.*
import com.lin0721.linmusic.core.network.OnlineStateProvider
import com.lin0721.linmusic.feature.cloud.upload.NosUploadClient
import com.lin0721.linmusic.feature.playlist.data.PlaylistRepository
import com.lin0721.linmusic.feature.playlist.domain.*
import com.lin0721.linmusic.feature.podcast.ui.FakePlaybackController
import com.lin0721.linmusic.feature.podcast.ui.testResourceProvider
import java.lang.reflect.Proxy
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PlaylistEditingTest {
    private class MemoryStore : DataStore<Preferences> {
        override val data = MutableStateFlow(emptyPreferences())
        override suspend fun updateData(transform: suspend (Preferences) -> Preferences) = transform(data.value).also { data.value = it }
    }
    private inline fun <reified T> stub(noinline call: (String, Array<out Any?>?) -> Any? = { name, _ -> error("Unexpected $name") }): T =
        Proxy.newProxyInstance(T::class.java.classLoader, arrayOf(T::class.java)) { _, method, args -> call(method.name, args) } as T

    private inner class Fixture(scheduler: TestCoroutineScheduler, name: String = "测试用户喜欢的音乐", owner: Long = 1) {
        val prefs = UserPreferences(MemoryStore())
        val serverLikes = (1L..50L).toMutableSet()
        val writes = mutableListOf<Long>()
        var failingId: Long? = null
        val api = object : SongLikeApi {
            override suspend fun getLikedSongIds(body: LikeSongListRequest) = LikeSongListResponse(200, serverLikes.toList())
            override suspend fun likeSong(body: LikeSongRequest): LikeSongResponse {
                writes += body.trackId; delay(20)
                if (body.trackId == failingId) return LikeSongResponse(400)
                if (body.like) serverLikes.add(body.trackId) else serverLikes.remove(body.trackId)
                return LikeSongResponse(200)
            }
        }
        val likes = SongLikeRepositoryImpl(api, prefs, now = { scheduler.currentTime })
        val detail = PlaylistDetail(id = 777, name = name, creator = PlaylistCreator(userId = owner),
            tracks = (1L..50L).map { Track(it, "Track $it") }, trackCount = 50,
            trackIds = (1L..50L).map { PlaylistTrackId(it) })
        var detailReads = 0
        val manipulations = mutableListOf<Triple<String,Long,List<Long>>>()
        val playlists: PlaylistRepository = stub { method, args -> when (method) {
            "getPlaylistDetail" -> { detailReads++; flowOf(Result.success(detail)) }
            "manipulatePlaylistTracks" -> {
                @Suppress("UNCHECKED_CAST") val ids = args!![2] as List<Long>
                manipulations += Triple(args[0] as String, args[1] as Long, ids); flowOf(Result.success(Unit))
            }
            else -> error(method)
        } }
        val player = FakePlaybackController()
        val users = FakeUserPlaylistRepository()
        val create = CreatePlaylistAndAddSongUseCase(FakeCreateRepository(), playlists, PlaylistMutationBus())
        val collect = SongCollectDelegate(users, playlists, likes, create, prefs, testResourceProvider)
        val batches = TrackBatchActionsDelegate(playlists, player, FakeSongDownloader(), likes, create, users, prefs, testResourceProvider)
        val offline = OfflinePlayability(object : OnlineStateProvider { override val online = MutableStateFlow(true) }, CachedAudioIndex { emptySet() })
        val vm = PlaylistViewModel(collect, SyncProfileAfterLoginUseCase(prefs, stub()), LoadLikedSongIdsUseCase(prefs, likes),
            stub(), stub(), stub(), playlists, likes, stub { method, _ ->
                if (method == "getSimilarSongs") flowOf(Result.success(emptyList<Track>())) else error(method)
            }, create, UpdatePlaylistCoverUseCase(stub(), NosUploadClient(), playlists), player, prefs,
            testResourceProvider, PlaylistMutationBus(), stub(), FakeSongDownloader(), offline, batches)
        val store = ViewModelStore().apply { put("editing", vm) }
        suspend fun load() { prefs.saveUserProfile(UserProfile(1, "Fixture", "")); vm.loadPlaylist(777) }
        fun state() = (vm.uiState.value as PlaylistUiState.Success)
        fun select(vararg ids: Long) { vm.enterSelection(); ids.forEach { vm.toggleSelected(it) } }
    }

    @Test fun fiftySingleUnlikesRemoveRowsWithoutRefetchingEvenWhenPlaylistIdIsNotUserId() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler)); val f = Fixture(testScheduler)
        try {
            f.load(); advanceUntilIdle()
            (1L..50L).forEach { f.vm.toggleLikeSong(it, false) }; advanceUntilIdle()
            assertTrue(f.state().playlist.tracks.isEmpty()); assertTrue(f.likes.likedSongIds.value.isEmpty())
            assertEquals(1, f.detailReads); assertEquals(50, f.writes.size); assertTrue(f.vm.pendingLikeIds.value.isEmpty())
            f.vm.loadPlaylist(777); advanceUntilIdle()
            assertTrue("An old detail response cannot reinsert acknowledged removals", f.state().playlist.tracks.isEmpty())
        } finally { f.store.clear(); Dispatchers.resetMain() }
    }

    @Test fun partialUnlikeKeepsOnlyFailedIdsSelectedAndRetriesOnlyThoseSongs() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler)); val f = Fixture(testScheduler)
        try {
            f.load(); advanceUntilIdle(); f.failingId = 31
            f.select(*(1L..50L).toList().toLongArray()); f.vm.batchLike(false)
            assertTrue(f.vm.batchProgress.value.active); advanceUntilIdle()
            assertEquals(listOf(31L), f.state().playlist.tracks.map { it.id })
            assertEquals(setOf(31L), f.vm.selection.value.selectedIds); assertTrue(f.vm.selection.value.active)
            f.failingId = null; f.vm.batchLike(false); advanceUntilIdle()
            assertEquals(51, f.writes.size); assertFalse(f.vm.selection.value.active)
            assertEquals(1, f.detailReads)
        } finally { f.store.clear(); Dispatchers.resetMain() }
    }

    @Test fun ordinaryPlaylistUnlikesKeepTracksAndRemovingTracksDoesNotRemoveTheirHearts() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler)); val f = Fixture(testScheduler, "Study")
        try {
            f.load(); advanceUntilIdle(); f.select(1, 2); f.vm.batchLike(false); advanceUntilIdle()
            assertEquals(50, f.state().playlist.tracks.size)
            f.select(3, 4); f.vm.batchAddToPlaylist(888); advanceUntilIdle()
            assertEquals(Triple("add", 888L, listOf(3L, 4L)), f.manipulations.single())
            f.select(5, 6); f.vm.batchRemove(); advanceUntilIdle()
            assertEquals(48, f.state().playlist.tracks.size)
            assertTrue(f.likes.likedSongIds.value.containsAll(listOf(5L, 6L)))
            assertEquals(Triple("del", 777L, listOf(5L, 6L)), f.manipulations.last())
        } finally { f.store.clear(); Dispatchers.resetMain() }
    }

    @Test fun anotherUsersPlaylistCannotBeModifiedByBatchRemove() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler)); val f = Fixture(testScheduler, "他人的喜欢的音乐", owner = 2)
        try {
            f.load(); advanceUntilIdle(); f.select(1, 2); f.vm.batchRemove(); advanceUntilIdle()
            assertTrue(f.manipulations.isEmpty()); assertEquals(setOf(1L, 2L), f.vm.selection.value.selectedIds)
        } finally { f.store.clear(); Dispatchers.resetMain() }
    }

    @Test fun stopWaitsForTheCurrentSongAndRetainsUnprocessedSelections() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler)); val f = Fixture(testScheduler)
        try {
            f.load(); advanceUntilIdle(); f.select(*(1L..50L).toList().toLongArray()); f.vm.batchLike(false)
            advanceTimeBy(1700); runCurrent(); f.vm.exitSelection(); advanceUntilIdle()
            assertFalse(f.vm.batchProgress.value.active); assertTrue(f.vm.selection.value.count in 1..49)
            f.vm.batchLike(false); advanceUntilIdle()
            assertEquals(50, f.writes.size); assertTrue(f.state().playlist.tracks.isEmpty())
        } finally { f.store.clear(); Dispatchers.resetMain() }
    }
    @Test fun unlikingInAnotherUsersLikesChangesOnlyMyHeartsAndKeepsTheirListVisible() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler)); val f = Fixture(testScheduler, "他人的喜欢的音乐", owner = 2)
        try {
            f.load(); advanceUntilIdle(); f.select(1, 2); f.vm.batchLike(false); advanceUntilIdle()
            assertEquals(50, f.state().playlist.tracks.size)
            assertFalse(f.likes.likedSongIds.value.contains(1)); assertFalse(f.likes.likedSongIds.value.contains(2))
            assertTrue(f.manipulations.isEmpty())
        } finally { f.store.clear(); Dispatchers.resetMain() }
    }

}

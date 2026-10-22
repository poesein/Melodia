package com.lin0721.linmusic.feature.albumlibrary

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.lifecycle.ViewModelStore
import com.lin0721.linmusic.core.auth.UserPreferences
import com.lin0721.linmusic.core.auth.UserProfile
import com.lin0721.linmusic.core.model.PlaylistDetail
import com.lin0721.linmusic.core.model.Track
import com.lin0721.linmusic.core.player.PlayMode
import com.lin0721.linmusic.core.player.PlaybackController
import com.lin0721.linmusic.core.player.QueueItem
import com.lin0721.linmusic.feature.library.data.AlbumSubItem
import com.lin0721.linmusic.feature.playlist.data.PlaylistRepository
import java.lang.reflect.Proxy
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CollectedAlbumsViewModelTest {
    private class MemoryPreferences : DataStore<Preferences> {
        override val data = MutableStateFlow(emptyPreferences())
        override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences = transform(data.value).also { data.value = it }
    }

    private class Fixture {
        val preferences = UserPreferences(MemoryPreferences())
        var readAlbums: suspend (Long) -> List<AlbumSubItem> = { listOf(AlbumSubItem(it, "Album $it")) }
        var readDetail: suspend (Long) -> PlaylistDetail = { PlaylistDetail(id = it, tracks = listOf(Track(it * 10 + 1), Track(it * 10 + 2))) }
        val mode = MutableStateFlow(PlayMode.LIST_LOOP)
        var restoreModeOnInit: PlayMode? = null
        val queues = mutableListOf<List<QueueItem>>()
        var initializations = 0
        private val controller = Proxy.newProxyInstance(PlaybackController::class.java.classLoader, arrayOf(PlaybackController::class.java)) { _, method, args ->
            when (method.name) {
                "getPlayMode" -> mode
                "initController" -> { initializations++; restoreModeOnInit?.let { mode.value = it }; Unit }
                "toggleShuffle", "toggleRepeat" -> { mode.value = PlayMode.LIST_LOOP; Unit }
                "playQueue" -> { @Suppress("UNCHECKED_CAST") val queue = args[0] as List<QueueItem>; queues += queue; Unit }
                else -> error("Unexpected controller call: ${method.name}")
            }
        } as PlaybackController
        private val playlists = Proxy.newProxyInstance(PlaylistRepository::class.java.classLoader, arrayOf(PlaylistRepository::class.java)) { _, method, args ->
            when (method.name) {
                "getAlbumDetail" -> { val id = args[0] as Long; flow { emit(Result.success(readDetail(id))) } }
                "loadAllTracks" -> { val detail = args[0] as PlaylistDetail; flow { emit(Result.success(detail.tracks)) } }
                else -> error("Unexpected repository call: ${method.name}")
            }
        } as PlaylistRepository
        private val repository = CollectedAlbumsRepository { uid -> flow { emit(Result.success(readAlbums(uid))) } }
        val vm = CollectedAlbumsViewModel(repository, playlists, controller, preferences)
        val store = ViewModelStore().apply { put("albums", vm) }
        suspend fun login(uid: Long = 1) { preferences.saveUserProfile(UserProfile(uid, "Fixture", "")) }
    }

    @Test fun sequentialAndAlbumRandomPlaybackUseTheNormalControllerWithSongShuffleOff() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val f = Fixture()
        try {
            f.login(); runCurrent()
            val selection = listOf(AlbumSubItem(1), AlbumSubItem(2))
            f.mode.value = PlayMode.LIST_LOOP
            f.restoreModeOnInit = PlayMode.SHUFFLE
            f.vm.play(selection, false); runCurrent()
            assertEquals(listOf(11L, 12L, 21L, 22L), f.queues.single().map { it.songId })
            assertEquals(PlayMode.LIST_LOOP, f.mode.value)
            f.restoreModeOnInit = PlayMode.SINGLE_LOOP
            f.vm.play(selection, true); runCurrent()
            val blocks = f.queues.last().map { it.songId }.chunked(2)
            assertEquals(setOf(listOf(11L, 12L), listOf(21L, 22L)), blocks.toSet())
            assertEquals(PlayMode.LIST_LOOP, f.mode.value)
            assertEquals(2, f.initializations)
            assertFalse(f.vm.state.value.preparing)
            assertEquals(2, f.vm.state.value.preparedAlbums)
        } finally { f.store.clear(); Dispatchers.resetMain() }
    }

    @Test fun failedPreparationLeavesTheCurrentQueueAndModeAlone() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val f = Fixture()
        try {
            f.login(); runCurrent()
            f.mode.value = PlayMode.SHUFFLE
            f.readDetail = { if (it == 2L) error("Cannot read album") else PlaylistDetail(it, tracks = listOf(Track(10))) }
            f.vm.play(listOf(AlbumSubItem(1), AlbumSubItem(2)), false)
            advanceUntilIdle()
            assertTrue(f.queues.isEmpty())
            assertEquals(PlayMode.SHUFFLE, f.mode.value)
            assertFalse(f.vm.state.value.preparing)
            assertEquals(0, f.initializations)
        } finally { f.store.clear(); Dispatchers.resetMain() }
    }

    @Test fun explicitCancellationStopsPreparationWithoutReplacingPlayback() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val f = Fixture()
        try {
            f.login(); runCurrent()
            f.readDetail = { awaitCancellation() }
            f.vm.play(listOf(AlbumSubItem(1)), false); runCurrent()
            assertTrue(f.vm.state.value.preparing)
            f.vm.cancelPlaybackPreparation(); runCurrent()
            assertFalse(f.vm.state.value.preparing)
            assertTrue(f.queues.isEmpty())
        } finally { f.store.clear(); Dispatchers.resetMain() }
    }

    @Test fun aNewPlayRequestWinsOverAnOldNonCooperativeDetailRequest() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val f = Fixture(); val release = CompletableDeferred<Unit>()
        try {
            f.login(); runCurrent()
            f.readDetail = {
                if (it == 1L) withContext(NonCancellable) { release.await() }
                PlaylistDetail(it, tracks = listOf(Track(it * 10)))
            }
            f.vm.play(listOf(AlbumSubItem(1)), false); runCurrent()
            f.vm.play(listOf(AlbumSubItem(2)), false); runCurrent()
            assertEquals(listOf(20L), f.queues.single().map { it.songId })
            release.complete(Unit); advanceUntilIdle()
            assertEquals(1, f.queues.size)
            assertFalse(f.vm.state.value.preparing)
        } finally { release.complete(Unit); f.store.clear(); Dispatchers.resetMain() }
    }

    @Test fun accountChangeCancelsOldQueuePreparationAndReplacesTheCollection() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val f = Fixture(); val release = CompletableDeferred<Unit>()
        try {
            f.login(); runCurrent()
            f.readDetail = { withContext(NonCancellable) { release.await() }; PlaylistDetail(it, tracks = listOf(Track(10))) }
            f.vm.play(listOf(AlbumSubItem(1)), false); runCurrent()
            f.login(2); runCurrent()
            assertEquals(listOf(2L), f.vm.state.value.albums.map { it.id })
            assertFalse(f.vm.state.value.preparing)
            release.complete(Unit); advanceUntilIdle()
            assertTrue(f.queues.isEmpty())
        } finally { release.complete(Unit); f.store.clear(); Dispatchers.resetMain() }
    }

    @Test fun logoutClearsVisibleAlbumsAndCancelsPreparation() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val f = Fixture()
        try {
            f.login(); runCurrent()
            f.readDetail = { awaitCancellation() }
            f.vm.play(listOf(AlbumSubItem(1)), false); runCurrent()
            f.preferences.clearUserProfile(); runCurrent()
            assertFalse(f.vm.state.value.loggedIn)
            assertFalse(f.vm.state.value.preparing)
            assertTrue(f.vm.state.value.albums.isEmpty())
            assertTrue(f.queues.isEmpty())
        } finally { f.store.clear(); Dispatchers.resetMain() }
    }

    @Test fun refreshFailurePreservesTheLastCompleteCollection() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val f = Fixture()
        try {
            f.login(); runCurrent()
            f.readAlbums = { error("Network failed") }
            f.vm.refresh(); runCurrent()
            assertEquals(listOf(1L), f.vm.state.value.albums.map { it.id })
            assertNotNull(f.vm.state.value.error)
            assertFalse(f.vm.state.value.loading)
        } finally { f.store.clear(); Dispatchers.resetMain() }
    }
}

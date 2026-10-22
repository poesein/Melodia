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

    private class Fixture(scope: CoroutineScope) {
        val preferences = UserPreferences(MemoryPreferences())
        var readAlbums: suspend (Long) -> List<AlbumSubItem> = { listOf(AlbumSubItem(it, "Album $it")) }
        var readDetail: suspend (Long) -> PlaylistDetail = { PlaylistDetail(id = it, tracks = listOf(Track(it * 10 + 1), Track(it * 10 + 2))) }
        val mode = MutableStateFlow(PlayMode.LIST_LOOP)
        var restoreModeOnInit: PlayMode? = null
        val queues = mutableListOf<List<QueueItem>>()
        val queue = MutableStateFlow<List<QueueItem>>(emptyList())
        val index = MutableStateFlow(-1)
        val context = MutableStateFlow<String?>(null)
        var initializations = 0
        private val controller = Proxy.newProxyInstance(PlaybackController::class.java.classLoader, arrayOf(PlaybackController::class.java)) { _, method, args ->
            when (method.name) {
                "getPlayMode" -> mode
                "getQueue" -> queue
                "getCurrentIndex" -> index
                "getPlayContext" -> context
                "initController" -> { initializations++; restoreModeOnInit?.let { mode.value = it }; Unit }
                "toggleShuffle", "toggleRepeat" -> { mode.value = PlayMode.LIST_LOOP; Unit }
                "playQueue" -> { @Suppress("UNCHECKED_CAST") val queue = args[0] as List<QueueItem>; queues += queue; this.queue.value = queue; index.value = 0; context.value = args[2] as String?; Unit }
                "appendToQueue" -> { @Suppress("UNCHECKED_CAST") val items = args[0] as List<QueueItem>; queue.value += items; Unit }
                else -> error("Unexpected controller call: ${method.name}")
            }
        } as PlaybackController
        private val playlists = Proxy.newProxyInstance(PlaylistRepository::class.java.classLoader, arrayOf(PlaylistRepository::class.java)) { _, method, args ->
            when (method.name) {
                "getAlbumDetail" -> { val id = args[0] as Long; flow { emit(Result.success(readDetail(id))) } }
                "peekAlbumDetail" -> null
                "loadAllTracks" -> { val detail = args[0] as PlaylistDetail; flow { emit(Result.success(detail.tracks)) } }
                else -> error("Unexpected repository call: ${method.name}")
            }
        } as PlaylistRepository
        private val repository = CollectedAlbumsRepository { uid -> flow { emit(Result.success(readAlbums(uid))) } }
        val messages = mutableListOf<String>()
        val player = CollectedAlbumsPlayer(controller, preferences, scope, messages::add)
        val vm = CollectedAlbumsViewModel(repository, playlists, controller, preferences, player)
        val store = ViewModelStore().apply { put("albums", vm) }
        suspend fun login(uid: Long = 1) { preferences.saveUserProfile(UserProfile(uid, "Fixture", "")) }
    }

    @Test fun sequentialAndAlbumRandomPlaybackUseTheNormalControllerWithSongShuffleOff() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val f = Fixture(backgroundScope)
        try {
            f.login(); runCurrent()
            val selection = listOf(AlbumSubItem(1), AlbumSubItem(2))
            f.mode.value = PlayMode.LIST_LOOP
            f.restoreModeOnInit = PlayMode.SHUFFLE
            f.vm.play(selection, false); advanceUntilIdle(); advanceTimeBy(1000); runCurrent()
            assertEquals(listOf(11L, 12L, 21L, 22L), f.queue.value.map { it.songId })
            assertEquals(PlayMode.LIST_LOOP, f.mode.value)
            f.restoreModeOnInit = PlayMode.SINGLE_LOOP
            f.vm.play(selection, true); runCurrent()
            advanceTimeBy(1000); runCurrent()
            val blocks = f.queue.value.map { it.songId }.chunked(2)
            assertEquals(setOf(listOf(11L, 12L), listOf(21L, 22L)), blocks.toSet())
            assertEquals(PlayMode.LIST_LOOP, f.mode.value)
            assertEquals(2, f.initializations)
            assertFalse(f.vm.state.value.preparing)
        } finally { f.store.clear(); Dispatchers.resetMain() }
    }

    @Test fun failureInTheNextAlbumDoesNotPreventTheFirstAlbumPlaying() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val f = Fixture(backgroundScope)
        try {
            f.login(); runCurrent()
            f.mode.value = PlayMode.SHUFFLE
            f.readDetail = { if (it == 2L) error("Cannot read album") else PlaylistDetail(it, tracks = listOf(Track(10))) }
            f.vm.play(listOf(AlbumSubItem(1), AlbumSubItem(2)), false)
            advanceUntilIdle(); advanceTimeBy(1000); runCurrent()
            assertEquals(listOf(10L), f.queue.value.map { it.songId })
            assertEquals(PlayMode.LIST_LOOP, f.mode.value)
            assertFalse(f.vm.state.value.preparing)
            assertEquals(1, f.initializations)
            assertEquals(1, f.messages.size)
        } finally { f.store.clear(); Dispatchers.resetMain() }
    }

    @Test fun explicitCancellationStopsPreparationWithoutReplacingPlayback() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val f = Fixture(backgroundScope)
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
        val f = Fixture(backgroundScope); val release = CompletableDeferred<Unit>()
        try {
            f.login(); runCurrent()
            f.readDetail = {
                if (it == 1L) withContext(NonCancellable) { release.await() }
                PlaylistDetail(it, tracks = listOf(Track(it * 10)))
            }
            f.vm.play(listOf(AlbumSubItem(1)), false); runCurrent()
            f.vm.play(listOf(AlbumSubItem(2)), false); advanceTimeBy(500); runCurrent()
            assertEquals(listOf(20L), f.queues.single().map { it.songId })
            release.complete(Unit); advanceUntilIdle()
            assertEquals(1, f.queues.size)
            assertFalse(f.vm.state.value.preparing)
        } finally { release.complete(Unit); f.store.clear(); Dispatchers.resetMain() }
    }

    @Test fun accountChangeCancelsOldQueuePreparationAndReplacesTheCollection() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val f = Fixture(backgroundScope); val release = CompletableDeferred<Unit>()
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
        val f = Fixture(backgroundScope)
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
        val f = Fixture(backgroundScope)
        try {
            f.login(); runCurrent()
            f.readAlbums = { error("Network failed") }
            f.vm.refresh(); runCurrent()
            assertEquals(listOf(1L), f.vm.state.value.albums.map { it.id })
            assertNotNull(f.vm.state.value.error)
            assertFalse(f.vm.state.value.loading)
        } finally { f.store.clear(); Dispatchers.resetMain() }
    }
    @Test fun twentyAlbumsStartAfterOnlyOneReadAndAppendJustOneAlbumAhead() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val f = Fixture(backgroundScope)
        val reads = mutableListOf<Long>()
        try {
            f.login(); runCurrent()
            f.readDetail = { id -> reads += id; PlaylistDetail(id, tracks = listOf(Track(id * 10))) }
            f.vm.play((1L..20L).map { AlbumSubItem(it) }, false); runCurrent()
            assertEquals(listOf(10L), f.queues.single().map { it.songId })
            advanceTimeBy(1000); runCurrent()
            assertEquals(listOf(1L, 2L), reads)
            // Leaving the page cancels only preparation, never an established playback session.
            f.vm.cancelPlaybackPreparation(); f.store.clear(); runCurrent()
            for (index in 1..18) {
                f.index.value = index
                advanceTimeBy(1000); runCurrent()
                assertEquals(index + 2, reads.size)
            }
            assertEquals((1L..20L).toList(), reads)
            assertEquals((1L..20L).map { it * 10 }, f.queue.value.map { it.songId })
        } finally { f.store.clear(); Dispatchers.resetMain() }
    }

    @Test fun aReplacedQueueCannotReceiveTheOldSessionsPendingAlbumEvenWithIdenticalSongs() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val f = Fixture(backgroundScope); val release = CompletableDeferred<Unit>()
        try {
            f.login(); runCurrent()
            f.readDetail = { id ->
                if (id == 2L) release.await()
                PlaylistDetail(id, tracks = listOf(Track(id * 10)))
            }
            f.vm.play(listOf(AlbumSubItem(1), AlbumSubItem(2)), false)
            advanceTimeBy(1000); runCurrent()
            f.queue.value = f.queue.value.map { it.copy(albumSessionId = null) }
            release.complete(Unit); advanceTimeBy(1000); runCurrent()
            assertEquals(listOf(10L), f.queue.value.map { it.songId })
            assertNull(f.queue.value.single().albumSessionId)
        } finally { release.complete(Unit); f.store.clear(); Dispatchers.resetMain() }
    }

    @Test fun randomAlbumSessionVisitsTwentyAlbumsExactlyOnceWithIntactTrackBlocks() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val f = Fixture(backgroundScope)
        try {
            f.login(); runCurrent()
            f.vm.play((1L..20L).map { AlbumSubItem(it) }, true)
            advanceTimeBy(1000); runCurrent()
            for (album in 1..18) {
                f.index.value = album * 2
                advanceTimeBy(1000); runCurrent()
            }
            val blocks = f.queue.value.map { it.songId }.chunked(2)
            assertEquals(20, blocks.size)
            assertEquals((1L..20L).map { listOf(it * 10 + 1, it * 10 + 2) }.toSet(), blocks.toSet())
        } finally { f.store.clear(); Dispatchers.resetMain() }
    }

    @Test fun laterFailureSkipsOnlyThatAlbumAndAccountChangeStopsFurtherAppends() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val f = Fixture(backgroundScope)
        try {
            f.login(); runCurrent()
            f.readDetail = { id ->
                if (id == 2L) error("missing metadata")
                PlaylistDetail(id, tracks = listOf(Track(id * 10)))
            }
            f.vm.play((1L..4L).map { AlbumSubItem(it, "Album $it") }, false)
            advanceTimeBy(1500); runCurrent()
            assertEquals(listOf(10L, 30L), f.queue.value.map { it.songId })
            assertTrue(f.messages.single().contains("Album 2"))
            f.login(2); f.index.value = 1
            advanceTimeBy(1500); runCurrent()
            assertEquals(listOf(10L, 30L), f.queue.value.map { it.songId })
        } finally { f.store.clear(); Dispatchers.resetMain() }
    }

}

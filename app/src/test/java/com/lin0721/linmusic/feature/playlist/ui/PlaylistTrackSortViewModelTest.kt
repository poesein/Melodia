package com.lin0721.linmusic.feature.playlist.ui

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.lifecycle.viewModelScope
import com.lin0721.linmusic.core.auth.UserPreferences
import com.lin0721.linmusic.core.auth.UserProfile
import java.io.IOException
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PlaylistTrackSortViewModelTest {
    private class MemoryStore : DataStore<Preferences> {
        val values = MutableStateFlow(emptyPreferences())
        var readGate: CompletableDeferred<Unit>? = null
        var writeDelay = 0L
        var failWrites = false
        override val data: Flow<Preferences> = flow { readGate?.await(); emitAll(values) }
        override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences {
            if (writeDelay > 0) delay(writeDelay)
            if (failWrites) throw IOException("test disk failure")
            return transform(values.value).also { values.value = it }
        }
    }
    private class Fixture(scheduler: TestCoroutineScheduler) {
        val disk = MemoryStore()
        val accounts = UserPreferences(MemoryStore())
        val repository = PlaylistTrackSortRepository(disk, CoroutineScope(SupervisorJob() + StandardTestDispatcher(scheduler)))
        val models = mutableListOf<PlaylistTrackSortViewModel>()
        fun model() = PlaylistTrackSortViewModel(repository, accounts).also { models += it }
        suspend fun login(id: Long) = accounts.saveUserProfile(UserProfile(id, "User $id", ""))
        fun close() { models.forEach { it.viewModelScope.cancel() }; repository.close() }
    }
    private val playlist = PlaylistSortTarget(42)

    @Test fun orderRemainsUnloadedUntilAccountAndSortHaveActuallyBeenRead() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val f = Fixture(testScheduler)
        try {
            f.login(1)
            f.repository.save(PlaylistSortScope(1, playlist), PlaylistTrackSort(reversed = true))
            val gate = CompletableDeferred<Unit>(); f.disk.readGate = gate
            val vm = f.model(); vm.bind(playlist)
            runCurrent()
            assertFalse(vm.state.value.loaded)
            assertEquals(1L, vm.state.value.scope?.accountId)
            gate.complete(Unit); advanceUntilIdle()
            assertTrue(vm.state.value.loaded)
            assertTrue(vm.state.value.sort.reversed)
        } finally { f.close(); Dispatchers.resetMain() }
    }

    @Test fun rapidChangesAndRecreatedViewModelKeepTheLastChoice() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val f = Fixture(testScheduler)
        try {
            f.login(1)
            val vm = f.model(); vm.bind(playlist); advanceUntilIdle()
            val scope = vm.state.value.scope!!
            f.disk.writeDelay = 100
            vm.save(scope, PlaylistTrackSort(reversed = true))
            runCurrent() // The first write is already in flight.
            vm.save(scope, PlaylistTrackSort(PlaylistSortOption.TITLE, true))
            vm.save(scope, PlaylistTrackSort(PlaylistSortOption.DURATION, true))
            val expected = PlaylistTrackSort(PlaylistSortOption.DURATION, true)
            assertEquals(expected, vm.state.value.sort)
            advanceUntilIdle()
            assertFalse(vm.state.value.saving)
            assertEquals(expected, f.repository.observe(scope).first())
            val recreated = f.model(); recreated.bind(playlist); advanceUntilIdle()
            assertEquals(expected, recreated.state.value.sort)
        } finally { f.close(); Dispatchers.resetMain() }
    }

    @Test fun accountGuestAndAlbumScopesStayIndependentAndIgnoreStaleCallbacks() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val f = Fixture(testScheduler)
        try {
            f.login(1)
            val vm = f.model(); vm.bind(playlist); advanceUntilIdle()
            val firstScope = vm.state.value.scope!!
            val firstOrder = PlaylistTrackSort(PlaylistSortOption.TITLE, true)
            vm.save(firstScope, firstOrder); advanceUntilIdle()
            f.login(2); advanceUntilIdle()
            assertEquals(PlaylistTrackSort(), vm.state.value.sort)
            val secondScope = vm.state.value.scope!!
            vm.save(firstScope, PlaylistTrackSort(PlaylistSortOption.DURATION)) // Old UI callback.
            assertEquals(PlaylistTrackSort(), vm.state.value.sort)
            vm.save(secondScope, PlaylistTrackSort(reversed = true)); advanceUntilIdle()
            f.accounts.clearUserProfile(); advanceUntilIdle()
            assertNull(vm.state.value.scope!!.accountId)
            assertEquals(PlaylistTrackSort(), vm.state.value.sort)
            f.login(1); advanceUntilIdle()
            assertEquals(firstOrder, vm.state.value.sort)
            vm.bind(PlaylistSortTarget(42, isAlbum = true)); advanceUntilIdle()
            assertEquals(PlaylistTrackSort(), vm.state.value.sort)
            vm.bind(playlist); advanceUntilIdle()
            assertEquals(firstOrder, vm.state.value.sort)
        } finally { f.close(); Dispatchers.resetMain() }
    }

    @Test fun aDelayedOldResourceReadCannotReplaceTheNewResource() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val f = Fixture(testScheduler)
        try {
            f.login(1)
            val other = PlaylistSortTarget(43)
            f.repository.save(PlaylistSortScope(1, playlist), PlaylistTrackSort(reversed = true))
            val gate = CompletableDeferred<Unit>(); f.disk.readGate = gate
            val vm = f.model(); vm.bind(playlist); runCurrent()
            vm.bind(other); runCurrent()
            gate.complete(Unit); advanceUntilIdle()
            assertEquals(other, vm.state.value.target)
            assertEquals(PlaylistTrackSort(), vm.state.value.sort)
            assertTrue(vm.state.value.loaded)
        } finally { f.close(); Dispatchers.resetMain() }
    }

    @Test fun invalidPreferencesRequireExplicitResourceScopedRecovery() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val f = Fixture(testScheduler)
        try {
            f.login(1)
            val scope = PlaylistSortScope(1, playlist)
            f.disk.edit { it[stringPreferencesKey(scope.key)] = "2|title|1" }
            val vm = f.model(); vm.bind(playlist); advanceUntilIdle()
            assertFalse(vm.state.value.loaded)
            assertNotNull(vm.state.value.error)
            vm.reset(scope); advanceUntilIdle()
            assertTrue(vm.state.value.loaded)
            assertEquals(PlaylistTrackSort(), vm.state.value.sort)
            assertNull(vm.state.value.error)
        } finally { f.close(); Dispatchers.resetMain() }
    }

    @Test fun writeFailureIsVisibleAndCanBeRetriedWithoutChangingAnotherResource() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val f = Fixture(testScheduler)
        try {
            f.login(1)
            val vm = f.model(); vm.bind(playlist); advanceUntilIdle()
            val scope = vm.state.value.scope!!
            f.disk.failWrites = true
            vm.save(scope, PlaylistTrackSort(reversed = true)); advanceUntilIdle()
            assertTrue(vm.state.value.loaded)
            assertNotNull(vm.state.value.error)
            assertFalse(vm.state.value.saving)
            assertEquals(PlaylistTrackSort(), f.repository.observe(scope).first())
            f.disk.failWrites = false
            vm.save(scope, PlaylistTrackSort(reversed = true)); advanceUntilIdle()
            assertNull(vm.state.value.error)
            assertEquals(PlaylistTrackSort(reversed = true), f.repository.observe(scope).first())
        } finally { f.close(); Dispatchers.resetMain() }
    }

    @Test fun twoRetainedFramesContinuouslyShareTheNewestOrderIncludingOnReturn() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val f = Fixture(testScheduler)
        try {
            f.login(1)
            val first = f.model(); first.bind(playlist)
            val second = f.model(); second.bind(playlist)
            advanceUntilIdle()
            val scope = first.state.value.scope!!
            f.disk.writeDelay = 100
            first.save(scope, PlaylistTrackSort(reversed = true))
            runCurrent() // First write has started but has not committed.
            val finalOrder = PlaylistTrackSort(PlaylistSortOption.TITLE, true)
            second.save(scope, finalOrder)
            runCurrent()
            assertEquals(finalOrder, first.state.value.sort)
            assertEquals(finalOrder, second.state.value.sort)
            assertTrue(first.state.value.saving)
            advanceUntilIdle()
            first.bind(playlist) // Returning to a frame whose target has not changed.
            runCurrent()
            assertEquals(finalOrder, first.state.value.sort)
            assertFalse(first.state.value.saving)
            assertEquals(finalOrder, f.repository.observe(scope).first())
        } finally { f.close(); Dispatchers.resetMain() }
    }

    @Test fun anAcceptedChoiceFinishesAfterItsFrameIsImmediatelyClearedAndSurvivesRecreation() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val f = Fixture(testScheduler)
        try {
            f.login(1)
            val frame = f.model(); frame.bind(playlist); advanceUntilIdle()
            val scope = frame.state.value.scope!!
            val expected = PlaylistTrackSort(PlaylistSortOption.DURATION, true)
            f.disk.writeDelay = 100
            frame.save(scope, expected)
            frame.viewModelScope.cancel() // No scheduler turn between the tap and page disposal.
            advanceUntilIdle()
            assertEquals(expected, f.repository.observe(scope).first())
            // A new repository avoids accidentally passing by reading an optimistic cache.
            val reopened = PlaylistTrackSortRepository(f.disk, backgroundScope)
            val recreated = PlaylistTrackSortViewModel(reopened, f.accounts)
            f.models += recreated
            recreated.bind(playlist); advanceUntilIdle()
            assertTrue(recreated.state.value.loaded)
            assertEquals(expected, recreated.state.value.sort)
        } finally { f.close(); Dispatchers.resetMain() }
    }
}

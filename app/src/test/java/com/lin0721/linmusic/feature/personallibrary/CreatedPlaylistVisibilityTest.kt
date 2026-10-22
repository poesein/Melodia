package com.lin0721.linmusic.feature.personallibrary

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import com.lin0721.linmusic.core.playlistmutation.PlaylistMutationBus
import com.lin0721.linmusic.core.playlistmutation.PlaylistMutationEvent
import java.io.IOException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CreatedPlaylistVisibilityTest {
    private class Store : DataStore<Preferences> {
        override val data = MutableStateFlow(emptyPreferences())
        var fail = false
        override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences {
            if (fail) throw IOException("Write failed")
            return transform(data.value).also { data.value = it }
        }
    }

    @Test fun creationBeforeOpeningThePageIsSavedWithoutRevealingOtherHiddenPlaylists() = runTest {
        val store = Store(); val repository = LibraryPageLayoutRepository(store)
        repository.update(LibraryPage.PERSONAL) { it.copy(visiblePlaylistIds = setOf("10", "offline_saved"),
            listColumns = 4, itemOrders = mapOf("playlists" to listOf("10", "20"))) }
        repository.update(LibraryPage.LOCAL) { it.copy(itemLimits = mapOf("artists" to 35)) }
        val localBefore = repository.observe(LibraryPage.LOCAL).first()
        val bus = PlaylistMutationBus()
        val listener = CreatedPlaylistVisibility(bus, repository, backgroundScope) { throw AssertionError(it) }
        // Registration is synchronous: even an event before any UI/ViewModel is created is observed.
        bus.emit(PlaylistMutationEvent.Created(30)); runCurrent()
        bus.emit(PlaylistMutationEvent.Created(30)); runCurrent()
        bus.emit(PlaylistMutationEvent.Subscribed(20, true)); runCurrent()
        val restored = LibraryPageLayoutRepository(store).observe(LibraryPage.PERSONAL).first()
        assertEquals(setOf("10", "offline_saved", "30"), restored.visiblePlaylistIds)
        assertTrue(restored.playlistIsVisible("30")); assertFalse(restored.playlistIsVisible("20"))
        assertEquals(4, restored.listColumns)
        assertEquals(listOf("10", "20"), restored.itemOrders["playlists"])
        assertEquals(localBefore, repository.observe(LibraryPage.LOCAL).first())
        listener.close()
    }

    @Test fun defaultShowAllAndAnExplicitlyEmptySelectionBothHandleCreationCorrectly() = runTest {
        val repository = LibraryPageLayoutRepository(Store()); val bus = PlaylistMutationBus()
        val listener = CreatedPlaylistVisibility(bus, repository, backgroundScope) { throw AssertionError(it) }
        bus.emit(PlaylistMutationEvent.Created(30)); runCurrent()
        assertNull(repository.observe(LibraryPage.PERSONAL).first().visiblePlaylistIds)
        repository.update(LibraryPage.PERSONAL) { it.copy(visiblePlaylistIds = emptySet()) }
        bus.emit(PlaylistMutationEvent.Created(-2)); runCurrent()
        assertTrue(repository.observe(LibraryPage.PERSONAL).first().visiblePlaylistIds!!.isEmpty())
        bus.emit(PlaylistMutationEvent.Created(31)); runCurrent()
        assertEquals(setOf("31"), repository.observe(LibraryPage.PERSONAL).first().visiblePlaylistIds)
        listener.close()
    }

    @Test fun aFailedWriteDoesNotEraseSavedChoicesOrStopLaterCreationEvents() = runTest {
        val store = Store(); val repository = LibraryPageLayoutRepository(store); val bus = PlaylistMutationBus()
        repository.update(LibraryPage.PERSONAL) { it.copy(visiblePlaylistIds = setOf("10")) }
        val errors = mutableListOf<Exception>()
        val listener = CreatedPlaylistVisibility(bus, repository, backgroundScope) { errors += it }
        store.fail = true
        bus.emit(PlaylistMutationEvent.Created(30)); runCurrent()
        assertEquals(1, errors.size)
        assertEquals(setOf("10"), repository.observe(LibraryPage.PERSONAL).first().visiblePlaylistIds)
        store.fail = false
        bus.emit(PlaylistMutationEvent.Created(31)); runCurrent()
        assertEquals(setOf("10", "31"), repository.observe(LibraryPage.PERSONAL).first().visiblePlaylistIds)
        listener.close()
    }
}

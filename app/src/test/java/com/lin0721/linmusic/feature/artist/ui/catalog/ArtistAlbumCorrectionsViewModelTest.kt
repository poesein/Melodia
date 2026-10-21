package com.lin0721.linmusic.feature.artist.ui.catalog

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.lifecycle.ViewModelStore
import com.lin0721.linmusic.core.model.ArtistAlbum
import com.lin0721.linmusic.feature.artist.data.catalog.ArtistAlbumCorrectionsRepository
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ArtistAlbumCorrectionsViewModelTest {
    private class Store : DataStore<Preferences> {
        override val data = MutableStateFlow(emptyPreferences())
        var gate: CompletableDeferred<Unit>? = null
        var fail = false
        override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences {
            gate?.await()
            if (fail) throw IOException("Cannot write saved choices")
            return transform(data.value).also { data.value = it }
        }
    }

    @Test fun pendingSaveUsesOriginalArtistAndCannotClobberTheNewScreen() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val store = Store()
        val repository = ArtistAlbumCorrectionsRepository(store)
        val model = ArtistAlbumCorrectionsViewModel(repository)
        val lifecycle = ViewModelStore().apply { put("edits", model) }
        try {
            model.selectArtist(1); advanceUntilIdle()
            val gate = CompletableDeferred<Unit>(); store.gate = gate
            model.setDecision(ArtistAlbum(22, "Added"), true); runCurrent()
            assertTrue(model.state.value.saving)
            model.selectArtist(2); runCurrent()
            assertEquals(2L, model.state.value.artistId)
            assertFalse(model.state.value.saving)
            gate.complete(Unit); advanceUntilIdle()
            assertEquals(2L, model.state.value.artistId)
            assertTrue(model.state.value.value.entries.isEmpty())
            assertEquals(22L, repository.observe(1).first().entries.single().album.id)
        } finally { lifecycle.clear(); Dispatchers.resetMain() }
    }

    @Test fun failedSaveKeepsPriorChoicesAndAllowsRetry() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val store = Store()
        val repository = ArtistAlbumCorrectionsRepository(store)
        val model = ArtistAlbumCorrectionsViewModel(repository)
        val lifecycle = ViewModelStore().apply { put("edits", model) }
        try {
            model.selectArtist(1); advanceUntilIdle()
            val album = ArtistAlbum(22, "Added")
            model.setDecision(album, true); advanceUntilIdle()
            store.fail = true
            model.setDecision(album, false); advanceUntilIdle()
            assertNotNull(model.state.value.error)
            assertFalse(model.state.value.saving)
            assertTrue(model.state.value.value.entries.single().show)
            store.fail = false
            model.setDecision(album, false); advanceUntilIdle()
            assertNull(model.state.value.error)
            assertFalse(model.state.value.value.entries.single().show)
        } finally { lifecycle.clear(); Dispatchers.resetMain() }
    }

    @Test fun pausedSavedChoicesSurviveSwitchingAwayAndReturningToArtist() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val model = ArtistAlbumCorrectionsViewModel(ArtistAlbumCorrectionsRepository(Store()))
        val lifecycle = ViewModelStore().apply { put("edits", model) }
        try {
            model.selectArtist(1); advanceUntilIdle()
            model.setDecision(ArtistAlbum(22, "Added"), true); advanceUntilIdle()
            model.setEnabled(false); advanceUntilIdle()
            model.selectArtist(2); advanceUntilIdle()
            model.selectArtist(1); advanceUntilIdle()
            assertFalse(model.state.value.value.enabled)
            assertEquals(1, model.state.value.value.entries.size)
            model.setEnabled(true); advanceUntilIdle()
            assertTrue(model.state.value.value.enabled)
            assertEquals(1, model.state.value.value.entries.size)
        } finally { lifecycle.clear(); Dispatchers.resetMain() }
    }
}

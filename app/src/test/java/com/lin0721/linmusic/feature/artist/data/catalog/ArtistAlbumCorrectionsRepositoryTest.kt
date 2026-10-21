package com.lin0721.linmusic.feature.artist.data.catalog

import androidx.datastore.core.DataStore
import androidx.datastore.core.okio.OkioStorage
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferencesSerializer
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.lin0721.linmusic.core.model.ArtistAlbum
import java.io.File
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.*
import okio.FileSystem
import okio.Path.Companion.toPath
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class ArtistAlbumCorrectionsRepositoryTest {
    @get:Rule val temporary = TemporaryFolder()
    private val album = ArtistAlbum(id = 123, name = "Important Album", size = 5)
    private fun store(scope: CoroutineScope, file: File = File(temporary.newFolder(), "edits.preferences_pb")): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(storage = OkioStorage(FileSystem.SYSTEM, PreferencesSerializer,
            producePath = { file.absolutePath.toPath() }), scope = scope)

    @Test fun choicesAndPausedApplicationSurviveStoreRecreationAndResumeWithoutReentry() = runTest {
        val file = File(temporary.newFolder(), "edits.preferences_pb")
        val scope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))
        val repository = ArtistAlbumCorrectionsRepository(store(scope, file))
        repository.setDecision(1, album, true)
        repository.setDecision(1, album.copy(id = 124), false)
        repository.setEnabled(1, false)
        scope.cancel(); scope.coroutineContext[Job]!!.join()
        val restarted = ArtistAlbumCorrectionsRepository(store(backgroundScope, file))
        val previous = restarted.observe(1).first()
        assertFalse(previous.enabled)
        assertEquals(listOf(true, false), previous.entries.map { it.show })
        restarted.setEnabled(1, true)
        assertEquals(previous.entries, restarted.observe(1).first().entries)
        assertEquals(listOf(album), restarted.observe(1).first().apply(emptyList(), emptyList(), true))
    }

    @Test fun artistAndPerEntryPausesAreIndependentAndNeverDeleteRecords() = runTest {
        val repository = ArtistAlbumCorrectionsRepository(store(backgroundScope))
        repository.setDecision(1, album, true)
        repository.setDecision(2, album, false)
        repository.setEntryActive(1, album.id, false)
        assertFalse(repository.observe(1).first().entries.single().active)
        assertTrue(repository.observe(1).first().entries.single().show)
        assertFalse(repository.observe(2).first().entries.single().show)
        repository.setEntryActive(1, album.id, true)
        assertTrue(repository.observe(1).first().entries.single().active)
    }

    @Test fun concurrentUpdatesAreMergedInsideTheDataStoreTransaction() = runTest {
        val repository = ArtistAlbumCorrectionsRepository(store(backgroundScope))
        coroutineScope { (1L..12L).map { id -> async { repository.setDecision(1, album.copy(id = id), id % 2 == 0L) } }.awaitAll() }
        assertEquals((1L..12L).toSet(), repository.observe(1).first().entries.map { it.album.id }.toSet())
    }

    @Test fun albumShortcutEnablesAndAddsAtomicallyWithoutDiscardingSavedHiddenChoices() = runTest {
        val repository = ArtistAlbumCorrectionsRepository(store(backgroundScope))
        repository.setDecision(1, album, false)
        repository.setEnabled(1, false)
        repository.setDecision(1, album.copy(id = 222), true, enableCorrections = true)
        val saved = repository.observe(1).first()
        assertTrue(saved.enabled)
        assertFalse(saved.entries.first { it.album.id == album.id }.show)
        assertTrue(saved.entries.first { it.album.id == 222L }.show)
    }

    @Test fun shortcutUpdatesAHiddenAlbumWithoutErasingItsPreviouslySavedMetadata() = runTest {
        val repository = ArtistAlbumCorrectionsRepository(store(backgroundScope))
        val rich = album.copy(picUrl = "cover", publishTime = 12345L, type = "Album")
        repository.setDecision(1, rich, false)
        repository.setDecision(1, ArtistAlbum(album.id, album.name), true, enableCorrections = true)
        val correction = repository.observe(1).first().entries.single()
        assertEquals(rich, correction.album)
        assertTrue(correction.show)
    }

    @Test fun corruptOrFutureDataIsNotOverwrittenByRestoreOrEdit() = runTest {
        val store = store(backgroundScope)
        val repository = ArtistAlbumCorrectionsRepository(store)
        val key = stringPreferencesKey("artist_1_corrections")
        for (raw in listOf("broken-json", "{\"version\":2,\"entries\":[]}")) {
            store.edit { it[key] = raw }
            try { repository.setEnabled(1, true); fail("Must retain unrecognized data") } catch (_: java.io.IOException) { }
            try { repository.setDecision(1, album, true); fail("Must retain unrecognized data") } catch (_: java.io.IOException) { }
            assertEquals(raw, store.data.first()[key])
        }
    }

    @Test fun firstUseDoesNotChangeOtherArtistsOrUnrelatedPreferencesAndUpdatesReplaceOnlyOneRelease() = runTest {
        val store = store(backgroundScope)
        val marker = stringPreferencesKey("unrelated")
        store.edit { it[marker] = "keep" }
        val repository = ArtistAlbumCorrectionsRepository(store)
        assertTrue(repository.observe(1).first().entries.isEmpty())
        repository.setDecision(1, album, true)
        repository.setDecision(1, album.copy(name = "New metadata"), false)
        assertEquals(1, repository.observe(1).first().entries.size)
        assertFalse(repository.observe(1).first().entries.single().show)
        assertTrue(repository.observe(2).first().entries.isEmpty())
        assertEquals("keep", store.data.first()[marker])
    }
}

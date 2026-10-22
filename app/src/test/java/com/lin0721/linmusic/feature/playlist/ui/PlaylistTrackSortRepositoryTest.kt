package com.lin0721.linmusic.feature.playlist.ui

import androidx.datastore.core.DataStore
import androidx.datastore.core.okio.OkioStorage
import androidx.datastore.preferences.core.*
import java.io.File
import java.io.IOException
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
class PlaylistTrackSortRepositoryTest {
    @get:Rule val temporary = TemporaryFolder()
    private fun store(scope: CoroutineScope, file: File = File(temporary.newFolder(), "sort.preferences_pb")): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(storage = OkioStorage(FileSystem.SYSTEM, PreferencesSerializer,
            producePath = { file.absolutePath.toPath() }), scope = scope)
    private fun scope(account: Long?, id: Long = 42, album: Boolean = false) = PlaylistSortScope(account, PlaylistSortTarget(id, album))

    @Test fun realFileRecreationKeepsSortDimensionAndReverseWithoutAffectingOtherResources() = runTest {
        val file = File(temporary.newFolder(), "sort.preferences_pb")
        val firstScope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))
        val first = PlaylistTrackSortRepository(store(firstScope, file), firstScope)
        first.save(scope(1), PlaylistTrackSort(PlaylistSortOption.TITLE, true))
        first.save(scope(1, 43), PlaylistTrackSort(reversed = true))
        first.save(scope(2), PlaylistTrackSort(PlaylistSortOption.DURATION))
        first.save(scope(null), PlaylistTrackSort(PlaylistSortOption.ARTIST, true))
        firstScope.cancel(); firstScope.coroutineContext[Job]!!.join()
        val reopened = PlaylistTrackSortRepository(store(backgroundScope, file), backgroundScope)
        assertEquals(PlaylistTrackSort(PlaylistSortOption.TITLE, true), reopened.observe(scope(1)).first())
        assertEquals(PlaylistTrackSort(reversed = true), reopened.observe(scope(1, 43)).first())
        assertEquals(PlaylistTrackSort(PlaylistSortOption.DURATION), reopened.observe(scope(2)).first())
        assertEquals(PlaylistTrackSort(PlaylistSortOption.ARTIST, true), reopened.observe(scope(null)).first())
        assertEquals(PlaylistTrackSort(), reopened.observe(scope(1, album = true)).first())
        assertEquals(PlaylistTrackSort(), reopened.observe(scope(1, 99)).first())
    }

    @Test fun albumAndPlaylistWithSameIdAreIndependentAndBothStartInOriginalOrder() = runTest {
        val repository = PlaylistTrackSortRepository(store(backgroundScope), backgroundScope)
        assertEquals(PlaylistTrackSort(), repository.observe(scope(1)).first())
        assertEquals(PlaylistTrackSort(), repository.observe(scope(1, album = true)).first())
        repository.save(scope(1), PlaylistTrackSort(reversed = true))
        assertEquals(PlaylistTrackSort(), repository.observe(scope(1, album = true)).first())
        repository.save(scope(1, album = true), PlaylistTrackSort(PlaylistSortOption.ARTIST))
        assertEquals(PlaylistTrackSort(reversed = true), repository.observe(scope(1)).first())
    }

    @Test fun everyDimensionAndDirectionRoundTripsAndDefaultOnlyClearsThisResource() = runTest {
        val store = store(backgroundScope)
        val repository = PlaylistTrackSortRepository(store, backgroundScope)
        val marker = stringPreferencesKey("unrelated")
        store.edit { it[marker] = "keep" }
        repository.save(scope(2), PlaylistTrackSort(reversed = true))
        for (option in PlaylistSortOption.entries) for (reverse in listOf(false, true)) {
            val value = PlaylistTrackSort(option, reverse)
            repository.save(scope(1), value)
            assertEquals(value, repository.observe(scope(1)).first())
        }
        repository.save(scope(1), PlaylistTrackSort())
        assertEquals(PlaylistTrackSort(), repository.observe(scope(1)).first())
        assertEquals(PlaylistTrackSort(reversed = true), repository.observe(scope(2)).first())
        assertEquals("keep", store.data.first()[marker])
    }

    @Test fun malformedAndFutureValuesAreRetainedUntilExplicitResetOfTheirOwnScope() = runTest {
        val store = store(backgroundScope)
        val repository = PlaylistTrackSortRepository(store, backgroundScope)
        val key = stringPreferencesKey(scope(1).key)
        repository.save(scope(2), PlaylistTrackSort(reversed = true))
        for (raw in listOf("invalid", "2|title|1", "1|new_sort|0", "1|title|unknown")) {
            store.edit { it[key] = raw }
            try { repository.observe(scope(1)).first(); fail("Unknown order must not be silently discarded") } catch (_: IOException) { }
            try { repository.save(scope(1), PlaylistTrackSort()); fail("Unknown order must be retained") } catch (_: IOException) { }
            assertEquals(raw, store.data.first()[key])
        }
        repository.reset(scope(1))
        assertEquals(PlaylistTrackSort(), repository.observe(scope(1)).first())
        assertEquals(PlaylistTrackSort(reversed = true), repository.observe(scope(2)).first())
    }
}

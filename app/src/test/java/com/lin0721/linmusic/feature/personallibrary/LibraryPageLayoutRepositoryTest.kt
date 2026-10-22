package com.lin0721.linmusic.feature.personallibrary

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
class LibraryPageLayoutRepositoryTest {
    @get:Rule val temporary = TemporaryFolder()
    private fun store(scope: CoroutineScope, file: File = File(temporary.newFolder(), "layout.preferences_pb")): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(storage = OkioStorage(FileSystem.SYSTEM, PreferencesSerializer,
            producePath = { file.absolutePath.toPath() }), scope = scope)

    @Test fun savedOrderVisibilityAndBothColumnCountsSurviveRestartWithLocalPageIndependent() = runTest {
        val file = File(temporary.newFolder(), "layout.preferences_pb")
        val scope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))
        val repository = LibraryPageLayoutRepository(store(scope, file))
        repository.update(LibraryPage.PERSONAL) { it.moved(LibraryPage.PERSONAL, "artists", -1).copy(listColumns = 2, coverColumns = 5, playlistMode = "covers",
            sections = it.moved(LibraryPage.PERSONAL, "artists", -1).sections.map { section -> if (section.key == "local_recent") section.copy(visible = true) else section }) }
        repository.update(LibraryPage.LOCAL) { it.copy(sections = it.sections.map { section -> section.copy(visible = section.key != "recent") }) }
        repository.update(LibraryPage.LOCAL) { it.copy(itemLimits = mapOf("artists" to 0), itemOrders = mapOf("albums" to listOf("album2", "album1"))) }
        repository.update(LibraryPage.PERSONAL) { it.copy(visiblePlaylistIds = setOf("-2", "10", "offline_saved")) }
        scope.cancel(); scope.coroutineContext[Job]!!.join()
        val restarted = LibraryPageLayoutRepository(store(backgroundScope, file))
        val personal = restarted.observe(LibraryPage.PERSONAL).first()
        assertEquals("artists", personal.sections.first().key)
        assertEquals(2, personal.listColumns); assertEquals(5, personal.playlistColumns)
        assertEquals(setOf("-2", "10", "offline_saved"), personal.visiblePlaylistIds)
        assertTrue(personal.sections.take(3).all { it.visible })
        assertTrue(personal.sections.first { it.key == "local_recent" }.visible)
        assertTrue(personal.sections.drop(3).filterNot { it.key == "local_recent" }.none { it.visible })
        assertFalse(restarted.observe(LibraryPage.LOCAL).first().sections.first { it.key == "recent" }.visible)
        assertEquals(0, restarted.observe(LibraryPage.LOCAL).first().itemLimit("artists"))
        assertEquals(listOf("album2", "album1"), restarted.observe(LibraryPage.LOCAL).first().itemOrders["albums"])
    }
    @Test fun concurrentEditsMergeAndDoNotAffectScanOrAccountPreferences() = runTest {
        val store = store(backgroundScope)
        val marker = stringPreferencesKey("unrelated")
        store.edit { it[marker] = "keep" }
        val repository = LibraryPageLayoutRepository(store)
        coroutineScope {
            awaitAll(async { repository.update(LibraryPage.PERSONAL) { it.copy(listColumns = 4) } },
                async { repository.update(LibraryPage.PERSONAL) { it.copy(coverColumns = 5) } },
                async { repository.update(LibraryPage.LOCAL) { it.moved(LibraryPage.LOCAL, "albums", -1) } })
        }
        val saved = repository.observe(LibraryPage.PERSONAL).first()
        assertEquals(4, saved.listColumns); assertEquals(5, saved.coverColumns)
        assertEquals("keep", store.data.first()[marker])
    }
    @Test fun invalidAndFutureSettingsArePreservedOnReadAndWrite() = runTest {
        val store = store(backgroundScope)
        val repository = LibraryPageLayoutRepository(store)
        val key = stringPreferencesKey("personal_layout")
        for (raw in listOf("invalid-json", "{\"version\":2}")) {
            store.edit { it[key] = raw }
            try { repository.observe(LibraryPage.PERSONAL).first(); fail("Unknown layout must not be discarded") } catch (_: IOException) { }
            try { repository.update(LibraryPage.PERSONAL) { it.copy(listColumns = 2) }; fail("Must retain raw data") } catch (_: IOException) { }
            assertEquals(raw, store.data.first()[key])
            assertEquals(4, repository.observe(LibraryPage.LOCAL).first().sections.size)
        }
    }
}

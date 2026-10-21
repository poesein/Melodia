package com.lin0721.linmusic.feature.personallibrary

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test

class LibraryPageLayoutPersistenceTest {
    @Test fun realAndroidDataStoreRecreationKeepsBothPageLayouts() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File(context.cacheDir, "layout-test-${System.nanoTime()}.preferences_pb")
        var scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val repository = LibraryPageLayoutRepository(PreferenceDataStoreFactory.create(scope = scope, produceFile = { file }))
            repository.update(LibraryPage.PERSONAL) { it.copy(listColumns = 5, coverColumns = 2, playlistMode = "covers").moved(LibraryPage.PERSONAL, "albums", -1) }
            repository.update(LibraryPage.LOCAL) { it.copy(sections = it.sections.map { section -> section.copy(visible = section.key != "local_playlists") }) }
            repository.update(LibraryPage.LOCAL) { it.copy(itemLimits = mapOf("albums" to 0), itemOrders = mapOf("local_playlists" to listOf("3", "1", "2"))) }
            repository.update(LibraryPage.PERSONAL) { it.copy(sections = it.sections.map { section -> if (section.key == "local_artists") section.copy(visible = true) else section }) }
            scope.cancel(); scope.coroutineContext[Job]!!.join()
            scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            val restored = LibraryPageLayoutRepository(PreferenceDataStoreFactory.create(scope = scope, produceFile = { file }))
            assertEquals(5, restored.observe(LibraryPage.PERSONAL).first().listColumns)
            assertEquals(2, restored.observe(LibraryPage.PERSONAL).first().playlistColumns)
            assertEquals(listOf("playlists", "albums", "artists"), restored.observe(LibraryPage.PERSONAL).first().sections.take(3).map { it.key })
            assertTrue(restored.observe(LibraryPage.PERSONAL).first().sections.first { it.key == "local_artists" }.visible)
            assertFalse(restored.observe(LibraryPage.LOCAL).first().sections.first { it.key == "local_playlists" }.visible)
            assertEquals(0, restored.observe(LibraryPage.LOCAL).first().itemLimit("albums"))
            assertEquals(listOf("3", "1", "2"), restored.observe(LibraryPage.LOCAL).first().itemOrders["local_playlists"])
        } finally { scope.cancel(); scope.coroutineContext[Job]!!.join(); file.delete() }
    }
}

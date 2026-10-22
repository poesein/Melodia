package com.lin0721.linmusic.feature.albumlibrary

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test

class CollectedAlbumsPreferencesPersistenceTest {
    @Test fun realDataStoreRecreationKeepsColumnsGroupingAndSortWithoutResettingOtherChoices() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File(context.cacheDir, "collected-albums-${System.nanoTime()}.preferences_pb")
        var scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val original = CollectedAlbumsPreferences(PreferenceDataStoreFactory.create(scope = scope, produceFile = { file }))
            assertEquals(AlbumPageLayout(), original.layout.first())
            original.update { it.copy(columns = 5, groupByArtist = true, sort = AlbumPageSort.NAME) }
            scope.cancel(); scope.coroutineContext[Job]!!.join()
            scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            val restored = CollectedAlbumsPreferences(PreferenceDataStoreFactory.create(scope = scope, produceFile = { file }))
            assertEquals(AlbumPageLayout(columns = 5, groupByArtist = true, sort = AlbumPageSort.NAME), restored.layout.first())
            restored.update { it.copy(columns = 1) }
            scope.cancel(); scope.coroutineContext[Job]!!.join()
            scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            val restoredAgain = CollectedAlbumsPreferences(PreferenceDataStoreFactory.create(scope = scope, produceFile = { file }))
            assertEquals(AlbumPageLayout(columns = 1, groupByArtist = true, sort = AlbumPageSort.NAME), restoredAgain.layout.first())
        } finally {
            scope.cancel(); scope.coroutineContext[Job]!!.join(); file.delete()
        }
    }
}

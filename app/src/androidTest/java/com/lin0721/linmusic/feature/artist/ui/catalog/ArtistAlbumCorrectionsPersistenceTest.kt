package com.lin0721.linmusic.feature.artist.ui.catalog

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.platform.app.InstrumentationRegistry
import com.lin0721.linmusic.core.model.ArtistAlbum
import com.lin0721.linmusic.feature.artist.data.catalog.ArtistAlbumCorrectionsRepository
import java.io.File
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test

class ArtistAlbumCorrectionsPersistenceTest {
    @Test fun realAndroidDataStoreRestartsWithoutLosingPausedManualChoices() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File(context.cacheDir, "album-corrections-${System.nanoTime()}.preferences_pb")
        val firstScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val secondScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val first = ArtistAlbumCorrectionsRepository(PreferenceDataStoreFactory.create(scope = firstScope, produceFile = { file }))
            first.setDecision(1, ArtistAlbum(10, "Included"), true)
            first.setDecision(1, ArtistAlbum(11, "Hidden"), false)
            first.setEntryActive(1, 11, false)
            first.setEnabled(1, false)
            firstScope.cancel(); firstScope.coroutineContext[Job]!!.join()
            val second = ArtistAlbumCorrectionsRepository(PreferenceDataStoreFactory.create(scope = secondScope, produceFile = { file }))
            assertFalse(second.observe(1).first().enabled)
            assertEquals(2, second.observe(1).first().entries.size)
            second.setEnabled(1, true)
            second.setEntryActive(1, 11, true)
            assertEquals(listOf(10L), second.observe(1).first().apply(listOf(ArtistAlbum(11, "Hidden")), emptyList(), true).map { it.id })
            assertTrue(second.observe(2).first().entries.isEmpty())
        } finally {
            firstScope.cancel(); secondScope.cancel()
            firstScope.coroutineContext[Job]!!.join(); secondScope.coroutineContext[Job]!!.join()
            file.delete()
        }
    }
}

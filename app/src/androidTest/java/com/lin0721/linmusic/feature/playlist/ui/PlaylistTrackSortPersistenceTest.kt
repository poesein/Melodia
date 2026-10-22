package com.lin0721.linmusic.feature.playlist.ui

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test

class PlaylistTrackSortPersistenceTest {
    @Test fun androidDataStoreRecreationRetainsDirectionAndSeparatesAlbumAccountAndGuest() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File(context.cacheDir, "sort-test-${System.nanoTime()}.preferences_pb")
        var scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val playlist = PlaylistSortScope(1, PlaylistSortTarget(42))
        val album = PlaylistSortScope(1, PlaylistSortTarget(42, isAlbum = true))
        try {
            val first = PlaylistTrackSortRepository(PreferenceDataStoreFactory.create(scope = scope, produceFile = { file }), scope)
            first.save(playlist, PlaylistTrackSort(PlaylistSortOption.TITLE, true))
            first.save(album, PlaylistTrackSort(PlaylistSortOption.DURATION))
            scope.cancel(); scope.coroutineContext[Job]!!.join()
            scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            val reopened = PlaylistTrackSortRepository(PreferenceDataStoreFactory.create(scope = scope, produceFile = { file }), scope)
            assertEquals(PlaylistTrackSort(PlaylistSortOption.TITLE, true), reopened.observe(playlist).first())
            assertEquals(PlaylistTrackSort(PlaylistSortOption.DURATION), reopened.observe(album).first())
            assertEquals(PlaylistTrackSort(), reopened.observe(PlaylistSortScope(2, PlaylistSortTarget(42))).first())
            assertEquals(PlaylistTrackSort(), reopened.observe(PlaylistSortScope(null, PlaylistSortTarget(42))).first())
        } finally { scope.cancel(); scope.coroutineContext[Job]!!.join(); file.delete() }
    }
}

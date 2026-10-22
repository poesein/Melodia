package com.lin0721.linmusic.core.preferences

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.assertEquals
import org.junit.Test
import java.nio.file.Files

/** Old count preferences must not force a destructive settings migration. */
class RetiredArtistAlbumSettingsTest {
    @Test
    fun retiredKeysCanRemainWhileCurrentSettingsPersistNormally() = runBlocking {
        val directory = Files.createTempDirectory("melodia-retired-artist-settings").toFile()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val store = PreferenceDataStoreFactory.create(scope = scope,
                produceFile = { directory.resolve("settings.preferences_pb") })
            val legacyDefault = intPreferencesKey("artist_album_min_tracks")
            val legacyArtist = intPreferencesKey("artist_album_min_tracks_42")
            store.edit { it[legacyDefault] = 8; it[legacyArtist] = 12 }
            val current = SettingsPreferences(store)
            assertEquals(UiScale.DEFAULT_PERCENT, current.uiScalePercent.first())
            current.saveUiScalePercent(110)
            assertEquals(110, current.uiScalePercent.first())
            assertEquals(8, store.data.first()[legacyDefault])
            assertEquals(12, store.data.first()[legacyArtist])
        } finally {
            scope.cancel()
            scope.coroutineContext[Job]!!.join()
            directory.deleteRecursively()
        }
    }
}

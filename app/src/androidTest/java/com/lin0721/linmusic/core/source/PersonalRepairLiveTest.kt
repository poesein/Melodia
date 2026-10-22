package com.lin0721.linmusic.core.source

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.platform.app.InstrumentationRegistry
import android.media.MediaMetadataRetriever
import android.net.Uri
import com.lin0721.linmusic.feature.localmusic.domain.LocalTrack
import com.lin0721.linmusic.feature.localmusic.domain.LocalTrackSource
import com.lin0721.linmusic.feature.localmusic.domain.buildLocalLibraryIndex
import com.lin0721.linmusic.core.network.NetworkStateProvider
import com.lin0721.linmusic.core.player.data.PlaybackRepositoryImpl
import com.lin0721.linmusic.core.player.data.PlaybackSourceKind
import com.lin0721.linmusic.core.preferences.SettingsPreferences
import com.lin0721.linmusic.core.source.provider.KugouSearchProvider
import com.lin0721.linmusic.core.source.provider.KuwoSearchProvider
import com.lin0721.linmusic.core.source.provider.QQSearchProvider
import com.lin0721.linmusic.feature.albumlibrary.CollectedAlbumsPlayback
import com.lin0721.linmusic.feature.library.data.AlbumSubItem
import com.lin0721.linmusic.feature.playlist.data.PlaylistRepository
import com.lin0721.linmusic.feature.source.plugin.LxPluginEngine
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.koin.core.context.GlobalContext
import java.io.File
import kotlin.random.Random

/** Explicit opt-in online acceptance. No keys, signed stream URLs or account data enter reports. */
class PersonalRepairLiveTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun realEdriveCompilationFilesKeepTheirTrackArtistsAndOneAlbum() {
        val folder = File(context.getExternalFilesDir(null), "50 Classical Masterworks")
        val files = listOf(File(folder, "sample-0.flac"), File(folder, "sample-1.flac"))
        assumeTrue(files.all { it.isFile })
        val tracks = files.mapIndexed { index, file ->
            val metadata = MediaMetadataRetriever()
            try {
                metadata.setDataSource(file.absolutePath)
                LocalTrack(index.toLong() + 1L, null,
                    metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE).orEmpty(),
                    metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST).orEmpty(),
                    metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM),
                    metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)!!.toLong(),
                    file.length(), Uri.fromFile(file), file.absolutePath, 0L, LocalTrackSource.EXTERNAL)
            } finally { metadata.release() }
        }
        assertEquals(2, tracks.map { it.artist }.distinct().size)
        val library = buildLocalLibraryIndex(tracks)
        assertEquals(1, library.albums.size); assertEquals("50 Classical Masterworks", library.albums.single().name)
        assertEquals(2, library.albums.single().tracks.size)
        assertTrue(tracks.all { it.durationMs > 60_000L })
    }

    @Test fun realAlbumRequestsPreserveAlbumBlocksAndSkipAnUnavailableAlbum() = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("live") == "true")
        val repository = GlobalContext.get().get<PlaylistRepository>()
        val albums = listOf(AlbumSubItem(2722159, "Lost in the Dream"), AlbumSubItem(1782384, "Slave Ambient"))
        withTimeout(90_000) {
            val loaded = albums.associate { it.id to CollectedAlbumsPlayback.loadTracks(it, repository) }
            assertEquals(10, loaded.getValue(2722159).size)
            assertTrue(repository.getAlbumDetail(2722159).first().getOrThrow().publishTime > 0L)
            for (shuffle in listOf(false, true)) {
                val skipped = mutableListOf<Long>()
                val selected = albums + AlbumSubItem(1, "Unavailable test album")
                val expected = CollectedAlbumsPlayback.orderedAlbums(selected, shuffle, Random(8))
                    .filter { it.id != 1L }.flatMap { loaded.getValue(it.id) }.map { it.id }
                val queue = CollectedAlbumsPlayback.prepare(selected, shuffle,
                    { album -> if (album.id == 1L) error("unavailable fixture") else loaded.getValue(album.id) },
                    Random(8), onSkipped = { skipped += it.id })
                assertEquals(expected, queue.map { it.songId }); assertEquals(listOf(1L), skipped)
            }
        }
    }

    @Test fun paidJavaScriptSourceResolvesLostInTheDreamInStrictMetadataMode() = runBlocking {
        val script = File(context.getExternalFilesDir(null), "repair-source.js")
        assumeTrue(InstrumentationRegistry.getArguments().getString("live") == "true" && script.isFile)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val files = listOf("repair-source.preferences_pb", "repair-settings.preferences_pb").map { File(context.cacheDir, it) }
        val prefs = SourcePreferences(PreferenceDataStoreFactory.create(scope = scope, produceFile = { files[0] }))
        val settings = SettingsPreferences(PreferenceDataStoreFactory.create(scope = scope, produceFile = { files[1] }))
        val engine = LxPluginEngine(context)
        try {
            val info = engine.loadAndVerifyScript(script.readText()).getOrThrow()
            prefs.saveLxPlugins(listOf(LxPluginItem(id = "repair-probe", name = info.name,
                sources = info.sources, rawScript = info.rawScript)))
            prefs.saveLxPluginEnabled(true); prefs.saveFallbackEnabled(true)
            prefs.saveCommunityPriority(true); prefs.saveStrictFallbackEnabled(true)
            settings.saveWifiQuality("standard")
            val source = LxAudioSourceProvider(engine, prefs, listOf(KuwoSearchProvider(), KugouSearchProvider(), QQSearchProvider()))
            val delegate = GlobalContext.get().get<PlaybackRepositoryImpl>()
            val router = AudioSourceRouter(delegate, UnmApiClient(), prefs, settings, NetworkStateProvider { true }, listOf(source))
            val infoResult = withTimeout(150_000) { router.getSongPlaybackInfo(28274391).first().getOrThrow() }
            assertEquals(PlaybackSourceKind.STRICT_VERIFIED, infoResult.sourceKind)
            assertTrue(infoResult.strictVerified); assertFalse(infoResult.isFreeTrial)
            assertTrue(infoResult.url.startsWith("http")); assertEquals(64, infoResult.cacheIdentity?.length)
            withContext(Dispatchers.IO) {
                val client = okhttp3.OkHttpClient.Builder().callTimeout(12, java.util.concurrent.TimeUnit.SECONDS).build()
                val call = client.newCall(okhttp3.Request.Builder().url(infoResult.url).header("Range", "bytes=0-65535").build())
                try {
                    call.execute().use { response ->
                        assertTrue("Resolved audio must be readable", response.isSuccessful)
                        assertTrue(response.body!!.byteStream().read(ByteArray(1024)) > 0)
                        call.cancel()
                    }
                } finally { call.cancel() }
            }
        } finally {
            engine.reset(); scope.cancel(); files.forEach { it.delete() }
        }
    }
}

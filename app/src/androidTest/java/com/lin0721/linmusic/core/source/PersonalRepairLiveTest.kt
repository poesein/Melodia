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
import com.lin0721.linmusic.feature.albumlibrary.CollectedAlbumReader
import com.lin0721.linmusic.feature.albumlibrary.CollectedAlbumsPlayer
import com.lin0721.linmusic.core.auth.UserPreferences
import com.lin0721.linmusic.core.auth.UserProfile
import com.lin0721.linmusic.core.player.*
import kotlinx.coroutines.flow.MutableStateFlow
import java.lang.reflect.Proxy
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

    @Test fun twentyRealAlbumsPlayLazilyAndReuseTheirMetadata() = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("live") == "true")
        val repository = GlobalContext.get().get<PlaylistRepository>()
        val ids = listOf(252239059L, 246998350L, 246546742L, 152093658L, 135284731L,
            135351810L, 133273445L, 130455648L, 96398784L, 38283803L, 36033184L,
            142407925L, 35550720L, 35429466L, 2722159L, 72491087L, 1782384L,
            1782385L, 1782382L, 72397320L)
        var networkReads = 0
        val reader = CollectedAlbumReader(fetch = { id ->
            networkReads++; repository.getAlbumDetail(id).first().getOrThrow()
        })
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        val file = File(context.cacheDir, "album-session-${System.nanoTime()}.preferences_pb")
        val prefs = UserPreferences(PreferenceDataStoreFactory.create(scope = scope, produceFile = { file }))
        val queue = MutableStateFlow<List<QueueItem>>(emptyList())
        val index = MutableStateFlow(-1)
        val mode = MutableStateFlow(PlayMode.LIST_LOOP)
        val playContext = MutableStateFlow<String?>(null)
        val appended = MutableStateFlow(0)
        val starts = mutableListOf(0)
        val messages = mutableListOf<String>()
        val controller = Proxy.newProxyInstance(PlaybackController::class.java.classLoader,
            arrayOf(PlaybackController::class.java)) { _, method, args ->
            when (method.name) {
                "getQueue" -> queue
                "getCurrentIndex" -> index
                "getPlayMode" -> mode
                "getPlayContext" -> playContext
                "initController" -> Unit
                "playQueue" -> {
                    @Suppress("UNCHECKED_CAST") val items = args[0] as List<QueueItem>
                    queue.value = items; index.value = 0; playContext.value = args[2] as String?; Unit
                }
                "appendToQueue" -> {
                    @Suppress("UNCHECKED_CAST") val items = args[0] as List<QueueItem>
                    starts += queue.value.size; queue.value += items; appended.value++; Unit
                }
                else -> error("Unexpected call: ${method.name}")
            }
        } as PlaybackController
        try {
            prefs.saveUserProfile(UserProfile(1, "Acceptance", ""))
            val player = CollectedAlbumsPlayer(controller, prefs, scope, messages::add)
            withTimeout(240_000) {
                val albums = ids.map { AlbumSubItem(it, "Album $it") }
                withContext(Dispatchers.Main) {
                    player.start(albums, false, 1) { album ->
                        CollectedAlbumsPlayback.queueItems(album,
                            CollectedAlbumsPlayback.loadTracks(album, repository, reader.read(album.id)))
                    }
                    assertTrue("Playback starts before the other 19 albums are read", networkReads <= 3)
                    assertEquals(0, appended.value)
                }
                for (next in 1 until ids.size) {
                    appended.first { it >= next }
                    withContext(Dispatchers.Main) { index.value = starts[next] }
                }
                assertTrue(queue.value.size > 100)
                assertTrue(messages.isEmpty())
                val initialRequests = networkReads
                val expected = albums.flatMap { album -> CollectedAlbumsPlayback.queueItems(album,
                    CollectedAlbumsPlayback.loadTracks(album, repository, reader.read(album.id))) }
                assertEquals(expected.map { it.songId }, queue.value.map { it.songId })
                val diskReader = CollectedAlbumReader(fetch = { id -> networkReads++; repository.getAlbumDetail(id).first().getOrThrow() },
                    peek = { repository.peekAlbumDetail(it) })
                albums.forEach { diskReader.read(it.id) }
                assertEquals(initialRequests, networkReads)
                InstrumentationRegistry.getInstrumentation().sendStatus(0, android.os.Bundle().apply {
                    putInt("albumCount", ids.size); putInt("queueTracks", queue.value.size)
                    putInt("initialNetworkRequests", initialRequests); putInt("lazyAppends", appended.value)
                    putInt("extraRequestsForMemoryAndDiskCache", networkReads - initialRequests)
                })
            }
        } finally {
            scope.cancel(); scope.coroutineContext[Job]!!.join(); file.delete()
        }
    }

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

    @Test fun realAlbumTrackListsKeepTheirOriginalOrder() = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("live") == "true")
        val repository = GlobalContext.get().get<PlaylistRepository>()
        val albums = listOf(AlbumSubItem(2722159, "Lost in the Dream"), AlbumSubItem(1782384, "Slave Ambient"))
        withTimeout(90_000) {
            val loaded = albums.associate { it.id to CollectedAlbumsPlayback.loadTracks(it, repository) }
            assertEquals(10, loaded.getValue(2722159).size)
            assertTrue(repository.getAlbumDetail(2722159).first().getOrThrow().publishTime > 0L)
            for (shuffle in listOf(false, true)) {
                val ordered = CollectedAlbumsPlayback.orderedAlbums(albums, shuffle, Random(8))
                val queue = ordered.flatMap { album -> CollectedAlbumsPlayback.queueItems(album, loaded.getValue(album.id)) }
                assertEquals(ordered.flatMap { loaded.getValue(it.id) }.map { it.id }, queue.map { it.songId })
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

package com.lin0721.linmusic.core.player

import android.content.Context
import android.content.ComponentName
import android.os.Handler
import android.os.Looper
import androidx.media3.common.AudioAttributes
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.*
import org.junit.Test
import com.lin0721.linmusic.core.preferences.SettingsPreferences
import com.lin0721.linmusic.core.source.SourcePreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.channels.Channel
import org.koin.core.context.GlobalContext

@androidx.annotation.OptIn(UnstableApi::class)
class GaplessPlaybackTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context: Context get() = instrumentation.targetContext
    private fun main(block: () -> Unit) = instrumentation.runOnMainSync(block)

    private fun tone(name: String): File {
        val samples = 48_000 * 2
        val data = ByteBuffer.allocate(44 + samples * 2).order(ByteOrder.LITTLE_ENDIAN)
        data.put("RIFF".toByteArray()).putInt(36 + samples * 2).put("WAVEfmt ".toByteArray())
        data.putInt(16).putShort(1).putShort(1).putInt(48_000).putInt(96_000).putShort(2).putShort(16)
        data.put("data".toByteArray()).putInt(samples * 2)
        repeat(samples) { data.putShort((kotlin.math.sin(it * 2.0 * Math.PI * 440 / 48_000) * 4000).toInt().toShort()) }
        return File(context.cacheDir, name).apply { writeBytes(data.array()) }
    }
    private fun item(id: Int, file: File) = MediaItem.Builder().setMediaId(id.toString()).setUri(file.toURI().toString()).build()

    @Test fun nativePlaylistKeepsOneAudioTrackAcrossThreeSongs() {
        val file = tone("gapless-test.wav")
        val done = CountDownLatch(1)
        val transitions = mutableListOf<String>()
        val statesAfterReady = mutableListOf<Int>()
        var ready = false
        var audioTrackInitializations = 0
        var error: PlaybackException? = null
        lateinit var player: CrossfadePlayer
        main {
            val primary = ExoPlayer.Builder(context).build().apply {
                setPreloadConfiguration(ExoPlayer.PreloadConfiguration(5_000_000L))
                addAnalyticsListener(object : AnalyticsListener {
                    override fun onAudioTrackInitialized(eventTime: AnalyticsListener.EventTime, audioTrackConfig: androidx.media3.exoplayer.audio.AudioSink.AudioTrackConfig) {
                        audioTrackInitializations++
                    }
                })
            }
            player = CrossfadePlayer(primary, ExoPlayer.Builder(context).build(), AudioAttributes.DEFAULT)
            player.setHandleAudioFocus(false)
            player.addListener(object : Player.Listener {
                override fun onPlayerError(e: PlaybackException) { error = e; done.countDown() }
                override fun onPlaybackStateChanged(state: Int) {
                    if (ready) statesAfterReady += state
                    if (state == Player.STATE_READY && !ready) {
                        ready = true
                        assertTrue(GaplessPlaylist.append(player, "1", item(2, file)))
                    }
                    if (state == Player.STATE_ENDED) done.countDown()
                }
                override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                    if (reason != Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) return
                    transitions += mediaItem!!.mediaId
                    Handler(Looper.getMainLooper()).post {
                        GaplessPlaylist.trimConsumed(player)
                        assertEquals(1, player.mediaItemCount)
                        if (mediaItem.mediaId == "2") assertTrue(GaplessPlaylist.append(player, "2", item(3, file)))
                    }
                }
            })
            player.setMediaItem(item(1, file))
            player.prepare()
            player.play()
        }
        try {
            assertTrue("Playback timed out", done.await(20, TimeUnit.SECONDS))
            main {
                assertNull(error)
                assertEquals(listOf("2", "3"), transitions)
                assertFalse("Unexpected rebuffer at track boundary: $statesAfterReady", statesAfterReady.contains(Player.STATE_BUFFERING))
                assertEquals("Same-format WAV must reuse the AudioTrack", 1, audioTrackInitializations)
                assertEquals(2000L, player.duration)
            }
        } finally { main { player.release() }; file.delete() }
    }

    @Test fun removingPreparedSuccessorPreservesCurrentPosition() {
        val file = tone("gapless-edit.wav")
        val ready = CountDownLatch(1)
        lateinit var player: ExoPlayer
        main {
            player = ExoPlayer.Builder(context).build()
            player.addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(state: Int) { if (state == Player.STATE_READY) ready.countDown() }
            })
            player.setMediaItem(item(1, file), 500)
            player.prepare()
        }
        try {
            assertTrue(ready.await(10, TimeUnit.SECONDS))
            main {
                assertFalse(GaplessPlaylist.append(player, "wrong", item(2, file)))
                assertEquals(1, player.mediaItemCount)
                assertTrue(GaplessPlaylist.append(player, "1", item(2, file)))
                assertEquals(2, player.mediaItemCount)
                GaplessPlaylist.clearUpcoming(player)
                assertEquals(1, player.mediaItemCount)
                assertEquals("1", player.currentMediaItem!!.mediaId)
                assertEquals(500L, player.currentPosition)
                assertEquals(Player.STATE_READY, player.playbackState)
            }
        } finally { main { player.release() }; file.delete() }
    }

    @Test fun managerAndMediaSessionAdvanceAndWrapLocalQueueInStrictMode() = runBlocking {
        val file = tone("gapless-manager.wav")
        val koin = GlobalContext.get()
        val manager = koin.get<PlayerManager>()
        val settings = koin.get<SettingsPreferences>()
        val sources = koin.get<SourcePreferences>()
        val previousFade = settings.crossfadeEnabled.first()
        val previousAutoNext = settings.autoPlayNext.first()
        val previousStrict = sources.strictFallbackEnabled.first()
        withContext(Dispatchers.Main) { manager.awaitQueueRestored(); manager.initController() }
        val oldQueue = manager.queue.value
        val oldIndex = manager.currentIndex.value
        val oldContext = manager.playContext.value
        val oldSource = manager.playSource.value
        val oldPosition = manager.currentPosition.value
        val oldMode = manager.playMode.value
        val autoTransitions = Channel<String>(Channel.UNLIMITED)
        val bufferingAfterReady = mutableListOf<Int>()
        var localReady = false
        val observerFuture = withContext(Dispatchers.Main) {
            MediaController.Builder(context, SessionToken(context, ComponentName(context, MelodiaPlaybackService::class.java)))
                .buildAsync()
        }
        val observer = withContext(Dispatchers.IO) { observerFuture.get(5, TimeUnit.SECONDS) }
        withContext(Dispatchers.Main) {
            observer.addListener(object : Player.Listener {
                override fun onMediaItemTransition(item: MediaItem?, reason: Int) {
                    if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) autoTransitions.trySend(item?.mediaId.orEmpty())
                }
                override fun onPlaybackStateChanged(state: Int) {
                    if (observer.currentMediaItem?.mediaId?.startsWith("-92000") != true) return
                    if (localReady && state in listOf(Player.STATE_BUFFERING, Player.STATE_ENDED)) bufferingAfterReady += state
                    if (state == Player.STATE_READY) localReady = true
                }
            })
        }
        try {
            settings.saveCrossfadeEnabled(false)
            settings.saveAutoPlayNext(false)
            sources.saveStrictFallbackEnabled(true)
            val items = (1L..3L).map { n -> QueueItem(-920_000L - n, "Gapless $n", "Test", "", localUri = file.toURI().toString()) }
            withContext(Dispatchers.Main) {
                while (manager.playMode.value != PlayMode.LIST_LOOP) manager.rotatePlayMode()
                manager.playQueue(items, 0, "gapless-test", null, 0)
            }
            withTimeout(20_000) {
                for (index in listOf(1, 2, 0)) {
                    manager.currentTrack.first { it?.mediaId == items[index].songId.toString() }
                    // This is a combine/stateIn stream; await its next emission rather than asserting
                    // the previous cached value inside the currentTrack emission's dispatch turn.
                    manager.currentQueueItem.first { it?.songId == items[index].songId }
                    // Observe the real session event, rather than extras in an equals-conflated UI flow.
                    assertEquals(items[index].songId.toString(), autoTransitions.receive())
                    withContext(Dispatchers.Main) {
                        assertEquals(index, manager.currentIndex.value)
                        assertEquals(items[index].songId, manager.currentQueueItem.value?.songId)
                        assertEquals("gapless-test", manager.playContext.value)
                        assertEquals(items[index].title, manager.currentTrack.value?.mediaMetadata?.title?.toString())
                        assertEquals(items[index].artist, manager.currentTrack.value?.mediaMetadata?.artist?.toString())
                        assertEquals(items[index].title, manager.nowPlaying.value?.title)
                    }
                }
                assertTrue("The session rebuffered or ended between tracks: $bufferingAfterReady", bufferingAfterReady.isEmpty())
            }
        } finally {
            withContext(Dispatchers.Main) { observer.release(); manager.pause() }
            autoTransitions.close()
            settings.saveCrossfadeEnabled(previousFade)
            settings.saveAutoPlayNext(previousAutoNext)
            sources.saveStrictFallbackEnabled(previousStrict)
            withContext(Dispatchers.Main) {
                while (manager.playMode.value != oldMode) manager.rotatePlayMode()
                if (oldQueue.isNotEmpty()) {
                    manager.playQueue(oldQueue, oldIndex.coerceIn(oldQueue.indices), oldContext, oldSource, oldPosition)
                    manager.pause()
                } else manager.clearQueue()
            }
            file.delete()
        }
    }

    @Test fun invalidSuccessorDoesNotInterruptCurrentSongEarly() {
        val file = tone("gapless-failure.wav")
        val healthy = CountDownLatch(1)
        val errorReached = CountDownLatch(1)
        var currentStayedHealthy = false
        var staged = false
        lateinit var player: ExoPlayer
        main {
            player = ExoPlayer.Builder(context).build().apply {
                setPreloadConfiguration(ExoPlayer.PreloadConfiguration(5_000_000L))
            }
            player.addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(state: Int) {
                    if (state != Player.STATE_READY || staged) return
                    staged = true
                    GaplessPlaylist.append(player, "1", item(2, File(context.cacheDir, "absent-gapless-file.wav")))
                    Handler(Looper.getMainLooper()).postDelayed({
                        currentStayedHealthy = player.currentMediaItem?.mediaId == "1" &&
                            player.isPlaying && player.currentPosition >= 1000
                        healthy.countDown()
                    }, 1500)
                }
                override fun onPlayerError(error: PlaybackException) { errorReached.countDown() }
            })
            player.setMediaItem(item(1, file))
            player.prepare()
            player.play()
        }
        try {
            assertTrue(healthy.await(8, TimeUnit.SECONDS))
            assertTrue("A failed preload interrupted the current song", currentStayedHealthy)
            assertTrue("Missing next source should report an error at its boundary", errorReached.await(8, TimeUnit.SECONDS))
        } finally { main { player.release() }; file.delete() }
    }
}

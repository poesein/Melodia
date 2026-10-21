package com.lin0721.linmusic.core.player

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.test.platform.app.InstrumentationRegistry
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.net.ServerSocket
import java.net.InetAddress
import kotlin.concurrent.thread
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.*
import org.junit.Test

@OptIn(UnstableApi::class)
class PreparedTransitionTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private fun main(block: () -> Unit) = instrumentation.runOnMainSync(block)
    private fun tone(name: String, rate: Int = 48_000, channels: Int = 1, durationMs: Int = 2000): File {
        val frames = rate * durationMs / 1000
        val bytes = frames * channels * 2
        val b = ByteBuffer.allocate(44 + bytes).order(ByteOrder.LITTLE_ENDIAN)
        b.put("RIFF".toByteArray()).putInt(36 + bytes).put("WAVEfmt ".toByteArray())
        b.putInt(16).putShort(1).putShort(channels.toShort()).putInt(rate).putInt(rate * channels * 2)
        b.putShort((channels * 2).toShort()).putShort(16).put("data".toByteArray()).putInt(bytes)
        repeat(frames) { frame -> repeat(channels) {
            b.putShort((kotlin.math.sin(frame * 2.0 * Math.PI * 440 / rate) * 4000).toInt().toShort())
        } }
        return File(context.cacheDir, name).apply { writeBytes(b.array()) }
    }
    private fun item(id: String, file: File) = MediaItem.Builder().setMediaId(id).setUri(file.toURI().toString()).build()

    private class Capture : BaseAudioProcessor() {
        val pcm = ByteArrayOutputStream()
        override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
            check(inputAudioFormat.encoding == C.ENCODING_PCM_16BIT)
            return inputAudioFormat
        }
        override fun queueInput(inputBuffer: ByteBuffer) {
            if (!inputBuffer.hasRemaining()) return // The pipeline also passes its shared empty buffer.
            val copy = ByteArray(inputBuffer.remaining())
            inputBuffer.duplicate().get(copy)
            pcm.write(copy)
            replaceOutputBuffer(inputBuffer.remaining()).put(inputBuffer).flip()
        }
    }

    @Test fun rawModeDeliversExactlyTheConcatenatedPcmWithoutAddedSilence() {
        val file = tone("transition-pcm.wav")
        val capture = Capture()
        val done = CountDownLatch(1)
        var tracks = 0
        var underruns = 0
        var started = false
        var failure: PlaybackException? = null
        lateinit var player: ExoPlayer
        main {
            val factory = object : DefaultRenderersFactory(context) {
                override fun buildAudioSink(context: Context, enableFloatOutput: Boolean, enableAudioTrackPlaybackParams: Boolean): AudioSink =
                    DefaultAudioSink.Builder(context).setAudioProcessors(arrayOf(capture)).build()
            }
            player = ExoPlayer.Builder(context, factory).build()
            player.addAnalyticsListener(object : AnalyticsListener {
                override fun onAudioTrackInitialized(eventTime: AnalyticsListener.EventTime, audioTrackConfig: AudioSink.AudioTrackConfig) { tracks++ }
                override fun onAudioPositionAdvancing(eventTime: AnalyticsListener.EventTime, playoutStartSystemTimeMs: Long) { started = true }
                override fun onAudioUnderrun(eventTime: AnalyticsListener.EventTime, bufferSize: Int, bufferSizeMs: Long, elapsedSinceLastFeedMs: Long) {
                    if (started) underruns++
                }
            })
            player.addListener(object : Player.Listener {
                override fun onPlayerError(error: PlaybackException) { failure = error; done.countDown() }
                override fun onPlaybackStateChanged(state: Int) { if (state == Player.STATE_ENDED) done.countDown() }
            })
            player.setMediaItems(listOf(item("1", file), item("2", file)))
            player.prepare()
            player.play()
        }
        try {
            assertTrue(done.await(15, TimeUnit.SECONDS))
            main {
                assertNull(failure)
                assertEquals(1, tracks)
                assertEquals("Output must not starve at the join", 0, underruns)
            }
            val original = file.readBytes().drop(44).toByteArray()
            assertArrayEquals("No samples may be added, dropped or overlapped in raw mode", original + original, capture.pcm.toByteArray())
        } finally { main { player.release() }; file.delete() }
    }

    @Test fun smoothModePreparesPausedStandbyAndOverlapsDifferentFormats() = exerciseSmooth(false)

    @Test fun smoothModePreparesHttpSuccessorBeforeLocalTrackEnds() = exerciseSmooth(true)

    @Test fun longOverlapIsBoundedByShortSuccessorWithoutEndingCurrentTrackEarly() = exerciseSmooth(false, shortNext = true)

    private fun exerciseSmooth(http: Boolean, shortNext: Boolean = false) {
        val first = tone("transition-first.wav")
        val second = tone("transition-second.wav", rate = 44_100, channels = 2, durationMs = if (shortNext) 400 else 2000)
        val server = if (http) LocalAudioServer(second.readBytes()) else null
        val successor = if (server != null) MediaItem.Builder().setMediaId("2").setUri(server.url).build() else item("2", second)
        val standbyReady = CountDownLatch(1)
        val switched = CountDownLatch(1)
        var warmedWhileFirstPlayed = false
        var overlap = false
        var switchPosition = 0L
        lateinit var primary: ExoPlayer
        lateinit var secondary: ExoPlayer
        lateinit var player: CrossfadePlayer
        main {
            primary = ExoPlayer.Builder(context).build()
            secondary = ExoPlayer.Builder(context).build()
            player = CrossfadePlayer(primary, secondary, AudioAttributes.DEFAULT)
            player.setHandleAudioFocus(false)
            var staged = false
            primary.addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(state: Int) {
                    if (state == Player.STATE_READY && !staged) {
                        staged = true
                        assertTrue(player.stageVerifiedTransition("1", 1, successor,
                            if (shortNext) 1000 else 1800, if (shortNext) 1000 else 200, 0))
                    }
                }
            })
            secondary.addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(state: Int) {
                    if (state == Player.STATE_READY && standbyReady.count > 0) {
                        warmedWhileFirstPlayed = primary.isPlaying && !secondary.playWhenReady && primary.currentPosition < 1200
                        standbyReady.countDown()
                    }
                }
            })
            player.addListener(object : Player.Listener {
                override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                    if (mediaItem?.mediaId == "2") {
                        switchPosition = primary.currentPosition
                        overlap = primary.isPlaying && secondary.isPlaying
                        switched.countDown()
                    }
                }
            })
            player.setMediaItem(item("1", first)); player.prepare(); player.play()
        }
        try {
            assertTrue(standbyReady.await(8, TimeUnit.SECONDS))
            assertTrue(warmedWhileFirstPlayed)
            assertTrue(switched.await(8, TimeUnit.SECONDS))
            assertTrue("Both outputs must be playing during the join", overlap)
            assertTrue("200ms overlap must begin before the first track ends: $switchPosition", switchPosition in 1750L..1999L)
        } finally { main { player.release() }; server?.close(); first.delete(); second.delete() }
    }

    /** Bounded synthetic streaming fixture; no external service or real music is requested. */
    private class LocalAudioServer(private val audio: ByteArray) : AutoCloseable {
        private val socket = ServerSocket(0, 4, InetAddress.getByName("127.0.0.1"))
        val url = "http://127.0.0.1:${socket.localPort}/tone.wav"
        private val worker = thread(name = "transition-audio-fixture", isDaemon = true) {
            while (!socket.isClosed) {
                try {
                    socket.accept().use { client ->
                        client.soTimeout = 5000
                        val input = client.getInputStream().bufferedReader()
                        while (!input.readLine().isNullOrEmpty()) { /* Consume request headers. */ }
                        val output = client.getOutputStream()
                        output.write(("HTTP/1.1 200 OK\r\nContent-Type: audio/wav\r\nContent-Length: ${audio.size}\r\nConnection: close\r\n\r\n").toByteArray())
                        var offset = 0
                        while (offset < audio.size) {
                            val size = minOf(4096, audio.size - offset)
                            output.write(audio, offset, size)
                            output.flush()
                            offset += size
                            Thread.sleep(2)
                        }
                    }
                } catch (_: Exception) { /* A cancelled player may close its HTTP request early. */ }
            }
        }
        override fun close() { socket.close(); worker.join(1000) }
    }

    @Test fun cancellingAStagedSuccessorDoesNotPlayItLater() {
        val file = tone("transition-cancel.wav")
        val staged = CountDownLatch(1)
        val ended = CountDownLatch(1)
        var wrongTransition = false
        lateinit var player: CrossfadePlayer
        main {
            val primary = ExoPlayer.Builder(context).build()
            val secondary = ExoPlayer.Builder(context).build()
            player = CrossfadePlayer(primary, secondary, AudioAttributes.DEFAULT)
            player.setHandleAudioFocus(false)
            var once = false
            player.addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(state: Int) {
                    if (state == Player.STATE_READY && !once) {
                        once = true
                        player.stageVerifiedTransition("1", 5, item("2", file), 1800, 200, 0)
                        Handler(Looper.getMainLooper()).postDelayed({ player.cancelVerifiedTransition(); staged.countDown() }, 200)
                    }
                    if (state == Player.STATE_ENDED) ended.countDown()
                }
                override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) { if (mediaItem?.mediaId == "2") wrongTransition = true }
            })
            player.setMediaItem(item("1", file)); player.prepare(); player.play()
        }
        try {
            assertTrue(staged.await(5, TimeUnit.SECONDS))
            assertTrue(ended.await(8, TimeUnit.SECONDS))
            assertFalse(wrongTransition)
        } finally { main { player.release() }; file.delete() }
    }
}

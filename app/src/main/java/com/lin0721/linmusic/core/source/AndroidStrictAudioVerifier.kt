package com.lin0721.linmusic.core.source

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaDataSource
import android.media.MediaExtractor
import android.media.MediaFormat
import com.lin0721.linmusic.core.model.Track
import com.lin0721.linmusic.core.player.data.PlaybackSourceKind
import com.lin0721.linmusic.core.player.data.SongPlaybackInfo
import java.io.IOException
import java.nio.ByteOrder
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import kotlin.coroutines.coroutineContext
import kotlin.math.abs
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.Authenticator
import okhttp3.CookieJar
import okhttp3.OkHttpClient

private const val VERIFICATION_TIMEOUT_NS = 30_000_000_000L
private const val CODEC_WAIT_US = 10_000L

internal data class DecodedPcm(val samples: FloatArray, val downloadedBytes: Long, val durationMs: Long?)

/**
 * Compares decoded excerpts against a preview supplied by the official repository for this target.
 * Tags, URLs, claimed duration and a plugin's self-reported verification are never audio evidence.
 * Agreement of sampled excerpts does not establish the identity of unsampled parts of the song.
 */
class AndroidStrictAudioVerifier(@Suppress("UNUSED_PARAMETER") context: Context) : StrictAudioVerifier {
    private val client = OkHttpClient.Builder()
        .followRedirects(false).followSslRedirects(false)
        .authenticator(Authenticator.NONE).proxyAuthenticator(Authenticator.NONE)
        .cookieJar(CookieJar.NO_COOKIES).cache(null)
        .connectTimeout(3, TimeUnit.SECONDS).readTimeout(3, TimeUnit.SECONDS)
        .callTimeout(5, TimeUnit.SECONDS).build()

    override suspend fun verify(targetTrack: Track, candidateUrl: String, officialPreview: SongPlaybackInfo): StrictAudioVerification? =
        withContext(Dispatchers.IO) {
            if (officialPreview.sourceKind != PlaybackSourceKind.OFFICIAL || !officialPreview.isFreeTrial ||
                officialPreview.url.isBlank() || candidateUrl.isBlank() || targetTrack.dt <= 0L) return@withContext null
            // An unknown preview offset cannot establish corresponding samples; do not guess the whole track.
            val startMs = officialPreview.referenceStartMs?.takeIf { it >= 0L } ?: return@withContext null
            if (startMs > Long.MAX_VALUE / 1_000 - 20_000) return@withContext null
            val jobContext = coroutineContext
            val deadline = System.nanoTime() + VERIFICATION_TIMEOUT_NS
            val check = Runnable {
                jobContext.ensureActive()
                if (System.nanoTime() > deadline) throw IOException("Audio excerpt verification timed out")
            }
            try {
                val reference = decodeUrl(officialPreview.url, 0L, 12_000L, check) ?: return@withContext null
                val candidateFrom = (startMs - 500L).coerceAtLeast(0L)
                val candidate = decodeUrl(candidateUrl, candidateFrom, 13_000L, check) ?: return@withContext null
                // Completeness is a separate gate: a matching short preview is not a full-length source.
                val actualDurationMs = candidate.durationMs?.takeIf { it > 0L } ?: return@withContext null
                val toleranceMs = minOf(3_000L, (targetTrack.dt / 100L).coerceAtLeast(1L))
                if (abs(actualDurationMs - targetTrack.dt) > toleranceMs) return@withContext null
                check.run()
                val match = PcmIdentityMatcher.match(reference.samples, candidate.samples,
                    ((startMs - candidateFrom) * PcmIdentityMatcher.SAMPLE_RATE / 1000L).toInt()) ?: return@withContext null
                check.run()
                val evidence = "pcm-excerpt-v1|${targetTrack.id}|${digest(candidateUrl.toByteArray())}|" +
                    "${digest(officialPreview.url.toByteArray())}|$startMs|${match.candidateOffsetSamples}|" +
                    "${pcmDigest(reference.samples)}|${pcmDigest(candidate.samples)}"
                StrictAudioVerification(digest(evidence.toByteArray()), match.comparedDurationMs, match.normalizedCorrelation)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // Never include signed stream URLs, response headers or credentials in diagnostics.
                jobContext.ensureActive()
                null
            }
        }

    // A synchronous boundary permits native instrumentation to replay the production decoder directly.
    private fun decodeUrl(url: String, fromMs: Long, durationMs: Long, cancellationCheck: Runnable): DecodedPcm? {
        if (fromMs < 0 || durationMs !in 1..20_000 || fromMs > Long.MAX_VALUE / 1_000 - durationMs) return null
        val deadline = System.nanoTime() + VERIFICATION_TIMEOUT_NS
        val check = Runnable {
            cancellationCheck.run()
            if (System.nanoTime() > deadline) throw IOException("Audio excerpt decoding timed out")
        }
        val reader = BoundedAudioRangeReader(url, client, cancellationCheck = check)
        val source = object : MediaDataSource() {
            override fun getSize(): Long = reader.size()
            override fun readAt(position: Long, buffer: ByteArray, offset: Int, size: Int): Int =
                reader.readAt(position, buffer, offset, size)
            override fun close() = reader.close()
        }
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            check.run()
            extractor.setDataSource(source)
            val track = (0 until extractor.trackCount).firstOrNull {
                extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
            } ?: return null
            val format = extractor.getTrackFormat(track)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: return null
            val containerDurationMs = if (format.containsKey(MediaFormat.KEY_DURATION))
                (format.getLong(MediaFormat.KEY_DURATION) / 1000L).takeIf { it > 0L } else null
            extractor.selectTrack(track)
            extractor.seekTo(fromMs * 1000L, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
            val decoder = MediaCodec.createDecoderByType(mime)
            codec = decoder
            decoder.configure(format, null, null, 0)
            decoder.start()
            val accumulator = MonoPcmAccumulator(fromMs, durationMs)
            val info = MediaCodec.BufferInfo()
            var sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            var channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            var encoding = AudioFormat.ENCODING_PCM_16BIT
            var inputDone = false
            var outputDone = false
            val toUs = (fromMs + durationMs) * 1000L
            while (!outputDone) {
                check.run()
                if (!inputDone) {
                    val input = decoder.dequeueInputBuffer(CODEC_WAIT_US)
                    if (input >= 0) {
                        val buffer = decoder.getInputBuffer(input) ?: return null
                        val count = extractor.readSampleData(buffer, 0)
                        val time = extractor.sampleTime
                        if (count < 0 || time >= toUs) {
                            decoder.queueInputBuffer(input, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            decoder.queueInputBuffer(input, 0, count, time, 0)
                            extractor.advance()
                        }
                    }
                }
                val output = decoder.dequeueOutputBuffer(info, CODEC_WAIT_US)
                when {
                    output == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        val actual = decoder.outputFormat
                        sampleRate = actual.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                        channels = actual.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                        encoding = if (actual.containsKey(MediaFormat.KEY_PCM_ENCODING)) actual.getInteger(MediaFormat.KEY_PCM_ENCODING)
                            else AudioFormat.ENCODING_PCM_16BIT
                    }
                    output >= 0 -> {
                        try {
                            if (sampleRate !in 8_000..384_000 || channels !in 1..8 ||
                                encoding !in setOf(AudioFormat.ENCODING_PCM_16BIT, AudioFormat.ENCODING_PCM_FLOAT)) return null
                            val buffer = decoder.getOutputBuffer(output)
                            if (buffer != null && info.size > 0 && info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 0) {
                                buffer.position(info.offset)
                                buffer.limit(info.offset + info.size)
                                buffer.order(ByteOrder.nativeOrder())
                                val bytesPerSample = if (encoding == AudioFormat.ENCODING_PCM_FLOAT) 4 else 2
                                val frames = info.size / bytesPerSample / channels
                                for (frame in 0 until frames) {
                                    var mono = 0.0
                                    for (channel in 0 until channels) {
                                        mono += if (encoding == AudioFormat.ENCODING_PCM_FLOAT) buffer.float.toDouble()
                                            else buffer.short.toDouble() / 32768.0
                                    }
                                    accumulator.add(info.presentationTimeUs + frame * 1_000_000L / sampleRate, mono / channels)
                                }
                            }
                        } finally { decoder.releaseOutputBuffer(output, false) }
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0 || info.presentationTimeUs >= toUs) outputDone = true
                    }
                }
            }
            return DecodedPcm(accumulator.finish(), reader.downloadedBytes, containerDurationMs)
        } finally {
            runCatching { codec?.stop() }
            runCatching { codec?.release() }
            runCatching { extractor.release() }
            source.close()
        }
    }

    private fun digest(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes)
        .joinToString("") { "%02x".format(it) }

    private fun pcmDigest(samples: FloatArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
        for (sample in samples) {
            val bits = sample.toRawBits()
            digest.update(bits.toByte())
            digest.update((bits ushr 8).toByte())
            digest.update((bits ushr 16).toByte())
            digest.update((bits ushr 24).toByte())
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}

private class MonoPcmAccumulator(private val fromMs: Long, durationMs: Long) {
    private val sums = DoubleArray((durationMs * PcmIdentityMatcher.SAMPLE_RATE / 1000L).toInt())
    private val counts = IntArray(sums.size)
    private var last = -1
    fun add(timeUs: Long, value: Double) {
        if (!value.isFinite() || timeUs < fromMs * 1000L) return
        val position = ((timeUs - fromMs * 1000L) * PcmIdentityMatcher.SAMPLE_RATE / 1_000_000L)
        if (position !in 0 until sums.size.toLong()) return
        val index = position.toInt()
        sums[index] += value
        counts[index]++
        last = maxOf(last, index)
    }
    fun finish(): FloatArray = FloatArray(last + 1) { i -> if (counts[i] == 0) 0f else (sums[i] / counts[i]).toFloat() }
}


package com.lin0721.linmusic.core.source

import java.io.Closeable
import java.io.IOException
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request

private const val INPUT_BUDGET_BYTES = 2 * 1024 * 1024
private const val CHUNK_BYTES = 64 * 1024

/** Bounded random access for container headers, excerpts and tail metadata; never touches playback cache. */
internal class BoundedAudioRangeReader(
    url: String,
    private val client: OkHttpClient,
    private val maximumBytes: Int = INPUT_BUDGET_BYTES,
    private val cancellationCheck: Runnable = Runnable {}
) : Closeable {
    private val initialUrl = url.toHttpUrlOrNull()?.takeIf { it.username.isEmpty() && it.password.isEmpty() }
        ?: throw IOException("Unsupported audio URL")
    private val chunks = mutableMapOf<Long, ByteArray>()
    private var totalSize = -1L
    private var entityTag: String? = null
    private var closed = false
    var downloadedBytes: Long = 0L
        private set

    @Synchronized fun size(): Long { if (totalSize < 0) chunk(0); return totalSize }

    @Synchronized fun readAt(position: Long, output: ByteArray, offset: Int, length: Int): Int {
        cancellationCheck.run()
        if (closed || position < 0 || offset < 0 || length < 0 || offset > output.size - length) throw IOException("Invalid audio read")
        if (length == 0) return 0
        if (totalSize >= 0 && position >= totalSize) return -1
        val block = position / CHUNK_BYTES
        val bytes = chunk(block)
        val local = (position % CHUNK_BYTES).toInt()
        if (local >= bytes.size) return -1
        val count = minOf(length, bytes.size - local)
        bytes.copyInto(output, offset, local, local + count)
        return count
    }

    private fun chunk(index: Long): ByteArray {
        chunks[index]?.let { return it }
        if (closed || index < 0 || index > Long.MAX_VALUE / CHUNK_BYTES) throw IOException("Invalid audio range")
        val remaining = maximumBytes.toLong() - downloadedBytes
        if (remaining <= 0) throw IOException("Audio read budget exceeded")
        val start = index * CHUNK_BYTES
        if (totalSize >= 0 && start >= totalSize) return byteArrayOf()
        val count = minOf(CHUNK_BYTES.toLong(), remaining, if (totalSize >= 0) totalSize - start else Long.MAX_VALUE).toInt()
        val end = start + count - 1
        var url: HttpUrl = initialUrl
        repeat(4) { redirect ->
            cancellationCheck.run()
            val request = Request.Builder().url(url).header("Range", "bytes=$start-$end")
                .header("Accept-Encoding", "identity").apply {
                    entityTag?.takeIf { !it.startsWith("W/") }?.let { header("If-Match", it) }
                }.build()
            val call = client.newCall(request)
            call.execute().use { response ->
                var fullyConsumed = false
                try {
                    cancellationCheck.run()
                    if (response.code in setOf(301, 302, 303, 307, 308)) {
                        if (redirect == 3) throw IOException("Too many audio redirects")
                        val next = response.header("Location")?.let { url.resolve(it) } ?: throw IOException("Invalid audio redirect")
                        if ((url.isHttps && !next.isHttps) || next.username.isNotEmpty() || next.password.isNotEmpty())
                            throw IOException("Unsafe audio redirect")
                        url = next
                        return@use
                    }
                    val currentTag = response.header("ETag")
                    if (entityTag != null && currentTag != entityTag) throw IOException("Audio object changed while reading")
                    if (entityTag == null && currentTag != null) entityTag = currentTag
                    val body = response.body ?: throw IOException("Missing audio body")
                    if (response.code == 200 && start == 0L) {
                        // Small servers may ignore Range. Only a known, wholly budgeted object is safe to retain.
                        val size = body.contentLength()
                        if (size !in 1..remaining) throw IOException("Audio server ignored range")
                        val all = readExactly(body.byteStream(), size.toInt())
                        fullyConsumed = true
                        totalSize = size
                        var cursor = 0
                        var block = 0L
                        while (cursor < all.size) {
                            chunks[block++] = all.copyOfRange(cursor, minOf(all.size, cursor + CHUNK_BYTES))
                            cursor += CHUNK_BYTES
                        }
                        return chunks[index] ?: byteArrayOf()
                    }
                    if (response.code != 206) throw IOException("Audio range unavailable")
                    val range = response.header("Content-Range")?.let { CONTENT_RANGE.matchEntire(it.trim()) }
                        ?: throw IOException("Invalid audio range response")
                    val actualStart = range.groupValues[1].toLongOrNull() ?: throw IOException("Invalid audio range start")
                    val actualEnd = range.groupValues[2].toLongOrNull() ?: throw IOException("Invalid audio range end")
                    val actualTotal = range.groupValues[3].toLongOrNull() ?: throw IOException("Unknown audio object size")
                    if (actualStart != start || actualEnd < actualStart || actualEnd > end || actualTotal <= actualEnd ||
                        (totalSize >= 0 && actualTotal != totalSize)) throw IOException("Inconsistent audio range")
                    val expected = (actualEnd - actualStart + 1).toInt()
                    if (body.contentLength() >= 0 && body.contentLength() != expected.toLong()) throw IOException("Inconsistent audio body")
                    totalSize = actualTotal
                    val bytes = readExactly(body.byteStream(), expected)
                    fullyConsumed = body.contentLength() == expected.toLong()
                    return bytes.also { chunks[index] = it }
                } finally {
                    // Avoid OkHttp draining an unread redirect/error/over-budget body while closing it.
                    // An unknown-length body may also contain bytes beyond the advertised range.
                    if (!fullyConsumed) call.cancel()
                }
            }
        }
        throw IOException("Audio redirect unavailable")
    }

    private fun readExactly(input: java.io.InputStream, length: Int): ByteArray {
        if (length < 0 || length.toLong() > maximumBytes.toLong() - downloadedBytes) throw IOException("Audio read budget exceeded")
        val result = ByteArray(length)
        var read = 0
        while (read < length) {
            cancellationCheck.run()
            val count = input.read(result, read, minOf(8192, length - read))
            if (count < 0) throw IOException("Truncated audio range")
            if (count == 0) throw IOException("Empty audio read")
            downloadedBytes += count
            read += count
        }
        cancellationCheck.run()
        return result
    }

    @Synchronized override fun close() { closed = true; chunks.clear() }

    private companion object { val CONTENT_RANGE = Regex("bytes (\\d+)-(\\d+)/(\\d+)", RegexOption.IGNORE_CASE) }
}

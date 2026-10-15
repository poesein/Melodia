package com.lin0721.linmusic.core.source

import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CancellationException
import okhttp3.Call
import okhttp3.EventListener
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BoundedAudioRangeReaderTest {
    private val client = OkHttpClient.Builder().followRedirects(false).followSslRedirects(false).build()

    @Test fun `range reader supports distant metadata without downloading intervening audio`() {
        MockWebServer().use { server ->
            val start = ByteArray(65_536) { 1 }
            val tail = ByteArray(65_536) { 2 }
            server.enqueue(range(0, start, 1_048_576))
            server.enqueue(range(983_040, tail, 1_048_576))
            BoundedAudioRangeReader(server.url("/audio").toString(), client).use { reader ->
                assertEquals(1_048_576L, reader.size())
                val result = ByteArray(4)
                assertEquals(4, reader.readAt(983_042, result, 0, 4))
                assertTrue(result.all { it == 2.toByte() })
                assertEquals(131_072L, reader.downloadedBytes)
                assertEquals(4, reader.readAt(983_042, result, 0, 4))
                assertEquals(131_072L, reader.downloadedBytes)
            }
            assertEquals("bytes=0-65535", server.takeRequest().getHeader("Range"))
            assertEquals("bytes=983040-1048575", server.takeRequest().getHeader("Range"))
        }
    }

    @Test fun `byte budget is enforced even across unrelated ranges`() {
        MockWebServer().use { server ->
            server.enqueue(range(0, ByteArray(65_536), 1_048_576))
            BoundedAudioRangeReader(server.url("/audio").toString(), client, maximumBytes = 65_536).use { reader ->
                reader.size()
                expectIOException { reader.readAt(131_072, ByteArray(8), 0, 8) }
                assertEquals(65_536L, reader.downloadedBytes)
            }
            assertEquals(1, server.requestCount)
        }
    }

    @Test fun `ignored range cannot trigger an unbounded full download`() {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(200).setBody(Buffer().write(ByteArray(131_072))))
            BoundedAudioRangeReader(server.url("/audio").toString(), client, maximumBytes = 65_536).use { reader ->
                expectIOException { reader.size() }
                assertEquals(0L, reader.downloadedBytes)
            }
        }
    }

    @Test fun `small server without range support is wholly budgeted`() {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody(Buffer().write(ByteArray(200) { 3 })))
            BoundedAudioRangeReader(server.url("/audio").toString(), client).use { reader ->
                assertEquals(200L, reader.size())
                assertEquals(200L, reader.downloadedBytes)
                assertEquals(-1, reader.readAt(200, ByteArray(1), 0, 1))
            }
        }
    }

    @Test fun `wrong content range and unknown size are rejected`() {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(206).setHeader("Content-Range", "bytes 1-4/100").setBody("test"))
            BoundedAudioRangeReader(server.url("/audio").toString(), client).use { expectIOException { it.size() } }
            server.enqueue(MockResponse().setResponseCode(206).setHeader("Content-Range", "bytes 0-3/*").setBody("test"))
            BoundedAudioRangeReader(server.url("/audio").toString(), client).use { expectIOException { it.size() } }
        }
    }

    @Test fun `closed readers do not return previously cached bytes`() {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("test"))
            val reader = BoundedAudioRangeReader(server.url("/audio").toString(), client)
            reader.size()
            reader.close()
            expectIOException { reader.readAt(0, ByteArray(1), 0, 1) }
        }
    }

    @Test fun `changing entity tags reject mixed audio objects`() {
        MockWebServer().use { server ->
            server.enqueue(range(0, ByteArray(65_536), 1_048_576).setHeader("ETag", "\"first\""))
            server.enqueue(range(65_536, ByteArray(65_536), 1_048_576).setHeader("ETag", "\"second\""))
            BoundedAudioRangeReader(server.url("/audio").toString(), client).use { reader ->
                reader.size()
                expectIOException { reader.readAt(65_536, ByteArray(1), 0, 1) }
                assertEquals(65_536L, reader.downloadedBytes)
            }
            server.takeRequest()
            assertEquals("\"first\"", server.takeRequest().getHeader("If-Match"))
        }
    }

    @Test fun `redirect body is cancelled before closing rather than drained`() {
        MockWebServer().use { server ->
            val cancellations = AtomicInteger()
            val observedClient = client.newBuilder().eventListener(object : EventListener() {
                override fun canceled(call: Call) { cancellations.incrementAndGet() }
            }).build()
            server.enqueue(MockResponse().setResponseCode(302).setHeader("Location", "/data")
                .setBody(Buffer().write(ByteArray(131_072))))
            server.enqueue(MockResponse().setBody("data"))
            BoundedAudioRangeReader(server.url("/redirect").toString(), observedClient).use { reader ->
                assertEquals(4L, reader.size())
                assertEquals(4L, reader.downloadedBytes)
            }
            assertEquals(1, cancellations.get())
            assertEquals(2, server.requestCount)
        }
    }

    @Test fun `cancellation is propagated without issuing an HTTP request`() {
        MockWebServer().use { server ->
            val reader = BoundedAudioRangeReader(server.url("/audio").toString(), client,
                cancellationCheck = Runnable { throw CancellationException("cancelled") })
            try { reader.size(); throw AssertionError("Expected cancellation") }
            catch (_: CancellationException) { /* expected */ }
            finally { reader.close() }
            assertEquals(0, server.requestCount)
        }
    }

    private fun range(start: Long, bytes: ByteArray, total: Long) = MockResponse().setResponseCode(206)
        .setHeader("Content-Range", "bytes $start-${start + bytes.size - 1}/$total").setBody(Buffer().write(bytes))

    private fun expectIOException(block: () -> Unit) {
        try { block(); throw AssertionError("Expected IOException") }
        catch (_: IOException) { /* expected */ }
    }
}

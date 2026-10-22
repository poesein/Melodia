package com.lin0721.linmusic.core.source

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import okio.GzipSink
import okio.buffer
import org.junit.Assert.*
import org.junit.Test

class SourceHttpClientBodyLimitTest {
    @Test fun `GET accepts a complete UTF8 response at the exact byte limit`() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setHeader("Content-Type", "text/plain; charset=utf-8").setBody("音乐"))
            assertEquals("音乐", SourceHttpClient.get(server.url("/metadata").toString(), maxBodyBytes = 6L))
        }
    }

    @Test fun `POST preserves small JSON responses and request data`() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("{\"url\":\"ok\"}"))
            assertEquals("{\"url\":\"ok\"}", SourceHttpClient.post(server.url("/resolve").toString(), "{\"id\":1}",
                mapOf("X-Test" to "metadata"), maxBodyBytes = 64L))
            val request = server.takeRequest()
            assertEquals("POST", request.method)
            assertEquals("{\"id\":1}", request.body.readUtf8())
            assertEquals("metadata", request.getHeader("X-Test"))
        }
    }

    @Test fun `GET rejects a declared oversized response`() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("x".repeat(65)))
            val error = runCatching { SourceHttpClient.get(server.url("/large").toString(), maxBodyBytes = 64L) }.exceptionOrNull()
            assertTrue(error is SourceException)
            assertTrue(error!!.message!!.contains("响应体"))
        }
    }

    @Test fun `POST also bounds chunked responses without Content-Length`() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setChunkedBody("音".repeat(32), 7))
            val error = runCatching { SourceHttpClient.post(server.url("/chunked").toString(), "{}", maxBodyBytes = 64L) }.exceptionOrNull()
            assertTrue(error is SourceException)
            assertTrue(error!!.message!!.contains("超过"))
        }
    }

    @Test fun `compressed responses are bounded after decompression`() = runBlocking {
        MockWebServer().use { server ->
            val compressed = Buffer()
            GzipSink(compressed).buffer().use { it.writeUtf8("x".repeat(10_000)) }
            assertTrue(compressed.size < 64L)
            server.enqueue(MockResponse().setHeader("Content-Encoding", "gzip").setBody(compressed))
            val error = runCatching { SourceHttpClient.get(server.url("/compressed").toString(), maxBodyBytes = 64L) }.exceptionOrNull()
            assertTrue(error is SourceException)
        }
    }

    @Test fun `existing callers retain their explicit opt-in behavior`() = runBlocking {
        MockWebServer().use { server ->
            val payload = "x".repeat(256)
            server.enqueue(MockResponse().setChunkedBody(payload, 32))
            assertEquals(payload, SourceHttpClient.get(server.url("/legacy").toString()))
        }
    }
}

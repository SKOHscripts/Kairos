package com.skohscripts.kairos.ui.app

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** HTTP/1.1 minimal du transport Android des méthodes WebDAV (docs/spec/connexions-en-ligne.md § Transport des méthodes WebDAV). */
class Http11Test {
    /** Source qui rend les octets par petits morceaux, comme un socket. */
    private fun source(text: String, step: Int = 7): ByteSource {
        val bytes = text.encodeToByteArray()
        var at = 0
        return ByteSource { buffer, offset, length ->
            if (at >= bytes.size) -1 else {
                val count = minOf(step, length, bytes.size - at)
                bytes.copyInto(buffer, offset, at, at + count)
                at += count
                count
            }
        }
    }

    @Test
    fun theRequestCarriesHostLengthAndCloseButNoCookieNorCompression() {
        val body = "<x/>".encodeToByteArray()
        val text = Http11.encode("PROPFIND", "/alice/?q=1", "dav.example:8443",
            mapOf("Depth" to "1", "Authorization" to "Basic YTpi", "Cookie" to "s=1", "Accept-Encoding" to "gzip", "Host" to "evil"), body).decodeToString()
        assertTrue(text.startsWith("PROPFIND /alice/?q=1 HTTP/1.1\r\nHost: dav.example:8443\r\n"), text)
        assertTrue("Depth: 1\r\n" in text && "Authorization: Basic YTpi\r\n" in text && "Content-Length: 4\r\n" in text)
        assertTrue("Cookie" !in text && "Accept-Encoding" !in text && "evil" !in text)
        assertTrue(text.endsWith("Connection: close\r\n\r\n<x/>"))
    }

    @Test
    fun headerInjectionIsRefused() {
        assertFailsWith<IllegalArgumentException> { Http11.encode("GET", "/", "h", mapOf("X" to "a\r\nEvil: 1"), null) }
        assertFailsWith<IllegalArgumentException> { Http11.encode("GET /x", "/", "h", emptyMap(), null) }
        assertFailsWith<IllegalArgumentException> { Http11.encode("GET", "/ a", "h", emptyMap(), null) }
    }

    @Test
    fun fixedLengthBody() {
        val response = Http11.read(source("HTTP/1.1 207 Multi-Status\r\nContent-Type: text/xml\r\nContent-Length: 5\r\nX-A: 1\r\nX-A: 2\r\n\r\nhello"))
        assertEquals(207, response.status)
        assertEquals("text/xml", response.headers["content-type"])
        assertEquals("1, 2", response.headers["x-a"])
        assertEquals("hello", response.body.decodeToString())
    }

    @Test
    fun chunkedBodyWithExtensionsAndTrailer() {
        val raw = "HTTP/1.1 200 OK\r\nTransfer-Encoding: chunked\r\n\r\n5;ext=1\r\nhello\r\n6\r\n world\r\n0\r\nX-Trailer: t\r\n\r\n"
        assertEquals("hello world", Http11.read(source(raw, step = 3)).body.decodeToString())
    }

    @Test
    fun bodyUntilCloseAndNoBodyFor304() {
        assertEquals("all of it", Http11.read(source("HTTP/1.0 200 OK\r\n\r\nall of it")).body.decodeToString())
        assertContentEquals(ByteArray(0), Http11.read(source("HTTP/1.1 304 Not Modified\r\nContent-Length: 10\r\n\r\n")).body)
    }

    @Test
    fun interimResponsesAreSkipped() {
        val response = Http11.read(source("HTTP/1.1 100 Continue\r\n\r\nHTTP/1.1 207 Multi-Status\r\nContent-Length: 2\r\n\r\nok"))
        assertEquals(207, response.status)
        assertEquals("ok", response.body.decodeToString())
    }

    @Test
    fun aTooBigBodyStopsTheRead() {
        assertFailsWith<HttpTooBigException> { Http11.read(source("HTTP/1.1 200 OK\r\nContent-Length: 11\r\n\r\nhello world"), maxBody = 10) }
        assertFailsWith<HttpTooBigException> { Http11.read(source("HTTP/1.1 200 OK\r\n\r\nhello world"), maxBody = 10) }
        assertFailsWith<HttpTooBigException> { Http11.read(source("HTTP/1.1 200 OK\r\nTransfer-Encoding: chunked\r\n\r\nb\r\nhello world\r\n0\r\n\r\n"), maxBody = 10) }
    }

    @Test
    fun cutOrGarbledResponsesAreTransportFailures() {
        assertFailsWith<HttpTransportException> { Http11.read(source("HTTP/1.1 200 OK\r\nContent-Length: 10\r\n\r\nshort")) }
        assertFailsWith<HttpTransportException> { Http11.read(source("HTTP/1.1 200 OK\r\nContent-")) }
        assertFailsWith<HttpTransportException> { Http11.read(source("SSH-2.0-OpenSSH\r\n\r\n")) }
        assertFailsWith<HttpTransportException> { Http11.read(source("HTTP/1.1 200 OK\r\nTransfer-Encoding: chunked\r\n\r\nzz\r\n")) }
    }
}

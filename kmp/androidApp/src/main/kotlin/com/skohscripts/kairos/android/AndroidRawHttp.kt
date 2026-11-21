package com.skohscripts.kairos.android

import com.skohscripts.kairos.ui.app.Http11
import com.skohscripts.kairos.ui.app.HttpRequest
import com.skohscripts.kairos.ui.app.HttpResponse
import com.skohscripts.kairos.ui.app.HttpTooBigException
import com.skohscripts.kairos.ui.app.HttpTransportException
import com.skohscripts.kairos.ui.app.MAX_BODY_BYTES
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException
import java.net.URI
import java.net.URISyntaxException
import java.util.Locale
import java.util.zip.GZIPInputStream
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLException
import javax.net.ssl.SSLPeerUnverifiedException
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory

/**
 * Méthodes WebDAV sur Android (docs/spec/connexions-en-ligne.md § Transport des méthodes WebDAV) : `HttpURLConnection` refuse
 * `PROPFIND` et `REPORT` (`ProtocolException`, liste fermée de méthodes). Ici, HTTP/1.1 minimal ([Http11]) sur un `SSLSocket` du
 * système : `https` seulement, SNI et vérification du nom d'hôte, délais de 10 s de connexion et du total de la requête. Le
 * mandataire du système n'est pas utilisé (limite connue). Bloquant : appelé depuis `Dispatchers.IO`.
 */
internal object AndroidRawHttp {
    /** Méthodes que `HttpURLConnection` accepte ; les autres passent par ce transport. */
    private val NATIVE = setOf("GET", "HEAD", "POST", "PUT", "DELETE", "OPTIONS", "TRACE", "PATCH")

    fun handles(method: String): Boolean = method.uppercase(Locale.ROOT) !in NATIVE

    fun fetch(request: HttpRequest): HttpResponse {
        require(request.timeoutMillis > 0)
        val uri = try {
            URI(request.url)
        } catch (error: URISyntaxException) {
            throw HttpTransportException(tls = false, message = "Invalid address", cause = error, invalidAddress = true)
        }
        val host = uri.host?.removePrefix("[")?.removeSuffix("]")
        if (!"https".equals(uri.scheme, ignoreCase = true) || host.isNullOrEmpty()) {
            throw HttpTransportException(tls = false, message = "https only", invalidAddress = true)
        }
        val port = if (uri.port == -1) 443 else uri.port
        val hostHeader = (if (':' in host) "[$host]" else host) + if (port == 443) "" else ":$port"
        val target = (uri.rawPath?.takeIf { it.isNotEmpty() } ?: "/") + (uri.rawQuery?.let { "?$it" } ?: "")
        val deadline = System.nanoTime() + request.timeoutMillis * 1_000_000
        fun remaining(): Int {
            val left = (deadline - System.nanoTime()) / 1_000_000
            if (left <= 0) throw SocketTimeoutException("HTTP timeout")
            return left.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        }
        val plain = Socket()
        try {
            plain.connect(InetSocketAddress(host, port), minOf(10_000, remaining()))
            // The host name given here is the SNI name (Conscrypt), and endpoint identification checks the certificate.
            val socket = (SSLSocketFactory.getDefault() as SSLSocketFactory).createSocket(plain, host, port, true) as SSLSocket
            socket.use { ssl ->
                ssl.sslParameters = ssl.sslParameters.apply { endpointIdentificationAlgorithm = "HTTPS" }
                ssl.soTimeout = remaining()
                ssl.startHandshake()
                if (!HttpsURLConnection.getDefaultHostnameVerifier().verify(host, ssl.session)) {
                    throw SSLPeerUnverifiedException("Host name does not match the certificate")
                }
                ssl.outputStream.apply {
                    write(Http11.encode(request.method, target, hostHeader, request.headers, request.body))
                    flush()
                }
                val input = ssl.inputStream
                val response = Http11.read({ buffer, offset, length ->
                    // A read timeout alone does not stop a server that sends a slow stream: each read gets what is left.
                    ssl.soTimeout = remaining()
                    input.read(buffer, offset, length)
                }, head = request.method.equals("HEAD", ignoreCase = true))
                return if (response.headers["content-encoding"].equals("gzip", ignoreCase = true)) {
                    HttpResponse(response.status, response.headers, gunzip(response.body))
                } else response
            }
        } catch (error: SSLException) {
            throw HttpTransportException(tls = true, message = "TLS transport failed", cause = error)
        } catch (error: IOException) {
            throw HttpTransportException(tls = false, message = "HTTP transport failed", cause = error)
        } finally {
            runCatching { plain.close() }
        }
    }

    /** Un serveur qui compresse sans qu'on le demande : décompressé ici, sous le même plafond. */
    private fun gunzip(body: ByteArray): ByteArray = GZIPInputStream(ByteArrayInputStream(body)).use { input ->
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8_192)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            if (count > MAX_BODY_BYTES - output.size()) throw HttpTooBigException()
            output.write(buffer, 0, count)
        }
        output.toByteArray()
    }
}

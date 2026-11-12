package com.skohscripts.kairos.android

import com.skohscripts.kairos.ui.app.HttpFetcher
import com.skohscripts.kairos.ui.app.HttpRequest
import com.skohscripts.kairos.ui.app.HttpResponse
import com.skohscripts.kairos.ui.app.HttpTooBigException
import com.skohscripts.kairos.ui.app.HttpTransportException
import com.skohscripts.kairos.ui.app.MAX_BODY_BYTES
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import java.util.zip.GZIPInputStream
import javax.net.ssl.SSLException

object AndroidHttp : HttpFetcher {
    override suspend fun fetch(request: HttpRequest): HttpResponse = withContext(Dispatchers.IO) {
        require(request.timeoutMillis > 0)
        val connection = URL(request.url).openConnection() as HttpURLConnection
        val expired = AtomicBoolean(false)
        coroutineScope {
            // A read timeout alone does not stop a server that sends a slow stream.
            val timeout = launch(Dispatchers.Default) {
                delay(request.timeoutMillis)
                expired.set(true)
                connection.disconnect()
            }
            try {
                connection.instanceFollowRedirects = false
                connection.useCaches = false
                connection.connectTimeout = minOf(10_000L, request.timeoutMillis).toInt()
                connection.readTimeout = request.timeoutMillis.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
                connection.requestMethod = request.method
                connection.setRequestProperty("Accept-Encoding", "gzip")
                request.headers.forEach { (name, value) ->
                    if (!name.equals("Cookie", ignoreCase = true) && !name.equals("Cookie2", ignoreCase = true)) {
                        connection.setRequestProperty(name, value)
                    }
                }
                request.body?.let { body ->
                    connection.doOutput = true
                    connection.setFixedLengthStreamingMode(body.size)
                    connection.outputStream.use { it.write(body) }
                }
                val status = connection.responseCode
                val headers = connection.headerFields.entries.mapNotNull { (name, values) ->
                    name?.lowercase(Locale.ROOT)?.let { it to values.joinToString(", ") }
                }.toMap()
                val stream = if (status >= 400) connection.errorStream else connection.inputStream
                val body = if (stream == null) byteArrayOf() else stream.use { raw ->
                    val decoded = if (headers["content-encoding"].equals("gzip", ignoreCase = true)) GZIPInputStream(raw) else raw
                    decoded.use { input ->
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
                if (expired.get()) throw SocketTimeoutException()
                HttpResponse(status, headers, body)
            } catch (error: SSLException) {
                throw HttpTransportException(tls = true, message = "TLS transport failed", cause = error)
            } catch (error: IOException) {
                throw HttpTransportException(tls = false, message = "HTTP transport failed", cause = error)
            } finally {
                timeout.cancel()
                connection.disconnect()
            }
        }
    }
}

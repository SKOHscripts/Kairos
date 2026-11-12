package com.skohscripts.kairos.desktop

import com.skohscripts.kairos.ui.app.HttpFetcher
import com.skohscripts.kairos.ui.app.HttpRequest
import com.skohscripts.kairos.ui.app.HttpResponse
import com.skohscripts.kairos.ui.app.HttpTooBigException
import com.skohscripts.kairos.ui.app.HttpTransportException
import com.skohscripts.kairos.ui.app.MAX_BODY_BYTES
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.ProxySelector
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest as JdkRequest
import java.net.http.HttpResponse as JdkResponse
import java.time.Duration
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.zip.GZIPInputStream
import javax.net.ssl.SSLException

object DesktopHttp : HttpFetcher {
    private val client by lazy {
        HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NEVER)
            .connectTimeout(Duration.ofSeconds(10))
            .apply { ProxySelector.getDefault()?.let { proxy(it) } }
            .build()
    }
    private val deadlines = Executors.newSingleThreadScheduledExecutor { task ->
        Thread(task, "kairos-http-deadline").apply { isDaemon = true }
    }

    override suspend fun fetch(request: HttpRequest): HttpResponse = withContext(Dispatchers.IO) {
        try {
            val started = System.nanoTime()
            val builder = JdkRequest.newBuilder(URI(request.url))
                .timeout(Duration.ofMillis(request.timeoutMillis))
            request.headers.forEach { (name, value) ->
                // Cookies must not cross the calendar transport boundary.
                if (!name.equals("Cookie", true) && !name.equals("Cookie2", true)) builder.header(name, value)
            }
            val body = request.body?.let { JdkRequest.BodyPublishers.ofByteArray(it) }
                ?: JdkRequest.BodyPublishers.noBody()
            val response = client.send(builder.method(request.method, body).build(), JdkResponse.BodyHandlers.ofInputStream())
            val headers = response.headers().map().mapKeys { it.key.lowercase(Locale.ROOT) }
                .mapValues { it.value.joinToString(", ") }
            val remaining = request.timeoutMillis - TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started)
            response.body().use { raw ->
                // HttpClient's request timeout ends at the headers when the body is a stream.
                val deadline = deadlines.schedule({ runCatching { raw.close() } }, remaining.coerceAtLeast(0), TimeUnit.MILLISECONDS)
                try {
                    if (remaining <= 0) throw IOException("HTTP timeout")
                    val stream = if (headers["content-encoding"].equals("gzip", true)) GZIPInputStream(raw) else raw
                    stream.use {
                        val output = ByteArrayOutputStream()
                        val buffer = ByteArray(8192)
                        while (true) {
                            val count = it.read(buffer)
                            if (count < 0) break
                            if (output.size() + count > MAX_BODY_BYTES) throw HttpTooBigException()
                            output.write(buffer, 0, count)
                        }
                        if (TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started) >= request.timeoutMillis) {
                            throw IOException("HTTP timeout")
                        }
                        HttpResponse(response.statusCode(), headers, output.toByteArray())
                    }
                } finally {
                    deadline.cancel(false)
                }
            }
        } catch (error: SSLException) {
            throw HttpTransportException(true, "TLS failure", error)
        } catch (error: IOException) {
            throw HttpTransportException(false, "HTTP transport failure", error)
        } catch (error: InterruptedException) {
            Thread.currentThread().interrupt()
            throw HttpTransportException(false, "HTTP interrupted", error)
        }
    }
}

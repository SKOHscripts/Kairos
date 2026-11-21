package com.skohscripts.kairos.ui.app

/** Source d'octets bloquante fournie par la plateforme (un socket) : nombre d'octets lus, `-1` en fin de flux. */
fun interface ByteSource {
    fun read(buffer: ByteArray, offset: Int, length: Int): Int
}

/**
 * HTTP/1.1 minimal, sans plateforme (docs/spec/connexions-en-ligne.md § Transport des méthodes WebDAV) : Android l'utilise pour
 * `PROPFIND` et `REPORT`, que `HttpURLConnection` refuse. Une requête par connexion (`Connection: close`), aucun cookie, aucune
 * compression demandée ; le socket, TLS et les délais restent à la plateforme (`AndroidRawHttp`).
 */
object Http11 {
    /** Taille maximale de la ligne d'état et des en-têtes réunis. */
    const val MAX_HEAD_BYTES = 64 * 1024

    private val TOKEN = Regex("[!#$%&'*+.^_`|~0-9A-Za-z-]+")
    private val MANAGED = setOf("host", "content-length", "connection", "transfer-encoding", "cookie", "cookie2", "accept-encoding")

    /**
     * La requête en octets : [target] est le chemin et la requête (`/a/b?c`), [host] l'hôte avec son port s'il n'est pas celui par
     * défaut. `Host`, `Content-Length` et `Connection` sont posés ici ; `Cookie`, `Cookie2` et `Accept-Encoding` ne passent pas.
     * Une méthode, un nom ou une valeur d'en-tête qui pourrait couper la requête (retour à la ligne) est refusée.
     */
    fun encode(method: String, target: String, host: String, headers: Map<String, String>, body: ByteArray?): ByteArray {
        require(TOKEN.matches(method)) { "bad method" }
        require(target.startsWith("/") && target.none { it.isWhitespace() || it.code < 32 }) { "bad target" }
        require(host.isNotEmpty() && host.none { it.isWhitespace() || it.code < 32 || it == '/' }) { "bad host" }
        val head = StringBuilder("$method $target HTTP/1.1\r\nHost: $host\r\n")
        headers.forEach { (name, value) ->
            require(TOKEN.matches(name)) { "bad header name" }
            require(value.none { it == '\r' || it == '\n' }) { "bad header value" }
            if (name.lowercase() !in MANAGED) head.append(name).append(": ").append(value).append("\r\n")
        }
        if (body != null) head.append("Content-Length: ").append(body.size).append("\r\n")
        head.append("Connection: close\r\n\r\n")
        val bytes = head.toString().encodeToByteArray()
        return if (body == null) bytes else bytes + body
    }

    /**
     * Lit une réponse : ligne d'état, en-têtes (noms en minuscules, valeurs répétées jointes par « , »), corps de longueur fixe,
     * découpé (`chunked`) ou lu jusqu'à la fermeture ; aucun corps pour une réponse à `HEAD`, 1xx, 204 et 304. Les réponses 1xx
     * intermédiaires sont sautées. Le corps n'est **pas** décompressé. Plus de [maxBody] octets : [HttpTooBigException] ;
     * réponse illisible ou coupée : [HttpTransportException].
     */
    fun read(source: ByteSource, head: Boolean = false, maxBody: Int = MAX_BODY_BYTES): HttpResponse {
        val input = Buffered(source)
        while (true) {
            val statusLine = input.line()
            val parts = statusLine.split(' ', limit = 3)
            if (parts.size < 2 || !parts[0].startsWith("HTTP/1.")) throw HttpTransportException(false, "bad status line")
            val status = parts[1].toIntOrNull()?.takeIf { it in 100..599 } ?: throw HttpTransportException(false, "bad status")
            val headers = LinkedHashMap<String, String>()
            while (true) {
                val line = input.line()
                if (line.isEmpty()) break
                val colon = line.indexOf(':')
                if (colon <= 0) throw HttpTransportException(false, "bad header")
                val name = line.substring(0, colon).trim().lowercase()
                val value = line.substring(colon + 1).trim()
                headers[name] = headers[name]?.let { "$it, $value" } ?: value
            }
            if (status in 100..199) continue
            val body = when {
                head || status == 204 || status == 304 -> ByteArray(0)
                headers["transfer-encoding"]?.lowercase()?.contains("chunked") == true -> input.chunked(maxBody)
                headers["content-length"] != null -> {
                    val length = headers["content-length"]!!.substringBefore(',').trim().toLongOrNull()
                        ?: throw HttpTransportException(false, "bad length")
                    if (length > maxBody) throw HttpTooBigException()
                    input.exactly(length.toInt())
                }
                else -> input.untilEnd(maxBody)
            }
            return HttpResponse(status, headers, body)
        }
    }

    private class Buffered(private val source: ByteSource) {
        private val buffer = ByteArray(8_192)
        private var start = 0
        private var end = 0
        private var headBytes = 0

        private fun fill(): Boolean {
            if (start < end) return true
            val count = source.read(buffer, 0, buffer.size)
            if (count <= 0) return false
            start = 0
            end = count
            return true
        }

        /** Une ligne terminée par CRLF (ou LF seul), sans sa fin. */
        fun line(): String {
            val out = mutableListOf<Byte>()
            while (true) {
                if (!fill()) throw HttpTransportException(false, "connection closed in headers")
                val b = buffer[start++]
                if (++headBytes > MAX_HEAD_BYTES) throw HttpTransportException(false, "headers too large")
                if (b == '\n'.code.toByte()) {
                    if (out.isNotEmpty() && out.last() == '\r'.code.toByte()) out.removeAt(out.lastIndex)
                    return out.toByteArray().decodeToString()
                }
                out += b
            }
        }

        fun exactly(length: Int): ByteArray {
            val out = ByteArray(length)
            var at = 0
            while (at < length) {
                if (!fill()) throw HttpTransportException(false, "connection closed in body")
                val count = minOf(end - start, length - at)
                buffer.copyInto(out, at, start, start + count)
                start += count
                at += count
            }
            return out
        }

        fun untilEnd(maxBody: Int): ByteArray {
            val out = Growing()
            while (fill()) {
                val count = end - start
                if (out.size + count > maxBody) throw HttpTooBigException()
                out.append(buffer, start, count)
                start = end
            }
            return out.toByteArray()
        }

        fun chunked(maxBody: Int): ByteArray {
            val out = Growing()
            while (true) {
                headBytes = 0
                val size = line().substringBefore(';').trim().toIntOrNull(16)?.takeIf { it >= 0 }
                    ?: throw HttpTransportException(false, "bad chunk size")
                if (size == 0) {
                    // Trailer : lignes d'en-tête jusqu'à la ligne vide.
                    while (line().isNotEmpty()) Unit
                    return out.toByteArray()
                }
                if (out.size + size > maxBody) throw HttpTooBigException()
                val chunk = exactly(size)
                out.append(chunk, 0, chunk.size)
                if (line().isNotEmpty()) throw HttpTransportException(false, "bad chunk end")
            }
        }
    }

    /** Tableau d'octets extensible (commun : pas de `ByteArrayOutputStream`). */
    private class Growing {
        private var data = ByteArray(8_192)
        var size = 0
            private set

        fun append(source: ByteArray, offset: Int, count: Int) {
            if (size + count > data.size) data = data.copyOf(maxOf(data.size * 2, size + count))
            source.copyInto(data, size, offset, offset + count)
            size += count
        }

        fun toByteArray(): ByteArray = data.copyOf(size)
    }
}

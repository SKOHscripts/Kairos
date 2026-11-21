package com.skohscripts.kairos.web

import com.skohscripts.kairos.ui.app.HttpFetcher
import com.skohscripts.kairos.ui.app.HttpRequest
import com.skohscripts.kairos.ui.app.HttpResponse
import com.skohscripts.kairos.ui.app.HttpTooBigException
import com.skohscripts.kairos.ui.app.HttpTransportException
import com.skohscripts.kairos.ui.app.MAX_BODY_BYTES
import kotlinx.coroutines.await

/**
 * Transport du navigateur (docs/spec/connexions-en-ligne.md § Web) : `fetch` par `KairosWeb.fetchText`, `GET` seulement, **aucun
 * en-tête envoyé** (ceux de [HttpRequest] sont ignorés : `User-Agent` et `Accept-Encoding` sont interdits aux pages, `If-None-Match`
 * déclencherait une requête préliminaire). Redirections suivies par le navigateur. La réponse n'a aucun en-tête : sans `ETag`, la
 * connexion ne reçoit jamais de `304`. Un serveur sans `Access-Control-Allow-Origin` échoue comme une panne de réseau.
 */
internal object WebHttp : HttpFetcher {
    override val httpsOnly = true

    override suspend fun fetch(request: HttpRequest): HttpResponse {
        if (request.method != "GET") throw HttpTransportException(tls = false, message = "GET only in the browser", invalidAddress = true)
        val timeout = request.timeoutMillis.coerceIn(1, Int.MAX_VALUE.toLong()).toInt()
        val result = KairosWeb.fetchText(request.url, timeout, MAX_BODY_BYTES).await<WebFetchResult>()
        return when (result.kind) {
            "ok" -> HttpResponse(result.status, emptyMap(), result.text.encodeToByteArray())
            "toobig" -> throw HttpTooBigException()
            "invalid" -> throw HttpTransportException(tls = false, message = "Invalid address", cause = BrowserFailure(result.message), invalidAddress = true)
            else -> throw HttpTransportException(tls = false, message = "Browser could not read the link", cause = BrowserFailure(result.message))
        }
    }

    /** Cause lisible du navigateur (« TypeError: Failed to fetch »), montrée comme détail technique. */
    private class BrowserFailure(message: String) : Exception(message)
}

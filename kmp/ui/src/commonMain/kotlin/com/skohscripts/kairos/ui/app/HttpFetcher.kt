package com.skohscripts.kairos.ui.app

/** Plafond du corps d'une réponse : au-delà, [ConnectionError.TOO_BIG] (docs/spec/connexions-en-ligne.md § Porte réseau unique). */
const val MAX_BODY_BYTES = 16 * 1024 * 1024

/** Une requête HTTP. [headers] : noms en casse libre ; [body] nul pour `GET`. */
class HttpRequest(
    val method: String,
    val url: String,
    val headers: Map<String, String> = emptyMap(),
    val body: ByteArray? = null,
    val timeoutMillis: Long = 30_000,
)

/** Une réponse HTTP : [headers] avec les noms en minuscules ; [body] déjà décompressé (gzip). */
class HttpResponse(val status: Int, val headers: Map<String, String>, val body: ByteArray)

/** Panne de transport (hors ligne, délai, TLS) : [tls] vrai pour une erreur de certificat ou de négociation TLS. */
class HttpTransportException(val tls: Boolean, message: String, cause: Throwable? = null) : Exception(message, cause)

/** Corps plus gros que [MAX_BODY_BYTES] : lecture interrompue. */
class HttpTooBigException : Exception("body exceeds $MAX_BODY_BYTES bytes")

/**
 * Transport HTTP d'une plateforme. Ne suit **aucune** redirection (c'est `HttpPolicy` / `OnlineGate`), n'envoie ni ne garde
 * aucun cookie, décompresse `gzip`, plafonne le corps à [MAX_BODY_BYTES] (sinon [HttpTooBigException]), délais de 10 s de
 * connexion et [HttpRequest.timeoutMillis] au total. Lève [HttpTransportException] pour une panne de transport.
 * Seule `OnlineGate` l'appelle pour un calendrier (docs/spec/connexions-en-ligne.md § Porte réseau unique).
 */
interface HttpFetcher {
    suspend fun fetch(request: HttpRequest): HttpResponse
}

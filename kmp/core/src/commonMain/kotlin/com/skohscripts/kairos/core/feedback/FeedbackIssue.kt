package com.skohscripts.kairos.core.feedback

/**
 * « Donner un avis » (docs/spec/retours.md, issue #211) : l'adresse d'une issue GitHub **préremplie** que le navigateur
 * ouvre ; Kairos n'envoie rien lui-même. Les paramètres `context`, `need`, `version` et `platform` sont les `id` des champs du
 * modèle `.github/ISSUE_TEMPLATE/feedback.yml` : GitHub remplit un champ d'un formulaire d'issue par le paramètre qui porte son id.
 * La version et la plateforme sont jointes d'office : elles voyagent dans l'adresse, sans permission réseau.
 */
object FeedbackIssue {
    const val REPOSITORY_URL = "https://github.com/SKOHscripts/Kairos"
    const val NEW_ISSUE_URL = "$REPOSITORY_URL/issues/new"
    const val TEMPLATE = "feedback.yml"
    const val FIELD_CONTEXT = "context"
    const val FIELD_NEED = "need"
    const val FIELD_VERSION = "version"
    const val FIELD_PLATFORM = "platform"

    /** Page de connexion vers laquelle `issues/new` redirige sans session : elle ré-encode l'adresse dans `return_to`. */
    const val LOGIN_PREFIX = "https://github.com/login?return_to="

    /**
     * Longueur maximale de l'adresse de connexion : `github.com` répond 414 au-delà d'environ 8 190 caractères (mesuré le
     * 2026-11-06) ; 8 000 garde une marge.
     */
    const val MAX_LOGIN_URL = 8000

    /**
     * L'adresse préremplie, ou nul si le titre ou le besoin est vide ; le contexte vide est omis. [version] (« 4.1.0 ») et
     * [platform] (« Android », « Windows », « Web »…) sont toujours joints, sauf s'ils sont vides.
     */
    fun url(title: String, context: String, need: String, version: String, platform: String): String? {
        val t = title.trim()
        val c = context.trim()
        val n = need.trim()
        if (t.isEmpty() || n.isEmpty()) return null
        return buildString {
            append(NEW_ISSUE_URL).append("?template=").append(TEMPLATE)
            append("&title=").append(encode(t))
            if (c.isNotEmpty()) append('&').append(FIELD_CONTEXT).append('=').append(encode(c))
            append('&').append(FIELD_NEED).append('=').append(encode(n))
            if (version.isNotBlank()) append('&').append(FIELD_VERSION).append('=').append(encode(version.trim()))
            if (platform.isNotBlank()) append('&').append(FIELD_PLATFORM).append('=').append(encode(platform.trim()))
        }
    }

    /** L'adresse [url] tient-elle, une fois enveloppée par la page de connexion de GitHub ? */
    fun fits(url: String): Boolean = LOGIN_PREFIX.length + encode(url).length <= MAX_LOGIN_URL

    /**
     * Encodage pourcent des octets UTF-8 : seuls les non-réservés de la RFC 3986 restent tels quels. Écrit à la main :
     * `java.net.URLEncoder` n'existe pas en commun (wasm) et code l'espace en `+`, qui n'est un espace que pour les formulaires.
     */
    fun encode(text: String): String = buildString {
        for (byte in text.encodeToByteArray()) {
            val b = byte.toInt() and 0xFF
            val c = b.toChar()
            if (c in 'A'..'Z' || c in 'a'..'z' || c in '0'..'9' || c == '-' || c == '.' || c == '_' || c == '~') {
                append(c)
            } else {
                append('%').append(HEX[b shr 4]).append(HEX[b and 0x0F])
            }
        }
    }

    private const val HEX = "0123456789ABCDEF"
}

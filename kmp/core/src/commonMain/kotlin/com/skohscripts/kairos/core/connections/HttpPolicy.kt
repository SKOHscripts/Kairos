package com.skohscripts.kairos.core.connections

object HttpPolicy {
    const val MAX_REDIRECTS = 5

    enum class Reason {
        BAD_URL,
        CREDENTIALS_IN_URL,
        BAD_PORT,
        UNSUPPORTED_SCHEME,
        INSECURE_NOT_ALLOWED,
        INSECURE_NEEDS_CONFIRMATION,
    }

    sealed interface Check {
        data class Ok(val url: String, val host: String, val insecure: Boolean) : Check
        data class Refused(val reason: Reason) : Check
    }

    fun check(url: String, kind: ConnectionKind, withCredentials: Boolean, confirmedInsecure: Boolean): Check {
        val parsed = parse(url)
        if (parsed is Invalid) return Check.Refused(parsed.reason)
        val address = parsed as Address
        if (address.scheme == "http") {
            if (kind != ConnectionKind.URL || withCredentials) return Check.Refused(Reason.INSECURE_NOT_ALLOWED)
            if (!confirmedInsecure) return Check.Refused(Reason.INSECURE_NEEDS_CONFIRMATION)
        }
        return Check.Ok(address.url, address.host, address.scheme == "http")
    }

    sealed interface Redirect {
        data class Follow(val url: String) : Redirect
        data class AskUser(val host: String) : Redirect
        data class Refuse(val error: ConnectionError) : Redirect
    }

    fun redirect(from: String, location: String, kind: ConnectionKind, followed: Int, acceptedRedirectHost: String?): Redirect {
        if (followed >= MAX_REDIRECTS) return Redirect.Refuse(ConnectionError.UNAVAILABLE)
        val origin = parse(from) as? Address ?: return Redirect.Refuse(ConnectionError.UNAVAILABLE)
        val target = parse(resolve(origin, location)) as? Address ?: return Redirect.Refuse(ConnectionError.UNAVAILABLE)
        if (origin.scheme == "https" && target.scheme == "http") return Redirect.Refuse(ConnectionError.INSECURE_REDIRECT)
        if (target.scheme == "http" && kind != ConnectionKind.URL) return Redirect.Refuse(ConnectionError.INSECURE_REDIRECT)
        if (origin.host == target.host) return Redirect.Follow(target.url)
        if (kind != ConnectionKind.URL) return Redirect.Refuse(ConnectionError.REDIRECT_HOST)
        if (target.host.equals(acceptedRedirectHost, ignoreCase = true)) return Redirect.Follow(target.url)
        return Redirect.AskUser(target.host)
    }

    fun host(url: String): String? = (parse(url, allowUserInfo = true) as? Address)?.host

    fun logLine(method: String, url: String, status: Int?, millis: Long, bytes: Int?): String =
        "${host(url) ?: "-"} $method ${status ?: "-"} ${millis}ms ${bytes?.let { "${it}B" } ?: "-"}"

    private sealed interface Parsed
    private data class Invalid(val reason: Reason) : Parsed
    private data class Address(val scheme: String, val name: String, val port: Int?, val suffix: String) : Parsed {
        val host: String get() = if (port == null) name else "$name:$port"
        val url: String get() = "$scheme://$host$suffix"
    }

    private fun parse(url: String, allowUserInfo: Boolean = false): Parsed {
        if (url.any { it.isWhitespace() || it.code < 32 || it.code == 127 || it == '\\' }) return Invalid(Reason.BAD_URL)
        val colon = url.indexOf(':')
        if (colon <= 0 || url[0] !in 'a'..'z' && url[0] !in 'A'..'Z') return Invalid(Reason.BAD_URL)
        val scheme = when (url.substring(0, colon).lowercase()) {
            "https", "webcal" -> "https"
            "http" -> "http"
            else -> return Invalid(Reason.UNSUPPORTED_SCHEME)
        }
        if (!url.startsWith("//", colon + 1)) return Invalid(Reason.BAD_URL)
        val start = colon + 3
        val end = url.indexOfAny(charArrayOf('/', '?', '#'), start).let { if (it < 0) url.length else it }
        var authority = url.substring(start, end)
        val userInfoEnd = authority.lastIndexOf('@')
        if (userInfoEnd >= 0) {
            if (!allowUserInfo) return Invalid(Reason.CREDENTIALS_IN_URL)
            authority = authority.substring(userInfoEnd + 1)
        }
        if (authority.isEmpty()) return Invalid(Reason.BAD_URL)
        val name: String
        val portText: String?
        if (authority.startsWith('[')) {
            val close = authority.indexOf(']')
            if (close < 2) return Invalid(Reason.BAD_URL)
            val literal = authority.substring(1, close)
            if (':' !in literal || literal.any { it !in "0123456789abcdefABCDEF:." }) return Invalid(Reason.BAD_URL)
            name = authority.substring(0, close + 1).lowercase()
            val remaining = authority.substring(close + 1)
            if (remaining.isNotEmpty() && !remaining.startsWith(':')) return Invalid(Reason.BAD_URL)
            portText = if (remaining.isEmpty()) null else remaining.substring(1)
        } else {
            val portStart = authority.indexOf(':')
            name = (if (portStart < 0) authority else authority.substring(0, portStart)).lowercase()
            if (name.isEmpty() || name.any { !it.isLetterOrDigit() && it != '.' && it != '-' && it != '_' }) return Invalid(Reason.BAD_URL)
            portText = if (portStart < 0) null else authority.substring(portStart + 1)
        }
        val port = if (portText == null) null else {
            if (portText.isEmpty() || portText.any { it !in '0'..'9' }) return Invalid(Reason.BAD_PORT)
            val number = portText.toIntOrNull() ?: return Invalid(Reason.BAD_PORT)
            if (number !in 1..65_535) return Invalid(Reason.BAD_PORT)
            number.takeUnless { it == if (scheme == "https") 443 else 80 }
        }
        return Address(scheme, name, port, url.substring(end))
    }

    private fun resolve(from: Address, location: String): String {
        val firstDelimiter = location.indexOfAny(charArrayOf('/', '?', '#')).let { if (it < 0) location.length else it }
        if (location.indexOf(':') in 0 until firstDelimiter) return location
        if (location.startsWith("//")) return "${from.scheme}:$location"
        val base = "${from.scheme}://${from.host}"
        if (location.isEmpty()) return base + from.suffix.substringBefore('#')
        if (location.startsWith('#')) return base + from.suffix.substringBefore('#') + location
        val fromPath = from.suffix.substringBefore('#').substringBefore('?')
        if (location.startsWith('?')) return base + fromPath + location
        val pathEnd = location.indexOfAny(charArrayOf('?', '#')).let { if (it < 0) location.length else it }
        val path = location.substring(0, pathEnd)
        val absolutePath = if (path.startsWith('/')) path else fromPath.substringBeforeLast('/', "") + "/" + path
        return base + removeDotSegments(absolutePath) + location.substring(pathEnd)
    }

    private fun removeDotSegments(path: String): String {
        val segments = mutableListOf<String>()
        for (segment in path.split('/')) {
            when (segment) {
                "." -> Unit
                ".." -> if (segments.size > 1) segments.removeAt(segments.lastIndex)
                else -> segments.add(segment)
            }
        }
        if (path.endsWith("/.") || path.endsWith("/..")) segments.add("")
        return segments.joinToString("/")
    }
}

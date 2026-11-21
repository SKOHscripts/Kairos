package com.skohscripts.kairos.core.connections

import com.skohscripts.kairos.core.calendar.CalendarImport
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.plus

/** Un calendrier distant proposé à l'utilisateur : [href] tel que le serveur l'écrit, [name] à afficher. */
data class RemoteCalendar(val href: String, val name: String)

/**
 * Réponses CalDAV (docs/spec/connexions-en-ligne.md § CalDAV), pures : lecture des `multistatus` par [CalDavXml]. Une réponse qui
 * n'est pas un `multistatus` lève [XmlException] (`truncated` vrai pour un document coupé).
 */
object CalDav {
    const val DAV = "DAV:"
    const val CALDAV = "urn:ietf:params:xml:ns:caldav"

    /** Une `response` : son `href`, son état global éventuel, et les propriétés de ses `propstat` en succès (2xx). */
    class Response(val href: String, val status: Int?, val ok: List<XmlElement>) {
        fun prop(ns: String, name: String): XmlElement? = ok.firstOrNull { it.ns == ns && it.name == name }
    }

    fun parseMultistatus(xml: String): List<Response> {
        val root = CalDavXml.parse(xml)
        if (root.ns != DAV || root.name != "multistatus") throw XmlException("not a multistatus")
        return root.children(DAV, "response").map { response ->
            val href = response.child(DAV, "href")?.text?.trim().orEmpty()
            val status = response.child(DAV, "status")?.text?.let(::statusCode)
            val ok = response.children(DAV, "propstat")
                .filter { statusCode(it.child(DAV, "status")?.text.orEmpty()) in 200..299 }
                .flatMap { it.child(DAV, "prop")?.children.orEmpty() }
            Response(href, status, ok)
        }
    }

    /** `HTTP/1.1 200 OK` → 200 ; illisible → 0. */
    fun statusCode(line: String): Int = line.trim().split(' ').getOrNull(1)?.toIntOrNull() ?: 0

    /** `current-user-principal` de la première réponse qui en a un (`unauthenticated` → nul). */
    fun principal(xml: String): String? = parseMultistatus(xml).firstNotNullOfOrNull { response ->
        response.prop(DAV, "current-user-principal")?.child(DAV, "href")?.text?.trim()?.takeIf { it.isNotEmpty() }
    }

    /** Premier `calendar-home-set`. */
    fun homeSet(xml: String): String? = parseMultistatus(xml).firstNotNullOfOrNull { response ->
        response.prop(CALDAV, "calendar-home-set")?.child(DAV, "href")?.text?.trim()?.takeIf { it.isNotEmpty() }
    }

    /**
     * Calendriers d'un `PROPFIND` profondeur 1 : `resourcetype` contient `calendar` et `supported-calendar-component-set` est absent,
     * vide ou contient `VEVENT`. Nom : `displayname`, sinon le dernier segment du chemin, décodé.
     */
    fun calendars(xml: String): List<RemoteCalendar> = parseMultistatus(xml).mapNotNull { response ->
        val isCalendar = response.prop(DAV, "resourcetype")?.has(CALDAV, "calendar") == true
        val components = response.prop(CALDAV, "supported-calendar-component-set")?.children(CALDAV, "comp")
            ?.mapNotNull { it.attributes["name"]?.uppercase() }.orEmpty()
        if (!isCalendar || response.href.isEmpty() || (components.isNotEmpty() && "VEVENT" !in components)) return@mapNotNull null
        val name = response.prop(DAV, "displayname")?.text?.trim()?.takeIf { it.isNotEmpty() }
            ?: percentDecode(response.href.trimEnd('/').substringAfterLast('/')).ifEmpty { response.href }
        RemoteCalendar(response.href, name)
    }

    /** Les `calendar-data` d'un `REPORT` ; une réponse en échec (état global hors 2xx) est ignorée. */
    fun calendarData(xml: String): List<String> = parseMultistatus(xml).mapNotNull { response ->
        if (response.status != null && response.status !in 200..299) return@mapNotNull null
        response.prop(CALDAV, "calendar-data")?.text?.takeIf { it.isNotBlank() }
    }

    /** Décodage `%XX` en UTF-8 ; une séquence invalide reste telle quelle. */
    fun percentDecode(text: String): String {
        if ('%' !in text) return text
        val bytes = ArrayList<Byte>(text.length)
        var k = 0
        while (k < text.length) {
            val c = text[k]
            val hex = if (c == '%' && k + 2 < text.length) text.substring(k + 1, k + 3).toIntOrNull(16) else null
            if (hex != null) {
                bytes += hex.toByte()
                k += 3
            } else {
                c.toString().encodeToByteArray().forEach { bytes += it }
                k++
            }
        }
        return bytes.toByteArray().decodeToString()
    }
}

/** Corps des requêtes CalDAV (docs/spec/connexions-en-ligne.md § CalDAV). */
object CalDavQueries {
    private const val HEAD = "<?xml version=\"1.0\" encoding=\"utf-8\"?>"

    const val PRINCIPAL = "$HEAD<D:propfind xmlns:D=\"DAV:\"><D:prop><D:current-user-principal/></D:prop></D:propfind>"

    const val HOME_SET = "$HEAD<D:propfind xmlns:D=\"DAV:\" xmlns:C=\"urn:ietf:params:xml:ns:caldav\">" +
        "<D:prop><C:calendar-home-set/></D:prop></D:propfind>"

    const val CALENDARS = "$HEAD<D:propfind xmlns:D=\"DAV:\" xmlns:C=\"urn:ietf:params:xml:ns:caldav\">" +
        "<D:prop><D:resourcetype/><D:displayname/><C:supported-calendar-component-set/></D:prop></D:propfind>"

    /** `calendar-query` des `VEVENT` de `today − 1 jour` à `today + 6 mois` (minuit UTC), `calendar-data` brut, sans `expand`. */
    fun calendarQuery(today: LocalDate): String {
        val start = stamp(today.minus(DatePeriod(days = 1)))
        val end = stamp(today.plus(DatePeriod(months = CalendarImport.HORIZON_MONTHS)))
        return "$HEAD<C:calendar-query xmlns:D=\"DAV:\" xmlns:C=\"urn:ietf:params:xml:ns:caldav\">" +
            "<D:prop><C:calendar-data/></D:prop><C:filter><C:comp-filter name=\"VCALENDAR\"><C:comp-filter name=\"VEVENT\">" +
            "<C:time-range start=\"$start\" end=\"$end\"/></C:comp-filter></C:comp-filter></C:filter></C:calendar-query>"
    }

    /** `2026-11-20` → `20261120T000000Z` (forme ISO de `LocalDate`, sans tirets). */
    private fun stamp(date: LocalDate): String = date.toString().replace("-", "") + "T000000Z"
}

/**
 * Rassemble les objets `VCALENDAR` d'un `REPORT` en un seul calendrier (docs/spec/connexions-en-ligne.md § CalDAV) : en-tête
 * `VERSION`, chaque `VTIMEZONE` une fois par `TZID`, puis les autres composants dans l'ordre reçu. Le résultat se lit par
 * `CalendarImport.plan` comme un fichier ; zéro objet donne un calendrier vide. Un composant non fermé est écarté.
 */
object CalDavMerge {
    fun merge(objects: List<String>): String {
        val zones = LinkedHashMap<String, List<String>>()
        val components = mutableListOf<List<String>>()
        for (text in objects) {
            val lines = text.removePrefix("﻿").split('\n').map { it.removeSuffix("\r") }.filter { it.isNotEmpty() }
            var inCalendar = false
            var k = 0
            while (k < lines.size) {
                val line = lines[k]
                val upper = line.uppercase()
                when {
                    !inCalendar -> if (upper == "BEGIN:VCALENDAR") inCalendar = true
                    upper == "END:VCALENDAR" -> inCalendar = false
                    upper.startsWith("BEGIN:") -> {
                        val type = upper.removePrefix("BEGIN:")
                        val end = closing(lines, k, type) ?: break
                        val component = lines.subList(k, end + 1).toList()
                        if (type == "VTIMEZONE") {
                            val tzid = component.firstOrNull { it.uppercase().startsWith("TZID:") || it.uppercase().startsWith("TZID;") }
                                ?.substringAfter(':')?.trim().orEmpty()
                            if (tzid !in zones) zones[tzid] = component
                        } else {
                            components += component
                        }
                        k = end
                    }
                }
                k++
            }
        }
        return buildString {
            append("BEGIN:VCALENDAR\r\nVERSION:2.0\r\nPRODID:-//Kairos//CalDAV//EN\r\n")
            (zones.values + components).forEach { component -> component.forEach { append(it).append("\r\n") } }
            append("END:VCALENDAR\r\n")
        }
    }

    /** Index de la ligne `END:<type>` qui ferme le composant ouvert en [start], en comptant les composants de même type imbriqués. */
    private fun closing(lines: List<String>, start: Int, type: String): Int? {
        var depth = 0
        for (k in start until lines.size) {
            val upper = lines[k].uppercase()
            if (upper == "BEGIN:$type") depth++
            if (upper == "END:$type") {
                depth--
                if (depth == 0) return k
            }
        }
        return null
    }
}

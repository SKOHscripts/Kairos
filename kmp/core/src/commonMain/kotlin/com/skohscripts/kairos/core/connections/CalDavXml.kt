package com.skohscripts.kairos.core.connections

/**
 * Un élément XML lu par [CalDavXml] : [ns] est l'URI de son espace de noms (vide sans espace), [name] son nom local, [text] le
 * texte de ses nœuds texte et `CDATA` directs, mis bout à bout (le texte des enfants n'y est pas).
 */
class XmlElement(
    val ns: String,
    val name: String,
    val attributes: Map<String, String>,
    val children: List<XmlElement>,
    val text: String,
) {
    fun child(ns: String, name: String): XmlElement? = children.firstOrNull { it.ns == ns && it.name == name }

    fun children(ns: String, name: String): List<XmlElement> = children.filter { it.ns == ns && it.name == name }

    fun has(ns: String, name: String): Boolean = child(ns, name) != null
}

/** Document refusé ; [truncated] : le texte s'arrête avant la fin du document (réponse coupée). */
class XmlException(message: String, val truncated: Boolean = false) : IllegalArgumentException(message)

/**
 * Lecteur XML minimal des réponses WebDAV (docs/spec/connexions-en-ligne.md § CalDAV), pur et commun à toutes les cibles (aucun
 * analyseur de plateforme n'existe en wasm). Lit éléments, attributs, espaces de noms (préfixes libres, espace par défaut), texte,
 * `CDATA`, les cinq entités prédéfinies et les références numériques. **Refuse** `<!DOCTYPE`, toute autre entité, une profondeur
 * au-delà de [MAX_DEPTH] et un document de plus de [MAX_CHARS] caractères : aucune lecture externe, ni XXE ni explosion d'entités.
 */
object CalDavXml {
    const val MAX_DEPTH = 32
    const val MAX_CHARS = 16 * 1024 * 1024

    fun parse(text: String): XmlElement {
        if (text.length > MAX_CHARS) throw XmlException("document too large")
        return Reader(text).document()
    }

    private class Reader(private val s: String) {
        private var i = 0

        fun document(): XmlElement {
            if (s.startsWith("﻿")) i = 1
            skipMisc()
            if (i >= s.length) throw XmlException("no root element", truncated = true)
            if (s.startsWith("<!DOCTYPE", i, ignoreCase = true) || s.startsWith("<!ENTITY", i, ignoreCase = true)) {
                throw XmlException("DOCTYPE refused")
            }
            if (s[i] != '<') throw XmlException("text before root")
            val root = element(emptyMap(), 1)
            skipMisc()
            if (i < s.length) throw XmlException("content after root")
            return root
        }

        /** Espaces, déclaration XML, instructions de traitement et commentaires hors de l'élément racine. */
        private fun skipMisc() {
            while (i < s.length) {
                when {
                    s[i].isWhitespace() -> i++
                    s.startsWith("<?", i) -> skipPast("?>")
                    s.startsWith("<!--", i) -> skipPast("-->")
                    else -> return
                }
            }
        }

        private fun skipPast(end: String) {
            val at = s.indexOf(end, i)
            if (at < 0) throw XmlException("unterminated markup", truncated = true)
            i = at + end.length
        }

        private fun element(parentScope: Map<String, String>, depth: Int): XmlElement {
            if (depth > MAX_DEPTH) throw XmlException("too deep")
            i++ // '<'
            val qName = name()
            val rawAttributes = mutableListOf<Pair<String, String>>()
            while (true) {
                skipSpaces()
                if (i >= s.length) throw XmlException("unterminated tag", truncated = true)
                when {
                    s.startsWith("/>", i) || s[i] == '>' -> break
                    else -> {
                        val attrName = name()
                        skipSpaces()
                        expect('=')
                        skipSpaces()
                        rawAttributes += attrName to quoted()
                    }
                }
            }
            val scope = parentScope.toMutableMap()
            for ((attrName, value) in rawAttributes) {
                when {
                    attrName == "xmlns" -> scope[""] = value
                    attrName.startsWith("xmlns:") -> scope[attrName.removePrefix("xmlns:")] = value
                }
            }
            val (ns, local) = resolve(qName, scope, element = true)
            val attributes = rawAttributes.filterNot { it.first == "xmlns" || it.first.startsWith("xmlns:") }
                .associate { (attrName, value) -> resolve(attrName, scope, element = false).second to value }
            if (s.startsWith("/>", i)) {
                i += 2
                return XmlElement(ns, local, attributes, emptyList(), "")
            }
            i++ // '>'
            val children = mutableListOf<XmlElement>()
            val text = StringBuilder()
            while (true) {
                if (i >= s.length) throw XmlException("unterminated element <$qName>", truncated = true)
                when {
                    s.startsWith("</", i) -> {
                        i += 2
                        val closing = name()
                        skipSpaces()
                        expect('>')
                        if (closing != qName) throw XmlException("mismatched </$closing>")
                        return XmlElement(ns, local, attributes, children, text.toString())
                    }
                    s.startsWith("<![CDATA[", i) -> {
                        val end = s.indexOf("]]>", i + 9)
                        if (end < 0) throw XmlException("unterminated CDATA", truncated = true)
                        text.append(s, i + 9, end)
                        i = end + 3
                    }
                    s.startsWith("<!--", i) -> skipPast("-->")
                    s.startsWith("<?", i) -> skipPast("?>")
                    s.startsWith("<!", i) -> throw XmlException("declaration refused")
                    s[i] == '<' -> children += element(scope, depth + 1)
                    else -> {
                        val next = s.indexOf('<', i).let { if (it < 0) s.length else it }
                        text.append(decode(s.substring(i, next)))
                        i = next
                    }
                }
            }
        }

        private fun resolve(qName: String, scope: Map<String, String>, element: Boolean): Pair<String, String> {
            val colon = qName.indexOf(':')
            if (colon < 0) return (if (element) scope[""].orEmpty() else "") to qName
            val prefix = qName.substring(0, colon)
            val uri = if (prefix == "xml") "http://www.w3.org/XML/1998/namespace" else scope[prefix]
                ?: throw XmlException("unbound prefix $prefix")
            return uri to qName.substring(colon + 1)
        }

        private fun name(): String {
            val start = i
            while (i < s.length && (s[i].isLetterOrDigit() || s[i] in "_-.:")) i++
            if (i >= s.length) throw XmlException("unterminated name", truncated = true)
            if (i == start) throw XmlException("name expected")
            return s.substring(start, i)
        }

        private fun quoted(): String {
            if (i >= s.length) throw XmlException("value expected", truncated = true)
            val quote = s[i]
            if (quote != '"' && quote != '\'') throw XmlException("quoted value expected")
            val end = s.indexOf(quote, i + 1)
            if (end < 0) throw XmlException("unterminated value", truncated = true)
            val raw = s.substring(i + 1, end)
            if ('<' in raw) throw XmlException("< in attribute")
            i = end + 1
            return decode(raw)
        }

        private fun skipSpaces() {
            while (i < s.length && s[i].isWhitespace()) i++
        }

        private fun expect(c: Char) {
            if (i >= s.length) throw XmlException("'$c' expected", truncated = true)
            if (s[i] != c) throw XmlException("'$c' expected")
            i++
        }

        /** Entités prédéfinies et références numériques ; toute autre entité est refusée. Fins de ligne normalisées (XML 1.0 § 2.11). */
        private fun decode(raw: String): String {
            val normalized = raw.replace("\r\n", "\n").replace('\r', '\n')
            if ('&' !in normalized) return normalized
            val out = StringBuilder(normalized.length)
            var k = 0
            while (k < normalized.length) {
                val c = normalized[k]
                if (c != '&') {
                    out.append(c)
                    k++
                    continue
                }
                val end = normalized.indexOf(';', k)
                if (end < 0 || end - k > 12) throw XmlException("bad entity")
                val entity = normalized.substring(k + 1, end)
                when {
                    entity == "lt" -> out.append('<')
                    entity == "gt" -> out.append('>')
                    entity == "amp" -> out.append('&')
                    entity == "quot" -> out.append('"')
                    entity == "apos" -> out.append('\'')
                    entity.startsWith("#x") || entity.startsWith("#X") -> out.appendCodePoint(entity.substring(2).toIntOrNull(16))
                    entity.startsWith("#") -> out.appendCodePoint(entity.substring(1).toIntOrNull())
                    else -> throw XmlException("entity &$entity; refused")
                }
                k = end + 1
            }
            return out.toString()
        }

        private fun StringBuilder.appendCodePoint(code: Int?) {
            if (code == null || code <= 0 || code > 0x10FFFF || code in 0xD800..0xDFFF) throw XmlException("bad character reference")
            if (code < 0x10000) {
                append(code.toChar())
            } else {
                val v = code - 0x10000
                append((0xD800 + (v shr 10)).toChar())
                append((0xDC00 + (v and 0x3FF)).toChar())
            }
        }
    }
}

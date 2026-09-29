package com.skohscripts.kairos.core.notes

/**
 * Tâche issue d'une note (docs/spec-v3/notes-capture.md § Conversion), règle
 * de Kairos 2 (issue #32), strictement conservative : titre = première ligne
 * non blanche (sans ses espaces de bord), tronquée à [TITLE_MAX] caractères ;
 * description = tout ce qui suit, tel quel, moins les lignes entièrement
 * blanches de tête et de queue. Une première ligne tronquée est reprise en
 * entier en tête de la description. Corps vide : `("", "")`.
 */
object NoteConversion {
    const val TITLE_MAX = 200

    fun fields(body: String): Pair<String, String> {
        val lines = body.lines()
        val first = lines.indexOfFirst { it.isNotBlank() }
        if (first < 0) return "" to ""
        val firstLine = lines[first].trim()
        var rest = lines.drop(first + 1)
        rest = rest.dropWhile { it.isBlank() }.dropLastWhile { it.isBlank() }
        var description = rest.joinToString("\n")
        if (firstLine.length > TITLE_MAX) description = if (description.isNotEmpty()) "$firstLine\n\n$description" else firstLine
        return firstLine.take(TITLE_MAX) to description
    }
}

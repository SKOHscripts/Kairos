package com.skohscripts.kairos.core.text

/**
 * Recherche sans casse ni accents (docs/spec/liste-fonctionnalites.md § Écran) : « reunion » trouve « Réunion ». Pur et commun à
 * toutes les plateformes : pas de `java.text.Normalizer` sur le web, d'où une table des lettres accentuées des six langues de
 * l'interface (`ё` comptée comme `е` en russe, `ß` comme `ss`, ligatures `œ` et `æ` dépliées).
 */
object SearchFold {
    private val TABLE: Map<Char, String> = buildMap {
        fun map(letters: String, base: String) = letters.forEach { put(it, base) }
        map("àáâãäåā", "a")
        map("çćč", "c")
        map("èéêëēė", "e")
        map("ìíîïī", "i")
        map("ñń", "n")
        map("òóôõöøō", "o")
        map("ùúûüū", "u")
        map("ýÿ", "y")
        put('œ', "oe")
        put('æ', "ae")
        put('ß', "ss")
        put('ё', "е")
    }

    /** [text] en minuscules, sans accents. */
    fun fold(text: String): String = buildString(text.length) {
        for (c in text.lowercase()) append(TABLE[c] ?: c.toString())
    }

    /** Vrai si chaque mot de [query] (séparés par des espaces) est contenu dans [text], sans casse ni accents ; vrai pour une requête vide. */
    fun matches(query: String, text: String): Boolean {
        val haystack = fold(text)
        return fold(query).split(' ', '\t').filter { it.isNotEmpty() }.all { it in haystack }
    }
}

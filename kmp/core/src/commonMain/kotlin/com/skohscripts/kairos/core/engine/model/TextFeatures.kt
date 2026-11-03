package com.skohscripts.kairos.core.engine.model

/**
 * Caractéristiques textuelles d'un titre (docs/spec/estimateur-modele.md § Le modèle, 1) : mots d'au moins 2 caractères et
 * trigrammes de caractères des mots d'au moins 3 caractères, entourés de bornes (`^ra`, `rap`, …, `rt$`). Normalisation écrite
 * à la main, identique sur toutes les cibles : minuscules (`lowercaseChar`), repli explicite des diacritiques des langues de
 * l'application et du « ё » russe, toute suite de chiffres réduite à un seul `0`, tout ce qui n'est ni lettre ni chiffre
 * devient un séparateur. Ni lemmatiseur ni liste de mots vides : l'idf de [ModelIndex] fait ce travail.
 */
object TextFeatures {
    data class Features(val words: List<String>, val trigrams: List<String>) {
        val isEmpty: Boolean get() = words.isEmpty() && trigrams.isEmpty()
    }

    /** Repli explicite (pas de `java.text.Normalizer`, absent de `common`) ; une lettre peut donner deux lettres (ß → ss). */
    private val FOLD: Map<Char, String> = buildMap {
        for (c in "àáâãäå") put(c, "a")
        put('ç', "c")
        for (c in "èéêë") put(c, "e")
        for (c in "ìíîï") put(c, "i")
        put('ñ', "n")
        for (c in "òóôõöø") put(c, "o")
        for (c in "ùúûü") put(c, "u")
        for (c in "ýÿ") put(c, "y")
        put('ß', "ss")
        put('œ', "oe")
        put('æ', "ae")
        put('ё', "е")
    }

    /** Les mots normalisés de [title], dans l'ordre (doublons compris). */
    fun words(title: String): List<String> {
        val out = ArrayList<String>()
        val word = StringBuilder()
        var inDigits = false
        fun flush() {
            if (word.isNotEmpty()) out.add(word.toString())
            word.clear()
            inDigits = false
        }
        for (raw in title) {
            val c = raw.lowercaseChar()
            when {
                // Accent décomposé (forme NFD, U+0300 à U+036F) : ignoré, le mot continue (« re\u0301diger » = « rediger »).
                c in '\u0300'..'\u036F' -> Unit
                c in '0'..'9' -> {
                    if (!inDigits) word.append('0')
                    inDigits = true
                }
                c.isLetterOrDigit() -> {
                    word.append(FOLD[c] ?: c.toString())
                    inDigits = false
                }
                else -> flush()
            }
        }
        flush()
        return out
    }

    /** Mots (≥ 2 caractères) et trigrammes bornés (mots ≥ 3 caractères), sans doublon, triés : présence seulement. */
    fun of(title: String): Features {
        val all = words(title)
        val words = all.filter { it.length >= 2 }.distinct().sorted()
        val trigrams = ArrayList<String>()
        for (w in all) {
            if (w.length < 3) continue
            val bounded = "^$w$"
            for (i in 0..bounded.length - 3) trigrams.add(bounded.substring(i, i + 3))
        }
        return Features(words, trigrams.distinct().sorted())
    }
}

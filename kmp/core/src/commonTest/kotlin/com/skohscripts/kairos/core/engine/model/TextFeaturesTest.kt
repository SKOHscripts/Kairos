package com.skohscripts.kairos.core.engine.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Normalisation et caractéristiques du titre (docs/spec/estimateur-modele.md § Le modèle, 1). */
class TextFeaturesTest {
    @Test
    fun accentsCaseDigitsAndPunctuationAreFolded() {
        assertEquals(listOf("rediger", "le", "rapport", "t0"), TextFeatures.words("Rédiger LE rapport T3 !"))
        // Toute suite de chiffres devient un seul 0 : « semaine 42 » et « semaine 7 » se confondent.
        assertEquals(TextFeatures.words("semaine 7"), TextFeatures.words("Semaine 42"))
        assertEquals(listOf("strasse", "noel", "oeuvre", "nino", "елка"), TextFeatures.words("Straße — Noël, œuvre/niño Ёлка"))
    }

    @Test
    fun wordsOfTwoAndTrigramsOfThreeLettersOrMore() {
        val f = TextFeatures.of("a de Rapport")
        assertEquals(listOf("de", "rapport"), f.words)
        assertEquals(listOf("^ra", "app", "ort", "por", "ppo", "rap", "rt$"), f.trigrams)
        assertTrue(TextFeatures.of("!? —").isEmpty)
    }

    @Test
    fun trigramsBringPluralsAndRelatedWordsTogether() {
        val shared = TextFeatures.of("rapports").trigrams.intersect(TextFeatures.of("rapport").trigrams.toSet())
        assertTrue(shared.size >= 5, "$shared")
    }
}

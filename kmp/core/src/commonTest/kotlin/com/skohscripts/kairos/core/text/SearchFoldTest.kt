package com.skohscripts.kairos.core.text

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Recherche de la liste des fonctionnalités (docs/spec/liste-fonctionnalites.md § Écran). */
class SearchFoldTest {
    @Test
    fun accentsAndCaseAreIgnored() {
        assertTrue(SearchFold.matches("reunion", "Réunion d’équipe"))
        assertTrue(SearchFold.matches("REUNION", "réunion"))
        assertTrue(SearchFold.matches("Équipe", "equipe"))
        assertTrue(SearchFold.matches("strasse", "Straße"))
        assertTrue(SearchFold.matches("oeuvre", "Œuvre"))
        assertTrue(SearchFold.matches("елка", "Ёлка"))
        assertTrue(SearchFold.matches("anadir", "Añadir"))
        assertEquals("cafe", SearchFold.fold("Café"))
    }

    @Test
    fun everyWordMustBeFoundAnEmptyQueryMatchesAll() {
        assertTrue(SearchFold.matches("deep  work", "Réserve un créneau de deep work"))
        assertFalse(SearchFold.matches("deep chrono", "Réserve un créneau de deep work"))
        assertTrue(SearchFold.matches("", "n’importe quoi"))
        assertTrue(SearchFold.matches("   ", "n’importe quoi"))
    }
}

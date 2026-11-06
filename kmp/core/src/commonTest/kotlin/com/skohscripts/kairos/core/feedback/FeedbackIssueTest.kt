package com.skohscripts.kairos.core.feedback

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Adresse préremplie de « Donner un avis » (docs/spec/retours.md § Critères de succès). */
class FeedbackIssueTest {
    private companion object {
        const val V = "4.1.0"
        const val P = "Windows"
    }

    @Test
    fun buildsThePrefilledIssueUrl() {
        assertEquals(
            "https://github.com/SKOHscripts/Kairos/issues/new?template=feedback.yml&title=Un%20titre" +
                "&context=Le%20matin%0Asur%20Android&need=Voir%20la%20semaine%20%26%20le%20mois%20%3F" +
                "&version=4.1.0-beta.2&platform=Android",
            FeedbackIssue.url("  Un titre ", "Le matin\nsur Android", "Voir la semaine & le mois ?\n", "4.1.0-beta.2", "Android"),
        )
    }

    @Test
    fun omitsAnEmptyContext() {
        assertEquals(
            "https://github.com/SKOHscripts/Kairos/issues/new?template=feedback.yml&title=T&need=N&platform=Web",
            FeedbackIssue.url("T", "  \n ", "N", "", "Web"),
        )
    }

    @Test
    fun noUrlWithoutTitleOrNeed() {
        assertNull(FeedbackIssue.url(" ", "contexte", "besoin", V, P))
        assertNull(FeedbackIssue.url("titre", "contexte", "\n", V, P))
    }

    @Test
    fun encodesUtf8BytesAndKeepsUnreserved() {
        assertEquals("Az09-._~", FeedbackIssue.encode("Az09-._~"))
        assertEquals("%C3%A9t%C3%A9%20%E2%80%99", FeedbackIssue.encode("été ’"))
        assertEquals("%D0%B4%2B%2F", FeedbackIssue.encode("д+/"))
        assertEquals("%F0%9F%99%82", FeedbackIssue.encode("🙂"))
    }

    @Test
    fun theLimitCountsTheLoginWrappedUrl() {
        // Lettres seules : l'adresse ré-encodée ne grandit que par ses séparateurs (`?`, `=`, `&`, `/`, `:`).
        val base = FeedbackIssue.url("t", "", "x", V, P)!!
        val overhead = FeedbackIssue.LOGIN_PREFIX.length + FeedbackIssue.encode(base).length - 1
        val longest = "x".repeat(FeedbackIssue.MAX_LOGIN_URL - overhead)
        assertTrue(FeedbackIssue.fits(FeedbackIssue.url("t", "", longest, V, P)!!))
        assertFalse(FeedbackIssue.fits(FeedbackIssue.url("t", "", longest + "x", V, P)!!))
    }

    @Test
    fun accentedAndCyrillicTextCountsItsEncodedBytes() {
        // « é » : 2 octets → `%C3%A9` → `%25C3%25A9` une fois ré-encodé, soit 10 caractères par lettre.
        assertTrue(FeedbackIssue.fits(FeedbackIssue.url("t", "", "é".repeat(700), V, P)!!))
        assertFalse(FeedbackIssue.fits(FeedbackIssue.url("t", "", "é".repeat(800), V, P)!!))
        assertFalse(FeedbackIssue.fits(FeedbackIssue.url("t", "", "д".repeat(800), V, P)!!))
        // Ordre de grandeur annoncé par la spec : 2 000 caractères de français courant passent.
        val french = "Je voudrais voir la semaine entière, avec les créneaux et les échéances. ".repeat(28).take(2000)
        assertTrue(FeedbackIssue.fits(FeedbackIssue.url("Idée", "", french, V, P)!!))
    }
}

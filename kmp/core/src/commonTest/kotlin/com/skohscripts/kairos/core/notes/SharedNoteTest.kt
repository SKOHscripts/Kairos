package com.skohscripts.kairos.core.notes

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Corps d'une note partagée depuis Android (docs/spec/partage-android.md, issue #293). */
class SharedNoteTest {
    @Test
    fun titleThenTextAndTextAloneWithoutTitle() {
        assertEquals("Comment réparer une fuite\nhttps://exemple.org/fuite", SharedNote.body("Comment réparer une fuite", "https://exemple.org/fuite"))
        assertEquals("https://exemple.org/fuite", SharedNote.body(null, "https://exemple.org/fuite"))
        assertEquals("https://exemple.org/fuite", SharedNote.body("  ", " https://exemple.org/fuite \n"))
        assertEquals("Titre seul", SharedNote.body("Titre seul", null))
    }

    @Test
    fun aTitleAlreadyInTheTextIsNotRepeated() {
        assertEquals("Titre de l'article https://x.org", SharedNote.body("titre de l'article", "Titre de l'article https://x.org"))
    }

    @Test
    fun theTitleHoldsOnOneLineAndInnerLinesAreKept() {
        assertEquals("Un titre sur deux lignes\nligne 1\n\nligne 2", SharedNote.body(" Un titre\n sur  deux lignes ", "ligne 1\n\nligne 2"))
    }

    @Test
    fun anEmptyShareGivesNothing() {
        assertEquals("", SharedNote.body(null, null))
        assertEquals("", SharedNote.body(" \n", "\t"))
    }

    @Test
    fun aLongShareIsCutWithAnEllipsisNeverInsideASurrogatePair() {
        val long = SharedNote.body(null, "a".repeat(20_000))
        assertEquals(SharedNote.MAX_BODY, long.length)
        assertTrue(long.endsWith("…"))
        // Un émoji (deux unités UTF-16) à cheval sur la coupe est retiré en entier.
        val emoji = SharedNote.body(null, "a".repeat(SharedNote.MAX_BODY - 2) + "😀" + "b".repeat(10))
        assertEquals("a".repeat(SharedNote.MAX_BODY - 2) + "…", emoji)
    }

    @Test
    fun aSharedArticleBecomesATaskTitledAfterIt() {
        val body = SharedNote.body("Comment réparer une fuite", "https://exemple.org/fuite")
        assertEquals("Comment réparer une fuite" to "https://exemple.org/fuite", NoteConversion.fields(body))
    }
}

package com.skohscripts.kairos.core.notes

import kotlin.test.Test
import kotlin.test.assertEquals

/** Règle de conversion d'une note en tâche (issue #32 de Kairos 2). */
class NoteConversionTest {
    @Test
    fun first_non_blank_line_is_the_title_rest_is_the_description() {
        assertEquals("Appeler Paul" to "", NoteConversion.fields("  Appeler Paul  "))
        assertEquals("Idée" to "  - point 1\n\n  - point 2", NoteConversion.fields("\n\n Idée \n\n  - point 1\n\n  - point 2\n\n"))
        assertEquals("" to "", NoteConversion.fields(" \n\t\n"))
    }

    @Test
    fun long_first_line_is_kept_whole_in_the_description() {
        val long = "x".repeat(250)
        assertEquals("x".repeat(200) to long, NoteConversion.fields(long))
        assertEquals("x".repeat(200) to "$long\n\nsuite", NoteConversion.fields("$long\nsuite"))
    }
}

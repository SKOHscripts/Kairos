package com.skohscripts.kairos.ui

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Chaque langue a exactement les mêmes clés, sans valeur vide
 * (docs/spec/i18n.md § Invariants) : une chaîne ajoutée en français
 * l'est aussi en anglais, et inversement.
 */
class StringsParityTest {
    private val resources = File("src/commonMain/composeResources")
    private val stringPattern = Regex("""<string name="([^"]+)">(.*?)</string>""", RegexOption.DOT_MATCHES_ALL)

    private fun strings(folder: String): Map<String, String> =
        stringPattern.findAll(File(resources, "$folder/strings.xml").readText())
            .associate { it.groupValues[1] to it.groupValues[2].trim() }

    @Test
    fun frenchAndEnglishHaveTheSameKeys() {
        val fr = strings("values")
        val en = strings("values-en")
        assertTrue(fr.isNotEmpty())
        assertEquals(fr.keys.sorted(), en.keys.sorted())
    }

    @Test
    fun noEmptyTranslation() {
        for (folder in listOf("values", "values-en")) {
            val empty = strings(folder).filterValues { it.isEmpty() }.keys
            assertTrue(empty.isEmpty(), "$folder : chaînes vides $empty")
        }
    }

    @Test
    fun noAndroidStyleEscapes() {
        // Les ressources Compose n'interprètent pas `\'` ni `\"` : ils s'afficheraient
        // tels quels (« Aujourd\'hui »). Apostrophe typographique ’ à la place.
        for (folder in listOf("values", "values-en")) {
            val escaped = strings(folder).filterValues { "\\'" in it || "\\\"" in it }.keys
            assertTrue(escaped.isEmpty(), "$folder : échappements Android $escaped")
        }
    }

    @Test
    fun placeholdersMatchAcrossLanguages() {
        val placeholder = Regex("""%\d+\$[sd]""")
        val fr = strings("values")
        val en = strings("values-en")
        for ((key, value) in fr) {
            assertEquals(
                placeholder.findAll(value).map { it.value }.toSet(),
                placeholder.findAll(en.getValue(key)).map { it.value }.toSet(),
                key,
            )
        }
    }
}

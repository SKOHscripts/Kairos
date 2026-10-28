package com.skohscripts.kairos.ui

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Les seules ressources Android de l'application (noms et descriptions des widgets dans le sélecteur du système,
 * docs/spec/widgets-android.md § Textes) ont les mêmes clés dans les six langues, sans valeur vide ni apostrophe droite.
 * Lues depuis `androidApp`, que ce module ne compile pas (le test tourne sur la JVM, sans SDK Android).
 */
class AndroidStringsParityTest {
    private val res = File("../androidApp/src/main/res")
    private val stringPattern = Regex("""<string name="([^"]+)">(.*?)</string>""", RegexOption.DOT_MATCHES_ALL)
    private val translated = listOf("values-fr", "values-es", "values-de", "values-ru", "values-it")

    private fun strings(folder: String): Map<String, String> =
        stringPattern.findAll(File(res, "$folder/strings.xml").readText()).associate { it.groupValues[1] to it.groupValues[2].trim() }

    @Test
    fun everyLanguageHasTheSameKeysAsEnglish() {
        val en = strings("values")
        assertTrue(en.isNotEmpty())
        for (folder in translated) assertEquals(en.keys.sorted(), strings(folder).keys.sorted(), folder)
    }

    @Test
    fun noEmptyValueNorStraightApostrophe() {
        for (folder in listOf("values") + translated) {
            val bad = strings(folder).filterValues { it.isEmpty() || '\'' in it }.keys
            assertTrue(bad.isEmpty(), "$folder : $bad")
        }
    }
}

package com.skohscripts.kairos.core

import com.skohscripts.kairos.core.model.BlockKind
import com.skohscripts.kairos.core.model.BlockRecurrence
import com.skohscripts.kairos.core.model.Settings
import com.skohscripts.kairos.core.settings.UiLanguage
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant

/** Jeu d'exemples (docs/spec/modele-donnees.md § Exemples) et sa traduction. */
class ExampleDataTest {
    private val today = LocalDate(2026, 9, 28)
    private val now = Instant.parse("2026-09-28T07:00:00Z")
    private val fr = ExampleData.snapshot(today, now, "fr")

    @Test
    fun anExampleIsRecognisedInEveryLanguageAndAUserTaskIsNot() {
        assertTrue(fr.tasks.all(ExampleData::isExample))
        for (language in UiLanguage.SUPPORTED) assertTrue(ExampleData.snapshot(today, now, language).tasks.all(ExampleData::isExample), language)
        assertTrue(fr.tasks.first().copy(title = "Relever le courrier").let { !ExampleData.isExample(it) })
    }

    @Test
    fun everyExampleIsTaggedAndPrefixed() {
        assertEquals(10, fr.tasks.size)
        assertTrue(fr.tasks.all { it.projectTag == "Exemple" && it.title.startsWith("[Exemple] ") })
        assertTrue(fr.timeBlocks.all { it.title.startsWith("[Exemple] ") })
        assertTrue(fr.notes.single().body.startsWith("[Exemple] "))
    }

    @Test
    fun coversTheShowcasedFeatures() {
        // Une P0 à échéance proche, une épinglée à 9h30, une mère et deux filles,
        // une dépendance, une tâche à traiter, une programmée plus tard.
        assertTrue(fr.tasks.any { it.priority == 0 && it.deadline == LocalDate(2026, 9, 30) })
        assertEquals(1, fr.tasks.count { it.pinnedStart?.hour == 9 && it.pinnedStart?.minute == 30 })
        val parent = fr.tasks.single { it.title.endsWith("Rédiger la documentation") }
        assertEquals(2, fr.tasks.count { it.parentId == parent.id })
        val dep = fr.dependencies.single()
        assertTrue(fr.tasks.single { it.id == dep.taskId }.title.endsWith("Déployer en production"))
        assertTrue(fr.tasks.single { it.id == dep.blockerId }.title.endsWith("Obtenir la validation du client"))
        assertEquals(1, fr.tasks.count { it.needsProcessing(fr.settings.typeRequired) })
        assertEquals(1, fr.tasks.count { it.scheduledDate == LocalDate(2026, 10, 5) })
    }

    @Test
    fun blocksOfTheExamples() {
        val meeting = fr.timeBlocks.single { it.start.hour == 13 }
        assertEquals(14, meeting.end.hour)
        assertEquals(BlockKind.DEEPWORK, fr.timeBlocks.single { it.start.hour == 10 }.kind)
        assertEquals(BlockRecurrence.DAILY, fr.timeBlocks.single { it.start.hour == 12 }.recurrence)
    }

    @Test
    fun idsAreDenseAndReferencesValid() {
        val ids = fr.tasks.map { it.id }
        assertEquals((1L..10L).toList(), ids)
        assertTrue(fr.tasks.mapNotNull { it.parentId }.all { it in ids })
    }

    @Test
    fun translatedInEnglish() {
        val en = ExampleData.snapshot(today, now, "en-US")
        assertTrue(en.tasks.all { it.projectTag == "Example" && it.title.startsWith("[Example] ") })
        assertTrue("Development" in en.settings.taskTypeList)
        assertEquals(fr.tasks.map { it.priority to it.fibonacciPoints }, en.tasks.map { it.priority to it.fibonacciPoints })
        // Les types des exemples existent dans les réglages de la même langue.
        assertTrue(en.tasks.map { it.taskType }.filter { it.isNotEmpty() }.all { it in en.settings.taskTypeList })
        assertTrue(fr.tasks.map { it.taskType }.filter { it.isNotEmpty() }.all { it in fr.settings.taskTypeList })
    }

    @Test
    fun anUntranslatedLanguageGetsTheEnglishExamples() {
        // Repli anglais (issue #70) : une langue système sans traduction (portugais, chinois…) donne l'anglais.
        val english = ExampleData.snapshot(today, now, "en").tasks.map { it.title }
        for (language in listOf("pt", "zh-Hans", "en-US", "")) {
            assertEquals(english, ExampleData.snapshot(today, now, language).tasks.map { it.title }, language)
        }
        assertEquals(fr.tasks.map { it.title }, ExampleData.snapshot(today, now, "fr-CA").tasks.map { it.title })
    }

    @Test
    fun everyTranslatedLanguageHasItsOwnConsistentExamples() {
        val prefixes = mapOf("es" to "[Ejemplo]", "de" to "[Beispiel]", "ru" to "[Пример]", "it" to "[Esempio]")
        for ((language, prefix) in prefixes) {
            val snapshot = ExampleData.snapshot(today, now, "$language-XX")
            assertEquals(10, snapshot.tasks.size, language)
            assertTrue(snapshot.tasks.all { it.title.startsWith("$prefix ") }, language)
            assertTrue(snapshot.timeBlocks.all { it.title.startsWith("$prefix ") }, language)
            // Mêmes priorités et tailles que le jeu français : seul le texte change.
            assertEquals(fr.tasks.map { it.priority to it.fibonacciPoints }, snapshot.tasks.map { it.priority to it.fibonacciPoints }, language)
            // Les types des exemples existent dans les réglages de la même langue.
            assertEquals(Settings.defaultTaskTypes(language), snapshot.settings.taskTypes, language)
            assertTrue(snapshot.tasks.map { it.taskType }.filter { it.isNotEmpty() }.all { it in snapshot.settings.taskTypeList }, language)
        }
    }
}

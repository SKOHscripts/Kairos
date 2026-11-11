package com.skohscripts.kairos.ui.guide

import com.skohscripts.kairos.ui.day.EditField
import com.skohscripts.kairos.ui.day.SheetSection
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Étapes de la première tâche récurrente et de la visite des Réglages (docs/spec/premiere-tache.md, issue #98). */
class RecurrenceCoachTest {
    private val steps = RecurrenceCoach.STEPS

    @Test
    fun theWalkGoesFromTheTitleToSavingThroughTheRule() {
        assertEquals(5, steps.size)
        assertEquals(setOf(EditField.TITLE), steps.first().fields)
        assertTrue(steps.last().save && steps.last().fields.isEmpty())
        // L'étape de la règle explique « Récurrence », ouvre les options avancées et montre l'aperçu ; elle seule.
        val rule = steps.single { it.preview }
        assertEquals(setOf(EditField.RECURRENCE), rule.fields)
        assertEquals(setOf(SheetSection.TIME), rule.sections)
        // Seulement de vrais champs de la fiche personnelle, chacun une fois.
        val covered = steps.flatMap { it.fields }
        assertTrue(EditField.personal.containsAll(covered))
        assertEquals(covered.size, covered.toSet().size)
    }

    @Test
    fun theStepsHaveTheirOwnTexts() {
        val first = FirstTaskCoach.STEPS.map { it.title }.toSet() + FirstTaskCoach.STEPS.map { it.body }
        assertTrue(steps.none { it.title in first || it.body in first })
        assertEquals(steps.size, steps.map { it.title }.toSet().size)
    }

    @Test
    fun theSettingsTourShowsOneCardAtATimeThenSaving() {
        val tour = SettingsTour.STEPS
        assertEquals(
            listOf(GuideSection.SETTINGS_WORKDAY, GuideSection.SETTINGS_HOURS, GuideSection.SETTINGS_CONTEXTS, GuideSection.SETTINGS_BEST_HOURS, null),
            tour.map { it.section },
        )
        assertTrue(tour.last().step.save)
        assertTrue(tour.dropLast(1).all { it.section?.screen == GuideScreen.SETTINGS && it.section.settingsCategory != null })
    }
}

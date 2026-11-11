package com.skohscripts.kairos.ui.guide

import com.skohscripts.kairos.ui.day.EditField
import com.skohscripts.kairos.ui.day.SheetSection
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Étapes de l'accompagnement de la première tâche (docs/spec/premiere-tache.md). */
class FirstTaskCoachTest {
    private val steps = FirstTaskCoach.STEPS

    @Test
    fun everyFieldOfTheSheetHasExactlyOneStep() {
        val covered = steps.flatMap { it.fields }
        // Les champs de la fiche personnelle ; « Assigné à » n'existe que dans la fiche d'une tâche d'équipe (TeamTaskCoachTest).
        assertEquals(EditField.personal.toSet(), covered.toSet())
        assertEquals(covered.size, covered.toSet().size, "un champ expliqué à deux étapes")
    }

    @Test
    fun theWalkStartsAtTheTitleAndEndsOnSaving() {
        assertEquals(9, steps.size)
        assertEquals(setOf(EditField.TITLE), steps.first().fields)
        assertTrue(steps.last().save && steps.last().fields.isEmpty())
        assertTrue(steps.dropLast(1).none { it.save })
    }

    @Test
    fun eachStepOpensTheSectionsOfTheFieldsItExplains() {
        // Fiche repliée (docs/spec/fiche-tache.md) : le titre est toujours visible, chaque autre étape ouvre sa section.
        assertEquals(emptySet(), steps.first().sections)
        assertEquals(setOf(SheetSection.QUALIFY), steps.first { EditField.PRIORITY in it.fields }.sections)
        // Échéance et date programmée : la même section, « Dates et temps ».
        assertEquals(setOf(SheetSection.TIME), steps.first { EditField.DEADLINE in it.fields }.sections)
        assertEquals(setOf(SheetSection.QUALIFY, SheetSection.TIME, SheetSection.SPLIT), steps.first { EditField.PROJECT in it.fields }.sections)
    }

    @Test
    fun everyStepHasItsOwnTexts() {
        val all = steps + FirstTaskCoach.placement(true) + FirstTaskCoach.placement(false)
        val distinct = all.distinctBy { it.title }
        assertEquals(distinct.size, distinct.map { it.body }.toSet().size)
        assertEquals(3, FirstTaskCoach.placement(true).size)
        assertEquals(3, FirstTaskCoach.placement(false).size)
        // Une tâche pas encore placée remplace la première étape, pas les autres.
        assertEquals(FirstTaskCoach.placement(true).drop(1).map { it.title }, FirstTaskCoach.placement(false).drop(1).map { it.title })
    }
}

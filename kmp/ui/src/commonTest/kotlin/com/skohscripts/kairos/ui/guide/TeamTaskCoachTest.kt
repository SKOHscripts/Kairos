package com.skohscripts.kairos.ui.guide

import com.skohscripts.kairos.ui.day.EditField
import com.skohscripts.kairos.ui.day.SheetSection
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Étapes du coach de la première tâche d'équipe (docs/spec/premiere-tache.md § Première tâche d'équipe, issue #97). */
class TeamTaskCoachTest {
    private val steps = TeamTaskCoach.STEPS

    @Test
    fun everyFieldOfTheTeamSheetHasExactlyOneStep() {
        // La fiche d'équipe a tous les champs de la fiche personnelle, plus « Assigné à ».
        val covered = steps.flatMap { it.fields }
        assertEquals(EditField.entries.toSet(), covered.toSet())
        assertEquals(covered.size, covered.toSet().size, "un champ expliqué à deux étapes")
    }

    @Test
    fun theWalkGoesFromTheTitleToTheAssigneeAndEndsOnSaving() {
        assertEquals(7, steps.size)
        assertEquals(setOf(EditField.TITLE, EditField.DESCRIPTION), steps[0].fields)
        assertEquals(setOf(EditField.ASSIGNEE), steps[1].fields)
        assertTrue(steps.last().save && steps.last().fields.isEmpty())
        assertTrue(SheetSection.SPLIT in steps.first { EditField.BLOCKERS in it.fields }.sections)
        assertEquals(setOf(SheetSection.TEAM), steps[1].sections)
    }

    @Test
    fun itsTextsAreItsOwn() {
        val personal = (FirstTaskCoach.STEPS + FirstTaskCoach.placement(true) + FirstTaskCoach.placement(false)).map { it.title }.toSet()
        assertTrue(steps.none { it.title in personal })
        assertEquals(steps.size, steps.map { it.body }.toSet().size)
    }
}

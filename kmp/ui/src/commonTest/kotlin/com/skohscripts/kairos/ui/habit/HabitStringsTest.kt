package com.skohscripts.kairos.ui.habit

import com.skohscripts.kairos.core.habit.HabitScience
import kotlin.test.Test
import kotlin.test.assertEquals

/** Chaque affirmation de la base scientifique a son texte, et aucun texte n'est sans affirmation (docs/spec/habitudes.md). */
class HabitStringsTest {
    @Test
    fun everyClaimHasItsTextAndNoTextIsOrphan() {
        assertEquals(HabitScience.CLAIMS.map { it.id }, CLAIM_TEXTS.keys.toList())
    }
}

package com.skohscripts.kairos.core.habit

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Base scientifique des habitudes (docs/spec/habitudes.md § Base scientifique). */
class HabitScienceTest {
    @Test
    fun everyClaimHasAtLeastOneExistingReference() {
        for (claim in HabitScience.CLAIMS) {
            assertTrue(claim.refs.isNotEmpty(), "${claim.id} sans référence")
            for (ref in claim.refs) assertTrue(HabitScience.reference(ref) != null, "${claim.id} cite $ref, inconnue")
        }
    }

    @Test
    fun noOrphanReference() {
        val cited = HabitScience.CLAIMS.flatMap { it.refs }.toSet()
        assertEquals(emptyList(), HabitScience.REFERENCES.map { it.id }.filterNot { it in cited })
    }

    @Test
    fun idsAreUniqueAndClaimsAreH1ToH13() {
        assertEquals(HabitScience.REFERENCES.size, HabitScience.REFERENCES.map { it.id }.toSet().size)
        assertEquals((1..13).map { "H$it" }, HabitScience.CLAIMS.map { it.id })
    }

    @Test
    fun doisAreWellFormed() {
        val doi = Regex("""^10\.\d{4,9}/\S+$""")
        for (r in HabitScience.REFERENCES) assertTrue(doi.matches(r.doi), "${r.id} : DOI mal formé ${r.doi}")
        assertEquals("https://doi.org/10.1002/ejsp.674", HabitScience.doiUrl("10.1002/ejsp.674"))
    }
}

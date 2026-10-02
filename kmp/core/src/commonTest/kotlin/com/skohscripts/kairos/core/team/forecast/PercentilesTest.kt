package com.skohscripts.kairos.core.team.forecast

import com.skohscripts.kairos.core.model.KairosSnapshot
import com.skohscripts.kairos.core.team.LoadPlan
import com.skohscripts.kairos.core.team.TeamFixtures
import com.skohscripts.kairos.core.team.TeamFixtures.member
import com.skohscripts.kairos.core.team.TeamFixtures.snapshot
import com.skohscripts.kairos.core.engine.Workdays
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PercentilesTest {
    private val ten = IntArray(10) { it + 1 }

    @Test
    fun nearestRankByHand() {
        // n = 10 : rang = ceil(p × 10 / 100), valeur = rang-ième.
        assertEquals(5, Percentiles.rank(10, 50))
        assertEquals(9, Percentiles.rank(10, 85))
        assertEquals(10, Percentiles.rank(10, 95))
        assertEquals(5, Percentiles.nearestRank(ten, 50))
        assertEquals(9, Percentiles.nearestRank(ten, 85))
        assertEquals(10, Percentiles.nearestRank(ten, 95))
        // n = 20 : 0,85 × 20 = 17 exactement (pas 18 par une erreur d'arrondi flottant), 0,95 × 20 = 19.
        assertEquals(17, Percentiles.rank(20, 85))
        assertEquals(19, Percentiles.rank(20, 95))
        // n = 3 : ceil(1,5) = 2 ; n = 1 : toujours la seule valeur.
        assertEquals(2, Percentiles.rank(3, 50))
        assertEquals(1, Percentiles.rank(1, 50))
        assertEquals(1, Percentiles.rank(1, 99))
        // Bornes : rang jamais hors [1, n].
        assertEquals(1, Percentiles.rank(10, 0))
        assertEquals(10, Percentiles.rank(10, 100))
        assertEquals(0, Percentiles.rank(0, 50))
        assertNull(Percentiles.nearestRank(IntArray(0), 50))
    }

    @Test
    fun nearestRankNeverInterpolates() {
        // Deux valeurs : le percentile est toujours l'une des deux, jamais une moyenne.
        val two = intArrayOf(10, 20)
        assertEquals(10, Percentiles.nearestRank(two, 50))
        assertEquals(20, Percentiles.nearestRank(two, 51))
        assertEquals(listOf("a", "b", "c").let { Percentiles.nearestRank(it, 50) }, "b")
    }

    @Test
    fun atLeastIsTheMirrorOfNearestRank() {
        // « Au moins X avec p % de chances » : la ceil(p × n)-ième plus grande valeur.
        assertEquals(6, Percentiles.atLeast(ten, 50)) // 5e plus grande
        assertEquals(2, Percentiles.atLeast(ten, 85)) // 9e plus grande
        assertEquals(1, Percentiles.atLeast(ten, 95)) // 10e plus grande
        // Cohérence : au moins `atLeast(p)` est atteint par au moins p % des valeurs.
        for (p in listOf(50, 85, 95)) {
            val v = Percentiles.atLeast(ten, p)!!
            assertTrue(ten.count { it >= v } * 100 >= p * ten.size)
        }
        assertNull(Percentiles.atLeast(IntArray(0), 50))
    }

    /**
     * Convergence contre une loi connue. Une seule tâche de 100 h chez un membre à 1 h/jour (donc un
     * jour ouvré par heure) ; facteurs d'erreur : 1 000 valeurs régulièrement espacées sur [0,8 ; 2,0]
     * (loi uniforme discrétisée, pas de 0,12 %). La fin tombe le `ceil(100 f)`-ième jour ouvré, f
     * ~ U[0,8 ; 2,0] : médiane exacte f = 1,4 donc 140 jours ouvrés, P85 f = 1,82 donc 182. Avec
     * 20 000 tirages, l'écart-type du quantile empirique de f est sqrt(p(1−p)/n)/densité =
     * 0,0035 × 1,2 = 0,0042 (P50) et 0,0025 × 1,2 = 0,0030 (P85), soit 0,3 % et 0,17 % de leur valeur :
     * la tolérance de 1 % est à plus de 3 écarts-types. (Et la graine est fixe : le test est déterministe.)
     */
    @Test
    fun theEstimatedPercentilesConvergeToTheExactOnes() {
        val s: KairosSnapshot = snapshot(
            tasks = listOf(ForecastFixtures.task(1, 1, hours = 100.0)),
            members = listOf(member(1, hoursPerDay = 1.0)),
        )
        val factors = List(1000) { 0.8 + 1.2 * (it + 0.5) / 1000 }
        val data = ForecastData(errorFactors = factors)
        val result = ForecastFixtures.run(s, data, seed = 20_260_930L, runs = 20_000)

        val holidays = emptySet<kotlinx.datetime.LocalDate>()
        fun workdays(date: kotlinx.datetime.LocalDate?): Int = 1 + Workdays.businessDaysBetween(TeamFixtures.monday, checkNotNull(date), holidays)
        val p50 = workdays(result.finishDate(50))
        val p85 = workdays(result.finishDate(85))
        assertTrue(kotlin.math.abs(p50 - 140) <= 1.4, "P50 = $p50 jours ouvrés, attendu 140 ± 1 %")
        assertTrue(kotlin.math.abs(p85 - 182) <= 1.82, "P85 = $p85 jours ouvrés, attendu 182 ± 1 %")
        assertEquals(20_000, result.runs)
        // Le plan déterministe de cette tâche serait fini en 100 jours (facteur 1) : la simulation l'a bien étiré.
        assertEquals(100, 1 + Workdays.businessDaysBetween(TeamFixtures.monday, LoadPlan.build(s, TeamFixtures.monday).task(1)!!.end!!, holidays))
    }
}

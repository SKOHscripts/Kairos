package com.skohscripts.kairos.core.team.forecast

import com.skohscripts.kairos.core.model.KairosSnapshot
import com.skohscripts.kairos.core.team.TeamFixtures.member
import com.skohscripts.kairos.core.team.TeamFixtures.monday
import com.skohscripts.kairos.core.team.TeamFixtures.snapshot
import com.skohscripts.kairos.core.team.forecast.ForecastFixtures.dep
import com.skohscripts.kairos.core.team.forecast.ForecastFixtures.task
import com.skohscripts.kairos.core.team.forecast.ScenarioModification.AddAbsence
import com.skohscripts.kairos.core.team.forecast.ScenarioModification.AddMember
import com.skohscripts.kairos.core.team.forecast.ScenarioModification.SetAvailability
import com.skohscripts.kairos.core.team.forecast.ScenarioModification.SetFocus
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.plus
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertTrue

/**
 * Propriétés de monotonie **tirage par tirage** (docs/spec/equipe-simulation.md § Critères de
 * succès) : la situation réelle et le scénario voient les mêmes aléas (mêmes tirages, mêmes
 * identités de tâches et de membres-semaines), donc la comparaison n'a pas de bruit. Elles ne
 * sont promises que **sans dépendance entre membres** (anomalies de l'ordonnancement de liste
 * de Graham, § Décisions) : ici, des dépendances seulement à l'intérieur d'un membre.
 */
class ScenarioPropertyTest {
    private val runs = 150

    /** Équipe de [members] membres, des tâches de durées et priorités variées, des chaînes de dépendances dans un même membre. */
    private fun team(members: Int, perMember: Int, variant: Int): KairosSnapshot {
        val tasks = ArrayList<com.skohscripts.kairos.core.model.Task>()
        val deps = ArrayList<com.skohscripts.kairos.core.model.TaskDependency>()
        var id = 1L
        for (m in 1..members) {
            var previous: Long? = null
            for (k in 0 until perMember) {
                val hours = 3.0 + ((m * 5 + k * 7 + variant * 3) % 11)
                // Une tâche sur sept sans estimation ni points (effort par défaut) : ni la priorité ni les points manquent pas à la file.
                val unestimated = (id + variant) % 7 == 0L
                tasks += task(id, m.toLong(), if (unestimated) null else hours, priority = (k + variant) % 3, points = if (unestimated) null else 3)
                if (k % 2 == 1 && previous != null) deps += dep(blocked = id, blocker = previous)
                previous = id
                id++
            }
        }
        return snapshot(tasks, (1..members).map { member(it.toLong()) }, deps = deps)
    }

    /** Historique avec de la dispersion, et un historique de capacité pour chaque membre (l'aléa de capacité est actif). */
    private fun data(members: Int) = ForecastData(
        errorFactors = listOf(0.5, 0.7, 0.9, 1.0, 1.0, 1.2, 1.5, 2.0, 3.0),
        capacityFactors = (1..members).associate { it.toLong() to listOf(0.5, 0.7, 0.8, 0.9, 1.0, 1.0, 1.1, 1.3, 1.5) },
    )

    private fun batch(s: KairosSnapshot, data: ForecastData, seed: Long, scope: ForecastScope = ForecastScope.Assigned): DrawBatch =
        MonteCarlo.prepare(ForecastFixtures.request(s, data, seed, runs, scope, options = ForecastOptions(capacityRandomness = true))).runBatch(0, runs)

    /** Nombre de tirages où la fin du scénario est **strictement** plus tardive (pour prouver que la propriété n'est pas vide). */
    private var strictlyLater = 0

    private fun assertNeverEarlier(real: DrawBatch, scenario: DrawBatch, label: String) {
        for (i in 0 until runs) {
            assertTrue(scenario.ends[i] >= real.ends[i], "$label : tirage $i, fin ${scenario.ends[i]} avant la fin réelle ${real.ends[i]}")
            if (scenario.ends[i] > real.ends[i]) strictlyLater++
        }
        // Les fins par tâche, triées dans le tirage, sont elles aussi coordonnée par coordonnée au plus tôt celles du réel.
        val a = real.taskEnds!!
        val b = scenario.taskEnds!!
        for (k in a.indices) assertTrue(b[k] >= a[k], "$label : fin de tâche rang $k plus tôt dans le scénario")
    }

    private val cases = (0 until 6).map { v -> Triple(2 + v % 3, 3 + v % 4, v) }
    private val seeds = listOf(1L, 20_261_001L, -77L)

    @Test
    fun addingAnAbsenceNeverBringsAnEndCloser() {
        for ((members, per, v) in cases) for (seed in seeds) {
            val real = team(members, per, v)
            val d = data(members)
            val realBatch = batch(real, d, seed)
            for (member in 1..members) for ((start, length) in listOf(0 to 0, 0 to 6, 3 to 11, 10 to 20)) {
                val begin = monday.plus(DatePeriod(days = start))
                val (scenario, skipped) = Scenario.apply(real, listOf(AddAbsence(member.toLong(), begin, begin.plus(DatePeriod(days = length)))))
                assertTrue(skipped.isEmpty())
                assertNeverEarlier(realBatch, batch(scenario, d, seed), "cas $v graine $seed membre $member absence $start+$length")
            }
        }
        assertTrue(strictlyLater > 0, "une absence aurait dû retarder au moins une fin")
    }

    @Test
    fun lowerAvailabilityOrFocusNeverBringsAnEndCloser() {
        for ((members, per, v) in cases) for (seed in seeds) {
            val real = team(members, per, v)
            val d = data(members)
            val realBatch = batch(real, d, seed)
            val half = Scenario.apply(real, listOf(SetAvailability(1, 50))).first
            assertNeverEarlier(realBatch, batch(half, d, seed), "cas $v graine $seed quotité 50 %")
            val focus = Scenario.apply(real, listOf(SetFocus(0.7))).first
            assertNeverEarlier(realBatch, batch(focus, d, seed), "cas $v graine $seed focus 70 %")
        }
        assertTrue(strictlyLater > 0)
    }

    @Test
    fun doublingEveryEffortNeverBringsAnEndCloser() {
        for ((members, per, v) in cases) for (seed in seeds) {
            val real = team(members, per, v)
            val doubled = real.copy(tasks = real.tasks.map { t -> t.copy(estimatedMinutes = t.estimatedMinutes?.let { it * 2 }) })
            val d = data(members)
            assertNeverEarlier(batch(real, d, seed), batch(doubled, d, seed), "cas $v graine $seed efforts doublés")
        }
        assertTrue(strictlyLater > 0)
    }

    @Test
    fun aHypotheticalMemberWithoutTasksChangesNothing() {
        for ((members, per, v) in cases) for (seed in seeds) {
            val real = team(members, per, v)
            val d = data(members)
            val a = batch(real, d, seed)
            val withExtra = batch(Scenario.apply(real, listOf(AddMember(-1, "Hypothétique", 100, 8.0))).first, d, seed)
            assertContentEquals(a.ends, withExtra.ends, "cas $v graine $seed")
            assertContentEquals(a.taskEnds, withExtra.taskEnds)
        }
    }

    @Test
    fun theSameScenarioSeenTwiceGivesTheSameDrawsAndRealAndScenarioShareTheirRandomness() {
        // Un scénario qui ne change rien (focus identique) rend exactement les mêmes tirages que le réel.
        val real = team(3, 4, 2)
        val d = data(3)
        val identical = Scenario.apply(real, listOf(SetFocus(real.settings.team!!.focusFactor))).first
        assertContentEquals(batch(real, d, 5L).ends, batch(identical, d, 5L).ends)
        // Et un scénario réellement différent s'écarte du réel sur ces mêmes tirages, sans bruit parasite : une absence
        // placée après toute la charge ne change aucun tirage.
        val farAbsence = Scenario.apply(real, listOf(AddAbsence(1, monday.plus(DatePeriod(days = 400)), monday.plus(DatePeriod(days = 410))))).first
        assertContentEquals(batch(real, d, 5L).ends, batch(farAbsence, d, 5L).ends)
    }

    @Test
    fun removingAMemberLeavesTheOthersTheirOwnDrawsUnchanged() {
        // Sans dépendance entre membres, la fin du travail des membres 1 et 2 ne dépend pas du membre 3 : retirer celui-ci
        // (ses tâches retournent au backlog, hors du périmètre « assigné ») laisse chaque tirage identique.
        val real = team(3, 4, 1)
        val d = data(3)
        val removed = Scenario.apply(real, listOf(ScenarioModification.RemoveMember(3))).first
        val others = ForecastScope.Tasks(real.tasks.filter { it.assigneeId != 3L }.map { it.id }.toSet())
        assertContentEquals(batch(real, d, 9L, others).ends, batch(removed, d, 9L).ends)
        assertContentEquals(batch(real, d, 9L, others).taskEnds, batch(removed, d, 9L).taskEnds)
    }
}

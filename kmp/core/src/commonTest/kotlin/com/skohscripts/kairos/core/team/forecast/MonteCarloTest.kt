package com.skohscripts.kairos.core.team.forecast

import com.skohscripts.kairos.core.model.KairosSnapshot
import com.skohscripts.kairos.core.model.TeamSettings
import com.skohscripts.kairos.core.team.LoadPlan
import com.skohscripts.kairos.core.team.TeamFixtures
import com.skohscripts.kairos.core.team.TeamFixtures.member
import com.skohscripts.kairos.core.team.TeamFixtures.monday
import com.skohscripts.kairos.core.team.TeamFixtures.snapshot
import com.skohscripts.kairos.core.team.forecast.ForecastFixtures.dep
import com.skohscripts.kairos.core.team.forecast.ForecastFixtures.run
import com.skohscripts.kairos.core.team.forecast.ForecastFixtures.task
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

class MonteCarloTest {
    private fun day(n: Int) = monday.plus(DatePeriod(days = n))

    /** Trois membres, des tâches de durées variées, des dépendances dans un même membre, des échéances. */
    private fun sample(): KairosSnapshot = snapshot(
        tasks = listOf(
            task(1, 1, 12.0, deadline = day(2)), task(2, 1, 6.0, priority = 0), task(3, 1, 9.0),
            task(4, 2, 20.0, deadline = day(4)), task(5, 2, 5.0), task(6, 3, 30.0, deadline = day(5)),
            task(7, 3, 4.0, priority = 2), task(8, 2, 7.0),
        ),
        members = listOf(member(1), member(2), member(3)),
        deps = listOf(dep(blocked = 3, blocker = 1), dep(blocked = 8, blocker = 4)),
    )

    private val spread = ForecastData(errorFactors = listOf(0.6, 0.8, 0.9, 1.0, 1.0, 1.1, 1.3, 1.6, 2.0, 2.5))

    @Test
    fun sameSeedSameResult() {
        val a = run(sample(), spread, seed = 7L, runs = 400)
        val b = run(sample(), spread, seed = 7L, runs = 400)
        assertEquals(a, b)
        assertEquals(7L, a.seed)
        assertEquals(400, a.runs)
        // Une autre graine donne d'autres tirages (la comparaison est exacte, le hasard ne s'en mêle pas : graines fixes).
        assertNotEquals(a, run(sample(), spread, seed = 8L, runs = 400))
    }

    @Test
    fun oneBatchEqualsTwentyBatchesInAnyOrder() {
        val mc = MonteCarlo.prepare(ForecastFixtures.request(sample(), spread, seed = 11L, runs = 1000))
        val whole = mc.run(batchSize = 1000)
        val twenty = mc.run(batchSize = 50)
        assertEquals(whole, twenty)
        // Les tranches se rangent à leur numéro : l'ordre d'arrivée est sans effet.
        val acc = mc.accumulator()
        for (from in (0 until 1000 step 50).reversed()) acc.add(mc.runBatch(from, 50))
        assertEquals(whole, acc.result())
        // Et une tranche ne dépend pas de ses voisines : le tirage 300 est le même seul ou dans la tranche entière.
        val alone = mc.accumulator().apply { add(mc.runBatch(300, 1)) }.result()
        val inside = mc.runBatch(250, 100)
        assertEquals(inside.ends[50], alone.samples.sortedEnds.single())
    }

    @Test
    fun partialResultIsMarkedInterrupted() {
        val mc = MonteCarlo.prepare(ForecastFixtures.request(sample(), spread, runs = 1000))
        val acc = mc.accumulator()
        acc.add(mc.runBatch(0, 250))
        val partial = acc.result(interrupted = true)
        assertTrue(partial.interrupted)
        assertEquals(250, partial.runs)
        assertEquals(1000, partial.requestedRuns)
        assertFalse(mc.run().interrupted)
    }

    @Test
    fun withAllFactorsAtOneAndNoRandomnessEveryPercentileIsTheDeterministicPlanEnd() {
        val s = sample()
        val plan = LoadPlan.build(s, monday)
        val end = plan.tasks.mapNotNull { it.end }.max()
        val r = run(s, ForecastFixtures.exact, runs = 300)
        assertEquals(end, r.finishDate(50))
        assertEquals(end, r.finishDate(85))
        assertEquals(end, r.finishDate(95))
        val finish = r.finish!!
        assertEquals(end, finish.p50)
        assertEquals(1, finish.histogram.size)
        assertEquals(300, finish.histogram.single().draws)
        assertEquals(0, finish.outOfHorizon)
        // Chaque échéance : en retard dans tous les tirages ou dans aucun, comme le plan.
        for (d in r.deadlines) {
            val planned = plan.task(d.taskId)!!
            assertEquals(if (planned.late) 300 else 0, d.lateDraws, "tâche ${d.taskId}")
        }
        // « Combien d'ici » : à la fin de chaque tâche, autant de tâches finies que le plan.
        val ends = plan.tasks.mapNotNull { it.end }.sorted()
        for (date in ends.distinct()) {
            val expected = ends.count { it <= date }
            val c = r.finishedBy(date)!!
            assertEquals(listOf(expected, expected, expected), listOf(c.atLeast50, c.atLeast85, c.atLeast95), "d'ici $date")
        }
        // Sans aléa de capacité, ni avec un historique de capacité absent : même chose.
        val withCapacity = run(s, ForecastFixtures.exact, runs = 100, options = ForecastOptions(capacityRandomness = true))
        assertEquals(end, withCapacity.finishDate(95))
    }

    @Test
    fun theReplayedPlanIsExactlyTheDeterministicPlanAfterAnEffortOverrideOfOne() {
        // Le moteur de posage est le même : LoadPlan.build avec les efforts du plan donne le même plan.
        val s = sample()
        val prepared = LoadPlan.prepare(s, monday)
        assertEquals(LoadPlan.build(s, monday), prepared.run())
        val same = s.tasks.associate { it.id to (it.estimatedMinutes!! / 60.0) }
        assertEquals(LoadPlan.build(s, monday), prepared.run(efforts = same))
    }

    @Test
    fun aConstantThroughputOfFivePerWeekFinishesTwentyTasksInExactlyFourWeeks() {
        val tasks = (1L..20L).map { task(it, 1, 1.0) }
        val s = snapshot(tasks, listOf(member(1)))
        val data = ForecastData(teamThroughput = List(12) { 5 })
        val r = run(s, data, runs = 200, model = ForecastModel.THROUGHPUT)
        // Quatre semaines pleines depuis le lundi : le dernier jour ouvré de la 4e semaine, vendredi 30 octobre.
        assertEquals(LocalDate(2026, 10, 30), r.finishDate(50))
        assertEquals(LocalDate(2026, 10, 30), r.finishDate(95))
        val h = r.finish!!.histogram
        assertEquals(listOf(WeekBucket(LocalDate(2026, 10, 26), 200)), h)
        assertEquals(ForecastModel.THROUGHPUT, r.model)
        assertFalse(r.taskLevel)
        assertTrue(r.deadlines.isEmpty() && r.lateSets.isEmpty() && r.bottleneck.isEmpty() && r.lateDistribution.isEmpty())
        // Combien d'ici : 5 tâches par semaine, une semaine comptée à sa fin.
        assertEquals(10, r.finishedBy(LocalDate(2026, 10, 16))!!.atLeast50)
        assertEquals(20, r.finishedBy(LocalDate(2026, 10, 30))!!.atLeast95)
        assertEquals(0, r.finishedBy(LocalDate(2026, 10, 8))!!.atLeast50)
    }

    @Test
    fun theCurrentWeekCountsInProportionToItsRemainingWorkdays() {
        // Mercredi matin : il reste 3 jours ouvrés sur 5 (mercredi, jeudi, vendredi) : 3 tâches cette semaine, puis 5 par semaine.
        val wednesday = Instant.parse("2026-10-07T07:00:00Z")
        val s = snapshot((1L..20L).map { task(it, 1, 1.0) }, listOf(member(1)))
        val data = ForecastData(teamThroughput = List(12) { 5 })
        val request = ForecastRequest(s, wednesday, ForecastFixtures.timeZone, data, 1L, model = ForecastModel.THROUGHPUT, runs = 100)
        val r = MonteCarlo.prepare(request).run()
        // 3 + 5 + 5 + 5 = 18 < 20 ≤ 23 : cinquième semaine, vendredi 6 novembre.
        assertEquals(LocalDate(2026, 11, 6), r.finishDate(50))
    }

    @Test
    fun throughputIsDrawnFromTheHistoryWeeksIncludingZeros() {
        val s = snapshot((1L..10L).map { task(it, 1, 1.0) }, listOf(member(1)))
        // Une semaine sur deux à 0, l'autre à 4 : 10 tâches en 3 à 5 semaines (jamais moins de 3 : 4 + 4 + 4 ≥ 10).
        val data = ForecastData(teamThroughput = listOf(0, 4, 0, 4, 0, 4, 0, 4))
        val r = run(s, data, runs = 1000, model = ForecastModel.THROUGHPUT)
        val weeks = r.finish!!.histogram
        assertTrue(weeks.first().weekStart >= LocalDate(2026, 10, 19), "pas avant la 3e semaine (${weeks.first().weekStart})")
        assertTrue(weeks.size > 1, "les semaines à zéro étalent la fin")
        assertEquals(1000, weeks.sumOf { it.draws })
    }

    @Test
    fun anEmptyHistoryFallsBackToTheDefaultLawAndIsUnreliable() {
        // Une tâche de 40 h pour un membre à 8 h/j : facteur ∈ [0,8 ; 2] donc 32 à 80 h, soit 4 à 10 jours ouvrés.
        val s = snapshot(listOf(task(1, 1, 40.0)), listOf(member(1)))
        val r = run(s, ForecastData(), runs = 600)
        assertEquals(DataSourceKind.DEFAULT, r.source.kind)
        assertEquals(0, r.source.samples)
        assertFalse(r.reliable)
        assertTrue(r.finishDate(50)!! >= day(3) && r.finishDate(95)!! <= day(13), "${r.finishDate(50)} ${r.finishDate(95)}")
        assertTrue(r.finishDate(50)!! < r.finishDate(95)!!, "la loi par défaut étale la fin")
    }

    @Test
    fun belowTheMinimumHistoryIsMixedWithTheDefaultLaw() {
        val few = ForecastData(errorFactors = listOf(3.0, 3.0, 3.0, 3.0), minSamples = 8)
        assertEquals(DataSourceKind.MIXED, few.estimationSource.kind)
        assertFalse(few.estimationSource.reliable)
        // n / minimum = 1/2 des tirages dans l'historique (3,0, hors de la loi par défaut, donc reconnaissable).
        // 4 000 tirages : écart-type de la part = sqrt(0,25 / 4 000) = 0,8 % ; tolérance 4 % (5 écarts-types).
        val draws = 4000
        var fromHistory = 0
        for (i in 0 until draws) {
            val rng = ForecastRandom.stream(ForecastRandom.drawBase(5L, i), ForecastRandom.DOMAIN_ERROR, 1L)
            val f = few.errorFactor(rng)
            if (f == 3.0) fromHistory++ else assertTrue(f in 0.8..2.0, "hors loi par défaut : $f")
        }
        assertTrue(kotlin.math.abs(fromHistory - draws / 2) <= draws * 4 / 100, "$fromHistory tirages dans l'historique sur $draws")
        // Au minimum, l'historique seul ; sans historique, la loi seule.
        val enough = ForecastData(errorFactors = List(8) { 3.0 })
        assertEquals(DataSourceKind.HISTORY, enough.estimationSource.kind)
        assertTrue((0 until 200).all { enough.errorFactor(ForecastRandom.stream(0L, 1L, it.toLong())) == 3.0 })
    }

    @Test
    fun theTriangularLawHasItsKnownMeanAndBounds() {
        // Loi (0,8 ; 1 ; 2) : moyenne (0,8 + 1 + 2) / 3 = 1,2667 ; 6 000 tirages stratifiés (u régulier) : erreur < 0,1 %.
        val n = 6000
        val values = List(n) { ForecastData.triangular((it + 0.5) / n) }
        assertTrue(values.all { it in 0.8..2.0 })
        assertEquals(1.2667, values.average(), 0.002)
        // Médiane analytique : F(1) = 1/6 < 1/2, donc 2 − sqrt(0,5 × 1,2 × 1) = 1,2254.
        assertEquals(1.2254, ForecastData.triangular(0.5), 0.0005)
    }

    @Test
    fun zeroThroughputNeverFinishesAndDoesNotLoop() {
        val s = snapshot(listOf(task(1, 1, 1.0), task(2, 1, 1.0)), listOf(member(1)))
        val data = ForecastData(teamThroughput = List(12) { 0 })
        val r = run(s, data, runs = 500, model = ForecastModel.THROUGHPUT)
        assertNull(r.finishDate(50))
        assertNull(r.finishDate(95))
        assertEquals(500, r.finish!!.outOfHorizon)
        assertTrue(r.finish.histogram.isEmpty())
        assertFalse(r.reliable)
        assertFalse(MonteCarlo.isAvailable(ForecastModel.THROUGHPUT, data))
        // Un historique vide se comporte de même.
        assertNull(run(s, ForecastData(), runs = 50, model = ForecastModel.THROUGHPUT).finishDate(50))
        // Disponible dès 4 semaines avec une tâche finie.
        assertFalse(ForecastData(teamThroughput = listOf(1, 1, 1, 0, 0)).throughputAvailable())
        assertTrue(ForecastData(teamThroughput = listOf(1, 1, 1, 0, 1)).throughputAvailable())
    }

    @Test
    fun aMemberWithoutAnyAvailabilityNeverFinishesAndIsOutOfHorizon() {
        // Absent pendant plus de deux ans : le garde-fou du plan rend « hors horizon », sans boucle infinie.
        val s = snapshot(
            listOf(task(1, 1, 8.0)), listOf(member(1)),
            absences = listOf(TeamFixtures.absence(1, 1, monday, monday.plus(DatePeriod(years = 3)))),
        )
        val r = run(s, runs = 30)
        assertNull(r.finishDate(50))
        assertEquals(30, r.finish!!.outOfHorizon)
    }

    @Test
    fun theScopeSelectsWhichTasksAreMeasuredNotWhichTasksTakeCapacity() {
        // Même membre : A (8 h, P0) puis B (8 h, P2). Mesurer B seul attend A ; mesurer A seul finit lundi.
        val s = snapshot(listOf(task(1, 1, 8.0, priority = 0), task(2, 1, 8.0, priority = 2)), listOf(member(1)))
        assertEquals(day(1), run(s, runs = 50).finishDate(50))
        assertEquals(monday, run(s, runs = 50, scope = ForecastScope.Tasks(setOf(1L))).finishDate(50))
        assertEquals(day(1), run(s, runs = 50, scope = ForecastScope.Tasks(setOf(2L))).finishDate(50))
        assertEquals(day(1), run(s, runs = 50, scope = ForecastScope.Member(1)).finishDate(50))
        assertEquals(1, run(s, runs = 50, scope = ForecastScope.Tasks(setOf(2L))).scopeSize)
        val empty = run(s, runs = 50, scope = ForecastScope.Member(99))
        assertEquals(0, empty.scopeSize)
        assertNull(empty.finish)
        assertNull(empty.finishDate(50))
    }

    @Test
    fun theCategoryScopeKeepsOnlyItsTasks() {
        val s = snapshot(
            listOf(task(1, 1, 8.0, priority = 0, type = "Dev"), task(2, 1, 8.0, priority = 1, type = "Doc"), task(3, 2, 24.0, type = "Dev")),
            listOf(member(1), member(2)),
        )
        val dev = run(s, runs = 50, scope = ForecastScope.Category("Dev"))
        assertEquals(2, dev.scopeSize)
        assertEquals(day(2), dev.finishDate(50)) // la tâche 3 : 24 h = 3 jours
        assertEquals(day(1), run(s, runs = 50, scope = ForecastScope.Category("Doc")).finishDate(50))
        assertEquals(0, run(s, runs = 50, scope = ForecastScope.Category("")).scopeSize)
    }

    @Test
    fun theBacklogIsSpreadByTheSuggestionBeforeThePlanWhenIncluded() {
        // Le membre 1 a 16 h de travail ; deux tâches de 8 h attendent au backlog, plus une à qualifier.
        val tasks = listOf(
            task(1, 1, 16.0), task(2, null, 8.0, priority = 0), task(3, null, 8.0, priority = 2),
            task(4, null, null, priority = null, points = null),
        )
        val s = snapshot(tasks, listOf(member(1), member(2)))
        // Sans backlog : seule la tâche 1 compte, le backlog n'existe pas pour le plan.
        val plain = run(s, runs = 20)
        assertEquals(1, plain.scopeSize)
        assertEquals(day(1), plain.finishDate(50))

        val suggestion = com.skohscripts.kairos.core.team.AssignmentSuggestion.suggest(s, ForecastFixtures.now, ForecastFixtures.timeZone)
        val placed = s.copy(tasks = s.tasks.map { t -> suggestion.suggestions.firstOrNull { it.taskId == t.id }?.let { t.copy(assigneeId = it.memberId) } ?: t })
        val expected = LoadPlan.build(placed, monday).tasks.mapNotNull { it.end }.max()

        val withBacklog = run(s, runs = 20, scope = ForecastScope.AssignedAndBacklog)
        assertEquals(3, withBacklog.scopeSize)
        assertEquals(1, withBacklog.toQualify)
        assertEquals(expected, withBacklog.finishDate(95))
        // L'option seule (périmètre « assigné ») fait prendre de la capacité au backlog sans le mesurer.
        val optionOnly = run(s, runs = 20, options = ForecastOptions(includeBacklog = true, capacityRandomness = false))
        assertEquals(1, optionOnly.scopeSize)
        // Une sélection de tâches du backlog les place d'office.
        val selected = run(s, runs = 20, scope = ForecastScope.Tasks(setOf(2L)))
        assertEquals(1, selected.scopeSize)
        assertTrue(selected.finishDate(50) != null)
    }

    @Test
    fun deadlinesAreScoredByTheirShareOfLateDraws() {
        // Une tâche de 8 h à finir lundi : facteur 1,0 la tient, 1,5 la manque. Moitié-moitié (historique de 8 valeurs).
        val s = snapshot(listOf(task(1, 1, 8.0, deadline = monday)), listOf(member(1)))
        val data = ForecastData(errorFactors = List(4) { 1.0 } + List(4) { 1.5 })
        val r = run(s, data, runs = 2000)
        val d = r.deadlines.single()
        assertEquals(1L, d.taskId)
        assertEquals(monday, d.deadline)
        // Part de retard 50 % ; écart-type sqrt(0,25 / 2 000) = 1,1 % ; tolérance 5 % (4,5 écarts-types).
        assertEquals(0.5, d.criticality, 0.05)
        assertEquals(1.0 - d.criticality, d.onTimeProbability, 1e-12)
        assertTrue(d.atRisk, "sous le seuil de 70 %")
        assertEquals(listOf(d), r.atRisk)
        assertEquals(listOf(d), r.mostCritical())
        // Distribution des retards : 0 ou 1, jamais plus ; somme = nombre de tirages.
        assertEquals(2000, r.lateDistribution.sum())
        assertEquals(0, r.lateDistribution[2] + r.lateDistribution[3])
        assertEquals(d.criticality, r.expectedLate, 1e-12)
        assertEquals(1.0 - d.criticality, r.allOnTimeProbability, 1e-12)
        assertTrue(r.lateSets.size == 2 && r.lateSets.sumOf { it.draws } == 2000)
        // Le seuil se règle : à 40 %, la tâche n'est plus « en danger ».
        val loose = s.copy(settings = s.settings.copy(team = TeamSettings(focusFactor = 1.0, deadlineRiskPercent = 40, enabled = true)))
        assertFalse(run(loose, data, runs = 2000).deadlines.single().atRisk)
    }

    @Test
    fun theBottleneckIsTheMemberWhoFinishesLastTiesCountingForEach() {
        val s = snapshot(listOf(task(1, 1, 24.0), task(2, 2, 8.0), task(3, 3, 8.0)), listOf(member(1), member(2), member(3)))
        val r = run(s, runs = 100)
        assertEquals(listOf(MemberShare(1L, 100, 100)), r.bottleneck)
        assertEquals(1.0, r.bottleneck.single().share)
        // Deux membres finissent le même jour, le dernier : chacun est compté.
        val tie = snapshot(listOf(task(1, 1, 16.0), task(2, 2, 16.0), task(3, 3, 8.0)), listOf(member(1), member(2), member(3)))
        assertEquals(listOf(MemberShare(1L, 100, 100), MemberShare(2L, 100, 100)), run(tie, runs = 100).bottleneck)
    }

    @Test
    fun theMostLikelyLateSetIsTheMostFrequentOneWithDeterministicTies() {
        // Deux tâches à échéance lundi, sur deux membres, 8 h chacune ; facteur 1 : tout juste à l'heure ; 2 : en retard.
        // Facteurs tirés indépendamment par tâche (historique {1, 2} par moitié) : les 4 ensembles sont possibles.
        val s = snapshot(listOf(task(1, 1, 8.0, deadline = monday), task(2, 2, 8.0, deadline = monday)), listOf(member(1), member(2)))
        val r = run(s, ForecastData(errorFactors = List(4) { 1.0 } + List(4) { 2.0 }), runs = 3000)
        assertEquals(3000, r.lateSets.sumOf { it.draws })
        assertEquals(3000, r.lateDistribution.sum())
        // Chaque ensemble a ~25 % ; l'ordre est celui des tirages (décroissant), puis la taille, puis les identifiants.
        val sorted = r.lateSets.sortedWith(compareByDescending<LateSet> { it.draws }.thenBy { it.taskIds.size }.thenBy { it.taskIds.firstOrNull() ?: 0L })
        assertEquals(sorted.map { it.taskIds }, r.lateSets.map { it.taskIds })
        assertEquals(r.lateSets.first(), r.mostLikelyLateSet)
        assertEquals((r.lateDistribution[1] + 2.0 * r.lateDistribution[2]) / 3000, r.expectedLate, 1e-12)
    }

    @Test
    fun accumulatorOrdersLateSetsByCountThenSizeThenIds() {
        // Craft : quatre tirages à la main pour deux tâches à échéance (ids 5 et 9).
        val setup = MonteCarlo.Setup(
            model = ForecastModel.EFFORT, day = monday, seed = 0L, runs = 8, scopeSize = 2, keepTaskEnds = false,
            deadlineTaskIds = listOf(5L, 9L), deadlineDates = listOf(monday, monday), memberIds = emptyList(), riskPercent = 70,
            source = ForecastData().estimationSource, taskLevel = true, toQualify = 0,
        )
        fun batch(vararg draws: Pair<Int, Int>): DrawBatch {
            val late = ByteArray(draws.size * 2)
            draws.forEachIndexed { i, (a, b) -> late[i * 2] = a.toByte(); late[i * 2 + 1] = b.toByte() }
            return DrawBatch(0, draws.size, IntArray(draws.size), null, late, ByteArray(0))
        }
        // {5} ×2, {9} ×2, {5, 9} ×2, {} ×2 : égalité à 2 partout.
        val acc = ForecastAccumulator(setup)
        acc.add(batch(1 to 0, 0 to 1, 1 to 0, 0 to 1, 1 to 1, 1 to 1, 0 to 0, 0 to 0))
        val r = acc.result()
        // Le plus petit ensemble d'abord (vide), puis par identifiants ({5} avant {9}), puis la paire.
        assertEquals(listOf(emptyList(), listOf(5L), listOf(9L), listOf(5L, 9L)), r.lateSets.map { it.taskIds })
        assertEquals(LateSet(emptyList(), 2), r.mostLikelyLateSet)
        assertEquals(listOf(2, 4, 2, 0), r.lateDistribution)
        assertEquals((4 * 1 + 2 * 2) / 8.0, r.expectedLate, 1e-12)
        assertEquals(0.25, r.allOnTimeProbability, 1e-12)
        assertEquals(listOf(4, 4), r.deadlines.map { it.lateDraws })
    }

    @Test
    fun theResultCarriesTheSeedTheRunCountAndTheSource() {
        val r = run(sample(), spread, seed = 99L, runs = 100)
        assertEquals(99L, r.seed)
        assertEquals(100, r.runs)
        assertEquals(100, r.requestedRuns)
        assertEquals(monday, r.day)
        assertEquals(DataSourceKind.HISTORY, r.source.kind)
        assertEquals(10, r.source.samples)
        assertTrue(r.reliable)
        assertFalse(r.interrupted)
    }
}

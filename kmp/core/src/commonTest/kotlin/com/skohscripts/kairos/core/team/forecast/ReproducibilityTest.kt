package com.skohscripts.kairos.core.team.forecast

import com.skohscripts.kairos.core.team.TeamFixtures.member
import com.skohscripts.kairos.core.team.TeamFixtures.monday
import com.skohscripts.kairos.core.team.TeamFixtures.snapshot
import com.skohscripts.kairos.core.team.forecast.ForecastFixtures.dep
import com.skohscripts.kairos.core.team.forecast.ForecastFixtures.task
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Reproductibilité **entre plateformes** : un test de `commonTest` tourne sur JVM, Android et
 * wasm, et ses valeurs sont **écrites en dur** (obtenues sur JVM, le tirage `17` du facteur
 * étant en plus recalculé hors de Kotlin, en Python). Si une cible divergeait (suite de
 * `kotlin.random.Random`, arrondi flottant, ordre de traitement), l'une de ces valeurs
 * changerait. Ne mettre à jour ces nombres qu'en connaissance de cause : un changement du
 * tirage invalide aussi les résultats que les gens ont gardés avec leur graine.
 */
class ReproducibilityTest {
    private fun day(n: Int) = monday.plus(DatePeriod(days = n))

    private val snap = snapshot(
        tasks = listOf(
            task(1, 1, 12.0, deadline = day(2)), task(2, 1, 6.0, priority = 0), task(3, 1, 9.0),
            task(4, 2, 20.0, deadline = day(4)), task(5, 2, 5.0), task(6, 3, 30.0, deadline = day(5)),
            task(7, 3, 4.0, priority = 2), task(8, 2, 7.0),
        ),
        members = listOf(member(1), member(2), member(3)),
        deps = listOf(dep(blocked = 3, blocker = 1), dep(blocked = 8, blocker = 4)),
    )

    private val data = ForecastData(
        errorFactors = listOf(0.6, 0.8, 0.9, 1.0, 1.0, 1.1, 1.3, 1.6, 2.0, 2.5),
        teamThroughput = listOf(2, 0, 3, 1, 4, 2, 0, 3, 2, 1, 2, 3),
        capacityFactors = mapOf(1L to List(8) { 0.6 + it * 0.1 }, 2L to List(8) { 0.5 + it * 0.1 }),
    )

    private val seed = 20_261_001L

    @Test
    fun theFirstFactorOfAKnownDrawIsFixed() {
        val rng = ForecastRandom.stream(ForecastRandom.drawBase(seed, 17), ForecastRandom.DOMAIN_ERROR, ForecastRandom.fnv1a64("uid-4"))
        assertEquals(0.6, data.errorFactor(rng))
    }

    @Test
    fun theEffortModelGivesTheSameNumbersOnEveryTarget() {
        val r = ForecastFixtures.run(snap, data, seed = seed, runs = 1000, options = ForecastOptions(capacityRandomness = true))
        assertEquals(LocalDate(2026, 10, 14), r.finishDate(50))
        assertEquals(LocalDate(2026, 10, 16), r.finishDate(85))
        assertEquals(LocalDate(2026, 10, 19), r.finishDate(95))
        assertEquals(
            listOf(WeekBucket(LocalDate(2026, 10, 5), 123), WeekBucket(LocalDate(2026, 10, 12), 754), WeekBucket(LocalDate(2026, 10, 19), 123)),
            r.finish!!.histogram,
        )
        assertEquals(listOf(1L to 492, 6L to 288, 4L to 221), r.deadlines.map { it.taskId to it.lateDraws })
        assertEquals(listOf(289, 458, 216, 37), r.lateDistribution)
        assertEquals(listOf(emptyList(), listOf(1L)), r.lateSets.take(2).map { it.taskIds })
        assertEquals(listOf(289, 278), r.lateSets.take(2).map { it.draws })
        assertEquals(listOf(2L to 562, 3L to 366, 1L to 277), r.bottleneck.map { it.memberId to it.draws })
        assertEquals(listOf(2, 1, 1), r.finishedBy(day(2))!!.let { listOf(it.atLeast50, it.atLeast85, it.atLeast95) })
        assertEquals(listOf(6, 4, 3), r.finishedBy(day(4))!!.let { listOf(it.atLeast50, it.atLeast85, it.atLeast95) })
        assertEquals(listOf(7, 6, 5), r.finishedBy(day(8))!!.let { listOf(it.atLeast50, it.atLeast85, it.atLeast95) })
    }

    @Test
    fun theThroughputModelGivesTheSameNumbersOnEveryTarget() {
        val r = ForecastFixtures.run(snap, data, seed = seed, runs = 1000, model = ForecastModel.THROUGHPUT)
        assertEquals(LocalDate(2026, 10, 30), r.finishDate(50))
        assertEquals(LocalDate(2026, 11, 13), r.finishDate(85))
        assertEquals(LocalDate(2026, 11, 20), r.finishDate(95))
        assertEquals(
            listOf(5, 189, 326, 236, 143, 68, 23, 7, 2, 1),
            r.finish!!.histogram.map { it.draws },
        )
        assertEquals(LocalDate(2026, 10, 12), r.finish.histogram.first().weekStart)
    }
}

package com.skohscripts.kairos.core.recap

import com.skohscripts.kairos.core.engine.DurationEstimator
import com.skohscripts.kairos.core.model.KairosSnapshot
import com.skohscripts.kairos.core.model.LearningSettings
import com.skohscripts.kairos.core.model.Settings
import com.skohscripts.kairos.core.model.Task
import com.skohscripts.kairos.core.model.TaskStatus
import com.skohscripts.kairos.core.model.WorkSession
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/** Ce que le modèle a appris, vu du bilan (docs/spec/bilan.md § Ce que Kairos apprend). Mercredi 11 novembre 2026, UTC. */
class ModelReviewTest {
    private val now = Instant.parse("2026-11-11T12:00:00Z")
    private val zone = TimeZone.UTC

    /** Une tâche terminée [daysAgo] jours avant maintenant, chronométrée [minutes] minutes, estimée [estimate] minutes. */
    private fun done(id: Long, title: String, daysAgo: Int, minutes: Int, type: String = "Rédaction", estimate: Int? = null): Pair<Task, WorkSession> {
        val end = now - daysAgo.days
        val start = end - minutes.minutes
        val task = Task(id, title, status = TaskStatus.DONE, taskType = type, fibonacciPoints = 3, estimatedMinutes = estimate, createdAt = start, updatedAt = end)
        return task to WorkSession(id, id, start, end, start)
    }

    // Trente rapports d'environ 45 min, un tous les deux jours jusqu'à aujourd'hui (deux cette semaine), puis un rapport ouvert.
    private val history = (1..30).map { i -> done(i.toLong(), "Rapport d'activité $i", daysAgo = 2 * (30 - i), minutes = 40 + (i % 3) * 5, estimate = 30) }
    private val snapshot = KairosSnapshot(
        tasks = history.map { it.first } + Task(100, "Rapport d'activité de décembre", taskType = "Rédaction", fibonacciPoints = 3, createdAt = now, updatedAt = now),
        workSessions = history.map { it.second },
        settings = Settings(learning = LearningSettings()),
    )
    private val monday = LocalDate(2026, 11, 9)
    private val sunday = LocalDate(2026, 11, 15)

    @Test
    fun theReviewSaysWhatTheModelLearnedAndWhereItStands() {
        val review = ModelReview.build(snapshot, monday, sunday, now, zone)
        val status = assertNotNull(review.status)
        assertEquals(30, status.corpus)
        // Terminées dans la semaine du 9 : aujourd'hui et lundi.
        assertEquals(2, review.learnedInPeriod)
        val inWeek = 2
        assertTrue(review.checked <= inWeek)
        assertTrue(review.inside <= review.checked)
        // Réel ÷ estimé des tâches de la semaine : chacune estimée 30 min, chronométrée 40 à 50 min.
        assertTrue(review.bias!!.ratio > 1.0)
        // Le rapport ouvert a une durée apprise fiable.
        assertTrue((review.bySource.values.sum()) >= 1)
        assertEquals(8, review.history.size)
        assertEquals(sunday, review.history.last().date)
        // Le corpus grandit d'une semaine à l'autre, jamais au-delà de ce qui était fini à la date du point.
        assertTrue(review.history.zipWithNext().all { (a, b) -> a.corpus <= b.corpus })
    }

    @Test
    fun aPointNeverSeesTheFuture() {
        val point = ModelReview.history(snapshot, LocalDate(2026, 10, 25), now, zone, weeks = 1).single()
        // Une tâche terminée après ce dimanche ne change pas le point : on en ajoute une, très longue, le 10 novembre.
        val (late, lateSession) = done(200, "Rapport d'activité exceptionnel", daysAgo = 1, minutes = 600)
        val withLate = snapshot.copy(tasks = snapshot.tasks + late, workSessions = snapshot.workSessions + lateSession)
        assertEquals(point, ModelReview.history(withLate, LocalDate(2026, 10, 25), now, zone, weeks = 1).single())
        // À cette date, seules les tâches terminées avant le lundi 26 octobre comptent.
        val before = history.count { (t, _) -> t.updatedAt < Instant.parse("2026-10-26T00:00:00Z") }
        assertEquals(before, point.corpus)
        // Une session à cheval sur la date est arrêtée à la date.
        val cut = Instant.parse("2026-10-26T00:00:00Z")
        val straddling = WorkSession(9, 1, cut - 30.minutes, cut + 60.minutes, cut)
        assertEquals(cut, ModelReview.asOf(KairosSnapshot(workSessions = listOf(straddling)), cut).workSessions.single().endedAt)
    }

    @Test
    fun aModelSwitchedOffGivesNoFigures() {
        val off = snapshot.copy(settings = Settings(learning = LearningSettings(model = false)))
        val review = ModelReview.build(off, monday, sunday, now, zone)
        assertNull(review.status)
        assertEquals(emptyList(), review.history)
        assertTrue(DurationEstimator.Source.MODEL !in review.bySource)
    }
}

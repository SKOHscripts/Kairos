package com.skohscripts.kairos.core.engine

import com.skohscripts.kairos.core.model.KairosSnapshot
import com.skohscripts.kairos.core.model.LearningSettings
import com.skohscripts.kairos.core.model.Settings
import com.skohscripts.kairos.core.model.Task
import com.skohscripts.kairos.core.model.TaskSpace
import com.skohscripts.kairos.core.model.TaskStatus
import com.skohscripts.kairos.core.model.WorkSession
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

/** Durées apprises (docs/spec/durees-apprises.md). */
class DurationEstimatorTest {
    private val t0 = Instant.parse("2026-09-28T07:00:00Z")
    private val settings = Settings(taskTypes = "")

    private fun sample(id: Long, minutes: Int, title: String = "Courrier", series: Long? = 1, space: TaskSpace = TaskSpace.PERSONAL) =
        DurationEstimator.Sample(id, series, title, space, minutes)

    private fun target(title: String = "Courrier", series: Long? = 1, estimated: Int? = null, space: TaskSpace = TaskSpace.PERSONAL, id: Long = 100) =
        Task(id, title, estimatedMinutes = estimated, seriesId = series, space = space, createdAt = t0, updatedAt = t0)

    private fun estimate(samples: List<DurationEstimator.Sample>, task: Task = target(), s: Settings = settings) = DurationEstimator.estimate(task, samples, s)

    @Test
    fun threeOccurrencesGiveAReliableMedianTwoDoNot() {
        val three = estimate(listOf(sample(1, 40), sample(2, 50), sample(3, 45)))!!
        assertEquals(45, three.minutes)
        assertTrue(three.reliable)
        assertEquals(DurationEstimator.Source.SERIES, three.source)
        val two = estimate(listOf(sample(1, 40), sample(2, 50)))!!
        assertEquals(2, two.samples)
        assertFalse(two.reliable, "n = 2 : affichée « peu fiable », jamais appliquée")
        assertEquals(emptyMap(), DurationEstimator.appliedMinutes(mapOf(1L to two)))
        assertEquals(mapOf(1L to 45), DurationEstimator.appliedMinutes(mapOf(1L to three)))
    }

    @Test
    fun anExplicitEstimateIsNeverOverridden() {
        assertNull(estimate(listOf(sample(1, 40), sample(2, 50), sample(3, 45)), target(estimated = 20)))
        // Une estimation à 0 compte comme absente, comme pour le placement.
        assertNotNull(estimate(listOf(sample(1, 40), sample(2, 50), sample(3, 45)), target(estimated = 0)))
    }

    @Test
    fun aForgottenTimerIsClippedBeforeTheMedian() {
        // 30, 30, 30, 600 : 600 > 3 × 30 est écarté, la médiane reste 30 sur 3 occurrences.
        val e = estimate(listOf(sample(1, 30), sample(2, 30), sample(3, 30), sample(4, 600)))!!
        assertEquals(30, e.minutes)
        assertEquals(3, e.samples)
        assertTrue(e.reliable)
    }

    @Test
    fun roundingIsToFiveMinutesHalfUpWithBoundsAndNoHalfEven() {
        assertEquals(15, estimate(listOf(sample(1, 10), sample(2, 15)))!!.minutes) // 12,5 -> 15 (le demi-pair donnerait 10)
        assertEquals(10, estimate(listOf(sample(1, 7), sample(2, 8)))!!.minutes) // 7,5 -> 10
        assertEquals(5, estimate(listOf(sample(1, 2), sample(2, 2)))!!.minutes) // plancher à 5 min
        // Plafond : la fenêtre de travail (9 h par défaut = 540 min).
        assertEquals(540, estimate(listOf(sample(1, 2000), sample(2, 2000), sample(3, 2000)))!!.minutes)
    }

    @Test
    fun theResultDoesNotDependOnTheOrderOfTheHistory() {
        val samples = (1L..8L).map { sample(it, 20 + it.toInt() * 5) }
        assertEquals(estimate(samples), estimate(samples.reversed()))
        assertEquals(estimate(samples), estimate(samples.shuffled(kotlin.random.Random(7))))
    }

    @Test
    fun onlyTheMostRecentOccurrencesCountByIdNotByDate() {
        // Histoire de 8 occurrences ; avec history = 5, seules les ids 4 à 8 comptent (médiane de 40,45,50,55,60 = 50).
        val samples = (1L..8L).map { sample(it, 20 + it.toInt() * 5) }
        assertEquals(50, estimate(samples)!!.minutes)
        assertEquals(5, estimate(samples)!!.samples)
        val short = estimate(samples, s = settings.copy(learning = LearningSettings(history = 3)))!!
        assertEquals(55, short.minutes) // 50, 55, 60 -> 55
    }

    @Test
    fun theSeriesSurvivesARenameAndALegacyTaskFallsBackOnItsTitle() {
        val renamed = listOf(sample(1, 40, "Ancien nom", series = null), sample(2, 40, "Autre", series = 1), sample(3, 40, "Autre", series = 1), sample(4, 40, "Autre", series = 1))
        // La série 1 est l'identifiant de la 1re tâche : la tâche 1 en fait partie, même sous un autre titre.
        assertEquals(DurationEstimator.Source.SERIES, estimate(renamed, target("Nouveau nom", series = 1))!!.source)
        // Sans série (tâche d'avant la 3.4), on se rattache au titre, sans tenir compte de la casse ni des espaces.
        val legacy = listOf(sample(1, 30, "  courrier ", series = null), sample(2, 30, "Courrier", series = null), sample(3, 30, "COURRIER", series = null))
        val byTitle = estimate(legacy, target("Courrier", series = null))!!
        assertEquals(DurationEstimator.Source.TITLE, byTitle.source)
        assertTrue(byTitle.reliable)
        // Un titre différent, sans série : rien.
        assertNull(estimate(legacy, target("Autre chose", series = null)))
    }

    @Test
    fun teamAndPersonalSpacesNeverFeedEachOther() {
        val teamHistory = listOf(sample(1, 40, space = TaskSpace.TEAM), sample(2, 40, space = TaskSpace.TEAM), sample(3, 40, space = TaskSpace.TEAM))
        assertNull(estimate(teamHistory, target(space = TaskSpace.PERSONAL)))
        assertNotNull(estimate(teamHistory, target(space = TaskSpace.TEAM)))
    }

    @Test
    fun aReceivedTaskNeitherLearnsNorTeaches() {
        val received = target().copy(origin = "manager-uid")
        assertNull(estimate(listOf(sample(1, 40), sample(2, 40), sample(3, 40)), received))
        val done = Task(1, "Courrier", status = TaskStatus.DONE, origin = "manager-uid", createdAt = t0, updatedAt = t0)
        assertEquals(emptyList(), DurationEstimator.samples(listOf(done), mapOf(1L to 60)))
    }

    @Test
    fun theFeatureCanBeSwitchedOffAndShortOrZeroSessionsAreIgnored() {
        val history = listOf(sample(1, 40), sample(2, 40), sample(3, 40))
        assertNull(estimate(history, s = settings.copy(learning = LearningSettings(enabled = false))))
        val tasks = (1L..4L).map { Task(it, "Courrier", status = TaskStatus.DONE, createdAt = t0, updatedAt = t0) }
        // 0 min, 1 min : ignorés (jamais comptés comme 0 minute) ; 2 min et plus comptent.
        assertEquals(listOf(3L, 4L), DurationEstimator.samples(tasks, mapOf(1L to 0, 2L to 1, 3L to 2, 4L to 90)).map { it.taskId })
    }

    @Test
    fun estimatesAreComputedFromTheSnapshotForOpenTasksOnly() {
        val done = (1L..3L).map { Task(it, "Courrier", status = TaskStatus.DONE, seriesId = 1, createdAt = t0, updatedAt = t0) }
        val open = Task(10, "Courrier", seriesId = 1, createdAt = t0, updatedAt = t0)
        val sessions = done.map { WorkSession(it.id, it.id, t0, t0 + kotlin.time.Duration.parse("40m"), t0) }
        val snapshot = KairosSnapshot(tasks = done + open, workSessions = sessions)
        val learned = DurationEstimator.estimates(snapshot, t0)
        assertEquals(setOf(10L), learned.keys)
        assertEquals(40, learned.getValue(10L).minutes)
        assertEquals(emptyMap(), DurationEstimator.estimates(snapshot.copy(settings = Settings(taskTypes = "", learning = LearningSettings(enabled = false))), t0))
    }

    @Test
    fun theSchedulerAppliesALearnedDurationOnlyWithoutAnExplicitOne() {
        val noEstimate = Task(1, "A", priority = 1, fibonacciPoints = 3, createdAt = t0, updatedAt = t0)
        val explicit = Task(2, "B", priority = 1, fibonacciPoints = 3, estimatedMinutes = 20, createdAt = t0, updatedAt = t0)
        val learned = mapOf(1L to 45, 2L to 45)
        assertEquals(45, Scheduling.durationMinutes(noEstimate, settings, learned))
        assertEquals(20, Scheduling.durationMinutes(explicit, settings, learned))
        assertEquals(settings.defaultTaskDurationMinutes, Scheduling.durationMinutes(noEstimate, settings))
        // Sans durée apprise, le planning est celui d'avant, octet pour octet.
        val day = LocalDate(2026, 9, 29)
        val before = Scheduling.buildDaySchedule(listOf(noEstimate, explicit), emptyList(), day, null, settings)
        assertEquals(before, Scheduling.buildDaySchedule(listOf(noEstimate, explicit), emptyList(), day, null, settings, learned = emptyMap()))
        val with = Scheduling.buildDaySchedule(listOf(noEstimate, explicit), emptyList(), day, null, settings, learned = learned)
        assertEquals(listOf(45, 20).sorted(), with.scheduled.map { it.durationMinutes }.sorted())
    }
}

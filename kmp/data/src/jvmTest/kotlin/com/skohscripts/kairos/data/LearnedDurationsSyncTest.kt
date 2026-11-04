package com.skohscripts.kairos.data

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.skohscripts.kairos.core.engine.DurationEstimator
import com.skohscripts.kairos.core.model.KairosSnapshot
import com.skohscripts.kairos.core.model.LearningSettings
import com.skohscripts.kairos.core.model.TaskRecurrence
import com.skohscripts.kairos.core.model.TaskStatus
import com.skohscripts.kairos.core.sync.RecordKind
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/**
 * Durées apprises partagées (#100, docs/spec/synchronisation.md § Durées apprises partagées) : deux appareils qui ont chronométré
 * chacun des occurrences, une fois fusionnés, donnent **les mêmes** durées apprises (série, titre et machine learning), alors
 * que leurs identifiants locaux sont dans un ordre différent ; une série terminée sur les deux garde une occurrence suivante.
 */
class LearnedDurationsSyncTest {
    private var now = Instant.parse("2026-11-04T08:00:00Z")
    private val clock = object : Clock {
        override fun now(): Instant = now
    }
    private val day = LocalDate(2026, 11, 4)

    private suspend fun repo(): KairosRepository =
        KairosRepository.open(KairosStore.open(JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)), { KairosSnapshot() }, clock, timeZone = TimeZone.UTC)

    private fun KairosRepository.export(): String = ExportCodec.encode(snapshot.value, "4.0.0", now, sync.value)

    private suspend fun KairosRepository.pull(from: KairosRepository) = merge(ExportCodec.decodeSynced(from.export())) { true }

    /** Une tâche faite en [minutes] (temps saisi), créée maintenant. */
    private suspend fun KairosRepository.done(title: String, minutes: Int, type: String = "") {
        val id = createTask(title)!!
        updateTask(id, TaskEdit(title = title, priority = 1, points = 3, taskType = type, manualTimeSpentMinutes = minutes, pinDay = day))
        toggleDone(id)
    }

    /** Les durées apprises des tâches à faire, par `uid` (les identifiants locaux diffèrent d'un appareil à l'autre). */
    private fun KairosRepository.learned(): Map<String, Pair<Int, Boolean>> {
        val uids = sync.value!!.uids[RecordKind.TASK].orEmpty()
        return DurationEstimator.estimates(snapshot.value, now, TimeZone.UTC).entries.associate { (id, e) -> uids.getValue(id) to (e.minutes to e.reliable) }
    }

    private suspend fun pair(history: Int): Pair<KairosRepository, KairosRepository> {
        val a = repo()
        val b = repo()
        // Mêmes réglages des deux côtés avant le premier contact : pas de conflit de réglage.
        for (r in listOf(a, b)) r.updateSettings(r.snapshot.value.settings.copy(learning = LearningSettings(history = history, minSamples = 2)))
        a.enableSync("PC")
        b.enableSync("Phone")
        return a to b
    }

    @Test
    fun twoMergedDevicesLearnTheSameDurations() = runTest {
        var compared = 0
        for (seed in 1..20) {
            val random = Random(seed)
            val (a, b) = pair(history = 3)
            a.createTask("Courrier")
            a.createTask("Rapport trimestriel")
            // Chacun chronomètre à son tour, sans voir l'autre : les identifiants locaux suivent l'ordre de chaque appareil.
            repeat(14) {
                now += (1 + random.nextInt(120)).minutes
                val device = if (random.nextBoolean()) a else b
                val title = if (random.nextBoolean()) "Courrier" else "Rapport trimestriel"
                device.done(title, 5 + random.nextInt(56), type = if (title == "Courrier") "Admin" else "Rédaction")
            }
            b.pull(a)
            a.pull(b)
            b.pull(a)
            val onA = a.learned()
            val onB = b.learned()
            assertTrue(onA.isNotEmpty(), "graine $seed")
            assertEquals(onA, onB, "graine $seed")
            compared += onA.size
        }
        assertTrue(compared >= 40)
    }

    @Test
    fun theSampleOrderIsTheSameOnBothDevicesAndAbsentWithoutSync() = runTest {
        val solo = repo()
        solo.done("Courrier", 10)
        assertEquals(null, solo.snapshot.value.sampleOrder)
        val (a, b) = pair(history = 5)
        a.done("Courrier", 10)
        now += 5.minutes
        b.done("Courrier", 20)
        now += 5.minutes
        a.done("Courrier", 30)
        b.pull(a)
        a.pull(b)
        fun KairosRepository.byRank(): List<String> {
            val uids = sync.value!!.uids[RecordKind.TASK].orEmpty()
            return snapshot.value.sampleOrder!!.entries.sortedBy { it.value }.map { uids.getValue(it.key) }
        }
        assertEquals(a.byRank(), b.byRank())
        assertEquals(3, a.byRank().size)
    }

    @Test
    fun nextOccurrenceIsNotDuplicated() = runTest {
        val (a, b) = pair(history = 5)
        val id = a.createTask("Relever le courrier")!!
        a.updateTask(id, TaskEdit(title = "Relever le courrier", priority = 1, points = 1, deadline = day, recurrence = TaskRecurrence.WEEKLY, manualTimeSpentMinutes = 12, pinDay = day))
        b.pull(a)
        // Terminée sur les deux appareils avant qu'ils se revoient : une seule occurrence suivante après la fusion.
        a.toggleDone(id)
        b.toggleDone(b.snapshot.value.tasks.single { it.title == "Relever le courrier" }.id)
        a.pull(b)
        b.pull(a)
        for (r in listOf(a, b)) {
            val open = r.snapshot.value.tasks.filter { it.title == "Relever le courrier" && it.status == TaskStatus.TODO }
            assertEquals(1, open.size)
        }
        // Le même `seriesId` logique : la première tâche de la série, du même `uid` des deux côtés.
        fun KairosRepository.seriesUid(): String {
            val next = snapshot.value.tasks.single { it.title == "Relever le courrier" && it.status == TaskStatus.TODO }
            return sync.value!!.uids[RecordKind.TASK].orEmpty().getValue(next.seriesId!!)
        }
        assertEquals(a.seriesUid(), b.seriesUid())
        assertEquals(a.learned(), b.learned())
    }
}

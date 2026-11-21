package com.skohscripts.kairos.data

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.skohscripts.kairos.core.model.KairosSnapshot
import com.skohscripts.kairos.core.model.Task
import com.skohscripts.kairos.core.model.TaskSpace
import com.skohscripts.kairos.core.model.TaskStatus
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Clock
import kotlin.time.Instant

/** Temps passé d'un toucher (docs/spec/durees-reelles.md, issue #271) : `setSpent` garde le jour de fin de la tâche. */
class SpentTest {
    private val now = Instant.parse("2026-11-24T18:00:00Z")
    private val clock = object : Clock {
        override fun now() = now
    }
    private val done = Instant.parse("2026-11-18T10:00:00Z")

    private suspend fun repo(): KairosRepository =
        KairosRepository.open(KairosStore.open(JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)), { KairosSnapshot() }, clock, timeZone = TimeZone.UTC).also {
            it.replaceAll(
                KairosSnapshot(
                    tasks = listOf(
                        Task(1, "Rapport", status = TaskStatus.DONE, createdAt = done, updatedAt = done),
                        Task(2, "Équipe", status = TaskStatus.DONE, space = TaskSpace.TEAM, createdAt = done, updatedAt = done),
                    ),
                ),
            )
        }

    private fun KairosRepository.task(id: Long) = snapshot.value.tasks.single { it.id == id }

    @Test
    fun noting_the_time_keeps_the_day_the_task_was_done() = runTest {
        val r = repo()
        r.setSpent(1, 45)
        assertEquals(45, r.task(1).manualTimeSpentMinutes)
        assertEquals(done, r.task(1).updatedAt)
        // « Annuler » rend la tâche sans temps.
        r.setSpent(1, null)
        assertNull(r.task(1).manualTimeSpentMinutes)
        assertEquals(done, r.task(1).updatedAt)
    }

    @Test
    fun ignoring_stores_zero_and_values_are_bounded() = runTest {
        val r = repo()
        r.setSpent(1, 0)
        assertEquals(0, r.task(1).manualTimeSpentMinutes)
        r.setSpent(1, 99_999)
        assertEquals(1440, r.task(1).manualTimeSpentMinutes)
    }

    @Test
    fun a_team_task_is_never_touched() = runTest {
        val r = repo()
        r.setSpent(2, 30)
        assertNull(r.task(2).manualTimeSpentMinutes)
    }
}

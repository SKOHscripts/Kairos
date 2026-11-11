package com.skohscripts.kairos.data

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.skohscripts.kairos.core.engine.TimeCorrection
import com.skohscripts.kairos.core.engine.TimeTracking
import com.skohscripts.kairos.core.model.KairosSnapshot
import com.skohscripts.kairos.core.model.Task
import com.skohscripts.kairos.core.model.TaskSpace
import com.skohscripts.kairos.core.model.WorkSession
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Clock
import kotlin.time.Instant

/** Corriger le temps passé dans la fiche (docs/spec/fiche-tache.md § Temps passé) : une écriture, et « Annuler ». */
class SpentCorrectionTest {
    private val now = Instant.parse("2026-11-11T18:00:00Z")
    private val clock = object : Clock {
        override fun now() = now
    }
    private val t0 = Instant.parse("2026-11-01T08:00:00Z")

    private suspend fun repo(space: TaskSpace = TaskSpace.PERSONAL): KairosRepository =
        KairosRepository.open(KairosStore.open(JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)), { KairosSnapshot() }, clock, timeZone = TimeZone.UTC).also {
            it.replaceAll(
                KairosSnapshot(
                    tasks = listOf(Task(1, "Rapport", manualTimeSpentMinutes = 30, space = space, createdAt = t0, updatedAt = t0)),
                    // Un chrono oublié de 6 h mardi, 1 h lundi.
                    workSessions = listOf(
                        WorkSession(1, 1, Instant.parse("2026-11-09T09:00:00Z"), Instant.parse("2026-11-09T10:00:00Z"), t0),
                        WorkSession(2, 1, Instant.parse("2026-11-10T09:00:00Z"), Instant.parse("2026-11-10T15:00:00Z"), t0),
                    ),
                ),
            )
        }

    private fun KairosRepository.spent() = TimeTracking.spentMinutesByTask(snapshot.value.workSessions, now, snapshot.value.tasks).getValue(1)

    private fun edit(plan: TimeCorrection.Plan) =
        TaskEdit(title = "Rapport", pinDay = LocalDate(2026, 11, 11), manualTimeSpentMinutes = plan.manual, sessionEnds = plan.ends)

    @Test
    fun loweringTheTotalTrimsTheForgottenSessionAndUndoRestoresIt() = runTest {
        val r = repo()
        val before = r.snapshot.value
        val plan = TimeCorrection.plan(before.workSessions, 30, target = 2 * 60, now = now, trimmable = true)
        r.updateTask(1, edit(plan))
        assertEquals(120, r.spent())
        assertEquals(Instant.parse("2026-11-10T10:00:00Z"), r.snapshot.value.workSessions.single { it.id == 2L }.endedAt)
        assertEquals(null, r.snapshot.value.tasks.single().manualTimeSpentMinutes)
        // « Annuler » : temps saisi et fins d'avant.
        r.restoreSpent(1, 30, before.workSessions.associate { it.id to it.endedAt!! })
        assertEquals(7 * 60 + 30, r.spent())
    }

    @Test
    fun aTeamTaskNeverTouchesItsSessions() = runTest {
        val r = repo(TaskSpace.TEAM)
        // Même si la fiche envoyait des fins (elle ne le fait pas), la tâche d'équipe garde ses sessions.
        r.updateTask(1, TaskEdit(title = "Rapport", pinDay = LocalDate(2026, 11, 11), sessionEnds = mapOf(2L to Instant.parse("2026-11-10T10:00:00Z"))))
        assertEquals(Instant.parse("2026-11-10T15:00:00Z"), r.snapshot.value.workSessions.single { it.id == 2L }.endedAt)
    }
}

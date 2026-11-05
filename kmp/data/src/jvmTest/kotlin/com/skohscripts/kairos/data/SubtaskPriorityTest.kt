package com.skohscripts.kairos.data

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.skohscripts.kairos.core.model.KairosSnapshot
import com.skohscripts.kairos.core.model.Settings
import com.skohscripts.kairos.core.model.TaskStatus
import com.skohscripts.kairos.core.model.TeamSettings
import com.skohscripts.kairos.core.team.TeamEventKind
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Clock
import kotlin.time.Instant

/** Priorité imposée aux sous-tâches (docs/spec/sous-taches.md § Priorité, issue #201). */
class SubtaskPriorityTest {
    private val now = Instant.parse("2026-11-05T08:00:00Z")
    private val clock = object : Clock {
        override fun now() = now
    }
    private val today = LocalDate(2026, 11, 5)

    private suspend fun empty(): KairosRepository =
        KairosRepository.open(KairosStore.open(JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)), { KairosSnapshot() }, clock, timeZone = TimeZone.UTC)

    private fun KairosRepository.priority(id: Long) = snapshot.value.tasks.single { it.id == id }.priority

    private fun edit(title: String, priority: Int?, newSubtasks: String = "") = TaskEdit(title = title, priority = priority, pinDay = today, newSubtasks = newSubtasks)

    @Test
    fun aNewSubtaskTakesThePriorityOfItsMother() = runTest {
        val repo = empty()
        val mother = repo.createTask("A")!!
        repo.setPriority(mother, 0)
        val child = repo.createTask("B", parentId = mother)!!
        assertEquals(0, repo.priority(child))
        // En lot depuis la fiche : la priorité enregistrée dans la même fiche.
        repo.updateTask(mother, edit("A", 1, newSubtasks = "C\nD"))
        val batch = repo.snapshot.value.tasks.filter { it.title in setOf("C", "D") }
        assertEquals(listOf(1, 1), batch.map { it.priority })
        // Une mère sans priorité n'impose rien.
        val free = repo.createTask("Libre")!!
        assertEquals(null, repo.priority(repo.createTask("Fille libre", parentId = free)!!))
    }

    @Test
    fun raisingAMotherRaisesItsLessImportantOpenDescendants() = runTest {
        val repo = empty()
        val a = repo.createTask("A")!!
        repo.setPriority(a, 2)
        val c = repo.createTask("C", parentId = a)!!
        val d = repo.createTask("D", parentId = c)!!
        val done = repo.createTask("Faite", parentId = a)!!
        repo.toggleDone(done)
        repo.setPriority(c, 1)
        assertEquals(1, repo.priority(c))
        assertEquals(1, repo.priority(d)) // de proche en proche
        repo.updateTask(a, edit("A", 0))
        assertEquals(listOf(0, 0), listOf(repo.priority(c), repo.priority(d)))
        assertEquals(2, repo.priority(done)) // l'historique ne change pas
        assertEquals(TaskStatus.DONE, repo.snapshot.value.tasks.single { it.id == done }.status)
    }

    @Test
    fun aSubtaskIsNeverLessImportantThanItsAncestors() = runTest {
        val repo = empty()
        val a = repo.createTask("A")!!
        repo.setPriority(a, 1)
        val b = repo.createTask("B", parentId = a)!!
        repo.setPriority(b, 2)
        assertEquals(1, repo.priority(b))
        repo.setPriority(b, null)
        assertEquals(1, repo.priority(b))
        repo.updateTask(b, edit("B", 2))
        assertEquals(1, repo.priority(b))
        // Plus prioritaire que sa mère : permis.
        repo.setPriority(b, 0)
        assertEquals(0, repo.priority(b))
    }

    @Test
    fun aTeamSubtaskInheritsAndItsRaiseIsJournaled() = runTest {
        val repo = empty()
        repo.replaceAll(KairosSnapshot(settings = Settings(team = TeamSettings(enabled = true, identity = "id-1"))))
        val mother = repo.createTeamTask("Mère")!!
        repo.setPriority(mother, 1)
        val child = repo.createTeamTask("Fille", mother)!!
        assertEquals(1, repo.priority(child))
        repo.setPriority(mother, 0)
        assertEquals(0, repo.priority(child))
        val raise = repo.snapshot.value.teamEvents.filter { it.taskId == child && it.kind == TeamEventKind.QUALIFIED }
        assertEquals(listOf("priority:1" to "priority:0"), raise.map { it.fromValue to it.toValue }.filter { it.second?.startsWith("priority") == true })
    }
}

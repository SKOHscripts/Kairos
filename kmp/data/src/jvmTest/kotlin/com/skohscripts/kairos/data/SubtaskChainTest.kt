package com.skohscripts.kairos.data

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.skohscripts.kairos.core.model.KairosSnapshot
import com.skohscripts.kairos.core.model.Settings
import com.skohscripts.kairos.core.model.TeamSettings
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Clock
import kotlin.time.Instant

/** File de sous-tâches enchaînées (docs/spec/sous-taches.md § File, issue #209). */
class SubtaskChainTest {
    private val clock = object : Clock {
        override fun now() = Instant.parse("2026-11-05T08:00:00Z")
    }
    private val today = LocalDate(2026, 11, 5)

    private suspend fun empty(): KairosRepository =
        KairosRepository.open(KairosStore.open(JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)), { KairosSnapshot() }, clock, timeZone = TimeZone.UTC)

    private fun edit(title: String, newSubtasks: String, chain: Boolean) =
        TaskEdit(title = title, pinDay = today, newSubtasks = newSubtasks, chainNewSubtasks = chain)

    /** Les dépendances, en titres : (bloquée, bloquante). */
    private fun KairosRepository.chains(): Set<Pair<String, String>> {
        val title = snapshot.value.tasks.associate { it.id to it.title }
        return snapshot.value.dependencies.mapTo(HashSet()) { title.getValue(it.taskId) to title.getValue(it.blockerId) }
    }

    @Test
    fun newSubtasksQueueUpInLineOrderAndTheChoiceIsRemembered() = runTest {
        val repo = empty()
        val mother = repo.createTask("A")!!
        assertNull(repo.snapshot.value.settings.subtaskChain) // coupé la première fois
        repo.updateTask(mother, edit("A", "B\n\n  C \nD", chain = true))
        assertEquals(setOf("C" to "B", "D" to "C"), repo.chains())
        assertEquals(true, repo.snapshot.value.settings.subtaskChain)
        // La mère ne prend aucun bloqueur, et les sous-tâches existantes ne s'enchaînent pas aux nouvelles.
        repo.updateTask(mother, edit("A", "E\nF", chain = true))
        assertEquals(setOf("C" to "B", "D" to "C", "F" to "E"), repo.chains())
    }

    @Test
    fun switchedOffOnlySubtasksAreBornAndTheChoiceIsForgotten() = runTest {
        val repo = empty()
        val mother = repo.createTask("A")!!
        repo.updateTask(mother, edit("A", "B\nC", chain = true))
        repo.updateTask(mother, edit("A", "D\nE", chain = false))
        assertEquals(setOf("C" to "B"), repo.chains())
        assertNull(repo.snapshot.value.settings.subtaskChain)
        // Sans nouvelle sous-tâche, le choix n'a pas servi : il n'est pas retenu.
        repo.updateTask(mother, edit("A", "", chain = true))
        assertNull(repo.snapshot.value.settings.subtaskChain)
        assertEquals(5, repo.snapshot.value.tasks.size)
    }

    @Test
    fun aTeamTaskQueuesItsSubtasksInTheTeamSpace() = runTest {
        val repo = empty()
        repo.replaceAll(KairosSnapshot(settings = Settings(team = TeamSettings(enabled = true, identity = "id-1"))))
        val mother = repo.createTeamTask("Mère")!!
        repo.updateTask(mother, edit("Mère", "Un\nDeux", chain = true))
        assertEquals(setOf("Deux" to "Un"), repo.chains())
        val space = repo.snapshot.value.tasks.single { it.id == mother }.space
        assertEquals(setOf(space), repo.snapshot.value.tasks.mapTo(HashSet()) { it.space })
    }
}

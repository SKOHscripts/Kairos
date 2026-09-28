package com.skohscripts.kairos.data

import app.cash.sqldelight.async.coroutines.awaitAsList
import app.cash.sqldelight.async.coroutines.awaitAsOneOrNull
import com.skohscripts.kairos.core.model.FIBONACCI_SCALE
import com.skohscripts.kairos.core.model.KairosSnapshot
import com.skohscripts.kairos.core.model.PRIORITY_VALUES
import com.skohscripts.kairos.core.model.Settings
import com.skohscripts.kairos.core.model.Task
import com.skohscripts.kairos.core.model.TaskStatus
import com.skohscripts.kairos.data.db.KairosDatabase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.Json
import kotlin.time.Clock

/**
 * Accès à la base (docs/spec-v3/modele-donnees.md § Dépôt).
 *
 * Toute la base est relue après chaque écriture et publiée dans [snapshot] :
 * les volumes d'un outil personnel (quelques milliers de lignes au plus) le
 * permettent, l'interface dérive tout d'un seul état cohérent, et le moteur
 * d'ordonnancement (jalon M2) a de toute façon besoin de toutes les tâches.
 * Les écritures sont sérialisées ([Mutex]) et transactionnelles ; [onChanged]
 * reçoit chaque nouvel état (sauvegarde de la version web).
 */
class KairosRepository(
    private val database: KairosDatabase,
    private val clock: Clock = Clock.System,
    private val onChanged: suspend (KairosSnapshot) -> Unit = {},
) {
    private val queries get() = database.kairosQueries
    private val mutex = Mutex()
    private val state = MutableStateFlow(KairosSnapshot())

    /** Dernier état lu de la base. */
    val snapshot: StateFlow<KairosSnapshot> = state.asStateFlow()

    /** Relit la base et publie l'état (à l'ouverture). */
    suspend fun load(): KairosSnapshot = mutex.withLock {
        readAll().also { state.value = it }
    }

    /** Capture : crée une tâche titre seul (ni priorité ni points : elle arrive « À traiter »). */
    suspend fun createTask(title: String): Long? {
        val clean = title.trim()
        if (clean.isEmpty()) return null
        var id: Long? = null
        write {
            val now = now()
            queries.insertTask(
                clean, "", null, null, "", TaskStatus.TODO.code, null, null, null, "", null, null, null, "", "",
                null, null, now, now,
            )
            id = queries.lastInsertedId().awaitAsOneOrNull()
        }
        return id
    }

    /** Qualification en un clic ; une valeur hors échelle est ignorée (vidée). */
    suspend fun setPriority(taskId: Long, priority: Int?) =
        modify(taskId) { it.copy(priority = priority?.takeIf { p -> p in PRIORITY_VALUES }) }

    suspend fun setPoints(taskId: Long, points: Int?) =
        modify(taskId) { it.copy(fibonacciPoints = points?.takeIf { p -> p in FIBONACCI_SCALE }) }

    /**
     * Fait ↔ à faire (Kairos 2 `toggle_task_done`). Terminer ferme le chrono
     * éventuellement ouvert sur la tâche. Une tâche archivée n'est pas touchée.
     * La recréation d'une occurrence récurrente arrive avec le moteur (M2).
     */
    suspend fun toggleDone(taskId: Long) {
        val task = find(taskId) ?: return
        val next = when (task.status) {
            TaskStatus.TODO -> TaskStatus.DONE
            TaskStatus.DONE -> TaskStatus.TODO
            TaskStatus.ARCHIVED -> return
        }
        write {
            if (next == TaskStatus.DONE) queries.closeOpenSessionsOf(now(), taskId)
            updateRow(task.copy(status = next))
        }
    }

    /** Champs essentiels du dialogue d'édition. Un titre vide garde l'ancien. */
    suspend fun updateEssentials(taskId: Long, edit: TaskEdit) = modify(taskId) { task ->
        task.copy(
            title = edit.title.trim().ifEmpty { task.title },
            description = edit.description,
            priority = edit.priority?.takeIf { it in PRIORITY_VALUES },
            fibonacciPoints = edit.points?.takeIf { it in FIBONACCI_SCALE },
            deadline = edit.deadline,
            estimatedMinutes = edit.estimatedMinutes?.takeIf { it > 0 },
            projectTag = edit.projectTag.trim(),
            taskType = edit.taskType.trim(),
        )
    }

    /**
     * Suppression définitive (Kairos 2 `delete_task`) : les dépendances où la
     * tâche figure sont supprimées avec elle ; ses sous-tâches et sessions
     * restent (références « molles »).
     */
    suspend fun deleteTask(taskId: Long) = write {
        queries.deleteDependenciesOf(taskId)
        queries.deleteTask(taskId)
    }

    suspend fun updateSettings(settings: Settings) = write {
        queries.writeSettings(json.encodeToString(Settings.serializer(), settings))
    }

    /**
     * Remplace **tout** le contenu de la base par [snapshot], identifiants
     * compris (import, exemples d'une base neuve, rechargement de la version
     * web). Transaction unique : en cas d'erreur, la base reste intacte.
     */
    suspend fun replaceAll(snapshot: KairosSnapshot) = write {
        queries.deleteAllTasks()
        queries.deleteAllTimeBlocks()
        queries.deleteAllDependencies()
        queries.deleteAllWorkSessions()
        queries.deleteAllNotes()
        queries.deleteSettings()
        for (t in snapshot.tasks) {
            queries.insertTaskWithId(
                t.id, t.title, t.description, t.priority?.toLong(), t.deadline.store(), t.projectTag, t.status.code,
                t.estimatedMinutes?.toLong(), t.pinnedStart.store(), t.parentId, t.recurrence.code,
                t.scheduledDate.store(), t.recurrenceDayOfMonth?.toLong(), t.recurrenceDayOfWeek?.toLong(),
                t.recurrencePeriod, t.taskType, t.fibonacciPoints?.toLong(), t.manualTimeSpentMinutes?.toLong(),
                t.createdAt.store(), t.updatedAt.store(),
            )
        }
        for (b in snapshot.timeBlocks) {
            queries.insertTimeBlockWithId(
                b.id, b.title, b.start.toString(), b.end.toString(), b.kind.code, b.recurrence.code, b.createdAt.store(),
            )
        }
        for (d in snapshot.dependencies) queries.insertDependencyWithId(d.id, d.taskId, d.blockerId, d.createdAt.store())
        for (s in snapshot.workSessions) {
            queries.insertWorkSessionWithId(s.id, s.taskId, s.startedAt.store(), s.endedAt?.store(), s.createdAt.store())
        }
        for (n in snapshot.notes) {
            queries.insertNoteWithId(n.id, n.body, n.status.code, n.convertedTaskId, n.createdAt.store(), n.updatedAt.store())
        }
        queries.writeSettings(json.encodeToString(Settings.serializer(), snapshot.settings))
    }

    // --- interne -----------------------------------------------------------------

    private fun now(): String = clock.now().store()

    private fun find(taskId: Long): Task? = state.value.tasks.firstOrNull { it.id == taskId }

    private suspend fun modify(taskId: Long, change: (Task) -> Task) {
        val task = find(taskId) ?: return
        write { updateRow(change(task)) }
    }

    private suspend fun updateRow(t: Task) {
        queries.updateTask(
            t.title, t.description, t.priority?.toLong(), t.deadline.store(), t.projectTag, t.status.code,
            t.estimatedMinutes?.toLong(), t.pinnedStart.store(), t.parentId, t.recurrence.code,
            t.scheduledDate.store(), t.recurrenceDayOfMonth?.toLong(), t.recurrenceDayOfWeek?.toLong(),
            t.recurrencePeriod, t.taskType, t.fibonacciPoints?.toLong(), t.manualTimeSpentMinutes?.toLong(),
            now(), t.id,
        )
    }

    private suspend fun write(block: suspend () -> Unit) {
        val updated = mutex.withLock {
            database.transaction { block() }
            readAll().also { state.value = it }
        }
        onChanged(updated)
    }

    private suspend fun readAll(): KairosSnapshot = KairosSnapshot(
        tasks = queries.allTasks().awaitAsList().map { it.toModel() },
        timeBlocks = queries.allTimeBlocks().awaitAsList().map { it.toModel() },
        dependencies = queries.allDependencies().awaitAsList().map { it.toModel() },
        workSessions = queries.allWorkSessions().awaitAsList().map { it.toModel() },
        notes = queries.allNotes().awaitAsList().map { it.toModel() },
        settings = queries.readSettings().awaitAsOneOrNull()
            ?.let { runCatching { json.decodeFromString(Settings.serializer(), it) }.getOrNull() }
            ?: Settings(),
    )

    companion object {
        /** Réglages : champs inconnus ignorés, champs absents à leur valeur par défaut. */
        internal val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

        /** Ouvre la base et, si elle est neuve, y pose [examples] (données d'exemple). */
        suspend fun open(
            opened: KairosStore.Opened,
            examples: () -> KairosSnapshot,
            clock: Clock = Clock.System,
            onChanged: suspend (KairosSnapshot) -> Unit = {},
        ): KairosRepository {
            val repository = KairosRepository(opened.database, clock, onChanged)
            if (opened.created) {
                // Une erreur de pose des exemples ne doit jamais empêcher le démarrage (Kairos 2).
                runCatching { repository.replaceAll(examples()) }
            }
            repository.load()
            return repository
        }
    }
}

/** Champs essentiels du dialogue d'édition (docs/spec-v3/vue-jour.md § Édition). */
data class TaskEdit(
    val title: String,
    val description: String = "",
    val priority: Int? = null,
    val points: Int? = null,
    val deadline: LocalDate? = null,
    val estimatedMinutes: Int? = null,
    val projectTag: String = "",
    val taskType: String = "",
)

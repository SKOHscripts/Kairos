package com.skohscripts.kairos.data

import app.cash.sqldelight.async.coroutines.awaitAsList
import app.cash.sqldelight.async.coroutines.awaitAsOneOrNull
import com.skohscripts.kairos.core.engine.Dependencies
import com.skohscripts.kairos.core.engine.Recurrence
import com.skohscripts.kairos.core.engine.Workdays
import com.skohscripts.kairos.core.model.BlockKind
import com.skohscripts.kairos.core.model.BlockRecurrence
import com.skohscripts.kairos.core.model.FIBONACCI_SCALE
import com.skohscripts.kairos.core.model.KairosSnapshot
import com.skohscripts.kairos.core.model.PRIORITY_VALUES
import com.skohscripts.kairos.core.model.Settings
import com.skohscripts.kairos.core.model.Task
import com.skohscripts.kairos.core.model.TaskRecurrence
import com.skohscripts.kairos.core.model.TaskStatus
import com.skohscripts.kairos.data.db.KairosDatabase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atTime
import kotlinx.datetime.todayIn
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
    private val timeZone: TimeZone = TimeZone.currentSystemDefault(),
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

    /**
     * Capture : crée une tâche titre seul (ni priorité ni points : elle arrive
     * « À traiter »). [parentId] en fait une sous-tâche ; une mère disparue est
     * ignorée (la tâche naît au premier niveau).
     */
    suspend fun createTask(title: String, parentId: Long? = null): Long? {
        val clean = title.trim()
        if (clean.isEmpty()) return null
        var id: Long? = null
        write {
            val parent = parentId?.takeIf { p -> state.value.tasks.any { it.id == p } }
            id = insertNew(newTask(clean, parent))
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
     * éventuellement ouvert sur la tâche et, pour une récurrente, crée
     * l'occurrence suivante (`Recurrence.nextOccurrence`, garde anti-doublon
     * comprise). Une tâche archivée n'est pas touchée.
     */
    suspend fun toggleDone(taskId: Long) {
        val task = find(taskId) ?: return
        val next = when (task.status) {
            TaskStatus.TODO -> TaskStatus.DONE
            TaskStatus.DONE -> TaskStatus.TODO
            TaskStatus.ARCHIVED -> return
        }
        write {
            updateRow(task.copy(status = next))
            if (next == TaskStatus.DONE) {
                queries.closeOpenSessionsOf(now(), taskId)
                Recurrence.nextOccurrence(task, today(), state.value.tasks, clock.now())?.let { insertNew(it) }
            }
        }
    }

    /**
     * « Décaler » (Kairos 2 `snooze_task`) : l'échéance passe au jour ouvré qui
     * suit (elle-même si elle est à venir, sinon aujourd'hui) ; week-ends et
     * jours fériés des réglages sautés.
     */
    suspend fun snooze(taskId: Long) {
        val settings = state.value.settings
        val today = today()
        val holidays = Workdays.holidaysFor(today, settings.holidaysFr, settings.extraHolidays)
        modify(taskId) { it.copy(deadline = Recurrence.nextSnoozeDate(it.deadline, today, holidays)) }
    }

    /**
     * Occurrences du mois de [today] des séries « le N du mois »
     * (`ensure_calendar_occurrences`). N'écrit rien s'il n'y a rien à créer :
     * appelé à chaque affichage de la vue Jour.
     */
    suspend fun ensureCalendarOccurrences(today: LocalDate) {
        val settings = state.value.settings
        val holidays = Workdays.holidaysFor(today, settings.holidaysFr, settings.extraHolidays)
        if (Recurrence.calendarOccurrences(state.value.tasks, today, holidays, clock.now()).isEmpty()) return
        write {
            // Recalculé dans la transaction : l'état a pu changer depuis la vérification.
            Recurrence.calendarOccurrences(state.value.tasks, today, holidays, clock.now()).forEach { insertNew(it) }
        }
    }

    /**
     * Édition complète (Kairos 2 `edit_task`), un seul enregistrement : champs,
     * récurrence, heure fixe, sous-tâches en lot, bloqueurs. Un titre vide
     * garde l'ancien ; une valeur hors échelle est vidée.
     */
    suspend fun updateTask(taskId: Long, edit: TaskEdit) {
        val task = find(taskId) ?: return
        write {
            val weekly = edit.recurrence == TaskRecurrence.WEEKLY
            val onDay = edit.recurrence == TaskRecurrence.MONTHLY_ON_DAY
            updateRow(
                task.copy(
                    title = edit.title.trim().ifEmpty { task.title },
                    description = edit.description,
                    priority = edit.priority?.takeIf { it in PRIORITY_VALUES },
                    fibonacciPoints = edit.points?.takeIf { it in FIBONACCI_SCALE },
                    deadline = edit.deadline,
                    scheduledDate = edit.scheduledDate,
                    estimatedMinutes = edit.estimatedMinutes?.takeIf { it > 0 },
                    projectTag = edit.projectTag.trim(),
                    taskType = edit.taskType.trim(),
                    recurrence = edit.recurrence,
                    // Le jour du mois ne vaut que pour la série calendaire ; l'ancre de la
                    // récurrence hebdomadaire est le jour de l'échéance (recurrence.md).
                    recurrenceDayOfMonth = edit.recurrenceDayOfMonth?.takeIf { onDay && it in 1..31 },
                    recurrenceDayOfWeek = edit.deadline?.takeIf { weekly }?.let(Workdays::weekday),
                    // La date programmée prime sur le jour affiché : l'heure fixe tombe ce jour-là.
                    pinnedStart = edit.pinTime?.let { (edit.scheduledDate ?: edit.pinDay).atTime(it) },
                    manualTimeSpentMinutes = edit.manualTimeSpentMinutes?.takeIf { it >= 0 },
                ),
            )
            edit.newSubtasks.lines().map { it.trim() }.filter { it.isNotEmpty() }.forEach { insertNew(newTask(it, taskId)) }
            setBlockers(taskId, edit.blockerIds)
        }
    }

    /**
     * Bloqueurs : [target] est l'ensemble **complet** voulu. Les absents sont
     * retirés ; un ajout qui créerait un cycle (ou une tâche inconnue) est
     * ignoré sans faire échouer le reste.
     */
    private suspend fun setBlockers(taskId: Long, target: Set<Long>) {
        val deps = state.value.dependencies
        val existing = deps.filter { it.taskId == taskId }.map { it.blockerId }.toSet()
        val wanted = target - taskId
        (existing - wanted).forEach { queries.deleteDependency(taskId, it) }
        val edges = deps.filter { it.taskId != taskId || it.blockerId in wanted }
            .map { Dependencies.Edge(it.taskId, it.blockerId) }.toMutableList()
        val known = state.value.tasks.map { it.id }.toSet()
        for (blocker in (wanted - existing).sorted()) {
            if (blocker !in known || Dependencies.wouldCreateCycle(edges, taskId, blocker)) continue
            queries.insertDependency(taskId, blocker, now())
            edges += Dependencies.Edge(taskId, blocker)
        }
    }

    /** Créneau (réunion ou deep work, ponctuel ou modèle récurrent) ; refusé si la fin n'est pas après le début. */
    suspend fun createBlock(edit: BlockEdit): Boolean {
        if (edit.end <= edit.start) return false
        write { queries.insertTimeBlock(edit.title.trim(), edit.start.toString(), edit.end.toString(), edit.kind.code, edit.recurrence.code, now()) }
        return true
    }

    /** Pour un récurrent, modifie le modèle, donc toutes ses occurrences. */
    suspend fun updateBlock(blockId: Long, edit: BlockEdit): Boolean {
        if (edit.end <= edit.start || state.value.timeBlocks.none { it.id == blockId }) return false
        write { queries.updateTimeBlock(edit.title.trim(), edit.start.toString(), edit.end.toString(), edit.kind.code, edit.recurrence.code, blockId) }
        return true
    }

    suspend fun deleteBlock(blockId: Long) = write { queries.deleteTimeBlock(blockId) }

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

    private fun today(): LocalDate = clock.todayIn(timeZone)

    private fun newTask(title: String, parentId: Long?) = Task(
        id = 0, title = title, parentId = parentId, createdAt = clock.now(), updatedAt = clock.now(),
    )

    /** Insère une nouvelle tâche (identifiant attribué par la base) et le rend. */
    private suspend fun insertNew(t: Task): Long? {
        queries.insertTask(
            t.title, t.description, t.priority?.toLong(), t.deadline.store(), t.projectTag, t.status.code,
            t.estimatedMinutes?.toLong(), t.pinnedStart.store(), t.parentId, t.recurrence.code,
            t.scheduledDate.store(), t.recurrenceDayOfMonth?.toLong(), t.recurrenceDayOfWeek?.toLong(),
            t.recurrencePeriod, t.taskType, t.fibonacciPoints?.toLong(), t.manualTimeSpentMinutes?.toLong(),
            now(), now(),
        )
        return queries.lastInsertedId().awaitAsOneOrNull()
    }

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
            timeZone: TimeZone = TimeZone.currentSystemDefault(),
            onChanged: suspend (KairosSnapshot) -> Unit = {},
        ): KairosRepository {
            val repository = KairosRepository(opened.database, clock, timeZone, onChanged)
            if (opened.created) {
                // Une erreur de pose des exemples ne doit jamais empêcher le démarrage (Kairos 2).
                runCatching { repository.replaceAll(examples()) }
            }
            repository.load()
            return repository
        }
    }
}

/**
 * Contenu du dialogue d'édition d'une tâche (docs/spec-v3/vue-jour.md §
 * Édition). [pinTime] vide désépingle ; sinon l'heure fixe tombe sur
 * [scheduledDate], à défaut sur [pinDay] (le jour affiché). [blockerIds] est
 * l'ensemble complet des bloqueurs voulus ; [newSubtasks] : une ligne, une
 * sous-tâche.
 */
data class TaskEdit(
    val title: String,
    val description: String = "",
    val priority: Int? = null,
    val points: Int? = null,
    val deadline: LocalDate? = null,
    val estimatedMinutes: Int? = null,
    val projectTag: String = "",
    val taskType: String = "",
    val scheduledDate: LocalDate? = null,
    val recurrence: TaskRecurrence = TaskRecurrence.NONE,
    val recurrenceDayOfMonth: Int? = null,
    val pinTime: LocalTime? = null,
    val pinDay: LocalDate,
    val manualTimeSpentMinutes: Int? = null,
    val newSubtasks: String = "",
    val blockerIds: Set<Long> = emptySet(),
)

/** Contenu du dialogue de créneau : horaires locaux, deep work ou occupé, récurrence. */
data class BlockEdit(
    val title: String,
    val start: LocalDateTime,
    val end: LocalDateTime,
    val kind: BlockKind = BlockKind.BUSY,
    val recurrence: BlockRecurrence = BlockRecurrence.NONE,
)

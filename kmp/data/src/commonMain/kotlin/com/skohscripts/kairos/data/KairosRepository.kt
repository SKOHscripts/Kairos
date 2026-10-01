package com.skohscripts.kairos.data

import app.cash.sqldelight.async.coroutines.awaitAsList
import app.cash.sqldelight.async.coroutines.awaitAsOneOrNull
import com.skohscripts.kairos.core.KairosBuild
import com.skohscripts.kairos.core.engine.Dependencies
import com.skohscripts.kairos.core.engine.Recurrence
import com.skohscripts.kairos.core.engine.Workdays
import com.skohscripts.kairos.core.model.BlockKind
import com.skohscripts.kairos.core.model.BlockRecurrence
import com.skohscripts.kairos.core.model.FIBONACCI_SCALE
import com.skohscripts.kairos.core.model.KairosSnapshot
import com.skohscripts.kairos.core.model.NoteStatus
import com.skohscripts.kairos.core.notes.NoteConversion
import com.skohscripts.kairos.core.model.PRIORITY_VALUES
import com.skohscripts.kairos.core.model.Settings
import com.skohscripts.kairos.core.model.Task
import com.skohscripts.kairos.core.model.TaskRecurrence
import com.skohscripts.kairos.core.model.TaskSpace
import com.skohscripts.kairos.core.model.TaskStatus
import com.skohscripts.kairos.core.model.TeamSettings
import com.skohscripts.kairos.core.team.MemberForm
import com.skohscripts.kairos.core.team.QualifiedField
import com.skohscripts.kairos.core.team.TeamEventKind
import com.skohscripts.kairos.core.team.TeamEventSource
import com.skohscripts.kairos.core.team.TeamEvents
import com.skohscripts.kairos.core.team.TeamMembers
import com.skohscripts.kairos.core.team.Workspaces
import com.skohscripts.kairos.core.team.exchange.PackBuilder
import com.skohscripts.kairos.core.team.exchange.PackMerge
import com.skohscripts.kairos.core.team.exchange.PackMergePlan
import com.skohscripts.kairos.core.team.exchange.ReceivedTasks
import com.skohscripts.kairos.core.team.exchange.ReportBuilder
import com.skohscripts.kairos.core.team.exchange.ReportChange
import com.skohscripts.kairos.core.team.exchange.ReportMerge
import com.skohscripts.kairos.core.team.exchange.ReportMergePlan
import com.skohscripts.kairos.core.team.exchange.ReportTaskUpdate
import com.skohscripts.kairos.core.team.exchange.TeamOrigin
import com.skohscripts.kairos.core.team.forecast.IgnoredModification
import com.skohscripts.kairos.core.team.forecast.Scenario
import com.skohscripts.kairos.core.team.forecast.ScenarioCodec
import com.skohscripts.kairos.core.team.forecast.ScenarioModification
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
import kotlinx.datetime.toInstant
import kotlinx.datetime.todayIn
import kotlinx.serialization.json.Json
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Accès à la base (docs/spec/modele-donnees.md § Dépôt).
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
    private val personal = MutableStateFlow(KairosSnapshot())

    /** Dernier état lu de la base : la base **complète** (export, sauvegardes, écrans d'équipe). */
    val snapshot: StateFlow<KairosSnapshot> = state.asStateFlow()

    /**
     * Ce que l'espace Perso a le droit de lire ([Workspaces.personalView] du
     * dernier état), calculé une fois par rechargement et publié à côté de
     * [snapshot]. Tout écran ou calcul Perso lit celui-ci, jamais [snapshot].
     */
    val personalSnapshot: StateFlow<KairosSnapshot> = personal.asStateFlow()

    /** Relit la base et publie l'état (à l'ouverture). */
    suspend fun load(): KairosSnapshot = mutex.withLock {
        readAll().also(::publish)
    }

    private fun publish(all: KairosSnapshot) {
        state.value = all
        personal.value = Workspaces.personalView(all)
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
    suspend fun setPriority(taskId: Long, priority: Int?, source: TeamEventSource? = null) =
        modify(taskId, source) { it.copy(priority = priority?.takeIf { p -> p in PRIORITY_VALUES }) }

    suspend fun setPoints(taskId: Long, points: Int?, source: TeamEventSource? = null) =
        modify(taskId, source) { it.copy(fibonacciPoints = points?.takeIf { p -> p in FIBONACCI_SCALE }) }

    /**
     * Catégorie (type) en un geste, pour le changement en lot du Backlog d'équipe ; vide =
     * « sans catégorie ». Tâche d'équipe : un événement `qualified` (`type`).
     */
    suspend fun setTaskType(taskId: Long, taskType: String, source: TeamEventSource? = null) =
        modify(taskId, source) { it.copy(taskType = taskType.trim()) }

    /**
     * Fait ↔ à faire (Kairos 2 `toggle_task_done`). Terminer ferme le chrono
     * éventuellement ouvert sur la tâche et, pour une récurrente, crée
     * l'occurrence suivante (`Recurrence.nextOccurrence`, garde anti-doublon
     * comprise). Une tâche archivée n'est pas touchée.
     *
     * Tâche d'équipe (docs/spec/equipe-backlog-suivi.md) : terminer pose l'avancement
     * à 100 et journalise `done` en gardant l'ancien avancement dans `fromValue` ;
     * rouvrir le restaure depuis ce dernier événement `done` et journalise
     * `reopened`. L'occurrence suivante d'une récurrente d'équipe a son propre
     * `teamUid`, aucun avancement ni début, et son événement `created`.
     */
    suspend fun toggleDone(taskId: Long, source: TeamEventSource? = null) {
        val task = find(taskId) ?: return
        val next = when (task.status) {
            TaskStatus.TODO -> TaskStatus.DONE
            TaskStatus.DONE -> TaskStatus.TODO
            TaskStatus.ARCHIVED -> return
        }
        write {
            val team = task.space == TaskSpace.TEAM
            val src = source ?: defaultSource(task)
            var updated = task.copy(status = next)
            var restored: Int? = null
            if (team && next == TaskStatus.DONE) {
                updated = updated.copy(progressPercent = DONE_PROGRESS)
            } else if (team) {
                // Dernier `done` de la tâche : son fromValue est l'avancement d'avant la fin. Sans
                // événement (base importée), l'avancement actuel est gardé.
                val lastDone = state.value.teamEvents.filter { it.taskId == taskId && it.kind == TeamEventKind.DONE }.maxByOrNull { it.id }
                restored = if (lastDone != null) lastDone.fromValue?.toIntOrNull() else task.progressPercent
                updated = updated.copy(progressPercent = restored)
            }
            updateRow(updated)
            if (team && next == TaskStatus.DONE) {
                journal(updated, TeamEventKind.DONE, task.progressPercent?.toString(), DONE_PROGRESS.toString(), src)
            } else if (team) {
                journal(updated, TeamEventKind.REOPENED, task.progressPercent?.toString(), restored?.toString(), src)
            }
            if (next == TaskStatus.DONE) {
                queries.closeOpenSessionsOf(now(), taskId)
                Recurrence.nextOccurrence(task, today(), state.value.tasks, clock.now())?.let { insertNew(it, src) }
            }
        }
    }

    /**
     * Démarre le chrono sur une tâche à faire (`start_timer`) : toute session
     * encore ouverte, sur n'importe quelle tâche, est d'abord fermée (au plus
     * une session ouverte à la fois). Sur une tâche d'équipe assignée et pas
     * encore commencée, le chrono la commence aussi (`startedOn` = aujourd'hui,
     * événement `started`).
     */
    suspend fun startTimer(taskId: Long, source: TeamEventSource? = null) {
        val task = find(taskId) ?: return
        if (task.status != TaskStatus.TODO) return
        write {
            val now = now()
            queries.closeAllOpenSessions(now)
            queries.insertWorkSession(taskId, now, now)
            if (task.space == TaskSpace.TEAM && task.assigneeId != null && task.startedOn == null) {
                val started = task.copy(startedOn = today())
                updateRow(started)
                journal(started, TeamEventKind.STARTED, null, started.startedOn.toString(), source ?: defaultSource(task))
            }
        }
    }

    /** Arrête le chrono : ferme la session ouverte, quelle que soit sa tâche. */
    suspend fun stopTimer() {
        if (state.value.workSessions.none { it.endedAt == null }) return
        write { queries.closeAllOpenSessions(now()) }
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
     *
     * Tâche d'équipe : les changements de priorité, de points, de catégorie et
     * d'échéance sont journalisés (`qualified`, un événement par champ), ainsi
     * que [TaskEdit.reassign] (`assigned`) ; les sous-tâches créées en lot
     * prennent l'espace et l'assigné (après réaffectation) de leur mère.
     */
    suspend fun updateTask(taskId: Long, edit: TaskEdit, source: TeamEventSource? = null) {
        val task = find(taskId) ?: return
        write {
            val weekly = edit.recurrence == TaskRecurrence.WEEKLY
            val onDay = edit.recurrence == TaskRecurrence.MONTHLY_ON_DAY
            var updated = task.copy(
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
            )
            val reassigned = edit.reassign?.let { reassigned(updated, it.memberId, it.keepInProgress) }
            if (reassigned != null) updated = reassigned
            updateRow(updated)
            if (task.space == TaskSpace.TEAM) {
                val src = source ?: defaultSource(task)
                journalQualification(task, updated, src)
                if (reassigned != null) journalAssignment(task, updated, src)
            }
            edit.newSubtasks.lines().map { it.trim() }.filter { it.isNotEmpty() }.forEach {
                insertNew(newTask(it, taskId).copy(space = updated.space, assigneeId = updated.assigneeId))
            }
            setBlockers(task, edit.blockerIds)
        }
    }

    /**
     * Bloqueurs : [target] est l'ensemble **complet** voulu. Les absents sont
     * retirés ; un ajout qui créerait un cycle (ou une tâche inconnue) est
     * ignoré sans faire échouer le reste. Un bloqueur d'un autre espace que la
     * tâche est ignoré aussi : pas de dépendance Perso ↔ Équipe (docs/spec/equipe.md).
     */
    private suspend fun setBlockers(task: Task, target: Set<Long>) {
        val taskId = task.id
        val deps = state.value.dependencies
        val existing = deps.filter { it.taskId == taskId }.map { it.blockerId }.toSet()
        val spaceOf = state.value.tasks.associate { it.id to it.space }
        // Un identifiant inconnu passe ce filtre : il est écarté plus bas, comme avant.
        val wanted = (target - taskId).filter { (spaceOf[it] ?: task.space) == task.space }.toSet()
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
    suspend fun deleteTask(taskId: Long, source: TeamEventSource? = null) = write {
        // Tâche d'équipe : l'événement `deleted` recopie le titre, et ceux de la tâche restent.
        find(taskId)?.takeIf { it.space == TaskSpace.TEAM }?.let { journal(it, TeamEventKind.DELETED, null, null, source ?: defaultSource(it)) }
        queries.deleteDependenciesOf(taskId)
        queries.deleteTask(taskId)
    }

    // --- Équipe : tâches (docs/spec/equipe-backlog-suivi.md § Dépôt) -------------------

    /**
     * Capture dans le backlog d'équipe : une tâche titre seul, `space = TEAM`, sans
     * assigné, avec une identité d'équipe ([Task.teamUid], tirée ici) ; événement
     * `created`. Une sous-tâche ([parentId], tâche d'équipe) prend l'assigné de sa
     * mère ; une mère inconnue ou Perso est ignorée (la tâche naît au premier
     * niveau). Titre vide → `null`.
     */
    suspend fun createTeamTask(title: String, parentId: Long? = null): Long? {
        val clean = title.trim()
        if (clean.isEmpty()) return null
        var id: Long? = null
        write {
            val parent = parentId?.let { p -> state.value.tasks.firstOrNull { it.id == p && it.space == TaskSpace.TEAM } }
            id = insertNew(newTask(clean, parent?.id).copy(space = TaskSpace.TEAM, assigneeId = parent?.assigneeId), TeamEventSource.MANUAL)
        }
        return id
    }

    /**
     * Assigne, réaffecte ou remet au backlog ([memberId] `null`) des tâches d'équipe à
     * faire. Un membre inconnu ou archivé est refusé (rien n'est écrit). Une tâche
     * déjà chez [memberId], faite, archivée, Perso ou inconnue est ignorée. Une tâche
     * **en cours** repasse à « À faire » (`startedOn` vidé) sauf si [keepInProgress] ;
     * un retour au backlog vide toujours `startedOn`. L'avancement est gardé.
     * Un événement `assigned` (de → vers, identifiants en texte) par tâche changée,
     * dans la même transaction. Rend le nombre de tâches changées.
     */
    suspend fun assign(
        taskIds: Collection<Long>,
        memberId: Long?,
        keepInProgress: Boolean = false,
        source: TeamEventSource = TeamEventSource.MANUAL,
    ): Int {
        var changed = 0
        write {
            for (id in taskIds.distinct()) {
                val task = state.value.tasks.firstOrNull { it.id == id } ?: continue
                val next = reassigned(task, memberId, keepInProgress) ?: continue
                updateRow(next)
                journalAssignment(task, next, source)
                changed++
            }
        }
        return changed
    }

    /**
     * « Commencer » : pose `startedOn` = aujourd'hui (fuseau du dépôt) sur une tâche
     * d'équipe à faire, assignée et pas encore commencée ; événement `started`.
     * `false` (rien d'écrit) dans tous les autres cas.
     */
    suspend fun startTeamTask(id: Long, source: TeamEventSource = TeamEventSource.MANUAL): Boolean {
        var done = false
        write {
            val task = state.value.tasks.firstOrNull { it.id == id }?.takeIf { it.isStartable() } ?: return@write
            val started = task.copy(startedOn = today())
            updateRow(started)
            journal(started, TeamEventKind.STARTED, null, started.startedOn.toString(), source)
            done = true
        }
        return done
    }

    /**
     * Avancement déclaré d'une tâche d'équipe à faire et assignée : [percent] borné à
     * 0-100 puis arrondi à la dizaine la plus proche. Au-dessus de 0, la tâche est
     * commencée si elle ne l'était pas (événement `started` puis `progress`). Sans
     * changement d'avancement ni de début, rien n'est écrit. `false` si rien n'a changé
     * (ou si la tâche n'est pas éligible).
     */
    suspend fun setProgress(id: Long, percent: Int, source: TeamEventSource = TeamEventSource.MANUAL): Boolean {
        var done = false
        write {
            val task = state.value.tasks.firstOrNull { it.id == id }
                ?.takeIf { it.space == TaskSpace.TEAM && it.status == TaskStatus.TODO && it.assigneeId != null } ?: return@write
            val target = roundProgress(percent)
            val before = task.progressPercent ?: 0
            val starts = target > 0 && task.startedOn == null
            if (target == before && !starts) return@write
            val next = task.copy(progressPercent = target, startedOn = if (starts) today() else task.startedOn)
            updateRow(next)
            if (starts) journal(next, TeamEventKind.STARTED, null, next.startedOn.toString(), source)
            if (target != before) journal(next, TeamEventKind.PROGRESS, before.toString(), target.toString(), source)
            done = true
        }
        return done
    }

    // --- Notes (docs/spec/notes-capture.md) -----------------------------------

    /** Capture libre : un corps nettoyé, rien d'autre ; vide → rien. */
    suspend fun createNote(body: String): Boolean {
        val clean = body.trim()
        if (clean.isEmpty()) return false
        write { queries.insertNote(clean, now(), now()) }
        return true
    }

    /** Nouveau corps d'une note (nettoyé) ; vide → la note garde l'ancien. */
    suspend fun editNote(noteId: Long, body: String) {
        val note = state.value.notes.firstOrNull { it.id == noteId } ?: return
        val clean = body.trim()
        if (clean.isEmpty()) return
        write { queries.updateNote(clean, note.status.code, note.convertedTaskId, now(), noteId) }
    }

    /**
     * Note → tâche (`convert_note_to_task`) : titre = première ligne, description
     * = le reste ([NoteConversion]) ; la tâche arrive « À traiter » ; la note est
     * archivée et liée à la tâche, jamais supprimée. Rend l'identifiant de la tâche.
     */
    suspend fun convertNote(noteId: Long): Long? {
        val note = state.value.notes.firstOrNull { it.id == noteId && it.status == NoteStatus.OPEN } ?: return null
        val (title, description) = NoteConversion.fields(note.body)
        if (title.isEmpty()) return null
        var id: Long? = null
        write {
            id = insertNew(newTask(title, null).copy(description = description))
            queries.updateNote(note.body, NoteStatus.ARCHIVED.code, id, now(), noteId)
        }
        return id
    }

    /** Classe une note sans suite : archivée, jamais supprimée. */
    suspend fun archiveNote(noteId: Long) {
        val note = state.value.notes.firstOrNull { it.id == noteId } ?: return
        write { queries.updateNote(note.body, NoteStatus.ARCHIVED.code, note.convertedTaskId, now(), noteId) }
    }

    /** Suppression définitive. */
    suspend fun deleteNote(noteId: Long) = write { queries.deleteNote(noteId) }

    // --- Équipe : membres et absences (docs/spec/equipe.md § Dépôt) --------------

    /**
     * Crée un membre. Le nom est nettoyé ; un nom vide, des heures non finies
     * ou un membre refusé rendent `null`. La quotité et les heures par jour sont
     * ramenées dans leurs bornes (1-100, 1-24) : la validation de saisie est
     * celle de `MemberForm`, ceci n'est qu'un garde-fou. L'`uid` (identité des
     * échanges) est tiré ici : `core` reste sans hasard. [isSelf] retire « C'est
     * moi » aux autres membres dans la même transaction.
     */
    @OptIn(ExperimentalUuidApi::class)
    suspend fun createMember(name: String, role: String, availabilityPercent: Int, hoursPerDay: Double, isSelf: Boolean): Long? {
        val clean = name.trim()
        if (clean.isEmpty() || !hoursPerDay.isFinite()) return null
        var id: Long? = null
        write {
            queries.insertTeamMember(
                Uuid.random().toString(), clean, role.trim(), boundedAvailability(availabilityPercent), boundedHours(hoursPerDay),
                if (isSelf) 1L else 0L, now(), now(),
            )
            id = queries.lastInsertedId().awaitAsOneOrNull()
            if (isSelf) id?.let { queries.clearSelfExcept(now(), it) }
        }
        return id
    }

    /**
     * Modifie les champs d'un membre (mêmes règles que [createMember]) ; `false`
     * si le membre est inconnu ou si le nom est vide. Poser [isSelf] retire « C'est
     * moi » aux autres, dans la même transaction ; un membre archivé n'est jamais
     * « moi ».
     */
    suspend fun updateMember(id: Long, name: String, role: String, availabilityPercent: Int, hoursPerDay: Double, isSelf: Boolean): Boolean {
        val clean = name.trim()
        if (clean.isEmpty() || !hoursPerDay.isFinite()) return false
        var done = false
        write {
            val member = state.value.members.firstOrNull { it.id == id } ?: return@write
            val self = isSelf && !member.archived
            queries.updateTeamMember(
                clean, role.trim(), boundedAvailability(availabilityPercent), boundedHours(hoursPerDay),
                if (self) 1L else 0L, now(), id,
            )
            if (self) queries.clearSelfExcept(now(), id)
            done = true
        }
        return done
    }

    /**
     * Archive un membre (départ de l'équipe) : ses tâches d'équipe **à faire**
     * repassent au backlog (`assigneeId = null`), les faites (et archivées) restent
     * à son nom, et « C'est moi » lui est retiré. `false` si le membre est inconnu
     * ou déjà archivé.
     */
    suspend fun archiveMember(id: Long): Boolean {
        var done = false
        write {
            val member = state.value.members.firstOrNull { it.id == id && !it.archived } ?: return@write
            // Chaque tâche remise au backlog est journalisée (`assigned`, de ce membre vers personne).
            state.value.tasks.filter { TeamMembers.isOpenTeamTaskOf(it, member.id) }.forEach {
                journal(it, TeamEventKind.ASSIGNED, member.id.toString(), null, TeamEventSource.MANUAL, memberId = null)
            }
            queries.unassignTasksOf(now(), member.id)
            queries.setMemberArchived(1L, now(), member.id)
            done = true
        }
        return done
    }

    /** Réactive un membre archivé ; `false` s'il est inconnu ou déjà actif. Ses anciennes tâches ne lui reviennent pas. */
    suspend fun restoreMember(id: Long): Boolean {
        var done = false
        write {
            val member = state.value.members.firstOrNull { it.id == id && it.archived } ?: return@write
            queries.setMemberArchived(0L, now(), member.id)
            done = true
        }
        return done
    }

    /**
     * Supprime un membre et ses absences. Refusé (`false`) si le membre est inconnu,
     * s'il a déjà eu une tâche, quel que soit son statut, ou s'il apparaît dans un
     * événement du journal (même d'une tâche supprimée) : seul l'archivage reste
     * possible, l'historique garde son nom.
     */
    suspend fun deleteMember(id: Long): Boolean {
        var done = false
        write {
            if (state.value.members.none { it.id == id } || TeamMembers.hasHadTask(state.value.tasks, id, state.value.teamEvents)) return@write
            queries.deleteAbsencesOfMember(id)
            queries.deleteTeamMember(id)
            done = true
        }
        return done
    }

    /** Ajoute une absence (jours début et fin inclus) ; `null` si la fin précède le début ou si le membre est inconnu. */
    suspend fun addAbsence(memberId: Long, start: LocalDate, end: LocalDate, label: String = ""): Long? {
        if (!MemberForm.isValidAbsence(start, end)) return null
        var id: Long? = null
        write {
            if (state.value.members.none { it.id == memberId }) return@write
            queries.insertMemberAbsence(memberId, start.toString(), end.toString(), label.trim(), now())
            id = queries.lastInsertedId().awaitAsOneOrNull()
        }
        return id
    }

    /** Modifie les dates et le libellé d'une absence ; `false` si elle est inconnue ou si la fin précède le début. */
    suspend fun updateAbsence(id: Long, start: LocalDate, end: LocalDate, label: String = ""): Boolean {
        if (!MemberForm.isValidAbsence(start, end)) return false
        var done = false
        write {
            if (state.value.absences.none { it.id == id }) return@write
            queries.updateMemberAbsence(start.toString(), end.toString(), label.trim(), id)
            done = true
        }
        return done
    }

    suspend fun deleteAbsence(id: Long) = write { queries.deleteMemberAbsence(id) }

    /**
     * Supprime définitivement les données d'équipe : membres, absences, tâches
     * `TEAM` avec leurs dépendances, leurs sessions, le journal et les scénarios ; toute tâche restante perd
     * son assigné. Les réglages d'équipe (`settings.team`) sont gardés. La
     * sauvegarde préalable est faite par l'interface (aucun retour en arrière ici).
     *
     * Les tâches **reçues** d'un manager (jalon E6, `Task.origin`) ne sont pas touchées : ce sont des tâches
     * personnelles du membre, pas des données de l'équipe qu'il manage ; elles n'ont ni espace `TEAM` ni assigné.
     */
    suspend fun clearTeamData() = write {
        queries.deleteAllTeamScenarios()
        queries.deleteAllTeamEvents()
        queries.deleteDependenciesOfTeamTasks()
        queries.deleteSessionsOfTeamTasks()
        queries.deleteTeamTasks()
        queries.unassignAllTasks()
        queries.deleteAllMemberAbsences()
        queries.deleteAllTeamMembers()
    }

    // --- Équipe : échanges par fichier (docs/spec/equipe-echanges.md) -------------------------------

    /**
     * Côté manager : le paquet de [memberId] (ses tâches d'équipe à faire et leurs sous-tâches), en texte JSON
     * (`kairos-team-pack`), ou `null` si l'envoi est impossible (espace Équipe sans identité, membre inconnu ou
     * archivé). Chaque tâche du paquet reçoit un événement `sent` (toValue = [packId], membre = destinataire,
     * source `manual`) dans la même transaction : le journal est écrit à l'export, avant que le fichier soit
     * enregistré. [packId] est tiré ici s'il n'est pas donné (identité du paquet, jamais de hasard dans `core`).
     */
    @OptIn(ExperimentalUuidApi::class)
    suspend fun exportPack(
        memberId: Long,
        packId: String = Uuid.random().toString(),
        now: Instant = clock.now(),
        appVersion: String = KairosBuild.VERSION_NAME,
    ): String? {
        if (PackBuilder.build(state.value, memberId, packId, now) == null) return null
        var text: String? = null
        write {
            // Reconstruit dans la transaction : l'état a pu changer depuis la vérification.
            val pack = PackBuilder.build(state.value, memberId, packId, now) ?: return@write
            val sent = pack.tasks.mapTo(HashSet()) { it.uid }
            state.value.tasks.filter { it.space == TaskSpace.TEAM && it.teamUid in sent }.sortedBy { it.id }
                .forEach { journal(it, TeamEventKind.SENT, null, packId, TeamEventSource.MANUAL, memberId = memberId) }
            text = TeamExchangeCodec.encodePack(pack, appVersion)
        }
        return text
    }

    /**
     * Côté membre : aperçu de la réception d'un paquet, **sans rien écrire** (compteurs et lignes du dialogue
     * « Recevoir 7 tâches de Corentin ? »). @throws ImportException si le texte n'est pas un paquet lisible.
     */
    fun previewPack(text: String): PackMergePlan = PackMerge.plan(state.value, TeamExchangeCodec.decodePack(text), clock.now())

    /**
     * Côté membre : reçoit un paquet, en **une** transaction (en cas d'erreur la base reste intacte). La sauvegarde
     * préalable est faite par l'interface. Un paquet refusé ([PackMergePlan.refusal]) n'écrit rien. Les tâches
     * créées sont personnelles et marquées de leur origine ; les mises à jour n'écrasent que les champs du manager
     * et ne touchent pas `updatedAt` ; les tâches absentes du paquet sont marquées retirées, jamais supprimées ;
     * les dépendances du paquet remplacent celles posées entre tâches reçues de ce manager. Aucun événement de
     * journal : le journal est celui du manager. @throws ImportException si le texte n'est pas un paquet lisible.
     */
    suspend fun receivePack(text: String): PackReport {
        val pack = TeamExchangeCodec.decodePack(text)
        var planned: PackMergePlan? = null
        write {
            // Calculé dans la transaction : c'est ce plan-là qui est appliqué.
            val plan = PackMerge.plan(state.value, pack, clock.now())
            planned = plan
            if (!plan.accepted) return@write
            val idByUid = HashMap<String, Long>()
            fun known(t: Task) = t.teamUid?.let { idByUid.putIfAbsent(it, t.id) }
            plan.updated.forEach { known(it.before) }
            plan.unchanged.forEach { known(it) }
            plan.removed.forEach { known(it) }
            for (n in plan.created) {
                val id = insertNew(n.task.copy(parentId = n.parentUid?.let(idByUid::get))) ?: continue
                idByUid[n.task.teamUid!!] = id
            }
            for (u in plan.updated) {
                updateRow(u.after.copy(parentId = u.parentUid?.let(idByUid::get) ?: u.after.parentId), touch = false)
            }
            for (t in plan.removed) updateRow(t.copy(originRemoved = true), touch = false)
            for (d in plan.dependencyRemoves) {
                val (blocked, blocker) = (idByUid[d.taskUid] ?: continue) to (idByUid[d.blockerUid] ?: continue)
                queries.deleteDependency(blocked, blocker)
            }
            for (d in plan.dependencyAdds) {
                val (blocked, blocker) = (idByUid[d.taskUid] ?: continue) to (idByUid[d.blockerUid] ?: continue)
                queries.insertDependency(blocked, blocker, now())
            }
        }
        val plan = checkNotNull(planned)
        return PackReport(plan, applied = plan.accepted)
    }

    /**
     * Origines à qui le membre peut renvoyer son avancement : une par manager (équipe) qui a encore des tâches reçues
     * non retirées (bouton « Renvoyer l'avancement… » de Réglages → Données). Vide pour un Kairos qui n'a jamais reçu de paquet.
     */
    fun origins(): List<TeamOrigin> = ReceivedTasks.origins(state.value)

    /**
     * Côté membre : le rapport d'avancement pour l'origine [originKey] (`TeamOrigin.key`), en texte JSON
     * (`kairos-team-report`), ou `null` si cette équipe n'a aucune tâche reçue non retirée. Les nouvelles sous-tâches du
     * membre sans identité en reçoivent une d'abord (écriture technique, `updatedAt` inchangé) : elle doit rester stable
     * d'un rapport à l'autre pour que le manager ne les crée pas deux fois.
     */
    @OptIn(ExperimentalUuidApi::class)
    suspend fun buildReport(originKey: String, now: Instant = clock.now(), appVersion: String = KairosBuild.VERSION_NAME): String? {
        val missing = ReportBuilder.subtasksNeedingUid(state.value, originKey)
        if (missing.isNotEmpty()) {
            write { missing.forEach { updateRow(it.copy(teamUid = Uuid.random().toString()), touch = false) } }
        }
        val report = ReportBuilder.build(state.value, originKey, now, timeZone) ?: return null
        return TeamExchangeCodec.encodeReport(report, appVersion)
    }

    /**
     * Côté manager : aperçu de l'intégration d'un rapport, **sans rien écrire** (de → vers, lignes ignorées, refus).
     * @throws ImportException si le texte n'est pas un rapport lisible.
     */
    fun previewReport(text: String): ReportMergePlan = ReportMerge.plan(state.value, TeamExchangeCodec.decodeReport(text), timeZone)

    /**
     * Côté manager : intègre un rapport, en **une** transaction (la sauvegarde préalable est faite par l'interface).
     * Un rapport refusé ([ReportMergePlan.refusal]) n'écrit rien. Seuls les champs du membre sont écrits (état, avancement,
     * commencement, temps rapporté) ; chaque changement est journalisé avec la source `report` et le membre qui rapporte
     * pour `memberId` ; la date de fin d'une tâche faite est celle du rapport (événement `done` daté de ce jour, à midi
     * local, sans dépasser l'instant courant). Terminer une tâche ferme son chrono et crée l'occurrence suivante d'une
     * récurrente, comme [toggleDone]. Les nouvelles sous-tâches deviennent des tâches d'équipe du même assigné que leur
     * mère. Pose enfin `lastReportAt` du membre. @throws ImportException si le texte n'est pas un rapport lisible.
     */
    suspend fun integrateReport(text: String): ReportIntegration {
        val report = TeamExchangeCodec.decodeReport(text)
        var planned: ReportMergePlan? = null
        write {
            val plan = ReportMerge.plan(state.value, report, timeZone)
            planned = plan
            if (!plan.accepted) return@write
            val member = checkNotNull(plan.member)
            for (u in plan.updates) applyReportUpdate(u, member.id)
            val idByUid = HashMap<String, Long>()
            for (n in plan.newSubtasks) {
                val parentId = n.parentTaskId ?: idByUid[n.parentUid]
                val done = n.status == TaskStatus.DONE
                val task = newTask(n.title.trim(), parentId).copy(
                    space = TaskSpace.TEAM, assigneeId = n.assigneeId, teamUid = n.uid,
                    status = n.status, progressPercent = if (done) DONE_PROGRESS else null,
                )
                val id = insertNew(task, TeamEventSource.REPORT, actor = member.id) ?: continue
                idByUid[n.uid] = id
                if (done) journal(task.copy(id = id), TeamEventKind.DONE, null, DONE_PROGRESS.toString(), TeamEventSource.REPORT, memberId = member.id)
            }
            queries.setMemberLastReport(plan.reportedAt.store(), member.id)
        }
        val plan = checkNotNull(planned)
        return ReportIntegration(plan, applied = plan.accepted)
    }

    /** Écrit une mise à jour de rapport et ses événements (source `report`, auteur = membre qui rapporte). */
    private suspend fun applyReportUpdate(u: ReportTaskUpdate, reporterId: Long) {
        val before = u.before
        val after = u.after
        updateRow(after)
        val src = TeamEventSource.REPORT
        val statusChanged = u.changes.any { it is ReportChange.Status }
        for (change in u.changes) {
            when (change) {
                is ReportChange.Started -> journal(after, TeamEventKind.STARTED, null, change.to.toString(), src, memberId = reporterId)
                // La fin et la réouverture portent leur propre avancement : pas d'événement `progress` en plus.
                is ReportChange.Progress -> if (!statusChanged) {
                    journal(after, TeamEventKind.PROGRESS, (change.from ?: 0).toString(), (change.to ?: 0).toString(), src, memberId = reporterId)
                }
                is ReportChange.Spent -> journal(after, TeamEventKind.TIME, change.from?.toString(), change.to.toString(), src, memberId = reporterId)
                is ReportChange.Status -> Unit
            }
        }
        if (!statusChanged) return
        if (after.status == TaskStatus.DONE) {
            val doneAt = u.doneOn?.atTime(DONE_HOUR, 0)?.toInstant(timeZone)?.coerceAtMost(clock.now()) ?: clock.now()
            journal(after, TeamEventKind.DONE, before.progressPercent?.toString(), DONE_PROGRESS.toString(), src, memberId = reporterId, at = doneAt.store())
            queries.closeOpenSessionsOf(now(), after.id)
            Recurrence.nextOccurrence(after, today(), state.value.tasks, clock.now())?.let { insertNew(it, src, actor = reporterId) }
        } else {
            journal(after, TeamEventKind.REOPENED, before.progressPercent?.toString(), after.progressPercent?.toString(), src, memberId = reporterId)
        }
    }

    // --- Équipe : scénarios « Et si… ? » (docs/spec/equipe-simulation.md § Scénarios) ----------

    /**
     * Enregistre un scénario : un nom nettoyé (vide : `null`, rien d'écrit) et la liste de ses
     * modifications (JSON). Rien n'est vérifié contre les données : une modification qui ne s'applique
     * plus est signalée au calcul et à [applyScenario], pas refusée ici.
     */
    suspend fun createScenario(name: String, modifications: List<ScenarioModification>): Long? {
        val clean = name.trim()
        if (clean.isEmpty()) return null
        var id: Long? = null
        write {
            queries.insertTeamScenario(clean, ScenarioCodec.encode(modifications), now(), now())
            id = queries.lastInsertedId().awaitAsOneOrNull()
        }
        return id
    }

    /** Renomme et remplace les modifications d'un scénario ; `false` s'il est inconnu ou si le nom est vide. */
    suspend fun updateScenario(id: Long, name: String, modifications: List<ScenarioModification>): Boolean {
        val clean = name.trim()
        if (clean.isEmpty()) return false
        var done = false
        write {
            if (state.value.teamScenarios.none { it.id == id }) return@write
            queries.updateTeamScenario(clean, ScenarioCodec.encode(modifications), now(), id)
            done = true
        }
        return done
    }

    /**
     * Copie un scénario sous [name] (par défaut son nom : le libellé « copie » est un texte d'interface,
     * que l'écran fournit) ; `null` s'il est inconnu. Seules les modifications lues sont copiées.
     */
    suspend fun duplicateScenario(id: Long, name: String? = null): Long? {
        val source = state.value.teamScenarios.firstOrNull { it.id == id } ?: return null
        return createScenario(name?.takeIf { it.isNotBlank() } ?: source.name, source.modifications)
    }

    suspend fun deleteScenario(id: Long) = write { queries.deleteTeamScenario(id) }

    /**
     * « Appliquer » un scénario aux données réelles : seulement ses modifications **réelles**
     * ([Scenario.realChanges] : réaffectations, retraits de membre, absences, quotités, priorités,
     * échéances, taux de focus), dans l'ordre, en **une seule transaction** : en cas d'erreur, rien n'est
     * écrit. Les membres et les tâches hypothétiques ne sont **jamais créés**. Chaque changement de
     * tâche est journalisé avec la source `scenario`. Une modification qui ne s'applique plus (tâche
     * faite ou supprimée, membre archivé ou disparu, valeur hors bornes) est ignorée et rendue avec sa
     * raison ; une modification déjà en place aussi ([Scenario.SkipReason.ALREADY_APPLIED]). Chaque
     * modification voit l'effet des précédentes (la transaction tient son propre état de travail).
     */
    suspend fun applyScenario(id: Long): ApplyReport {
        val scenario = state.value.teamScenarios.firstOrNull { it.id == id } ?: return ApplyReport(found = false)
        val applied = ArrayList<ScenarioModification>()
        val skipped = ArrayList<Scenario.Skipped>()
        val notApplicable = ArrayList<ScenarioModification>()
        write {
            applied.clear()
            skipped.clear()
            notApplicable.clear()
            // État de travail : `state` n'est relu qu'à la fin de la transaction.
            val tasks = LinkedHashMap(state.value.tasks.associateBy { it.id })
            var members = state.value.members
            var absences = state.value.absences
            var settings = state.value.settings
            scenario.modifications.forEachIndexed { index, mod ->
                if (!Scenario.isReal(mod)) {
                    notApplicable += mod
                    return@forEachIndexed
                }
                fun skip(reason: Scenario.SkipReason) {
                    skipped += Scenario.Skipped(index, mod, reason)
                }

                /** Membre actif désigné ; sinon la raison est notée et rien n'est appliqué. */
                fun activeMember(memberId: Long): com.skohscripts.kairos.core.team.TeamMember? {
                    val m = members.firstOrNull { it.id == memberId }
                    when {
                        m == null -> skip(Scenario.SkipReason.MEMBER_NOT_FOUND)
                        m.archived -> skip(Scenario.SkipReason.MEMBER_ARCHIVED)
                        else -> return m
                    }
                    return null
                }

                /** Tâche d'équipe à faire désignée par son `teamUid` ; sinon la raison est notée. */
                fun openTask(uid: String): Task? {
                    val t = tasks.values.firstOrNull { it.teamUid == uid && it.space == TaskSpace.TEAM }
                    when {
                        t == null -> skip(Scenario.SkipReason.TASK_NOT_FOUND)
                        t.status != TaskStatus.TODO -> skip(Scenario.SkipReason.TASK_NOT_OPEN)
                        else -> return t
                    }
                    return null
                }

                when (mod) {
                    is ScenarioModification.Reassign -> {
                        val task = openTask(mod.taskUid) ?: return@forEachIndexed
                        val target = mod.memberId
                        if (target != null && activeMember(target) == null) return@forEachIndexed
                        if (task.assigneeId == target) {
                            skip(Scenario.SkipReason.ALREADY_APPLIED)
                        } else {
                            val next = task.copy(assigneeId = target, startedOn = null)
                            updateRow(next)
                            journalAssignment(task, next, TeamEventSource.SCENARIO)
                            tasks[next.id] = next
                            applied += mod
                        }
                    }

                    is ScenarioModification.SetPriority -> {
                        val task = openTask(mod.taskUid) ?: return@forEachIndexed
                        when {
                            mod.priority !in PRIORITY_VALUES -> skip(Scenario.SkipReason.INVALID_VALUE)
                            task.priority == mod.priority -> skip(Scenario.SkipReason.ALREADY_APPLIED)
                            else -> {
                                val next = task.copy(priority = mod.priority)
                                updateRow(next)
                                journalQualification(task, next, TeamEventSource.SCENARIO)
                                tasks[next.id] = next
                                applied += mod
                            }
                        }
                    }

                    is ScenarioModification.SetDeadline -> {
                        val task = openTask(mod.taskUid) ?: return@forEachIndexed
                        if (task.deadline == mod.date) {
                            skip(Scenario.SkipReason.ALREADY_APPLIED)
                        } else {
                            val next = task.copy(deadline = mod.date)
                            updateRow(next)
                            journalQualification(task, next, TeamEventSource.SCENARIO)
                            tasks[next.id] = next
                            applied += mod
                        }
                    }

                    is ScenarioModification.AddAbsence -> {
                        val member = activeMember(mod.memberId) ?: return@forEachIndexed
                        when {
                            !MemberForm.isValidAbsence(mod.start, mod.end) -> skip(Scenario.SkipReason.INVALID_VALUE)
                            absences.any { it.memberId == member.id && it.start == mod.start && it.end == mod.end } ->
                                skip(Scenario.SkipReason.ALREADY_APPLIED)
                            else -> {
                                queries.insertMemberAbsence(member.id, mod.start.toString(), mod.end.toString(), "", now())
                                absences = absences + com.skohscripts.kairos.core.team.MemberAbsence(
                                    id = queries.lastInsertedId().awaitAsOneOrNull() ?: 0L, memberId = member.id,
                                    start = mod.start, end = mod.end, createdAt = clock.now(),
                                )
                                applied += mod
                            }
                        }
                    }

                    is ScenarioModification.SetAvailability -> {
                        val member = activeMember(mod.memberId) ?: return@forEachIndexed
                        when {
                            mod.percent !in MemberForm.MIN_AVAILABILITY..MemberForm.MAX_AVAILABILITY -> skip(Scenario.SkipReason.INVALID_VALUE)
                            member.availabilityPercent == mod.percent -> skip(Scenario.SkipReason.ALREADY_APPLIED)
                            else -> {
                                queries.updateTeamMember(
                                    member.name, member.role, mod.percent.toLong(), member.hoursPerDay,
                                    if (member.isSelf) 1L else 0L, now(), member.id,
                                )
                                members = members.map { if (it.id == member.id) it.copy(availabilityPercent = mod.percent) else it }
                                applied += mod
                            }
                        }
                    }

                    is ScenarioModification.RemoveMember -> {
                        val member = activeMember(mod.memberId) ?: return@forEachIndexed
                        // Comme archiveMember : chaque tâche remise au backlog est journalisée, ici avec la source `scenario`.
                        tasks.values.filter { TeamMembers.isOpenTeamTaskOf(it, member.id) }.forEach {
                            journal(it, TeamEventKind.ASSIGNED, member.id.toString(), null, TeamEventSource.SCENARIO, memberId = null)
                            tasks[it.id] = it.copy(assigneeId = null, startedOn = null)
                        }
                        queries.unassignTasksOf(now(), member.id)
                        queries.setMemberArchived(1L, now(), member.id)
                        members = members.map { if (it.id == member.id) it.copy(archived = true, isSelf = false) else it }
                        applied += mod
                    }

                    is ScenarioModification.SetFocus -> {
                        val current = (settings.team ?: TeamSettings()).focusFactor
                        when {
                            !mod.factor.isFinite() || mod.factor <= 0.0 || mod.factor > 1.0 -> skip(Scenario.SkipReason.INVALID_VALUE)
                            current == mod.factor && settings.team != null -> skip(Scenario.SkipReason.ALREADY_APPLIED)
                            else -> {
                                settings = withTeamIdentity(settings.copy(team = (settings.team ?: TeamSettings()).copy(focusFactor = mod.factor)))
                                queries.writeSettings(json.encodeToString(Settings.serializer(), settings))
                                applied += mod
                            }
                        }
                    }

                    // Écartés plus haut par Scenario.isReal.
                    is ScenarioModification.AddMember, is ScenarioModification.AddTasks -> notApplicable += mod
                }
            }
        }
        return ApplyReport(found = true, applied = applied, skipped = skipped, notApplicable = notApplicable, ignored = scenario.ignored)
    }

    private fun boundedAvailability(percent: Int): Long =
        percent.coerceIn(MemberForm.MIN_AVAILABILITY, MemberForm.MAX_AVAILABILITY).toLong()

    private fun boundedHours(hours: Double): Double = hours.coerceIn(MemberForm.MIN_HOURS, MemberForm.MAX_HOURS)

    /**
     * Enregistre les réglages. Si l'espace Équipe existe (`team` non nul) sans
     * identité, le dépôt en pose une (UUID, conservé si la base en a déjà une) :
     * le hasard reste hors de `core`, et rien n'est posé en mode solo.
     */
    suspend fun updateSettings(settings: Settings) = write {
        queries.writeSettings(json.encodeToString(Settings.serializer(), withTeamIdentity(settings)))
    }

    /** [settings] avec l'identité de l'équipe posée si elle manque (voir [updateSettings]). */
    @OptIn(ExperimentalUuidApi::class)
    private fun withTeamIdentity(settings: Settings): Settings {
        val team = settings.team
        if (team == null || team.identity.isNotEmpty()) return settings
        val identity = state.value.settings.team?.identity.orEmpty().ifEmpty { Uuid.random().toString() }
        return settings.copy(team = team.copy(identity = identity))
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
        queries.deleteAllTeamMembers()
        queries.deleteAllMemberAbsences()
        queries.deleteAllTeamEvents()
        queries.deleteAllTeamScenarios()
        queries.deleteSettings()
        for (t in snapshot.tasks) {
            queries.insertTaskWithId(
                t.id, t.title, t.description, t.priority?.toLong(), t.deadline.store(), t.projectTag, t.status.code,
                t.estimatedMinutes?.toLong(), t.pinnedStart.store(), t.parentId, t.recurrence.code,
                t.scheduledDate.store(), t.recurrenceDayOfMonth?.toLong(), t.recurrenceDayOfWeek?.toLong(),
                t.recurrencePeriod, t.taskType, t.fibonacciPoints?.toLong(), t.manualTimeSpentMinutes?.toLong(),
                t.createdAt.store(), t.updatedAt.store(), t.space.code.toLong(), t.assigneeId,
                t.progressPercent?.toLong(), t.startedOn.store(), t.teamUid,
                t.origin, if (t.originRemoved) 1L else 0L, t.reportedMinutes?.toLong(),
            )
        }
        for (m in snapshot.members) {
            queries.insertTeamMemberWithId(
                m.id, m.uid, m.name, m.role, m.availabilityPercent.toLong(), m.hoursPerDay,
                if (m.isSelf) 1L else 0L, if (m.archived) 1L else 0L, m.createdAt.store(), m.updatedAt.store(),
                m.lastReportAt?.store(),
            )
        }
        for (a in snapshot.absences) {
            queries.insertMemberAbsenceWithId(a.id, a.memberId, a.start.toString(), a.end.toString(), a.label, a.createdAt.store())
        }
        for (e in snapshot.teamEvents) {
            queries.insertTeamEventWithId(e.id, e.taskId, e.taskTitle, e.memberId, e.kind.code, e.fromValue, e.toValue, e.source.code, e.at.store())
        }
        for (sc in snapshot.teamScenarios) {
            queries.insertTeamScenarioWithId(sc.id, sc.name, ScenarioCodec.encode(sc.modifications), sc.createdAt.store(), sc.updatedAt.store())
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

    /**
     * Insère une nouvelle tâche (identifiant attribué par la base) et le rend. Une tâche
     * d'équipe reçoit son [Task.teamUid] (tiré ici s'il manque) et son événement `created`
     * (source [source], à défaut celle que donne [defaultSource]) : c'est le seul chemin
     * de création, donc aucune tâche d'équipe ne naît sans identité ni sans journal.
     */
    @OptIn(ExperimentalUuidApi::class)
    private suspend fun insertNew(task: Task, source: TeamEventSource? = null, actor: Long? = null): Long? {
        val t = if (task.space == TaskSpace.TEAM && task.teamUid == null) task.copy(teamUid = Uuid.random().toString()) else task
        queries.insertTask(
            t.title, t.description, t.priority?.toLong(), t.deadline.store(), t.projectTag, t.status.code,
            t.estimatedMinutes?.toLong(), t.pinnedStart.store(), t.parentId, t.recurrence.code,
            t.scheduledDate.store(), t.recurrenceDayOfMonth?.toLong(), t.recurrenceDayOfWeek?.toLong(),
            t.recurrencePeriod, t.taskType, t.fibonacciPoints?.toLong(), t.manualTimeSpentMinutes?.toLong(),
            now(), now(), t.space.code.toLong(), t.assigneeId, t.progressPercent?.toLong(), t.startedOn.store(), t.teamUid,
            t.origin, if (t.originRemoved) 1L else 0L, t.reportedMinutes?.toLong(),
        )
        val id = queries.lastInsertedId().awaitAsOneOrNull()
        if (t.space == TaskSpace.TEAM && id != null) {
            journal(t.copy(id = id), TeamEventKind.CREATED, null, t.title, source ?: defaultSource(t), memberId = actor ?: t.assigneeId)
        }
        return id
    }

    private fun find(taskId: Long): Task? = state.value.tasks.firstOrNull { it.id == taskId }

    private suspend fun modify(taskId: Long, source: TeamEventSource? = null, change: (Task) -> Task) {
        val task = find(taskId) ?: return
        write {
            val updated = change(task)
            updateRow(updated)
            if (task.space == TaskSpace.TEAM) journalQualification(task, updated, source ?: defaultSource(task))
        }
    }

    /** Écrit [t] ; [touch] faux garde son `updatedAt` (écriture qui n'est pas une action du propriétaire de la tâche). */
    private suspend fun updateRow(t: Task, touch: Boolean = true) {
        queries.updateTask(
            t.title, t.description, t.priority?.toLong(), t.deadline.store(), t.projectTag, t.status.code,
            t.estimatedMinutes?.toLong(), t.pinnedStart.store(), t.parentId, t.recurrence.code,
            t.scheduledDate.store(), t.recurrenceDayOfMonth?.toLong(), t.recurrenceDayOfWeek?.toLong(),
            t.recurrencePeriod, t.taskType, t.fibonacciPoints?.toLong(), t.manualTimeSpentMinutes?.toLong(),
            if (touch) now() else t.updatedAt.store(), t.space.code.toLong(), t.assigneeId, t.progressPercent?.toLong(),
            t.startedOn.store(), t.teamUid, t.origin, if (t.originRemoved) 1L else 0L, t.reportedMinutes?.toLong(), t.id,
        )
    }

    // --- Journal d'équipe (docs/spec/equipe-backlog-suivi.md § Journal) ---------------------

    /** Membre « C'est moi » actif, ou `null`. */
    private fun selfMemberId(): Long? = state.value.members.firstOrNull { it.isSelf && !it.archived }?.id

    /**
     * Source d'une modification quand l'appelant n'en donne pas : `self` si la tâche
     * d'équipe est assignée à « moi » (modification faite depuis l'espace Perso), `manual` sinon.
     */
    private fun defaultSource(task: Task): TeamEventSource =
        if (task.space == TaskSpace.TEAM && task.assigneeId != null && task.assigneeId == selfMemberId()) TeamEventSource.SELF else TeamEventSource.MANUAL

    /** Écrit un événement dans la transaction courante ; [memberId] = assigné au moment de l'événement. */
    private suspend fun journal(
        task: Task,
        kind: TeamEventKind,
        from: String?,
        to: String?,
        source: TeamEventSource,
        memberId: Long? = task.assigneeId,
        at: String = now(),
    ) {
        queries.insertTeamEvent(task.id, task.title, memberId, kind.code, from, to, source.code, at)
    }

    /** Un événement `qualified` par champ de qualification (priorité, points, catégorie, échéance) qui a changé. */
    private suspend fun journalQualification(old: Task, new: Task, source: TeamEventSource) {
        val changes = listOf(
            Triple(QualifiedField.PRIORITY, old.priority?.toString(), new.priority?.toString()),
            Triple(QualifiedField.POINTS, old.fibonacciPoints?.toString(), new.fibonacciPoints?.toString()),
            Triple(QualifiedField.TYPE, old.taskType.ifEmpty { null }, new.taskType.ifEmpty { null }),
            Triple(QualifiedField.DEADLINE, old.deadline?.toString(), new.deadline?.toString()),
        )
        for ((field, before, after) in changes) {
            if (before == after) continue
            journal(new, TeamEventKind.QUALIFIED, TeamEvents.qualifiedValue(field, before), TeamEvents.qualifiedValue(field, after), source)
        }
    }

    /** Événement `assigned` pour le passage de [old] à [new] (identifiants de membre en texte, `null` = backlog). */
    private suspend fun journalAssignment(old: Task, new: Task, source: TeamEventSource) {
        journal(new, TeamEventKind.ASSIGNED, old.assigneeId?.toString(), new.assigneeId?.toString(), source)
    }

    /**
     * La tâche [task] réaffectée à [memberId] (`null` = backlog), ou `null` si rien ne
     * change : tâche qui n'est pas une tâche d'équipe à faire, même titulaire, membre inconnu
     * ou archivé. Garde l'avancement ; `startedOn` n'est gardé que pour [keepInProgress] chez un membre.
     */
    private fun reassigned(task: Task, memberId: Long?, keepInProgress: Boolean): Task? {
        if (task.space != TaskSpace.TEAM || task.status != TaskStatus.TODO || task.assigneeId == memberId) return null
        if (memberId != null && state.value.members.none { it.id == memberId && !it.archived }) return null
        return task.copy(assigneeId = memberId, startedOn = if (memberId != null && keepInProgress) task.startedOn else null)
    }

    private fun Task.isStartable() =
        space == TaskSpace.TEAM && status == TaskStatus.TODO && assigneeId != null && startedOn == null

    private fun roundProgress(percent: Int): Int = (percent.coerceIn(0, 100) + 5) / 10 * 10

    private suspend fun write(block: suspend () -> Unit) {
        val updated = mutex.withLock {
            database.transaction { block() }
            readAll().also(::publish)
        }
        onChanged(updated)
    }

    private suspend fun readAll(): KairosSnapshot = KairosSnapshot(
        tasks = queries.allTasks().awaitAsList().map { it.toModel() },
        timeBlocks = queries.allTimeBlocks().awaitAsList().map { it.toModel() },
        dependencies = queries.allDependencies().awaitAsList().map { it.toModel() },
        workSessions = queries.allWorkSessions().awaitAsList().map { it.toModel() },
        notes = queries.allNotes().awaitAsList().map { it.toModel() },
        members = queries.allTeamMembers().awaitAsList().map { it.toModel() },
        absences = queries.allMemberAbsences().awaitAsList().map { it.toModel() },
        teamEvents = queries.allTeamEvents().awaitAsList().map { it.toModel() },
        teamScenarios = queries.allTeamScenarios().awaitAsList().map { it.toModel() },
        settings = queries.readSettings().awaitAsOneOrNull()
            ?.let { runCatching { json.decodeFromString(Settings.serializer(), it) }.getOrNull() }
            ?: Settings(),
    )

    companion object {
        /** Avancement d'une tâche d'équipe terminée. */
        private const val DONE_PROGRESS = 100

        /** Heure locale de l'événement `done` d'un rapport (le rapport ne donne qu'un jour : midi évite les bascules de jour). */
        private const val DONE_HOUR = 12

        /** Réglages : champs inconnus ignorés, champs absents à leur valeur par défaut. */
        internal val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; explicitNulls = false }

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
 * Contenu du dialogue d'édition d'une tâche (docs/spec/vue-jour.md §
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
    /**
     * Réaffectation portée par l'édition d'une **tâche d'équipe** (champ « Assigné à »
     * de la fiche) ; `null` = l'assigné ne change pas. Sans effet sur une tâche Perso.
     * Mêmes règles que [KairosRepository.assign].
     */
    val reassign: Reassignment? = null,
)

/** Nouveau titulaire d'une tâche d'équipe : [memberId] `null` = retour au backlog ; [keepInProgress] garde l'état « En cours ». */
data class Reassignment(val memberId: Long?, val keepInProgress: Boolean = false)

/**
 * Ce que [KairosRepository.applyScenario] a fait : [applied] (changements écrits, dans l'ordre),
 * [skipped] (ne s'appliquent plus, ou déjà en place, avec la raison et la position dans le
 * scénario), [notApplicable] (membres et tâches hypothétiques, jamais créés, et modifications qui
 * les désignent) et [ignored] (types inconnus à la lecture). [found] faux : scénario inconnu, rien
 * n'a été fait.
 */
data class ApplyReport(
    val found: Boolean,
    val applied: List<ScenarioModification> = emptyList(),
    val skipped: List<Scenario.Skipped> = emptyList(),
    val notApplicable: List<ScenarioModification> = emptyList(),
    val ignored: List<IgnoredModification> = emptyList(),
)

/**
 * Ce que [KairosRepository.receivePack] a fait : le [plan] (créées, mises à jour, retirées, dépendances) et
 * [applied], faux si le paquet a été refusé ([PackMergePlan.refusal]) et que rien n'a été écrit.
 */
data class PackReport(val plan: PackMergePlan, val applied: Boolean)

/**
 * Ce que [KairosRepository.integrateReport] a fait : le [plan] (mises à jour de → vers, sous-tâches, lignes ignorées)
 * et [applied], faux si le rapport a été refusé ([ReportMergePlan.refusal]) et que rien n'a été écrit.
 */
data class ReportIntegration(val plan: ReportMergePlan, val applied: Boolean)

/** Contenu du dialogue de créneau : horaires locaux, deep work ou occupé, récurrence. */
data class BlockEdit(
    val title: String,
    val start: LocalDateTime,
    val end: LocalDateTime,
    val kind: BlockKind = BlockKind.BUSY,
    val recurrence: BlockRecurrence = BlockRecurrence.NONE,
)

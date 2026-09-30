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
import com.skohscripts.kairos.core.model.NoteStatus
import com.skohscripts.kairos.core.notes.NoteConversion
import com.skohscripts.kairos.core.model.PRIORITY_VALUES
import com.skohscripts.kairos.core.model.Settings
import com.skohscripts.kairos.core.model.Task
import com.skohscripts.kairos.core.model.TaskRecurrence
import com.skohscripts.kairos.core.model.TaskStatus
import com.skohscripts.kairos.core.team.MemberForm
import com.skohscripts.kairos.core.team.TeamMembers
import com.skohscripts.kairos.core.team.Workspaces
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
     * Démarre le chrono sur une tâche à faire (`start_timer`) : toute session
     * encore ouverte, sur n'importe quelle tâche, est d'abord fermée (au plus
     * une session ouverte à la fois).
     */
    suspend fun startTimer(taskId: Long) {
        val task = find(taskId) ?: return
        if (task.status != TaskStatus.TODO) return
        write {
            val now = now()
            queries.closeAllOpenSessions(now)
            queries.insertWorkSession(taskId, now, now)
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
    suspend fun deleteTask(taskId: Long) = write {
        queries.deleteDependenciesOf(taskId)
        queries.deleteTask(taskId)
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
     * Supprime un membre et ses absences. Refusé (`false`) si le membre est inconnu
     * ou s'il a déjà eu une tâche, quel que soit son statut : seul l'archivage reste
     * possible, l'historique garde son nom.
     */
    suspend fun deleteMember(id: Long): Boolean {
        var done = false
        write {
            if (state.value.members.none { it.id == id } || TeamMembers.hasHadTask(state.value.tasks, id)) return@write
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
     * `TEAM` avec leurs dépendances et leurs sessions ; toute tâche restante perd
     * son assigné. Les réglages d'équipe (`settings.team`) sont gardés. La
     * sauvegarde préalable est faite par l'interface (aucun retour en arrière ici).
     */
    suspend fun clearTeamData() = write {
        queries.deleteDependenciesOfTeamTasks()
        queries.deleteSessionsOfTeamTasks()
        queries.deleteTeamTasks()
        queries.unassignAllTasks()
        queries.deleteAllMemberAbsences()
        queries.deleteAllTeamMembers()
    }

    private fun boundedAvailability(percent: Int): Long =
        percent.coerceIn(MemberForm.MIN_AVAILABILITY, MemberForm.MAX_AVAILABILITY).toLong()

    private fun boundedHours(hours: Double): Double = hours.coerceIn(MemberForm.MIN_HOURS, MemberForm.MAX_HOURS)

    /**
     * Enregistre les réglages. Si l'espace Équipe existe (`team` non nul) sans
     * identité, le dépôt en pose une (UUID, conservé si la base en a déjà une) :
     * le hasard reste hors de `core`, et rien n'est posé en mode solo.
     */
    @OptIn(ExperimentalUuidApi::class)
    suspend fun updateSettings(settings: Settings) = write {
        val team = settings.team
        val complete = if (team != null && team.identity.isEmpty()) {
            val identity = state.value.settings.team?.identity.orEmpty().ifEmpty { Uuid.random().toString() }
            settings.copy(team = team.copy(identity = identity))
        } else {
            settings
        }
        queries.writeSettings(json.encodeToString(Settings.serializer(), complete))
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
        queries.deleteSettings()
        for (t in snapshot.tasks) {
            queries.insertTaskWithId(
                t.id, t.title, t.description, t.priority?.toLong(), t.deadline.store(), t.projectTag, t.status.code,
                t.estimatedMinutes?.toLong(), t.pinnedStart.store(), t.parentId, t.recurrence.code,
                t.scheduledDate.store(), t.recurrenceDayOfMonth?.toLong(), t.recurrenceDayOfWeek?.toLong(),
                t.recurrencePeriod, t.taskType, t.fibonacciPoints?.toLong(), t.manualTimeSpentMinutes?.toLong(),
                t.createdAt.store(), t.updatedAt.store(), t.space.code.toLong(), t.assigneeId,
            )
        }
        for (m in snapshot.members) {
            queries.insertTeamMemberWithId(
                m.id, m.uid, m.name, m.role, m.availabilityPercent.toLong(), m.hoursPerDay,
                if (m.isSelf) 1L else 0L, if (m.archived) 1L else 0L, m.createdAt.store(), m.updatedAt.store(),
            )
        }
        for (a in snapshot.absences) {
            queries.insertMemberAbsenceWithId(a.id, a.memberId, a.start.toString(), a.end.toString(), a.label, a.createdAt.store())
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
            now(), now(), t.space.code.toLong(), t.assigneeId,
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
            now(), t.space.code.toLong(), t.assigneeId, t.id,
        )
    }

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
        settings = queries.readSettings().awaitAsOneOrNull()
            ?.let { runCatching { json.decodeFromString(Settings.serializer(), it) }.getOrNull() }
            ?: Settings(),
    )

    companion object {
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
)

/** Contenu du dialogue de créneau : horaires locaux, deep work ou occupé, récurrence. */
data class BlockEdit(
    val title: String,
    val start: LocalDateTime,
    val end: LocalDateTime,
    val kind: BlockKind = BlockKind.BUSY,
    val recurrence: BlockRecurrence = BlockRecurrence.NONE,
)

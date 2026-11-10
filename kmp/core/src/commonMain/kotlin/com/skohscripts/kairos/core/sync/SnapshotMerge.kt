package com.skohscripts.kairos.core.sync

import com.skohscripts.kairos.core.model.BlockException
import com.skohscripts.kairos.core.model.CalendarSource
import com.skohscripts.kairos.core.model.DayMark
import com.skohscripts.kairos.core.model.KairosSnapshot
import com.skohscripts.kairos.core.model.Note
import com.skohscripts.kairos.core.model.Task
import com.skohscripts.kairos.core.model.TaskDependency
import com.skohscripts.kairos.core.model.TimeBlock
import com.skohscripts.kairos.core.model.WorkSession
import kotlin.time.Instant

/** Une base et son état de synchronisation : un côté de la fusion. */
data class Synced(val snapshot: KairosSnapshot, val data: SyncData)

/** Un enregistrement de la portée prêt à fusionner : ses marques et ses champs. */
data class Rec(val marks: Marks, val lifted: Lifted) {
    /** La marque effective de chaque champ. */
    fun stamps(): Map<String, Stamp> = lifted.fields.keys.associateWith(marks::of)
}

/**
 * Conflit réel (docs/spec/synchronisation.md § Fusion, étape 4) : le champ [field] de [ref] (ou d'un réglage partagé quand
 * [ref] est nul) a été écrit des deux côtés **sans que l'un ait vu l'autre**. La valeur de [winner] est appliquée ; celle de
 * [loser] ([lostValue], texte canonique) est gardée pour « À revoir ».
 */
data class Conflict(val ref: RecordRef?, val field: String, val lostValue: String, val loser: Stamp, val winner: Stamp, val keptValue: String)

/** Élément supprimé d'un côté mais modifié de l'autre sans que la suppression l'ait vu : conservé, et signalé. */
data class KeptAfterDeletion(val ref: RecordRef)

/** Ce que la fusion change **ici**, par genre : enregistrements ajoutés, modifiés (champs changés), retirés. */
data class LocalChanges(
    val added: Map<RecordKind, Int> = emptyMap(),
    val modified: Map<RecordKind, Int> = emptyMap(),
    val removed: Map<RecordKind, Int> = emptyMap(),
    val settings: Boolean = false,
) {
    val isEmpty: Boolean get() = added.isEmpty() && modified.isEmpty() && removed.isEmpty() && !settings

    /** La fusion modifie ou supprime quelque chose d'existant (une sauvegarde est alors exigée). */
    val destructive: Boolean get() = modified.isNotEmpty() || removed.isNotEmpty() || settings
}

/**
 * Résultat de [SnapshotMerge.merge] : l'état fusionné (base et état de synchronisation), ce qui change ici, les conflits, les
 * éléments conservés, et s'il faut réécrire le fichier ([writeNeeded] : l'état fusionné, restreint à la portée, diffère de
 * celui du fichier). [clockAhead] : une marque du fichier est à plus de [Drift.WARN_MILLIS] dans le futur.
 */
data class MergeOutcome(
    val merged: Synced,
    val changes: LocalChanges,
    val conflicts: List<Conflict>,
    val kept: List<KeptAfterDeletion>,
    val writeNeeded: Boolean,
    val clockAhead: Boolean,
)

/**
 * Fusion de deux états (docs/spec/synchronisation.md § Fusion) : pure, déterministe, sans horloge (l'heure est un paramètre),
 * sans hasard, sans E/S. Chaque champ prend la valeur de la marque la plus grande (« dernier écrivain par champ ») ; un
 * enregistrement présent d'un seul côté survit s'il porte une marque que l'autre côté n'a pas vue (« ajout gagnant ») ; les
 * écritures simultanées d'un même champ sont listées ([Conflict]). Commutative, associative et idempotente sur la forme
 * canonique ([Canonical]), à l'identifiant local près (`SnapshotMergePropertiesTest`).
 *
 * Les réparations d'invariants ([SyncRepair]) ne font **pas** partie de la fusion : elles s'appliquent ensuite comme une écriture
 * locale ordinaire, que chaque appareil calcule à l'identique.
 */
object SnapshotMerge {
    /** Registres particuliers : pas de « dernier écrivain » mais un ordre fixe sur la valeur. */
    private val MIN_NON_NULL = mapOf(RecordKind.SESSION to setOf("endedAt"))

    /**
     * Fusionne [theirs] (le fichier) dans [ours] (cette base, réconciliée). Erreur si [theirs] porte une marque à plus de
     * [Drift.MAX_MILLIS] dans le futur de [nowMillis] (le fichier est alors refusé par l'appelant, sans rien fusionner).
     */
    fun merge(ours: Synced, theirs: Synced, nowMillis: Long): MergeOutcome {
        val theirTop = theirs.data.seen.values.fold(theirs.data.clock) { a, b -> if (b > a) b else a }
        require(theirTop.wall - nowMillis <= Drift.MAX_MILLIS) { "marque trop loin dans le futur" }
        val o = lift(ours)
        val t = lift(theirs)
        val oSeen = ours.data.seen
        val tSeen = theirs.data.seen
        val conflicts = ArrayList<Conflict>()
        val kept = ArrayList<KeptAfterDeletion>()
        // Rempli dans l'ordre des références (genre puis uid) : l'abaissement attribue les identifiants dans cet ordre.
        val merged = LinkedHashMap<RecordRef, Rec>()
        for (ref in (o.keys + t.keys).sorted()) {
            val a = o[ref]
            val b = t[ref]
            when {
                a != null && b != null -> merged[ref] = both(ref, a, b, oSeen, tSeen, conflicts)
                a != null -> survive(a, tSeen)?.let { merged[ref] = a; if (it) kept += KeptAfterDeletion(ref) }
                b != null -> survive(b, oSeen)?.let { merged[ref] = b; if (it) kept += KeptAfterDeletion(ref) }
            }
        }
        val settingsFields = SyncRecords.liftSettings(ours.snapshot.settings)
        val settings = fields(null, null, Rec(ours.data.settings, Lifted(settingsFields, Instant.DISTANT_PAST)),
            Rec(theirs.data.settings, Lifted(SyncRecords.liftSettings(theirs.snapshot.settings), Instant.DISTANT_PAST)), oSeen, tSeen, conflicts)

        var seen = Context.join(oSeen, tSeen)
        var clock = Hlc.receive(ours.data.clock, theirTop, nowMillis)
        // Marques d'un fichier d'avant la synchronisation : couvertes par personne, elles rendraient leurs éléments
        // impossibles à supprimer ailleurs. Elles deviennent une écriture locale de cette fusion (une seule marque fraîche).
        var adopted: Stamp? = null
        fun adopt(s: Stamp): Stamp = if (s.device != Stamp.LEGACY_DEVICE) s else adopted ?: Stamp(Hlc.tick(clock, nowMillis), ours.data.device).also { adopted = it }
        for ((ref, r) in merged.entries.toList()) merged[ref] = r.copy(marks = adoptMarks(r.marks, ::adopt))
        val settingsMarks = adoptMarks(settings.marks, ::adopt)
        adopted?.let { clock = it.hlc; seen = seen + (ours.data.device to it.hlc) }

        val lowered = lower(ours, merged, settings.lifted.fields, settingsMarks, seen, clock, theirs.data, o)
        val result = merged.filterKeys { it in lowered.second.marks }
        val changes = changes(o, result, settingsFields, settings.lifted.fields)
        // Sans relever les deux états : leur forme canonique se calcule sur les enregistrements déjà levés.
        val writeNeeded = Canonical.from(result, settings.lifted.fields, settingsMarks, seen) !=
            Canonical.from(t, SyncRecords.liftSettings(theirs.snapshot.settings), theirs.data.settings, theirs.data.seen)
        return MergeOutcome(
            merged = Synced(lowered.first, lowered.second),
            changes = changes,
            conflicts = conflicts,
            kept = kept,
            writeNeeded = writeNeeded,
            clockAhead = theirTop.wall - nowMillis > Drift.WARN_MILLIS,
        )
    }

    /** Les enregistrements de la portée d'un côté, par référence. Une marque manquante (base incomplète) est prise « legacy ». */
    fun lift(side: Synced): Map<RecordRef, Rec> {
        val scoped = SyncScope.of(side.snapshot, side.data)
        val out = HashMap<RecordRef, Rec>()
        for ((kind, records) in SyncReconcile.liftAll(scoped) { k, id -> side.data.uidOf(k, id) }) {
            for ((id, l) in records) {
                val uid = side.data.uidOf(kind, id) ?: continue
                val ref = RecordRef(kind, uid)
                val marks = side.data.marks[ref] ?: Stamp(Hlc(l.createdAt.toEpochMilliseconds().coerceAtLeast(0), 0), Stamp.LEGACY_DEVICE).let { Marks(it, it) }
                out[ref] = Rec(marks, l)
            }
        }
        return out
    }

    /**
     * Présent d'un seul côté : `null` s'il disparaît (l'autre côté a vu toutes ses marques et ne l'a plus), sinon `true` si
     * l'autre côté l'avait vu naître (il l'avait donc supprimé) : élément conservé et signalé ; `false` pour un simple ajout.
     */
    private fun survive(r: Rec, otherSeen: Map<String, Hlc>): Boolean? {
        val uncovered = (sequenceOf(r.marks.born, r.marks.base) + r.stamps().values.asSequence()).any { !Context.covers(otherSeen, it) }
        if (!uncovered) return null
        return Context.covers(otherSeen, r.marks.born)
    }

    private fun both(ref: RecordRef, a: Rec, b: Rec, aSeen: Map<String, Hlc>, bSeen: Map<String, Hlc>, conflicts: MutableList<Conflict>): Rec {
        val r = fields(ref, MIN_NON_NULL[ref.kind].orEmpty(), a, b, aSeen, bSeen, conflicts)
        val updated = listOfNotNull(a.lifted.updatedAt, b.lifted.updatedAt).maxOrNull()
        return Rec(
            r.marks.copy(born = minOf(a.marks.born, b.marks.born)),
            r.lifted.copy(createdAt = minOf(a.lifted.createdAt, b.lifted.createdAt), updatedAt = updated),
        )
    }

    /**
     * Fusion champ par champ. Valeurs égales : la plus grande marque. Valeurs différentes : la plus grande marque gagne
     * (marques égales, possible seulement entre deux copies migrées : la valeur texte la plus petite) ; si aucun côté n'avait vu
     * l'écriture de l'autre, un conflit est consigné. Registres [minNonNull] : la plus petite valeur non nulle, sans conflit.
     */
    private fun fields(ref: RecordRef?, minNonNull: Set<String>?, a: Rec, b: Rec, aSeen: Map<String, Hlc>, bSeen: Map<String, Hlc>, conflicts: MutableList<Conflict>): Rec {
        val values = LinkedHashMap<String, String>()
        val stamps = LinkedHashMap<String, Stamp>()
        val names = LinkedHashSet(a.lifted.fields.keys).apply { addAll(b.lifted.fields.keys) }
        for (name in names) {
            val va = a.lifted.fields[name]
            val vb = b.lifted.fields[name]
            val sa = a.marks.of(name)
            val sb = b.marks.of(name)
            when {
                va == null -> { values[name] = vb!!; stamps[name] = sb }
                vb == null -> { values[name] = va; stamps[name] = sa }
                va == vb -> { values[name] = va; stamps[name] = maxOf(sa, sb) }
                minNonNull != null && name in minNonNull -> {
                    val pick = listOf(va to sa, vb to sb).filter { it.first != NULL }.minByOrNull { it.first } ?: (va to sa)
                    values[name] = pick.first
                    stamps[name] = pick.second
                }
                else -> {
                    val aWins = sa > sb || (sa == sb && va < vb)
                    val (win, winStamp, lose, loseStamp) = if (aWins) Quad(va, sa, vb, sb) else Quad(vb, sb, va, sa)
                    values[name] = win
                    stamps[name] = winStamp
                    val seenByOther = Context.covers(bSeen, sa) || Context.covers(aSeen, sb)
                    if (!seenByOther && sa != sb) conflicts += Conflict(ref, name, lose, loseStamp, winStamp, win)
                }
            }
        }
        val base = minOf(a.marks.base, b.marks.base)
        return Rec(
            Marks(born = minOf(a.marks.born, b.marks.born), base = base, stamps = stamps.filterValues { it != base }, hashes = values.mapValues { SyncRecords.hash(it.value) }),
            Lifted(values, a.lifted.createdAt, a.lifted.updatedAt),
        )
    }

    private data class Quad(val win: String, val winStamp: Stamp, val lose: String, val loseStamp: Stamp)

    private fun adoptMarks(m: Marks, adopt: (Stamp) -> Stamp): Marks {
        if (m.all().none { it.device == Stamp.LEGACY_DEVICE }) return m
        return m.copy(born = adopt(m.born), base = adopt(m.base), stamps = m.stamps.mapValues { adopt(it.value) })
    }

    /**
     * Abaissement vers une base (étape 6) : les enregistrements déjà connus gardent leur identifiant local, les nouveaux en
     * reçoivent à partir du plus grand identifiant du genre, dans l'ordre des `uid` ; les références redeviennent des
     * identifiants ; un enregistrement à qui il manque un bout obligatoire disparaît (et ses marques avec lui) ; les champs
     * locaux et tout ce qui est hors portée restent ceux de [ours].
     */
    private fun lower(
        ours: Synced,
        merged: Map<RecordRef, Rec>,
        sharedSettings: Map<String, String>,
        settingsMarks: Marks,
        seen: Map<String, Hlc>,
        clock: Hlc,
        theirs: SyncData,
        ourRecs: Map<RecordRef, Rec>,
    ): Pair<KairosSnapshot, SyncData> {
        val s = ours.snapshot
        val known = ours.data.idsByUid()
        val nextId = HashMap<RecordKind, Long>()
        fun maxId(kind: RecordKind): Long = when (kind) {
            RecordKind.CALENDAR -> s.calendarSources.maxOfOrNull { it.id }
            RecordKind.TASK -> s.tasks.maxOfOrNull { it.id }
            RecordKind.BLOCK -> s.timeBlocks.maxOfOrNull { it.id }
            RecordKind.NOTE -> s.notes.maxOfOrNull { it.id }
            RecordKind.SESSION -> s.workSessions.maxOfOrNull { it.id }
            RecordKind.EXCEPTION -> s.blockExceptions.maxOfOrNull { it.id }
            RecordKind.DEPENDENCY -> s.dependencies.maxOfOrNull { it.id }
            RecordKind.DAYMARK -> s.dayMarks.maxOfOrNull { it.id }
        }.let { snap -> maxOf(snap ?: 0L, known[kind]?.values?.maxOrNull() ?: 0L) }
        // Identifiants de tous les enregistrements fusionnés, avant de construire (les références peuvent pointer en avant).
        val ids = HashMap<RecordKind, HashMap<String, Long>>()
        for (ref in merged.keys) { // déjà trié : genre puis uid
            val id = known[ref.kind]?.get(ref.uid) ?: (nextId.getOrPut(ref.kind) { maxId(ref.kind) } + 1).also { nextId[ref.kind] = it }
            ids.getOrPut(ref.kind) { HashMap() }[ref.uid] = id
        }
        val built = HashMap<RecordKind, HashMap<String, Long>>()
        // Une référence molle vers une tâche absente garde son uid : identité fantôme (déjà connue ici, ou un identifiant neuf).
        val phantoms = HashMap<String, Long>()
        // Identifiant **négatif** : jamais attribué par la base (AUTOINCREMENT), donc jamais repris par une tâche créée ensuite ici.
        var lowestPhantom = minOf(0L, known[RecordKind.TASK]?.values?.minOrNull() ?: 0L)
        fun taskId(uid: String): Long = ids[RecordKind.TASK]?.get(uid) ?: known[RecordKind.TASK]?.get(uid)
            ?: phantoms.getOrPut(uid) { --lowestPhantom }
        val idOf = SyncRecords.IdOf { kind, uid -> built[kind]?.get(uid) ?: if (kind == RecordKind.TASK) taskId(uid) else null }
        val scoped = SyncScope.of(s, ours.data)
        val ourTasks = scoped.tasks.associateBy { it.id }
        val ourNotes = scoped.notes.associateBy { it.id }
        val out = Built()
        for (kind in RecordKind.entries) {
            for ((ref, r) in merged) {
                if (ref.kind != kind) continue
                val id = ids.getValue(kind).getValue(ref.uid)
                val ok: Boolean = when (kind) {
                    RecordKind.CALENDAR -> out.calendars.add(SyncRecords.buildCalendar(id, r.lifted))
                    RecordKind.TASK -> out.tasks.add(keepUnresolved(SyncRecords.buildTask(id, r.lifted, idOf, ourTasks[id]), ourTasks[id], r, ourRecs[ref]?.lifted))
                    RecordKind.BLOCK -> SyncRecords.buildBlock(id, r.lifted, idOf)?.let(out.blocks::add) ?: false
                    RecordKind.NOTE -> out.notes.add(keepUnresolved(SyncRecords.buildNote(id, r.lifted, idOf), ourNotes[id], r, ourRecs[ref]?.lifted))
                    RecordKind.SESSION -> SyncRecords.buildSession(id, r.lifted, idOf)?.let(out.sessions::add) ?: false
                    RecordKind.EXCEPTION -> SyncRecords.buildException(id, r.lifted, idOf)?.let(out.exceptions::add) ?: false
                    RecordKind.DEPENDENCY -> SyncRecords.buildDependency(id, r.lifted, idOf)?.let(out.dependencies::add) ?: false
                    RecordKind.DAYMARK -> SyncRecords.buildDayMark(id, r.lifted, idOf)?.let(out.dayMarks::add) ?: false
                }
                if (ok) built.getOrPut(kind) { HashMap() }[ref.uid] = id
            }
        }
        // Même portée qu'à la levée : une session d'une tâche supprimée est reconstruite, jamais gardée en double (issue #193).
        val inScope = scoped
        fun <T> keep(all: List<T>, scopedList: List<T>, rebuilt: List<T>): List<T> {
            val scopedSet = scopedList.toHashSet()
            return all.filter { it !in scopedSet } + rebuilt
        }
        val scopedTaskIds = inScope.tasks.mapTo(HashSet()) { it.id }
        val allTaskIds = (s.tasks.filter { it.id !in scopedTaskIds }.map { it.id } + out.tasks.map { it.id }).toHashSet()
        val snapshot = s.copy(
            calendarSources = keep(s.calendarSources, inScope.calendars, out.calendars),
            tasks = keep(s.tasks, inScope.tasks, out.tasks),
            timeBlocks = keep(s.timeBlocks, inScope.blocks, out.blocks),
            notes = keep(s.notes, inScope.notes, out.notes),
            workSessions = keep(s.workSessions, inScope.sessions, out.sessions),
            blockExceptions = keep(s.blockExceptions, inScope.exceptions, out.exceptions),
            // Une dépendance hors portée vers une tâche que la fusion a retirée tombe avec elle.
            dependencies = keep(s.dependencies, inScope.dependencies, out.dependencies).filter { it.taskId in allTaskIds && it.blockerId in allTaskIds },
            dayMarks = keep(s.dayMarks, inScope.dayMarks, out.dayMarks),
            settings = SyncRecords.buildSettings(sharedSettings, s.settings),
        )
        val uidMaps = built.mapValues { (_, m) -> m.entries.associate { (uid, id) -> id to uid } }.toMutableMap()
        // Les identités fantômes encore référencées par la portée reconstruite (tâches, notes, sessions).
        val tasksBuilt = built[RecordKind.TASK].orEmpty()
        val byTaskId = (ids[RecordKind.TASK].orEmpty() + known[RecordKind.TASK].orEmpty() + phantoms).entries.associate { (uid, id) -> id to uid }
        val referenced = (out.tasks.flatMap { listOfNotNull(it.parentId, it.seriesId) } + out.notes.mapNotNull { it.convertedTaskId } +
            out.sessions.map { it.taskId }).toSet()
        val phantomIds = referenced.filter { id -> byTaskId[id]?.let { it !in tasksBuilt } == true }
        if (phantomIds.isNotEmpty()) uidMaps[RecordKind.TASK] = uidMaps[RecordKind.TASK].orEmpty() + phantomIds.associateWith { byTaskId.getValue(it) }
        val uids = uidMaps.filterValues { it.isNotEmpty() }
        val marks = merged.filterKeys { built[it.kind]?.containsKey(it.uid) == true }.mapValues { it.value.marks }
        val data = ours.data.copy(
            clock = clock,
            seen = seen,
            uids = uids,
            marks = marks,
            settings = settingsMarks,
            labels = theirs.labels + (theirs.device to theirs.label).takeIf { theirs.label.isNotEmpty() }.let { listOfNotNull(it).toMap() } + ours.data.labels,
        )
        return snapshot to data
    }

    /**
     * Une référence molle levée vide ici parce qu'elle pointait **hors de la portée** (une note convertie en tâche d'équipe)
     * garde sa valeur locale tant que la fusion ne l'a pas changée : la fusion ne touche pas à ce qu'elle ne voit pas.
     */
    private fun keepUnresolved(task: Task, local: Task?, r: Rec, ourLifted: Lifted?): Task {
        if (local == null || ourLifted == null) return task
        fun same(name: String) = r.lifted.fields[name] == ourLifted.fields[name] && ourLifted.fields[name] == NULL
        return task.copy(
            parentId = if (same("parentId")) local.parentId else task.parentId,
            seriesId = if (same("seriesId")) local.seriesId else task.seriesId,
        )
    }

    private fun keepUnresolved(note: Note, local: Note?, r: Rec, ourLifted: Lifted?): Note {
        if (local == null || ourLifted == null) return note
        val same = r.lifted.fields["convertedTaskId"] == ourLifted.fields["convertedTaskId"] && ourLifted.fields["convertedTaskId"] == NULL
        return if (same) note.copy(convertedTaskId = local.convertedTaskId) else note
    }

    private fun changes(before: Map<RecordRef, Rec>, after: Map<RecordRef, Rec>, settingsBefore: Map<String, String>, settingsAfter: Map<String, String>): LocalChanges {
        val added = HashMap<RecordKind, Int>()
        val modified = HashMap<RecordKind, Int>()
        val removed = HashMap<RecordKind, Int>()
        for ((ref, r) in after) {
            val old = before[ref]
            when {
                old == null -> added.inc(ref.kind)
                old.lifted.fields != r.lifted.fields -> modified.inc(ref.kind)
            }
        }
        for (ref in before.keys) if (ref !in after) removed.inc(ref.kind)
        return LocalChanges(added, modified, removed, settings = SyncScope.SHARED_SETTINGS.any { settingsBefore[it] != settingsAfter[it] })
    }

    private const val NULL = "null"

    /** `Map.merge` n'existe que sur la JVM : `core` compile aussi pour le web (wasm). */
    private fun HashMap<RecordKind, Int>.inc(kind: RecordKind) { this[kind] = (this[kind] ?: 0) + 1 }

    private class Built {
        val calendars = ArrayList<CalendarSource>()
        val tasks = ArrayList<Task>()
        val blocks = ArrayList<TimeBlock>()
        val notes = ArrayList<Note>()
        val sessions = ArrayList<WorkSession>()
        val exceptions = ArrayList<BlockException>()
        val dependencies = ArrayList<TaskDependency>()
        val dayMarks = ArrayList<DayMark>()
    }
}

package com.skohscripts.kairos.core.sync

import com.skohscripts.kairos.core.model.KairosSnapshot
import com.skohscripts.kairos.core.model.Note
import com.skohscripts.kairos.core.model.Task
import com.skohscripts.kairos.core.model.TaskDependency
import com.skohscripts.kairos.core.model.TaskRecurrence
import com.skohscripts.kairos.core.model.TaskStatus
import com.skohscripts.kairos.core.model.WorkSession
import kotlinx.datetime.LocalDate
import kotlin.random.Random
import kotlin.time.Instant

/**
 * Simulateur d'appareils pour les tests de propriétés (docs/spec/synchronisation.md § Tests) : chaque appareil fait des
 * écritures locales (réconciliées comme le fera le dépôt), puis échange son état avec un autre par [SnapshotMerge.merge]
 * suivie des réparations appliquées comme une écriture locale. À graine fixe, sans bibliothèque de test par propriétés.
 */
internal class SyncSim(seed: Int, deviceCount: Int = 3) {
    val random = Random(seed)
    var now: Long = Instant.parse("2026-10-01T08:00:00Z").toEpochMilliseconds()

    /** Une écriture de champ consignée : pour vérifier qu'aucune valeur n'est perdue en silence. */
    data class Write(val ref: RecordRef, val field: String, val value: String, val stamp: Stamp, val seenAtWrite: Map<String, Hlc>)

    val writes = ArrayList<Write>()
    val conflicts = ArrayList<Conflict>()
    val deleted = HashSet<RecordRef>()

    /** Suppressions locales et ce que l'appareil avait vu à ce moment : une suppression dépasse les écritures qu'elle a vues. */
    val deletions = ArrayList<Pair<RecordRef, Map<String, Hlc>>>()
    val created = HashSet<RecordRef>()

    inner class Device(val name: String) {
        var snapshot = KairosSnapshot()
        var data: SyncData = SyncReconcile.activate(snapshot, name, now)
        private var n = 0
        private val newUid = { "$name-${++n}" }

        val synced: Synced get() = Synced(snapshot, data)

        /** Une écriture locale : [change] puis réconciliation, comme `KairosRepository.write`. */
        fun write(change: (KairosSnapshot) -> KairosSnapshot) {
            now += 1 + random.nextInt(3_000)
            val before = snapshot
            val after = change(before)
            if (after == before) return
            val seenBefore = data.seen
            val oldMarks = data.marks
            val r = SyncReconcile.reconcile(after, data, before, now, newUid)
            snapshot = after
            data = r.data
            log(oldMarks, seenBefore)
        }

        private fun log(oldMarks: Map<RecordRef, Marks>, seenBefore: Map<String, Hlc>) {
            val lifted = SnapshotMerge.lift(synced)
            for ((ref, rec) in lifted) {
                val old = oldMarks[ref]
                if (old == null) created += ref
                for ((field, value) in rec.lifted.fields) {
                    val stamp = rec.marks.of(field)
                    if (stamp.device == name && (old == null || old.of(field) != stamp)) writes += Write(ref, field, value, stamp, seenBefore)
                }
            }
            for (ref in oldMarks.keys) if (ref !in data.marks) { deleted += ref; deletions += ref to seenBefore }
        }

        /** Lit l'état de [other] et le fusionne ici, puis applique les réparations comme une écriture locale. */
        fun pull(other: Device) {
            now += 1 + random.nextInt(3_000)
            val out = SnapshotMerge.merge(synced, other.synced, now)
            conflicts += out.conflicts
            snapshot = out.merged.snapshot
            data = out.merged.data
            // Comme le dépôt : le résultat installé passe par l'entonnoir d'écriture (réconciliation complète), puis les réparations.
            settle()
            val repaired = SyncRepair.repair(snapshot, data)
            if (repaired.repairs.isNotEmpty()) write { repaired.snapshot }
        }

        /** Réconciliation complète (sans état précédent), comme à l'installation d'une fusion ou à l'ouverture. */
        fun settle() {
            val seenBefore = data.seen
            val oldMarks = data.marks
            val r = SyncReconcile.reconcile(snapshot, data, null, now, newUid)
            if (!r.changed) return
            data = r.data
            log(oldMarks, seenBefore)
        }

        fun canonical(): Canonical = Canonical.of(snapshot, data)

        fun inScopeTasks(): List<Task> = SyncScope.of(snapshot).tasks

        // --- Opérations aléatoires ----------------------------------------------------------------------------------

        fun randomOp() {
            when (random.nextInt(12)) {
                0, 1 -> createTask()
                2, 3, 4 -> editTask()
                5 -> deleteTask()
                6 -> completeRecurring()
                7 -> startTimer()
                8 -> stopTimer()
                9 -> addDependency()
                10 -> noteOp()
                else -> settingsOp()
            }
        }

        /** Identifiants jamais réutilisés, comme `AUTOINCREMENT` : au-delà de tout identifiant déjà vu ici. */
        private val highest = HashMap<String, Long>()
        private fun next(kind: String, current: Long?): Long {
            val v = maxOf(highest[kind] ?: 0L, current ?: 0L) + 1
            highest[kind] = v
            return v
        }
        private fun nextTaskId() = next("task", maxOf(snapshot.tasks.maxOfOrNull { it.id } ?: 0, data.uids[RecordKind.TASK]?.keys?.maxOrNull() ?: 0))

        fun createTask() = write { s ->
            val at = Instant.fromEpochMilliseconds(now)
            val recurring = random.nextInt(4) == 0
            s.copy(tasks = s.tasks + Task(nextTaskId(), "T${random.nextInt(1000)}", priority = random.nextInt(3), fibonacciPoints = 3,
                recurrence = if (recurring) TaskRecurrence.DAILY else TaskRecurrence.NONE, createdAt = at, updatedAt = at))
        }

        fun editTask() = write { s ->
            val t = s.tasks.randomOrNull(random) ?: return@write s
            val at = Instant.fromEpochMilliseconds(now)
            val edited = when (random.nextInt(6)) {
                0 -> t.copy(title = "T${random.nextInt(1000)}")
                1 -> t.copy(priority = random.nextInt(3))
                2 -> t.copy(deadline = LocalDate(2026, 10, 1 + random.nextInt(28)))
                3 -> t.copy(description = "d${random.nextInt(100)}")
                4 -> t.copy(estimatedMinutes = 5 * (1 + random.nextInt(24)))
                else -> t.copy(parentId = s.tasks.filter { it.id != t.id }.randomOrNull(random)?.id)
            }
            s.copy(tasks = s.tasks.map { if (it.id == t.id) edited.copy(updatedAt = at) else it })
        }

        fun deleteTask() = write { s ->
            val t = s.tasks.randomOrNull(random) ?: return@write s
            s.copy(tasks = s.tasks.filter { it.id != t.id }, dependencies = s.dependencies.filter { it.taskId != t.id && it.blockerId != t.id })
        }

        fun completeRecurring() = write { s ->
            val t = s.tasks.filter { it.status == TaskStatus.TODO && it.recurrence == TaskRecurrence.DAILY }.randomOrNull(random) ?: return@write s
            val at = Instant.fromEpochMilliseconds(now)
            val next = t.copy(id = nextTaskId(), seriesId = t.seriesId ?: t.id, deadline = LocalDate(2026, 11, 1 + random.nextInt(28)), createdAt = at, updatedAt = at)
            s.copy(tasks = s.tasks.map { if (it.id == t.id) it.copy(status = TaskStatus.DONE, updatedAt = at) else it } + next)
        }

        fun startTimer() = write { s ->
            val t = s.tasks.randomOrNull(random) ?: return@write s
            val at = Instant.fromEpochMilliseconds(now)
            val closed = s.workSessions.map { if (it.endedAt == null) it.copy(endedAt = at) else it }
            s.copy(workSessions = closed + WorkSession(next("session", s.workSessions.maxOfOrNull { it.id }), t.id, at, null, at))
        }

        fun stopTimer() = write { s ->
            val at = Instant.fromEpochMilliseconds(now)
            s.copy(workSessions = s.workSessions.map { if (it.endedAt == null) it.copy(endedAt = at) else it })
        }

        fun addDependency() = write { s ->
            val a = s.tasks.randomOrNull(random) ?: return@write s
            val b = s.tasks.filter { it.id != a.id }.randomOrNull(random) ?: return@write s
            if (s.dependencies.any { it.taskId == a.id && it.blockerId == b.id }) return@write s
            s.copy(dependencies = s.dependencies + TaskDependency(next("dependency", s.dependencies.maxOfOrNull { it.id }), a.id, b.id, Instant.fromEpochMilliseconds(now)))
        }

        fun noteOp() = write { s ->
            val at = Instant.fromEpochMilliseconds(now)
            val note = s.notes.randomOrNull(random)
            when {
                note == null || random.nextInt(3) == 0 -> s.copy(notes = s.notes + Note(next("note", s.notes.maxOfOrNull { it.id }), "N${random.nextInt(100)}", createdAt = at, updatedAt = at))
                random.nextBoolean() -> s.copy(notes = s.notes.map { if (it.id == note.id) it.copy(body = "N${random.nextInt(100)}", updatedAt = at) else it })
                else -> s.copy(notes = s.notes.filter { it.id != note.id })
            }
        }

        fun settingsOp() = write { s ->
            if (random.nextBoolean()) s.copy(settings = s.settings.copy(workdayStartHour = 6 + random.nextInt(4))) else s.copy(settings = s.settings.copy(taskTypes = "A,B,${random.nextInt(9)}"))
        }
    }

    val devices: List<Device> = (0 until deviceCount).map { Device("d${it}ab12cd34") }

    /** Échanges complets jusqu'à stabilité : chacun lit chacun des autres, plusieurs tours. */
    fun converge(maxRounds: Int = 6) {
        repeat(maxRounds) {
            val before = devices.map { it.canonical() }
            for (a in devices) for (b in devices) if (a !== b) a.pull(b)
            if (devices.map { it.canonical() } == before && before.toSet().size == 1) return
        }
    }

    /** Une histoire aléatoire : opérations locales entremêlées d'échanges partiels. */
    fun run(steps: Int) {
        repeat(steps) {
            val d = devices.random(random)
            if (random.nextInt(4) == 0) d.pull(devices.filter { it !== d }.random(random)) else d.randomOp()
        }
    }
}

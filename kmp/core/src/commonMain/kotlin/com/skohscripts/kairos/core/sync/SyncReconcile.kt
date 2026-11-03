package com.skohscripts.kairos.core.sync

import com.skohscripts.kairos.core.model.BlockSource
import com.skohscripts.kairos.core.model.KairosSnapshot
import com.skohscripts.kairos.core.model.Task
import com.skohscripts.kairos.core.model.TaskRecurrence
import com.skohscripts.kairos.core.model.TaskStatus
import kotlin.time.Instant

/**
 * Réconciliation locale (docs/spec/synchronisation.md § Réconciliation locale) : déduit les marques de la **différence**
 * entre l'état de la base et l'état de synchronisation, après chaque écriture. Aucun chemin d'écriture du dépôt n'a à dater
 * quoi que ce soit. Pure et idempotente : une seconde passe ne change rien.
 */
object SyncReconcile {
    /** Résultat d'une passe : le nouvel état, et s'il diffère de l'ancien (rien à enregistrer sinon). */
    data class Result(val data: SyncData, val changed: Boolean)

    /**
     * Activation (premier lien, première fusion) : chaque enregistrement de la portée reçoit un `uid` de migration
     * déterministe (`legacy:<genre>:<id>:<createdAt en ms>`), sauf ceux de [presets] (exemples d'une base neuve, `example:<n>`) ;
     * marques `born = Stamp(createdAt, device)`, `base = Stamp(updatedAt ?? createdAt, device)` ; celles de [origin] (exemples
     * restés tels que posés) prennent la marque d'origine, couverte par tout le monde. Réglages : `base = Stamp(0, device)`.
     * L'horloge démarre au plus grand de ces instants et de l'heure courante, et le contexte couvre tout jusque-là.
     */
    fun activate(
        snapshot: KairosSnapshot,
        device: String,
        nowMillis: Long,
        presets: Map<RecordKind, Map<Long, String>> = emptyMap(),
        origin: Set<Pair<RecordKind, Long>> = emptySet(),
        label: String = "",
    ): SyncData {
        require(device.isNotEmpty() && device != Stamp.LEGACY_DEVICE && device.length <= Stamp.MAX_DEVICE) { "identité d'appareil invalide" }
        val scoped = SyncScope.of(snapshot)
        val uids = RecordKind.entries.associateWith { kind ->
            val created = createdAtById(scoped, kind)
            scoped.ids(kind).associateWith { id -> presets[kind]?.get(id) ?: legacyUid(kind, id, created.getValue(id)) }
        }
        val lifted = liftAll(scoped) { kind, id -> uids[kind]?.get(id) }
        var top = Hlc.ZERO
        val marks = HashMap<RecordRef, Marks>()
        for ((kind, records) in lifted) {
            for ((id, l) in records) {
                val stamps = if ((kind to id) in origin) {
                    Stamp.ORIGIN to Stamp.ORIGIN
                } else {
                    val born = Stamp(Hlc(l.createdAt.millis(), 0), device)
                    val base = Stamp(Hlc((l.updatedAt ?: l.createdAt).millis().coerceAtLeast(born.hlc.wall), 0), device)
                    if (base.hlc > top) top = base.hlc
                    born to base
                }
                marks[RecordRef(kind, uids.getValue(kind).getValue(id))] = Marks(stamps.first, stamps.second, hashes = hashes(l.fields))
            }
        }
        val clock = if (top > Hlc(nowMillis.coerceAtLeast(0), 0)) top else Hlc(nowMillis.coerceAtLeast(0), 0)
        return SyncData(
            device = device,
            clock = clock,
            seen = mapOf(device to clock),
            uids = uids.filterValues { it.isNotEmpty() },
            marks = marks,
            settings = Marks(Stamp(Hlc.ZERO, device), Stamp(Hlc.ZERO, device), hashes = hashes(SyncRecords.liftSettings(snapshot.settings))),
            label = label,
        )
    }

    /**
     * Une passe après une écriture locale. Pour chaque enregistrement de la portée : sans `uid` (nouveau), un `uid` (aléatoire
     * par [newUid], ou déterministe quand deux appareils peuvent créer « la même chose ») et `born = base = tick` ; avec `uid`,
     * une nouvelle marque pour chaque champ dont l'empreinte a changé. Un `uid` dont l'enregistrement n'existe plus perd ses
     * marques (la suppression se lit à l'absence, `SnapshotMerge`). Une seule marque `tick` par passe, posée seulement si
     * quelque chose a changé ; le contexte de l'appareil avance avec elle.
     *
     * [previous] (l'état publié avant l'écriture) limite le hachage à ce qui a changé ; sans lui (ouverture), tout est vérifié,
     * ce qui rattrape un arrêt entre la transaction et la réconciliation. Il sert aussi à reconnaître l'occurrence suivante
     * d'une récurrente terminée dans la même écriture (`next:<uid>`).
     */
    fun reconcile(snapshot: KairosSnapshot, data: SyncData, previous: KairosSnapshot?, nowMillis: Long, newUid: () -> String): Result {
        val scoped = SyncScope.of(snapshot)
        val uids = assignUids(scoped, data, previous, newUid)
        val prevScoped = previous?.let(SyncScope::of)
        var stamp: Stamp? = null
        fun tick(): Stamp = stamp ?: Stamp(Hlc.tick(data.clock, nowMillis), data.device).also { stamp = it }

        val marks = HashMap(data.marks)
        var changed = false
        val skip = prevScoped?.let { unchangedIds(scoped, it, data, uids) } ?: emptyMap()
        val lifted = liftAll(scoped, skip) { kind, id -> uids[kind]?.get(id) }
        for ((kind, records) in lifted) {
            for ((id, l) in records) {
                val ref = RecordRef(kind, uids.getValue(kind).getValue(id))
                val old = marks[ref]
                if (old == null) {
                    val s = tick()
                    marks[ref] = Marks(s, s, hashes = hashes(l.fields))
                    changed = true
                    continue
                }
                val updated = restamp(old, l.fields, ::tick)
                if (updated != old) {
                    marks[ref] = updated
                    changed = true
                }
            }
        }
        // Enregistrements disparus : leurs marques partent avec eux.
        val live = HashSet<RecordRef>()
        for ((kind, m) in uids) for (uid in m.values) live += RecordRef(kind, uid)
        val gone = marks.keys.filter { it !in live }
        if (gone.isNotEmpty()) {
            gone.forEach(marks::remove)
            changed = true
        }
        val settings = restamp(data.settings, SyncRecords.liftSettings(snapshot.settings), ::tick)
        if (settings != data.settings) changed = true
        if (uids != data.uids) changed = true
        if (!changed) return Result(data, false)
        val clock = stamp?.hlc ?: data.clock
        val seen = if (stamp != null) data.seen + (data.device to clock) else data.seen
        return Result(data.copy(clock = clock, seen = seen, uids = uids, marks = marks, settings = settings), true)
    }

    /** Nouvelle marque pour chaque champ dont l'empreinte diffère ; un champ jamais haché (ajouté depuis) l'est sans marque. */
    private fun restamp(old: Marks, fields: Map<String, String>, tick: () -> Stamp): Marks {
        var stamps = old.stamps
        var hashes = old.hashes
        for ((name, value) in fields) {
            val h = SyncRecords.hash(value)
            val known = old.hashes[name]
            if (known == h) continue
            hashes = hashes + (name to h)
            if (known != null) stamps = stamps + (name to tick())
        }
        return if (stamps === old.stamps && hashes === old.hashes) old else old.copy(stamps = stamps, hashes = hashes)
    }

    /**
     * Par genre, les identifiants à ne pas relire : modèle égal à celui de l'état précédent, `uid` déjà connu, et aucune
     * référence vers un enregistrement qui vient d'apparaître ou de disparaître de la portée (sa valeur levée changerait sans
     * que le modèle change). Le coût d'une passe reste ainsi proportionnel à ce que l'écriture touche.
     */
    private fun unchangedIds(now: SyncScope.Scoped, before: SyncScope.Scoped, data: SyncData, uids: Map<RecordKind, Map<Long, String>>): Map<RecordKind, Set<Long>> {
        val moved = RecordKind.entries.associateWith { k ->
            val a = data.uids[k].orEmpty().keys
            val b = uids[k].orEmpty().keys
            (a - b) + (b - a)
        }
        fun touches(kind: RecordKind, id: Long?): Boolean = id != null && id in moved.getValue(kind)
        fun <T> same(a: List<T>, b: List<T>, kind: RecordKind, id: (T) -> Long, refs: (T) -> Boolean): Set<Long> {
            val old = b.associateBy(id)
            val known = data.uids[kind].orEmpty()
            return a.filter { old[id(it)] == it && id(it) in known && !refs(it) }.mapTo(HashSet(), id)
        }
        return mapOf(
            RecordKind.CALENDAR to same(now.calendars, before.calendars, RecordKind.CALENDAR, { it.id }) { false },
            RecordKind.TASK to same(now.tasks, before.tasks, RecordKind.TASK, { it.id }) { touches(RecordKind.TASK, it.parentId) || touches(RecordKind.TASK, it.seriesId) },
            RecordKind.BLOCK to same(now.blocks, before.blocks, RecordKind.BLOCK, { it.id }) { touches(RecordKind.CALENDAR, (it.source as? BlockSource.Imported)?.calendarId) },
            RecordKind.NOTE to same(now.notes, before.notes, RecordKind.NOTE, { it.id }) { touches(RecordKind.TASK, it.convertedTaskId) },
            RecordKind.SESSION to same(now.sessions, before.sessions, RecordKind.SESSION, { it.id }) { touches(RecordKind.TASK, it.taskId) },
            RecordKind.EXCEPTION to same(now.exceptions, before.exceptions, RecordKind.EXCEPTION, { it.id }) { touches(RecordKind.BLOCK, it.blockId) },
            RecordKind.DEPENDENCY to same(now.dependencies, before.dependencies, RecordKind.DEPENDENCY, { it.id }) { touches(RecordKind.TASK, it.taskId) || touches(RecordKind.TASK, it.blockerId) },
            RecordKind.DAYMARK to same(now.dayMarks, before.dayMarks, RecordKind.DAYMARK, { it.id }) { touches(RecordKind.CALENDAR, (it.source as? BlockSource.Imported)?.calendarId) },
        )
    }

    /**
     * Les `uid` de la portée : ceux déjà connus, et pour les nouveaux un `uid` déterministe quand deux appareils peuvent créer
     * « la même chose » chacun de leur côté (docs/spec/synchronisation.md § Identité), aléatoire sinon. Un `uid` déterministe
     * déjà pris par un autre enregistrement retombe sur l'aléatoire. Ordre des genres : un genre ne référence que des genres
     * traités avant lui. Les nouveaux sont traités par identifiant croissant (tirages reproductibles).
     */
    private fun assignUids(scoped: SyncScope.Scoped, data: SyncData, previous: KairosSnapshot?, newUid: () -> String): Map<RecordKind, Map<Long, String>> {
        val out = HashMap<RecordKind, Map<Long, String>>()
        fun uid(kind: RecordKind, id: Long?): String? = id?.let { out[kind]?.get(it) }
        for (kind in RecordKind.entries) {
            val known = data.uids[kind].orEmpty()
            val ids = scoped.ids(kind)
            val m = LinkedHashMap<Long, String>()
            val taken = HashSet<String>()
            for (id in ids) known[id]?.let { m[id] = it; taken += it }
            val fresh = ids.filter { it !in m }.sorted()
            if (kind == RecordKind.TASK) {
                // Deux passes : les tâches ordinaires d'abord, puis celles dont l'uid dérive d'une autre (série, terminée).
                val byId = scoped.tasks.associateBy { it.id }
                val derived = fresh.filter { byId.getValue(it).let(::mayDerive) }
                for (id in fresh - derived.toSet()) m[id] = newUid().also { taken += it }
                out[kind] = m
                for (id in derived) {
                    val candidate = taskUid(byId.getValue(id), scoped, previous) { tid -> m[tid] }
                    m[id] = candidate?.takeIf { it !in taken } ?: newUid()
                    taken += m.getValue(id)
                }
            } else {
                for (id in fresh) {
                    val candidate = deterministic(kind, id, scoped, ::uid)
                    m[id] = candidate?.takeIf { it !in taken } ?: newUid()
                    taken += m.getValue(id)
                }
            }
            out[kind] = m
        }
        return out.filterValues { it.isNotEmpty() }
    }

    private fun mayDerive(t: Task): Boolean = t.seriesId != null && t.recurrence != TaskRecurrence.NONE

    /**
     * `uid` d'une tâche engendrée : `next:<uid de la terminée>` pour l'occurrence suivante d'une récurrente terminée dans la
     * même écriture ; `cal:<uid de la série>:<AAAA-MM>` pour une occurrence « le N du mois ». `null` sinon.
     */
    private fun taskUid(task: Task, scoped: SyncScope.Scoped, previous: KairosSnapshot?, uidOf: (Long) -> String?): String? {
        val series = task.seriesId ?: return null
        if (task.recurrence == TaskRecurrence.MONTHLY_ON_DAY) {
            if (task.recurrencePeriod.isEmpty()) return null
            return uidOf(series)?.let { bounded("cal:$it:${task.recurrencePeriod}") }
        }
        val before = previous?.tasks?.associateBy { it.id } ?: return null
        val finished = scoped.tasks.filter { c ->
            c.id != task.id && (c.id == series || c.seriesId == series) && c.status == TaskStatus.DONE &&
                before[c.id]?.status.let { it != null && it != TaskStatus.DONE }
        }
        return finished.singleOrNull()?.let { uidOf(it.id) }?.let { bounded("next:$it") }
    }

    private fun deterministic(kind: RecordKind, id: Long, scoped: SyncScope.Scoped, uid: (RecordKind, Long?) -> String?): String? = when (kind) {
        RecordKind.CALENDAR -> scoped.calendars.first { it.id == id }.let { c -> bounded("cal:" + c.name.lowercase().filterNot(Char::isWhitespace)) }
        RecordKind.BLOCK -> scoped.blocks.first { it.id == id }.let { b ->
            (b.source as? BlockSource.Imported)?.let { src -> uid(RecordKind.CALENDAR, src.calendarId)?.let { bounded("ics:$it:${src.uid}:${b.start}") } }
        }
        RecordKind.EXCEPTION -> scoped.exceptions.first { it.id == id }.let { x -> uid(RecordKind.BLOCK, x.blockId)?.let { bounded("exc:$it:${x.date}") } }
        RecordKind.DEPENDENCY -> scoped.dependencies.first { it.id == id }.let { d ->
            val t = uid(RecordKind.TASK, d.taskId)
            val b = uid(RecordKind.TASK, d.blockerId)
            if (t != null && b != null) bounded("dep:$t:$b") else null
        }
        RecordKind.DAYMARK -> scoped.dayMarks.first { it.id == id }.let { m ->
            (m.source as? BlockSource.Imported)?.let { src -> uid(RecordKind.CALENDAR, src.calendarId)?.let { bounded("mark:$it:${src.uid}:${m.start}") } }
        }
        RecordKind.TASK, RecordKind.NOTE, RecordKind.SESSION -> null
    }

    /** Un `uid` déterministe de plus de [SyncScope.MAX_UID] caractères est remplacé par son empreinte, elle aussi déterministe. */
    internal fun bounded(uid: String): String =
        if (uid.length <= SyncScope.MAX_UID) uid else uid.substringBefore(':') + ":#" + SyncRecords.hash(uid).toULong().toString(16)

    /** `legacy:<genre>:<id>:<createdAt en ms>` : le même sur deux copies d'une même origine, jamais pour deux créations séparées. */
    fun legacyUid(kind: RecordKind, id: Long, createdAt: Instant): String = "legacy:${kind.code}:$id:${createdAt.toEpochMilliseconds()}"

    /** Les enregistrements de la portée sous forme fusionnable, par genre puis identifiant local, sauf ceux de [skip]. */
    fun liftAll(scoped: SyncScope.Scoped, skip: Map<RecordKind, Set<Long>> = emptyMap(), uids: SyncRecords.UidOf): Map<RecordKind, Map<Long, Lifted>> {
        fun <T> lift(kind: RecordKind, list: List<T>, id: (T) -> Long, f: (T) -> Lifted): Map<Long, Lifted> {
            val s = skip[kind].orEmpty()
            val out = LinkedHashMap<Long, Lifted>()
            for (r in list) if (id(r) !in s) out[id(r)] = f(r)
            return out
        }
        return linkedMapOf(
            RecordKind.CALENDAR to lift(RecordKind.CALENDAR, scoped.calendars, { it.id }) { SyncRecords.lift(it) },
            RecordKind.TASK to lift(RecordKind.TASK, scoped.tasks, { it.id }) { SyncRecords.lift(it, uids) },
            RecordKind.BLOCK to lift(RecordKind.BLOCK, scoped.blocks, { it.id }) { SyncRecords.lift(it, uids) },
            RecordKind.NOTE to lift(RecordKind.NOTE, scoped.notes, { it.id }) { SyncRecords.lift(it, uids) },
            RecordKind.SESSION to lift(RecordKind.SESSION, scoped.sessions, { it.id }) { SyncRecords.lift(it, uids) },
            RecordKind.EXCEPTION to lift(RecordKind.EXCEPTION, scoped.exceptions, { it.id }) { SyncRecords.lift(it, uids) },
            RecordKind.DEPENDENCY to lift(RecordKind.DEPENDENCY, scoped.dependencies, { it.id }) { SyncRecords.lift(it, uids) },
            RecordKind.DAYMARK to lift(RecordKind.DAYMARK, scoped.dayMarks, { it.id }) { SyncRecords.lift(it, uids) },
        )
    }

    private fun createdAtById(scoped: SyncScope.Scoped, kind: RecordKind): Map<Long, Instant> = when (kind) {
        RecordKind.CALENDAR -> scoped.calendars.associate { it.id to it.createdAt }
        RecordKind.TASK -> scoped.tasks.associate { it.id to it.createdAt }
        RecordKind.BLOCK -> scoped.blocks.associate { it.id to it.createdAt }
        RecordKind.NOTE -> scoped.notes.associate { it.id to it.createdAt }
        RecordKind.SESSION -> scoped.sessions.associate { it.id to it.createdAt }
        RecordKind.EXCEPTION -> scoped.exceptions.associate { it.id to it.createdAt }
        RecordKind.DEPENDENCY -> scoped.dependencies.associate { it.id to it.createdAt }
        RecordKind.DAYMARK -> scoped.dayMarks.associate { it.id to it.createdAt }
    }

    private fun hashes(fields: Map<String, String>): Map<String, Long> = fields.mapValues { (_, v) -> SyncRecords.hash(v) }

    private fun Instant.millis(): Long = toEpochMilliseconds().coerceAtLeast(0)
}

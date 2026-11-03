package com.skohscripts.kairos.data

import com.skohscripts.kairos.core.sync.Hlc
import com.skohscripts.kairos.core.sync.Marks
import com.skohscripts.kairos.core.sync.RecordKind
import com.skohscripts.kairos.core.sync.RecordRef
import com.skohscripts.kairos.core.sync.Stamp
import com.skohscripts.kairos.core.sync.SyncData
import app.cash.sqldelight.async.coroutines.awaitAsList
import com.skohscripts.kairos.data.db.KairosQueries
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/**
 * Stockage de l'état de synchronisation dans les tables `sync_*` (docs/spec/synchronisation.md § Stockage) : lecture
 * complète à l'ouverture, écriture **par différence** après chaque réconciliation (seuls les enregistrements dont les marques
 * ont changé sont réécrits). Toujours appelé dans une transaction du dépôt.
 */
internal class SyncStore(private val queries: KairosQueries) {
    /** L'état enregistré, ou `null` si la base ne synchronise pas (pas de clé `device`). */
    suspend fun load(): SyncData? {
        val state = queries.allSyncState().awaitAsList().associate { it.key to it.value_ }
        val device = state[DEVICE] ?: return null
        val uids = HashMap<RecordKind, HashMap<Long, String>>()
        for (row in queries.allSyncIdentities().awaitAsList()) {
            val kind = RecordKind.fromCode(row.kind) ?: continue
            uids.getOrPut(kind) { HashMap() }[row.local_id] = row.uid
        }
        val marks = HashMap<RecordRef, Marks>()
        for (row in queries.allSyncMarks().awaitAsList()) {
            val kind = RecordKind.fromCode(row.kind) ?: continue
            decodeMarks(row.born, row.base, row.stamps, row.hashes)?.let { marks[RecordRef(kind, row.uid)] = it }
        }
        val settings = state[SETTINGS]?.split(SEP_RECORD)?.takeIf { it.size == 4 }?.let { decodeMarks(it[0], it[1], it[2], it[3]) }
            ?: Stamp(Hlc.ZERO, device).let { Marks(it, it) }
        return SyncData(
            device = device,
            clock = state[CLOCK]?.let(Hlc::parse) ?: Hlc.ZERO,
            seen = state[SEEN]?.let { decodeMap(it).mapNotNull { (k, v) -> Hlc.parse(v)?.let { k to it } }.toMap() } ?: emptyMap(),
            uids = uids,
            marks = marks,
            settings = settings,
            label = state[LABEL].orEmpty(),
            labels = state[LABELS]?.let(::decodeMap) ?: emptyMap(),
        )
    }

    /** Écrit [new] en ne touchant qu'à ce qui diffère de [old] (`null` : tout). */
    suspend fun save(old: SyncData?, new: SyncData) {
        if (old == null || old.device != new.device) queries.writeSyncState(DEVICE, new.device)
        if (old?.label != new.label) queries.writeSyncState(LABEL, new.label)
        if (old?.clock != new.clock) queries.writeSyncState(CLOCK, new.clock.toString())
        if (old?.seen != new.seen) queries.writeSyncState(SEEN, encodeMap(new.seen.mapValues { it.value.toString() }))
        if (old?.labels != new.labels) queries.writeSyncState(LABELS, encodeMap(new.labels))
        if (old?.settings != new.settings) queries.writeSyncState(SETTINGS, encodeMarks(new.settings).joinToString(SEP_RECORD))
        for (kind in RecordKind.entries) {
            val before = old?.uids?.get(kind).orEmpty()
            val after = new.uids[kind].orEmpty()
            if (before == after) continue
            for ((id, _) in before) if (id !in after) queries.deleteSyncIdentity(kind.code, id)
            for ((id, uid) in after) if (before[id] != uid) queries.insertSyncIdentity(kind.code, id, uid)
        }
        val oldMarks = old?.marks.orEmpty()
        if (oldMarks !== new.marks) {
            for (ref in oldMarks.keys) if (ref !in new.marks) queries.deleteSyncMark(ref.kind.code, ref.uid)
            for ((ref, m) in new.marks) {
                if (oldMarks[ref] == m) continue
                val (born, base, stamps, hashes) = encodeMarks(m)
                queries.writeSyncMark(ref.kind.code, ref.uid, born, base, stamps, hashes)
            }
        }
    }

    /** Valeur libre de `sync_state` (`lastMerge`, `examples`). */
    suspend fun read(key: String): String? = queries.allSyncState().awaitAsList().firstOrNull { it.key == key }?.value_

    suspend fun write(key: String, value: String) = queries.writeSyncState(key, value)

    /** Retire tout l'état de synchronisation (la base redevient une base qui ne synchronise pas). */
    suspend fun clear() {
        queries.clearSyncState()
        queries.clearSyncIdentities()
        queries.clearSyncMarks()
        queries.clearSyncReview()
    }

    companion object {
        const val DEVICE = "device"
        const val LABEL = "label"
        const val CLOCK = "clock"
        const val SEEN = "seen"
        const val LABELS = "labels"
        const val SETTINGS = "settings"
        const val LAST_MERGE = "lastMerge"
        const val EXAMPLES = "examples"

        private const val SEP_FIELD = "\t"
        private const val SEP_RECORD = "\n"
        private val mapJson = Json
        private val stringMap = MapSerializer(String.serializer(), String.serializer())

        private fun encodeMap(m: Map<String, String>): String = mapJson.encodeToString(stringMap, m.sortedByKey())
        private fun decodeMap(text: String): Map<String, String> = runCatching { mapJson.decodeFromString(stringMap, text) }.getOrDefault(emptyMap())

        /** `born`, `base`, `stamps` (« champ=marque » séparés par des tabulations) et `hashes` (« champ=empreinte hexadécimale »). */
        internal fun encodeMarks(m: Marks): List<String> = listOf(
            m.born.toString(),
            m.base.toString(),
            m.stamps.entries.sortedBy { it.key }.joinToString(SEP_FIELD) { "${it.key}=${it.value}" },
            m.hashes.entries.sortedBy { it.key }.joinToString(SEP_FIELD) { "${it.key}=${it.value.toULong().toString(16)}" },
        )

        internal fun decodeMarks(born: String, base: String, stamps: String, hashes: String): Marks? {
            val b = Stamp.parse(born) ?: return null
            val s = Stamp.parse(base) ?: return null
            fun pairs(text: String) = text.split(SEP_FIELD).filter { it.isNotEmpty() }.mapNotNull { e -> e.indexOf('=').takeIf { it > 0 }?.let { e.substring(0, it) to e.substring(it + 1) } }
            return Marks(
                born = b,
                base = s,
                stamps = pairs(stamps).mapNotNull { (k, v) -> Stamp.parse(v)?.let { k to it } }.toMap(),
                hashes = pairs(hashes).mapNotNull { (k, v) -> v.toULongOrNull(16)?.let { k to it.toLong() } }.toMap(),
            )
        }
    }
}

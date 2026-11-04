package com.skohscripts.kairos.data

import com.skohscripts.kairos.core.model.KairosSnapshot
import com.skohscripts.kairos.core.sync.MergeOutcome
import com.skohscripts.kairos.core.sync.RecordKind
import com.skohscripts.kairos.core.sync.RecordRef
import com.skohscripts.kairos.core.sync.Stamp
import com.skohscripts.kairos.core.sync.SyncReconcile
import com.skohscripts.kairos.core.sync.SyncRecords
import com.skohscripts.kairos.core.sync.SyncScope
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.time.Instant

/**
 * Un élément de « À revoir » (docs/spec/synchronisation.md § Interface) : un conflit ([kept] faux) sur le champ [field] de
 * [ref] (nul : un réglage partagé), la valeur écartée [lostValue] et la valeur appliquée [keptValue] (texte canonique), avec
 * leurs marques (appareil et heure) ; ou un élément conservé après une suppression faite ailleurs ([kept] vrai).
 */
data class SyncReviewItem(
    val id: Long,
    val ref: RecordRef?,
    val field: String,
    val title: String,
    val lostValue: String,
    val lost: Stamp?,
    val keptValue: String,
    val winner: Stamp?,
    val kept: Boolean,
    val at: Instant,
)

/** La dernière fusion qui a changé quelque chose ici : quand, et combien d'éléments ajoutés, modifiés, retirés. */
@Serializable
data class LastMerge(val atMillis: Long, val added: Int, val modified: Int, val removed: Int) {
    constructor(at: Instant, added: Int, modified: Int, removed: Int) : this(at.toEpochMilliseconds(), added, modified, removed)

    val at: Instant get() = Instant.fromEpochMilliseconds(atMillis)

    companion object {
        fun encode(m: LastMerge): String = Json.encodeToString(serializer(), m)
        fun decode(text: String): LastMerge? = runCatching { Json.decodeFromString(serializer(), text) }.getOrNull()
    }
}

/** Issue de [KairosRepository.merge]. */
sealed interface MergeResult {
    /** Fusion faite (éventuellement sans rien changer ici). */
    data class Applied(val outcome: MergeOutcome) : MergeResult

    /** La sauvegarde exigée avant une fusion qui modifie ou supprime a échoué : rien n'a été fusionné. */
    data object BackupFailed : MergeResult

    /** La base ne synchronise pas encore ([KairosRepository.enableSync] d'abord). */
    data object NotSynced : MergeResult
}

/**
 * Empreinte des exemples posés sur une base neuve (`sync_state.examples`, docs/spec/synchronisation.md § Identité) : une
 * entrée par enregistrement de la portée, `genre|id|empreinte` de ses champs. Les références y sont levées avec les `uid` de
 * migration, communs à toutes les copies.
 */
internal object ExampleMarks {
    data class Entry(val kind: RecordKind, val id: Long, val hash: Long)

    fun of(snapshot: KairosSnapshot): Map<Pair<RecordKind, Long>, Long> {
        val legacy = SyncReconcile.legacy(snapshot)
        val lifted = SyncReconcile.liftAll(SyncScope.of(snapshot)) { k, id -> legacy.uidOf(k, id) }
        val out = HashMap<Pair<RecordKind, Long>, Long>()
        for ((kind, records) in lifted) for ((id, l) in records) {
            out[kind to id] = SyncRecords.hash(l.fields.entries.joinToString("\u0000") { "${it.key}=${it.value}" })
        }
        return out
    }

    fun encode(snapshot: KairosSnapshot): String =
        of(snapshot).entries.sortedWith(compareBy({ it.key.first.ordinal }, { it.key.second }))
            .joinToString("\n") { (k, h) -> "${k.first.code}|${k.second}|$h" }

    fun decode(text: String): List<Entry> = text.lines().mapNotNull { line ->
        val parts = line.split('|')
        if (parts.size != 3) return@mapNotNull null
        val kind = RecordKind.fromCode(parts[0]) ?: return@mapNotNull null
        Entry(kind, parts[1].toLongOrNull() ?: return@mapNotNull null, parts[2].toLongOrNull() ?: return@mapNotNull null)
    }
}

/** La même table, clés triées (ordre d'insertion conservé) : `toSortedMap` n'existe que sur la JVM, `data` compile aussi pour le web. */
internal fun <V> Map<String, V>.sortedByKey(): Map<String, V> = entries.sortedBy { it.key }.associate { it.key to it.value }

/** « À revoir » et la dernière fusion en texte ([KairosRepository.exportSyncExtras], version web). */
@Serializable
internal data class SyncExtrasJson(val review: List<Row>, val lastMerge: String? = null) {
    @Serializable
    data class Row(
        val kind: String?,
        val uid: String?,
        val field: String,
        val title: String,
        val loserValue: String,
        val loserStamp: String,
        val winnerValue: String,
        val winnerStamp: String,
        val kept: Boolean,
        val createdAt: String,
    )

    companion object {
        private val json = Json { ignoreUnknownKeys = true }
        fun encode(e: SyncExtrasJson): String = json.encodeToString(serializer(), e)
        fun decode(text: String): SyncExtrasJson? = runCatching { json.decodeFromString(serializer(), text) }.getOrNull()
    }
}

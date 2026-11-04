package com.skohscripts.kairos.core.sync

/**
 * Aperçu du premier rapprochement (docs/spec/synchronisation.md § Premier rapprochement) : ce que chaque côté a seul et ce qui
 * diffère, compté sur les enregistrements de la portée, **valeurs seules** (les marques n'entrent pas en compte : deux copies
 * d'une même origine qui n'ont rien changé ne « diffèrent » pas). Tâches à part ([hereOnly], [fileOnly], [different]), le reste
 * dans [others] ; [settings] : un réglage partagé diffère. Pur.
 */
data class SyncSummary(
    val hereOnly: Int,
    val fileOnly: Int,
    val different: Int,
    val others: Int,
    val settings: Boolean,
) {
    companion object {
        fun of(ours: Synced, theirs: Synced): SyncSummary {
            val o = SnapshotMerge.lift(ours)
            val t = SnapshotMerge.lift(theirs)
            var hereOnly = 0
            var fileOnly = 0
            var different = 0
            var others = 0
            for (ref in o.keys + t.keys) {
                val a = o[ref]
                val b = t[ref]
                val counted = when {
                    a != null && b == null -> { if (ref.kind == RecordKind.TASK) hereOnly++; true }
                    a == null && b != null -> { if (ref.kind == RecordKind.TASK) fileOnly++; true }
                    a != null && b != null && a.lifted.fields != b.lifted.fields -> { if (ref.kind == RecordKind.TASK) different++; true }
                    else -> false
                }
                if (counted && ref.kind != RecordKind.TASK) others++
            }
            val settings = SyncRecords.liftSettings(ours.snapshot.settings) != SyncRecords.liftSettings(theirs.snapshot.settings)
            return SyncSummary(hereOnly, fileOnly, different, others, settings)
        }
    }
}

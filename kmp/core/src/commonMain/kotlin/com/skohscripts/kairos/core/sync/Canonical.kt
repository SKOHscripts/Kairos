package com.skohscripts.kairos.core.sync

import com.skohscripts.kairos.core.model.KairosSnapshot
import kotlin.time.Instant

/**
 * Forme canonique d'un état synchronisé (docs/spec/synchronisation.md § Fusion, propriétés) : la portée seulement,
 * enregistrements triés par `uid`, références en `uid`, chaque champ avec sa valeur et sa marque **effective**, le contexte.
 * Indépendante des identifiants locaux et de la représentation des marques (`base` ou marque propre) : deux appareils qui ont
 * convergé ont la même. Sert aux tests de propriétés et à décider s'il faut réécrire le fichier (`MergeOutcome.writeNeeded`).
 */
data class Canonical(
    val records: Map<RecordRef, Record>,
    val settings: Map<String, Pair<String, Stamp>>,
    val seen: Map<String, Hlc>,
) {
    data class Record(val born: Stamp, val fields: Map<String, Pair<String, Stamp>>, val createdAt: Instant, val updatedAt: Instant?)

    companion object {
        fun of(snapshot: KairosSnapshot, data: SyncData): Canonical =
            from(SnapshotMerge.lift(Synced(snapshot, data)), SyncRecords.liftSettings(snapshot.settings), data.settings, data.seen)

        /** D'après des enregistrements déjà levés (la fusion les a sous la main). */
        fun from(records: Map<RecordRef, Rec>, settings: Map<String, String>, settingsMarks: Marks, seen: Map<String, Hlc>): Canonical {
            val out = HashMap<RecordRef, Record>(records.size * 2)
            for ((ref, r) in records) {
                out[ref] = Record(r.marks.born, r.lifted.fields.mapValues { (name, v) -> v to r.marks.of(name) }, r.lifted.createdAt, r.lifted.updatedAt)
            }
            return Canonical(out, settings.mapValues { (name, v) -> v to settingsMarks.of(name) }, seen)
        }
    }
}

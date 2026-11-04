package com.skohscripts.kairos.core.sync

/**
 * Marques d'un enregistrement (docs/spec/synchronisation.md § Horloge hybride et marques) : [born], la marque de naissance
 * (immuable) ; [base], la marque des champs qui n'ont pas la leur ; [stamps], la marque propre de chaque champ modifié
 * depuis ; [hashes], l'empreinte de la valeur de **chaque** champ telle que marquée, qui permet de déduire une modification
 * sans relire l'ancienne valeur (`SyncReconcile`).
 */
data class Marks(
    val born: Stamp,
    val base: Stamp,
    val stamps: Map<String, Stamp> = emptyMap(),
    val hashes: Map<String, Long> = emptyMap(),
) {
    /** La marque d'un champ : la sienne, sinon [base]. */
    fun of(field: String): Stamp = stamps[field] ?: base

    /** Toutes les marques portées : naissance, base et champs. */
    fun all(): Sequence<Stamp> = sequenceOf(born, base) + stamps.values.asSequence()
}

/**
 * État de synchronisation d'une installation (docs/spec/synchronisation.md § Stockage) : son identité ([device], 12
 * caractères hexadécimaux, et [label], le nom lisible de l'appareil), son horloge, son contexte ([seen]), le `uid` de chaque
 * enregistrement de la portée ([uids], par genre : identifiant local → `uid`), leurs marques, et celles des réglages
 * partagés ([settings]). Pur : `data` le stocke dans les tables `sync_*`.
 */
data class SyncData(
    val device: String,
    val clock: Hlc,
    val seen: Map<String, Hlc>,
    val uids: Map<RecordKind, Map<Long, String>>,
    val marks: Map<RecordRef, Marks>,
    val settings: Marks,
    val label: String = "",
    val labels: Map<String, String> = emptyMap(),
) {
    /** Le `uid` d'un enregistrement local, s'il en a un. */
    fun uidOf(kind: RecordKind, id: Long): String? = uids[kind]?.get(id)

    /** Index inverse : `uid` → identifiant local, par genre. */
    fun idsByUid(): Map<RecordKind, Map<String, Long>> = uids.mapValues { (_, m) -> m.entries.associate { (id, uid) -> uid to id } }
}

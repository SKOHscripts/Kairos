package com.skohscripts.kairos.ui.app

import com.skohscripts.kairos.core.sync.RecordKind
import com.skohscripts.kairos.core.KairosBuild
import com.skohscripts.kairos.core.model.KairosSnapshot
import com.skohscripts.kairos.data.ExportCodec
import com.skohscripts.kairos.ui.generated.resources.Res
import com.skohscripts.kairos.ui.generated.resources.file_error
import kotlinx.datetime.TimeZone
import kotlinx.datetime.number
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.getString

/** Horodatage des noms de fichiers : « 20260929-0915 » (heure locale). */
fun fileStamp(services: AppServices): String {
    val t = services.clock.now().toLocalDateTime(TimeZone.currentSystemDefault())
    fun two(n: Int) = n.toString().padStart(2, '0')
    return "${t.year}${two(t.month.number)}${two(t.day)}-${two(t.hour)}${two(t.minute)}"
}

/**
 * Un nom propre pour un nom de fichier : lettres et chiffres en minuscules, le reste devient un tiret
 * (« Léa Martin » -> « léa-martin »), « membre » si rien ne reste.
 */
fun fileSlug(name: String): String =
    name.lowercase().map { if (it.isLetterOrDigit()) it else '-' }.joinToString("").split('-').filter { it.isNotEmpty() }.joinToString("-").ifEmpty { "membre" }

/**
 * Sauvegarde automatique de toutes les données actuelles, nommée
 * `<prefixe>-<fileStamp>.json` (`avant-import-…`, `avant-suppression-equipe-…`).
 * Sans sauvegarde réussie, le message d'erreur est affiché et le résultat est
 * `false` : l'appelant ne change alors rien.
 */
suspend fun saveBackup(services: AppServices, prefix: String, messages: (String) -> Unit): Boolean {
    val current = ExportCodec.encode(services.repository.snapshot.value, KairosBuild.VERSION_NAME, services.clock.now())
    val backed = runCatching { services.backups.save("$prefix-${fileStamp(services)}.json", current) }
    if (backed.isFailure) {
        services.diagnostics?.log("backup failed")
        messages(getString(Res.string.file_error))
        return false
    }
    services.diagnostics?.log("backup ok")
    return true
}

/**
 * Remplace toutes les données par [snapshot], après une sauvegarde des
 * données actuelles (`avant-import-…json`). Sans sauvegarde réussie, rien
 * n'est remplacé et le message d'erreur est affiché : `false`. [uids] : les
 * identités de synchronisation du fichier (`ExportCodec.decodeSynced`) ; une
 * base synchronisée les reprend, sans quoi un enregistrement créé ailleurs
 * changerait d'identité et reviendrait en double à la fusion suivante.
 */
suspend fun replaceWithBackup(
    services: AppServices,
    snapshot: KairosSnapshot,
    messages: (String) -> Unit,
    uids: Map<RecordKind, Map<Long, String>>? = null,
): Boolean {
    if (!saveBackup(services, "avant-import", messages)) return false
    // Filet : une écriture refusée par la base (contrainte, disque) annule la transaction ; l'application le dit au
    // lieu de quitter sur l'erreur SQLite brute (constaté avant que l'import ne valide les identifiants).
    if (runCatching { services.repository.replaceAll(snapshot, uids) }.isFailure) {
        services.diagnostics?.log("import full failed")
        messages(getString(Res.string.file_error))
        return false
    }
    services.diagnostics?.log("import full ok tasks=${snapshot.tasks.size}")
    services.teamUi.reset()
    return true
}

/**
 * Supprime les données d'équipe (`KairosRepository.clearTeamData`) après une
 * sauvegarde des données actuelles (`avant-suppression-equipe-…json`), même
 * mécanisme que l'import (docs/spec/equipe.md § Activation). Sans sauvegarde
 * réussie, rien n'est supprimé et le message d'erreur est affiché : `false`.
 */
suspend fun clearTeamDataWithBackup(services: AppServices, messages: (String) -> Unit): Boolean {
    if (!saveBackup(services, "avant-suppression-equipe", messages)) return false
    services.repository.clearTeamData()
    services.teamUi.reset()
    return true
}

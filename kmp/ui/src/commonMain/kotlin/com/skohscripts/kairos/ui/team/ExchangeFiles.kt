package com.skohscripts.kairos.ui.team

import com.skohscripts.kairos.core.team.TeamMember
import com.skohscripts.kairos.core.team.exchange.TeamOrigin
import com.skohscripts.kairos.ui.app.AppServices
import com.skohscripts.kairos.ui.app.fileSlug
import com.skohscripts.kairos.ui.app.fileStamp
import com.skohscripts.kairos.ui.generated.resources.Res
import com.skohscripts.kairos.ui.generated.resources.exchange_report_done
import com.skohscripts.kairos.ui.generated.resources.exchange_report_none
import com.skohscripts.kairos.ui.generated.resources.exchange_send_done
import com.skohscripts.kairos.ui.generated.resources.exchange_send_done_empty
import com.skohscripts.kairos.ui.generated.resources.exchange_send_none
import com.skohscripts.kairos.ui.generated.resources.exchange_send_unavailable
import com.skohscripts.kairos.ui.generated.resources.file_error
import org.jetbrains.compose.resources.getString

// Enregistrement des fichiers d'échange par le sélecteur de fichier habituel (`FileService.saveText`, comme l'export).
// Le transport (courriel, messagerie, clé USB) est l'affaire de l'utilisateur (docs/spec/equipe-echanges.md § Hors périmètre).

/**
 * Enregistre [text] sous [name] ; [done] est annoncé si le fichier est écrit, le message d'erreur de fichier sinon. Annuler ne
 * dit rien. Rend `true` seulement si le fichier a réellement été enregistré.
 */
private suspend fun saveExchangeFile(services: AppServices, name: String, text: String, done: String, messages: (String) -> Unit): Boolean {
    val saved = runCatching { services.files.saveText(name, text) }
    if (saved.isFailure) {
        messages(getString(Res.string.file_error))
        return false
    }
    if (saved.getOrThrow()) messages(done)
    return saved.getOrThrow()
}

/**
 * Côté manager : « Envoyer ses tâches… » de la fiche de [member]. Enregistre `kairos-paquet-<membre>-AAAAMMJJ-HHMM.json`
 * (`KairosRepository.preparePack`) ; l'envoi n'est journalisé sur les tâches (`recordPackSent`) que si le fichier est
 * réellement enregistré (annuler la boîte n'écrit rien). Un membre sans tâche à faire n'a rien à recevoir : le message le dit,
 * aucun fichier n'est proposé, sauf si un paquet lui a déjà été remis : le paquet vide est alors enregistré, il marque « retirées »
 * chez lui les tâches reçues. Un paquet impossible à préparer (`null`) est signalé aussi.
 */
internal suspend fun sendPack(services: AppServices, member: TeamMember, messages: (String) -> Unit) {
    val prepared = services.repository.preparePack(member.id)
    if (prepared == null) {
        messages(getString(Res.string.exchange_send_unavailable))
        return
    }
    val empty = prepared.taskUids.isEmpty()
    if (empty && !prepared.previouslySent) {
        messages(getString(Res.string.exchange_send_none, member.name))
        return
    }
    val done = getString(if (empty) Res.string.exchange_send_done_empty else Res.string.exchange_send_done, member.name)
    val name = "kairos-paquet-${fileSlug(member.name)}-${fileStamp(services)}.json"
    if (saveExchangeFile(services, name, prepared.text, done, messages)) services.repository.recordPackSent(prepared)
}

/**
 * Côté membre : « Renvoyer l'avancement… » pour [origin]. Enregistre `kairos-rapport-<membre>-AAAAMMJJ-HHMM.json`
 * (`KairosRepository.buildReport`) ; `null` (plus aucune tâche reçue de cette équipe) est signalé.
 */
internal suspend fun sendReport(services: AppServices, origin: TeamOrigin, messages: (String) -> Unit) {
    val text = services.repository.buildReport(origin.key)
    if (text == null) {
        messages(getString(Res.string.exchange_report_none))
        return
    }
    saveExchangeFile(services, "kairos-rapport-${fileSlug(origin.memberName)}-${fileStamp(services)}.json", text, getString(Res.string.exchange_report_done), messages)
}

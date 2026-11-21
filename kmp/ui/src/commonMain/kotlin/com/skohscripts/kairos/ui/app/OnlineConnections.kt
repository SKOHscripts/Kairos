package com.skohscripts.kairos.ui.app

import com.skohscripts.kairos.core.calendar.CalendarPlan
import com.skohscripts.kairos.core.connections.CalendarConnection
import com.skohscripts.kairos.core.connections.ConnectionError
import com.skohscripts.kairos.core.connections.SyncInterval
import kotlinx.coroutines.flow.StateFlow

/** Ce que l'utilisateur doit confirmer avant qu'un lien ICS soit lu. */
enum class IcsConfirmation { INSECURE_HTTP, REDIRECT_HOST }

/** Résultat de l'aperçu d'un lien ICS : rien n'est écrit tant que [OnlineConnections.addIcs] n'est pas appelé. */
sealed interface IcsPreviewResult {
    /**
     * Lien lu et plan calculé. [existingWithoutConnection] : un calendrier de même nom existe sans connexion, « Ajouter » s'y
     * rattache (D5). [insecure] / [redirectHost] : confirmations déjà données, à conserver dans la connexion.
     */
    data class Ready(
        val name: String,
        val plan: CalendarPlan,
        val host: String,
        val existingWithoutConnection: Boolean,
        val insecure: Boolean,
        val redirectHost: String?,
        /** Adresse finale à garder dans le magasin de secrets (schéma https, `webcal` converti). */
        val url: String,
        val etag: String?,
    ) : IcsPreviewResult

    /** Demande de confirmation : l'interface la pose, puis rappelle `previewIcs` avec la confirmation. */
    data class NeedsConfirmation(val kind: IcsConfirmation, val host: String) : IcsPreviewResult

    data class Failed(val error: ConnectionError, val host: String?) : IcsPreviewResult
}

/** Aperçu d'un calendrier CalDAV coché (docs/spec/connexions-en-ligne.md § CalDAV) : son plan, ou l'erreur de sa lecture. */
data class CalDavPreview(val calendar: CalDavCalendar, val plan: CalendarPlan?, val error: ConnectionError?)

/**
 * Connexions de calendrier en ligne, côté interface (docs/spec/connexions-en-ligne.md § Interface, § Synchronisation).
 * `AppServices.online` : nul sur le web (pas de transport ni de magasin de secrets pour l'instant).
 * Toutes les requêtes passent par `OnlineGate` ; aucun secret n'est exposé ici.
 */
interface OnlineConnections {
    /** Les lignes `calendar_connection` (une par calendrier connecté), triées par identifiant de source. */
    val connections: StateFlow<List<CalendarConnection>>

    /** Niveau de protection du magasin de secrets de la plateforme (pour le texte de la carte). */
    val secretLevel: SecretLevel

    /** Lit le lien et calcule le plan, sans rien écrire. [acceptedRedirectHost] : hôte de redirection déjà accepté. */
    suspend fun previewIcs(link: String, confirmedInsecure: Boolean, acceptedRedirectHost: String?): IcsPreviewResult

    /**
     * Ajoute (ou rattache, D5) le calendrier : sauvegarde automatique, secret `url:<id>`, plan appliqué, ligne de connexion.
     * Rend l'identifiant de la source, nul si le nom est vide.
     */
    suspend fun addIcs(preview: IcsPreviewResult.Ready, name: String, hideTitles: Boolean): Long?

    /** « Synchroniser maintenant » : force une synchronisation, hors file des échéances. */
    suspend fun syncNow(sourceId: Long)

    suspend fun setSyncEnabled(sourceId: Long, enabled: Boolean)

    suspend fun setInterval(sourceId: Long, interval: SyncInterval)

    /** Remplace le lien d'un calendrier connecté (erreur `AUTH` ou `GONE`) ; mêmes confirmations qu'à l'ajout. */
    suspend fun replaceIcsLink(sourceId: Long, link: String, confirmedInsecure: Boolean, acceptedRedirectHost: String?): IcsPreviewResult

    /** Serveur CalDAV : découverte des calendriers, sans rien écrire (docs/spec/connexions-en-ligne.md § CalDAV). */
    suspend fun discoverCalDav(account: CalDavAccount): CalDavClient.Discovery

    /** Lit chaque calendrier coché et calcule son plan, sans rien écrire. */
    suspend fun previewCalDav(account: CalDavAccount, host: String, calendars: List<CalDavCalendar>): List<CalDavPreview>

    /**
     * Ajoute les calendriers lus (plan non nul) : une sauvegarde automatique, un compte `account:<compte>` partagé, puis pour chacun
     * une source, son adresse `remote:<id>`, son plan et sa ligne `CALDAV`. Rend les identifiants des sources.
     */
    suspend fun addCalDav(account: CalDavAccount, host: String, previews: List<CalDavPreview>, hideTitles: Boolean): List<Long>

    /** « Modifier le mot de passe » (erreur `AUTH`) : réécrit le compte de [sourceId], puis synchronise les calendriers de ce compte. */
    suspend fun changeCalDavPassword(sourceId: Long, password: String)

    /** « Déconnecter » : efface les secrets puis la ligne, garde les événements. */
    suspend fun disconnect(sourceId: Long)

    /** « Retirer » : efface les secrets, la ligne, les créneaux et les journées. */
    suspend fun remove(sourceId: Long)
}

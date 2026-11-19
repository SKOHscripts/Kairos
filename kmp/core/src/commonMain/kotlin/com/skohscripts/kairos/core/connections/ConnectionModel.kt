package com.skohscripts.kairos.core.connections

import kotlin.time.Instant

/** Type d'une connexion (docs/spec/connexions-en-ligne.md § Modèle). Sans ligne `calendar_connection`, un calendrier est [FILE]. */
enum class ConnectionKind { FILE, URL, CALDAV, OAUTH }

enum class OAuthProvider { MICROSOFT, GOOGLE }

/** Fréquence de synchronisation : [minutes] est l'écart minimal entre deux essais réguliers, nul pour [MANUAL] et [ON_OPEN]. */
enum class SyncInterval(val minutes: Int?) {
    MANUAL(null),
    ON_OPEN(null),
    HOURLY(60),
    SIX_HOURS(360),
    DAILY(1_440),
}

/** Pourquoi une synchronisation a échoué ; chaque valeur a son texte d'interface (§ Hors ligne et erreurs). */
enum class ConnectionError {
    OFFLINE,
    TLS,
    AUTH,
    REAUTH,
    GONE,
    NOT_CALENDAR,
    TRUNCATED,
    TOO_BIG,
    UNAVAILABLE,
    REDIRECT_HOST,
    INSECURE_REDIRECT,
    NO_SECRET,
    UNSUPPORTED,
}

/**
 * Une ligne de `calendar_connection` : l'état d'une connexion, local à l'appareil, hors export et hors synchronisation du
 * fichier lié (§ Connexions et synchronisation du fichier lié, D1). Jamais d'adresse, d'identifiant ni de jeton ici :
 * ils vivent dans le magasin de secrets.
 */
data class CalendarConnection(
    /** `calendar_source.id` du calendrier connecté. */
    val sourceId: Long,
    val kind: ConnectionKind,
    val provider: OAuthProvider? = null,
    /** Hôte affiché, sans chemin ni requête. */
    val host: String? = null,
    /** Clé opaque du compte dans le magasin de secrets (CALDAV, OAUTH). */
    val account: String? = null,
    val syncEnabled: Boolean = true,
    val interval: SyncInterval = SyncInterval.ON_OPEN,
    val lastAttemptAt: Instant? = null,
    val lastSyncAt: Instant? = null,
    val lastError: ConnectionError? = null,
    val failures: Int = 0,
    val nextAttemptAt: Instant? = null,
    val etag: String? = null,
    /** Jour (ISO `AAAA-MM-JJ`) du plan dont vient [etag] : le plan dépend du jour, un ETag d'hier n'est pas repris. */
    val plannedOn: String? = null,
    val insecureConfirmed: Boolean = false,
    /** Hôte de redirection que l'utilisateur a accepté pour un lien ICS. */
    val redirectHost: String? = null,
) {
    init {
        require(kind != ConnectionKind.FILE) { "FILE n'a pas de ligne calendar_connection" }
    }
}

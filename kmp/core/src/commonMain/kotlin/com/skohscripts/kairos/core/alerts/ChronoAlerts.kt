package com.skohscripts.kairos.core.alerts

import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/** Les trois alertes du chrono (docs/spec/temps-reel-chrono.md § Alertes). */
enum class ChronoAlertKind { OVER, IDLE, POMODORO }

/**
 * Une alerte et l'instant où son seuil est franchi. [minutes] est la durée à
 * citer : le total estimé atteint (dépassement), la session en cours (oubli),
 * ou la durée de focus (pause).
 */
data class ChronoAlert(val kind: ChronoAlertKind, val at: Instant, val minutes: Int)

/**
 * Quand notifier (pur). Les seuils sont vérifiés chaque seconde dans la
 * page ; ici, les instants de franchissement se calculent d'avance, ce qui
 * sert aussi aux alarmes Android, programmées même application fermée.
 */
object ChronoAlerts {
    /**
     * Seuils d'une session démarrée à [startedAt], [baseMinutes] étant le temps
     * déjà passé sur la tâche avant elle (manuel compris). Un seuil à 0 ou
     * sans estimation est désactivé. Dépassement : total ≥ estimé ; oubli et
     * pause : durée de **la session** ≥ seuil.
     */
    fun thresholds(
        startedAt: Instant,
        baseMinutes: Int,
        estimateMinutes: Int?,
        idleMinutes: Int,
        pomodoroMinutes: Int,
    ): List<ChronoAlert> = buildList {
        if (estimateMinutes != null && estimateMinutes > 0) {
            add(ChronoAlert(ChronoAlertKind.OVER, startedAt + maxOf(0, estimateMinutes - baseMinutes).minutes, estimateMinutes))
        }
        if (idleMinutes > 0) add(ChronoAlert(ChronoAlertKind.IDLE, startedAt + idleMinutes.minutes, idleMinutes))
        if (pomodoroMinutes > 0) add(ChronoAlert(ChronoAlertKind.POMODORO, startedAt + pomodoroMinutes.minutes, pomodoroMinutes))
    }

    /**
     * Seuils franchis dans `]since, now]` : seuls ceux qui passent **pendant**
     * qu'on observe notifient ; un seuil déjà dépassé quand l'observation
     * commence ne notifie jamais (anti-répétition).
     */
    fun crossed(alerts: List<ChronoAlert>, since: Instant, now: Instant): List<ChronoAlert> =
        alerts.filter { it.at > since && it.at <= now }

    /** Seuils encore à venir après [now] (alarmes à programmer). */
    fun upcoming(alerts: List<ChronoAlert>, now: Instant): List<ChronoAlert> = alerts.filter { it.at > now }

    /**
     * Garde-fou (docs/spec/temps-reel-chrono.md § Garde-fou, issue #220) : l'instant où la session démarrée à [startedAt]
     * est close d'office, `début + limite` ; nul si [limitMinutes] vaut 0 ou moins (jamais).
     */
    fun autoStopAt(startedAt: Instant, limitMinutes: Int): Instant? = if (limitMinutes > 0) startedAt + limitMinutes.minutes else null

    /** Les seuils situés avant l'arrêt [stopAt] : une session close ne notifie plus (ni bandeau ni alarme). */
    fun beforeStop(alerts: List<ChronoAlert>, stopAt: Instant?): List<ChronoAlert> =
        if (stopAt == null) alerts else alerts.filter { it.at < stopAt }
}

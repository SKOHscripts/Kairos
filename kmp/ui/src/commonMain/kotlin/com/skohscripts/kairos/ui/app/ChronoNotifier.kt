package com.skohscripts.kairos.ui.app

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Ce que les notifications système peuvent faire ici (docs/spec/temps-reel-chrono.md § Notifications). */
enum class NotifyState {
    /** Les notifications sortent. */
    ACTIVE,

    /** Une autorisation est à demander (bouton « Activer les alertes chrono »). */
    CAN_REQUEST,

    /** Refusées (navigateur, système) : les alertes restent dans l'application. */
    DENIED,

    /** Aucune voie de notification système : les alertes restent dans l'application. */
    UNAVAILABLE,
}

/**
 * Ce qui peut retarder les alertes ici (docs/spec/temps-reel-chrono.md § Android, issue #219) : affiché sous « Alertes
 * chrono actives », avec un bouton vers le réglage du système ([ChronoNotifier.openHint]).
 */
enum class NotifyHint {
    /** Les alarmes exactes ne sont pas accordées (Android 12 et plus) : application fermée, les alertes peuvent tarder. */
    EXACT_ALARMS,

    /** Kairos est soumis à l'optimisation de la batterie. */
    BATTERY,
}

/**
 * Notifications et signaux du chrono fournis par la plateforme. Le bandeau
 * d'alerte dans l'application joue toujours ; ceci ajoute la voie système et
 * les renforts (titre de fenêtre, son) quand elle manque.
 */
interface ChronoNotifier {
    val state: StateFlow<NotifyState>

    /**
     * Vrai quand le système notifie lui-même les seuils (alarmes Android,
     * application fermée comprise) : l'application n'ajoute alors que son
     * bandeau, sans doubler la notification.
     */
    val systemHandlesAlerts: Boolean get() = false

    /** Demande l'autorisation de notifier (à l'opt-in, jamais au démarrage). */
    suspend fun requestPermission() {}

    /** Notification système immédiate ; `false` si elle n'a pas pu sortir. */
    suspend fun notify(title: String, body: String, tag: String): Boolean = false

    /**
     * Voie des seuils du chrono et du garde-fou ([tag] : la nature de l'alerte, ou `AUTO_STOP`), appelée par la veille au
     * moment du seuil (issue #219). Vrai si l'alerte est sortie ou confiée au système ; par défaut, les alarmes du système
     * si elles s'en chargent, sinon [notify]. Android la redéfinit : notification tout de suite, alarme du seuil annulée.
     */
    suspend fun notifyChrono(title: String, body: String, tag: String): Boolean = systemHandlesAlerts || notify(title, body, tag)

    /** Freins connus des alertes (vide par défaut). */
    val hints: StateFlow<Set<NotifyHint>> get() = NO_HINTS

    /** Ouvre le réglage du système qui lève [hint]. */
    fun openHint(hint: NotifyHint) {}

    /** Titre de la fenêtre ou de l'onglet : [prefix] avant le nom de l'application, `null` pour le titre normal. */
    fun setTitle(prefix: String?) {}

    /** Court signal sonore (dernier recours, seulement si activé dans les réglages). */
    fun beep() {}
}

private val NO_HINTS: StateFlow<Set<NotifyHint>> = MutableStateFlow(emptySet())

/** Aucune voie système (auto-test, tests) : tout reste dans l'application. */
object NoNotifier : ChronoNotifier {
    override val state: StateFlow<NotifyState> = MutableStateFlow(NotifyState.UNAVAILABLE)
}

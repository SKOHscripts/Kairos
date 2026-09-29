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

    /** Titre de la fenêtre ou de l'onglet : [prefix] avant le nom de l'application, `null` pour le titre normal. */
    fun setTitle(prefix: String?) {}

    /** Court signal sonore (dernier recours, seulement si activé dans les réglages). */
    fun beep() {}
}

/** Aucune voie système (auto-test, tests) : tout reste dans l'application. */
object NoNotifier : ChronoNotifier {
    override val state: StateFlow<NotifyState> = MutableStateFlow(NotifyState.UNAVAILABLE)
}

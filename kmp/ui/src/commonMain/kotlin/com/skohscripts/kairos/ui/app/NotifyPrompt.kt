package com.skohscripts.kairos.ui.app

/**
 * La demande d'autorisation de notifier au premier chrono (docs/spec/temps-reel-chrono.md § Demande au démarrage du
 * chrono, issue du 2026-11-07) : au plus **une fois par lancement de l'application**, quelle que soit la réponse. Rien n'est
 * stocké : au prochain lancement, tant que le système n'a ni accordé ni refusé, la demande revient ; le bouton facultatif de
 * « Maintenant » reste là entre-temps.
 */
class NotifyPrompt {
    private var claimed = false

    /** Vrai la première fois que [state] est « à demander » ; faux ensuite, et pour tout autre état. */
    fun claim(state: NotifyState): Boolean {
        if (state != NotifyState.CAN_REQUEST || claimed) return false
        claimed = true
        return true
    }
}

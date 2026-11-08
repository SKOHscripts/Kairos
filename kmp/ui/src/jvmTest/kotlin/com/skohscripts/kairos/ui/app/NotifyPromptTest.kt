package com.skohscripts.kairos.ui.app

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** La demande d'autorisation au premier chrono (docs/spec/temps-reel-chrono.md § Demande au démarrage du chrono). */
class NotifyPromptTest {
    @Test
    fun asksOnceWhenThePermissionIsToBeRequested() {
        val prompt = NotifyPrompt()
        assertTrue(prompt.claim(NotifyState.CAN_REQUEST))
        // Une fois par lancement, quelle que soit la réponse : ni la deuxième fois, ni après un refus.
        assertFalse(prompt.claim(NotifyState.CAN_REQUEST))
        assertFalse(prompt.claim(NotifyState.DENIED))
    }

    @Test
    fun otherStatesNeverAskAndKeepTheChance() {
        val prompt = NotifyPrompt()
        for (state in listOf(NotifyState.ACTIVE, NotifyState.DENIED, NotifyState.UNAVAILABLE)) assertFalse(prompt.claim(state))
        // Un état qui n'a rien demandé ne consomme pas la demande : elle part quand l'autorisation redevient à demander.
        assertTrue(prompt.claim(NotifyState.CAN_REQUEST))
    }

    @Test
    fun eachLaunchStartsFresh() {
        assertTrue(NotifyPrompt().claim(NotifyState.CAN_REQUEST))
        assertTrue(NotifyPrompt().claim(NotifyState.CAN_REQUEST))
    }
}

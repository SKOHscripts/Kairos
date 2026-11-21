package com.skohscripts.kairos.ui.app

import com.skohscripts.kairos.core.connections.ConnectionError
import com.skohscripts.kairos.ui.generated.resources.Res
import com.skohscripts.kairos.ui.generated.resources.online_network_blocked
import com.skohscripts.kairos.ui.generated.resources.online_network_hint
import org.jetbrains.compose.resources.StringResource

/**
 * Accès de Kairos au réseau selon le système (docs/spec/connexions-en-ligne.md § Interface) : sur Android, le téléphone peut couper
 * le réseau de Kairos seul (autorisation « Réseau » de GrapheneOS, interrupteur de données d'une surcouche), sans dialogue que l'app
 * puisse demander. `AppServices.networkAccess`, nul hors Android.
 */
interface NetworkAccess {
    /** Le système signale que le réseau est bloqué pour Kairos. Faux quand il ne le dit pas (pare-feu, VPN). */
    fun blocked(): Boolean

    /** Ouvre la page de Kairos dans les réglages du téléphone. */
    fun openSettings()
}

/**
 * L'aide à montrer sous un ajout qui a échoué en [error] : nulle hors `OFFLINE` ou sans [access] (bureau, web) ; sinon le blocage
 * signalé par le système, ou l'indice « peut-être bloqué » quand il ne dit rien.
 */
internal fun offlineHelp(error: ConnectionError, access: NetworkAccess?): StringResource? = when {
    error != ConnectionError.OFFLINE || access == null -> null
    access.blocked() -> Res.string.online_network_blocked
    else -> Res.string.online_network_hint
}

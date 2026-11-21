package com.skohscripts.kairos.ui.app

import com.skohscripts.kairos.core.connections.ConnectionError
import com.skohscripts.kairos.ui.generated.resources.Res
import com.skohscripts.kairos.ui.generated.resources.online_network_blocked
import com.skohscripts.kairos.ui.generated.resources.online_network_hint
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Aide quand le réseau semble bloqué pour Kairos (docs/spec/connexions-en-ligne.md § Réseau bloqué pour Kairos). */
class NetworkAccessTest {
    private fun access(blocked: Boolean) = object : NetworkAccess {
        override fun blocked() = blocked
        override fun openSettings() = Unit
    }

    @Test
    fun onlyAnOfflineFailureOnAndroidGetsHelp() {
        assertEquals(Res.string.online_network_blocked, offlineHelp(ConnectionError.OFFLINE, access(blocked = true)))
        assertEquals(Res.string.online_network_hint, offlineHelp(ConnectionError.OFFLINE, access(blocked = false)))
        assertNull(offlineHelp(ConnectionError.AUTH, access(blocked = true)))
        assertNull(offlineHelp(ConnectionError.OFFLINE, null))
    }
}

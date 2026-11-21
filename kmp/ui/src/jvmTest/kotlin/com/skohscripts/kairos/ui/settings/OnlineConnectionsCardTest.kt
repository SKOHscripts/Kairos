package com.skohscripts.kairos.ui.settings

import com.skohscripts.kairos.core.connections.ConnectionError
import com.skohscripts.kairos.ui.Platform
import com.skohscripts.kairos.ui.guide.FeatureCatalog
import com.skohscripts.kairos.ui.guide.GuideChapter
import com.skohscripts.kairos.ui.guide.GuideSection
import com.skohscripts.kairos.ui.guide.GuideTarget
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OnlineConnectionsCardTest {
    @Test
    fun everyConnectionErrorHasItsOwnLocalizedMessage() {
        val keys = ConnectionError.entries.map { connectionErrorText(it).key }
        assertEquals(ConnectionError.entries.size, keys.toSet().size)
        assertTrue(keys.all { it.startsWith("online_error_") })
    }

    @Test
    fun theBrowserSaysWhyItCouldNotReadALink() {
        assertEquals("online_error_browser", connectionErrorText(ConnectionError.OFFLINE, browser = true).key)
        assertEquals("online_error_offline", connectionErrorText(ConnectionError.OFFLINE).key)
        // Les autres erreurs ont le même texte partout.
        ConnectionError.entries.filter { it != ConnectionError.OFFLINE }.forEach {
            assertEquals(connectionErrorText(it), connectionErrorText(it, browser = true))
        }
    }

    @Test
    fun theOnlineGuideOpensDataOnlyOnSupportedPlatforms() {
        val card = FeatureCatalog.cards.single { it.id == "online_connections" }
        assertEquals(GuideChapter.ORGANIZE, card.chapter)
        assertEquals("4.2.0", card.since)
        // Le web lit un lien ICS depuis la 4.4.0 (#260, docs/spec/connexions-en-ligne.md § Web).
        assertEquals(setOf(Platform.ANDROID, Platform.DESKTOP, Platform.WEB), card.platforms)
        assertEquals(GuideTarget.Settings(GuideSection.SETTINGS_DATA), card.target)
        assertEquals("online_connections", FeatureCatalog.README_FEATURES["Online connections"])
    }
}

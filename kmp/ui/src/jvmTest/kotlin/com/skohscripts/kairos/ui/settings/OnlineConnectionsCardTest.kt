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
    fun theOnlineGuideOpensDataOnlyOnSupportedPlatforms() {
        val card = FeatureCatalog.cards.single { it.id == "online_connections" }
        assertEquals(GuideChapter.ORGANIZE, card.chapter)
        assertEquals("4.1.0", card.since)
        assertEquals(setOf(Platform.ANDROID, Platform.DESKTOP), card.platforms)
        assertEquals(GuideTarget.Settings(GuideSection.SETTINGS_DATA), card.target)
        assertEquals("online_connections", FeatureCatalog.README_FEATURES["Online connections"])
    }
}

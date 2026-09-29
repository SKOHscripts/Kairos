package com.skohscripts.kairos.ui.navigation

import androidx.compose.ui.unit.dp
import com.skohscripts.kairos.ui.Platform
import kotlin.test.Test
import kotlin.test.assertEquals

class NavigationLayoutTest {
    @Test
    fun wideWindowsGetTheRailOnEveryPlatform() {
        for (platform in Platform.entries) {
            assertEquals(NavigationLayout.RAIL, NavigationLayout.choose(platform, 600.dp))
            assertEquals(NavigationLayout.RAIL, NavigationLayout.choose(platform, 1200.dp))
        }
    }

    @Test
    fun onlyAndroidEverGetsABottomBar() {
        assertEquals(NavigationLayout.BOTTOM_BAR, NavigationLayout.choose(Platform.ANDROID, 411.dp))
        // Un navigateur ou une fenêtre de bureau rétrécis : barre en haut, jamais en bas.
        assertEquals(NavigationLayout.TOP_BAR, NavigationLayout.choose(Platform.DESKTOP, 411.dp))
        assertEquals(NavigationLayout.TOP_BAR, NavigationLayout.choose(Platform.WEB, 599.dp))
    }

    @Test
    fun atMostFiveDestinationsStartingOnDay() {
        // Limite MD3 d'une barre de navigation ; ordre du flux GTD.
        assertEquals(
            listOf(Destination.NOTES, Destination.DAY, Destination.WEEK, Destination.STATS, Destination.SETTINGS),
            Destination.entries,
        )
        assertEquals(Destination.DAY, Destination.START)
    }
}

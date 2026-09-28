package com.skohscripts.kairos.ui

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.skohscripts.kairos.ui.navigation.AppShell
import com.skohscripts.kairos.ui.navigation.Destination
import com.skohscripts.kairos.ui.navigation.NavigationLayout
import com.skohscripts.kairos.ui.theme.KairosTheme

/**
 * Racine de l'interface, appelée par le point d'entrée de chaque plateforme.
 *
 * Navigation par état (destination courante + écran « À propos » ouvert ou
 * non) plutôt qu'une bibliothèque de navigation : cinq destinations de premier
 * niveau et un seul écran secondaire n'en justifient pas une
 * (docs/spec-v3/navigation-theme.md § Décisions).
 */
@Composable
fun KairosApp(platform: Platform) {
    KairosTheme {
        var destination by rememberSaveable { mutableStateOf(Destination.START) }
        var aboutOpen by rememberSaveable { mutableStateOf(false) }
        BoxWithConstraints {
            AppShell(
                layout = NavigationLayout.choose(platform, maxWidth),
                destination = destination,
                aboutOpen = aboutOpen,
                onNavigate = {
                    destination = it
                    aboutOpen = false
                },
                onOpenAbout = { aboutOpen = true },
                onCloseAbout = { aboutOpen = false },
            )
        }
    }
}

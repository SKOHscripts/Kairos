package com.skohscripts.kairos.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.skohscripts.kairos.ui.app.AppServices
import com.skohscripts.kairos.ui.generated.resources.Res
import com.skohscripts.kairos.ui.generated.resources.error_open_title
import com.skohscripts.kairos.ui.generated.resources.loading
import com.skohscripts.kairos.ui.navigation.AppShell
import com.skohscripts.kairos.ui.navigation.Destination
import com.skohscripts.kairos.ui.navigation.NavigationLayout
import com.skohscripts.kairos.ui.theme.KairosTheme
import org.jetbrains.compose.resources.stringResource

/**
 * Racine de l'interface, appelée par le point d'entrée de chaque plateforme.
 * [openServices] ouvre la base (et le reste) une seule fois ; un écran de
 * chargement l'attend, un échec est affiché plutôt qu'un écran vide.
 *
 * Navigation par état (destination courante + écran « À propos » ouvert ou
 * non) plutôt qu'une bibliothèque de navigation : cinq destinations de premier
 * niveau et un seul écran secondaire n'en justifient pas une
 * (docs/spec-v3/navigation-theme.md § Décisions).
 */
@Composable
fun KairosApp(platform: Platform, openServices: suspend () -> AppServices) {
    KairosTheme {
        CompositionLocalProvider(LocalPlatform provides platform) {
            var services by remember { mutableStateOf<AppServices?>(null) }
            var failure by remember { mutableStateOf<Throwable?>(null) }
            LaunchedEffect(Unit) {
                runCatching { openServices() }.onSuccess { services = it }.onFailure { failure = it }
            }
            val ready = services
            when {
                ready != null -> KairosShell(platform, ready)
                failure != null -> Status(stringResource(Res.string.error_open_title), failure.toString())
                else -> Status(stringResource(Res.string.loading), null, progress = true)
            }
        }
    }
}

@Composable
private fun KairosShell(platform: Platform, services: AppServices) {
    var destination by rememberSaveable { mutableStateOf(Destination.START) }
    var aboutOpen by rememberSaveable { mutableStateOf(false) }
    BoxWithConstraints {
        AppShell(
            layout = NavigationLayout.choose(platform, maxWidth),
            services = services,
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

@Composable
private fun Status(title: String, detail: String?, progress: Boolean = false) {
    Surface(Modifier.fillMaxSize()) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
            modifier = Modifier.padding(24.dp),
        ) {
            if (progress) CircularProgressIndicator()
            Text(title, style = MaterialTheme.typography.titleMedium, color = if (progress) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error)
            detail?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        }
    }
}

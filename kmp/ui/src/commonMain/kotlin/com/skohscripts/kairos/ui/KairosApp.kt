package com.skohscripts.kairos.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.skohscripts.kairos.core.model.TeamSettings
import com.skohscripts.kairos.ui.app.AppServices
import com.skohscripts.kairos.ui.generated.resources.Res
import com.skohscripts.kairos.ui.generated.resources.error_open_title
import com.skohscripts.kairos.ui.generated.resources.loading
import com.skohscripts.kairos.ui.navigation.AppShell
import com.skohscripts.kairos.ui.navigation.Destination
import com.skohscripts.kairos.ui.navigation.NavState
import com.skohscripts.kairos.ui.navigation.LocalWindowWidth
import com.skohscripts.kairos.ui.navigation.NavigationLayout
import com.skohscripts.kairos.ui.navigation.Space
import com.skohscripts.kairos.ui.navigation.TeamDestination
import com.skohscripts.kairos.ui.theme.KairosTheme
import com.skohscripts.kairos.ui.theme.LocalSystemColorScheme
import com.skohscripts.kairos.ui.theme.ThemeColors
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

/**
 * Racine de l'interface, appelée par le point d'entrée de chaque plateforme.
 * [openServices] ouvre la base (et le reste) une seule fois ; un écran de
 * chargement l'attend, un échec est affiché plutôt qu'un écran vide.
 *
 * Navigation par état (destination courante + écran « À propos » ouvert ou
 * non) plutôt qu'une bibliothèque de navigation : cinq destinations de premier
 * niveau et un seul écran secondaire n'en justifient pas une
 * (docs/spec/navigation-theme.md § Décisions).
 */
@Composable
fun KairosApp(
    platform: Platform,
    /** Destination d'ouverture ; autre que « Jour » seulement pour les captures des magasins. */
    initialDestination: Destination = Destination.START,
    /** Couleurs du système (Android 12 et plus), `null` ailleurs (docs/spec/apparence.md). */
    systemColorScheme: ColorScheme? = null,
    openServices: suspend () -> AppServices,
) {
    KairosTheme {
        CompositionLocalProvider(LocalPlatform provides platform, LocalSystemColorScheme provides systemColorScheme) {
            var services by remember { mutableStateOf<AppServices?>(null) }
            var failure by remember { mutableStateOf<Throwable?>(null) }
            LaunchedEffect(Unit) {
                runCatching { openServices() }.onSuccess { services = it }.onFailure { failure = it }
            }
            val ready = services
            when {
                ready != null -> {
                    // Thème des réglages dès qu'ils sont lus ; le miel de la charte pendant le chargement.
                    val themeColor = ready.repository.snapshot.collectAsState().value.settings.themeColor
                    val scheme = remember(themeColor, systemColorScheme) { ThemeColors.schemeFor(themeColor, systemColorScheme) }
                    KairosTheme(scheme) { KairosShell(platform, ready, initialDestination) }
                }
                failure != null -> Status(stringResource(Res.string.error_open_title), failure.toString())
                else -> Status(stringResource(Res.string.loading), null, progress = true)
            }
        }
    }
}

@Composable
private fun KairosShell(platform: Platform, services: AppServices, initialDestination: Destination) {
    val repository = services.repository
    // Seul le sous-objet d'équipe intéresse la coquille : un changement d'un autre réglage ne la recompose pas.
    val team by remember { repository.snapshot.map { it.settings.team }.distinctUntilChanged() }
        .collectAsState(repository.snapshot.value.settings.team)
    val teamEnabled = team?.enabled == true
    // Espace d'ouverture : celui où l'on a quitté l'application, si la gestion d'équipe est toujours activée.
    var chosenSpace by rememberSaveable {
        mutableStateOf(if (teamEnabled && team?.lastSpace == TeamSettings.SPACE_TEAM) Space.TEAM else Space.PERSONAL)
    }
    var destination by rememberSaveable { mutableStateOf(initialDestination) }
    var teamDestination by rememberSaveable { mutableStateOf(TeamDestination.START) }
    var aboutOpen by rememberSaveable { mutableStateOf(false) }
    val nav = remember { NavState() }
    val scope = rememberCoroutineScope()

    // L'espace Équipe n'existe que tant que la gestion d'équipe est activée (valeur enregistrée).
    val space = if (teamEnabled) chosenSpace else Space.PERSONAL

    /** Mémorise l'espace pour la prochaine session (réglage technique `lastSpace`, sans effet sur l'export des solos). */
    fun persistSpace(space: Space) {
        val current = repository.snapshot.value.settings
        val base = current.team ?: return
        val last = if (space == Space.TEAM) TeamSettings.SPACE_TEAM else TeamSettings.SPACE_PERSONAL
        if (base.lastSpace != last) scope.launch { repository.updateSettings(current.copy(team = base.copy(lastSpace = last))) }
    }

    // Gestion d'équipe désactivée alors qu'on était dans l'espace Équipe : retour à l'espace Perso, vue Jour.
    LaunchedEffect(teamEnabled) {
        if (!teamEnabled && chosenSpace == Space.TEAM) {
            chosenSpace = Space.PERSONAL
            nav.day = null
            nav.week = null
            destination = Destination.START
            aboutOpen = false
            persistSpace(Space.PERSONAL)
        }
    }

    BoxWithConstraints {
        // Largeur de la fenêtre pour les écrans qui choisissent plein écran ou dialogue (fiche membre).
        CompositionLocalProvider(LocalWindowWidth provides maxWidth) {
            AppShell(
                layout = NavigationLayout.choose(platform, maxWidth),
                services = services,
                space = space,
                destination = destination,
                teamDestination = teamDestination,
                teamModeEnabled = teamEnabled,
                teamName = team?.name.orEmpty(),
                aboutOpen = aboutOpen,
                nav = nav,
                onNavigate = {
                    // La navigation principale ramène à aujourd'hui et à la semaine courante.
                    nav.day = null
                    nav.week = null
                    destination = it
                    aboutOpen = false
                },
                onNavigateTeam = {
                    teamDestination = it
                    aboutOpen = false
                },
                onSpaceChange = {
                    // Changer d'espace ouvre la première destination de l'autre espace (Jour ou Suivi).
                    nav.day = null
                    nav.week = null
                    destination = Destination.START
                    teamDestination = TeamDestination.START
                    aboutOpen = false
                    chosenSpace = it
                    persistSpace(it)
                },
                onOpenDay = { day ->
                    nav.day = day
                    destination = Destination.DAY
                    aboutOpen = false
                },
                onOpenAbout = { aboutOpen = true },
                onCloseAbout = { aboutOpen = false },
            )
        }
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

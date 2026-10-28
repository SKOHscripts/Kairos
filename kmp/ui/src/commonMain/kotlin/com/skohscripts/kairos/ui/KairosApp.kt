package com.skohscripts.kairos.ui

import com.skohscripts.kairos.core.model.LevelNames
import com.skohscripts.kairos.ui.day.LocalLevelNames
import com.skohscripts.kairos.ui.day.LocalCalendarNames
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.runtime.key
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.unit.dp
import com.skohscripts.kairos.core.model.TeamSettings
import com.skohscripts.kairos.core.settings.ThemeMode
import com.skohscripts.kairos.core.settings.UiLanguage
import com.skohscripts.kairos.ui.app.AppServices
import com.skohscripts.kairos.ui.app.LocalStartupSplash
import com.skohscripts.kairos.ui.app.LocalUiLanguage
import com.skohscripts.kairos.ui.app.StartupSplash
import com.skohscripts.kairos.ui.generated.resources.Res
import com.skohscripts.kairos.ui.generated.resources.error_open_title
import com.skohscripts.kairos.ui.generated.resources.loading
import com.skohscripts.kairos.ui.navigation.AppShell
import com.skohscripts.kairos.ui.navigation.Destination
import com.skohscripts.kairos.ui.navigation.NavState
import com.skohscripts.kairos.ui.navigation.LocalWindowWidth
import com.skohscripts.kairos.ui.navigation.NavigationLayout
import com.skohscripts.kairos.ui.navigation.Secondary
import com.skohscripts.kairos.ui.navigation.Space
import com.skohscripts.kairos.ui.navigation.TeamDestination
import com.skohscripts.kairos.ui.theme.KairosDefaultTheme
import com.skohscripts.kairos.ui.theme.KairosTheme
import com.skohscripts.kairos.ui.theme.LocalSystemColorScheme
import com.skohscripts.kairos.ui.theme.LocalSystemDark
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
 * Navigation par état (destination courante + écran secondaire, Réglages ou « À propos », ouvert ou
 * non) plutôt qu'une bibliothèque de navigation : cinq destinations de premier
 * niveau et deux écrans secondaires n'en justifient pas une
 * (docs/spec/navigation-theme.md § Décisions).
 */
@Composable
fun KairosApp(
    platform: Platform,
    /** Destination d'ouverture : l'Accueil ; une autre pour les tests, l'auto-test et les captures des magasins. */
    initialDestination: Destination = Destination.START,
    /** Destination d'ouverture de l'espace Équipe (quand il est ouvert) : l'Accueil aussi ; une autre pour les tests et les captures. */
    initialTeamDestination: TeamDestination = TeamDestination.HOME,
    /** Écran secondaire ouvert d'emblée (les Réglages des captures des magasins), `null` ailleurs. */
    initialSecondary: Secondary? = null,
    /** Couleurs du système (Android 12 et plus), claires ou sombres, `null` ailleurs (docs/spec/apparence.md). */
    systemColorScheme: ((dark: Boolean) -> ColorScheme)? = null,
    /** Le système est en sombre : `null` = `isSystemInDarkTheme()` ; forcé par les tests et les captures (issue #72). */
    systemDark: Boolean? = null,
    /** Rappelé quand le thème affiché passe du clair au sombre ou l'inverse (barres du système Android). */
    onDarkTheme: (dark: Boolean) -> Unit = {},
    /** Page de démarrage (docs/spec/demarrage.md) : les points d'entrée seulement, jamais les tests ni les captures. */
    splash: Boolean = false,
    openServices: suspend () -> AppServices,
) {
    // Thème du système (docs/spec/apparence.md § Thème sombre) : celui de tout ce qui précède la lecture des réglages.
    val systemIsDark = systemDark ?: isSystemInDarkTheme()
    KairosDefaultTheme(systemIsDark) {
        CompositionLocalProvider(
            LocalPlatform provides platform,
            LocalSystemColorScheme provides systemColorScheme?.invoke(systemIsDark),
            LocalSystemDark provides systemIsDark,
        ) {
            var services by remember { mutableStateOf<AppServices?>(null) }
            var failure by remember { mutableStateOf<Throwable?>(null) }
            LaunchedEffect(Unit) {
                runCatching { openServices() }.onSuccess { services = it }.onFailure { failure = it }
            }
            val ready = services
            // Sauvegardé : une rotation ne rejoue pas l'animation, un nouveau lancement si.
            var splashDone by rememberSaveable { mutableStateOf(!splash) }
            // L'application se compose sous la page de démarrage : le fondu la révèle déjà prête.
            Box(Modifier.fillMaxSize()) {
                CompositionLocalProvider(LocalStartupSplash provides !splashDone) {
                    when {
                        ready != null -> {
                            // Thème et langue des réglages dès qu'ils sont lus ; le miel de la charte et la langue du système
                            // pendant le chargement.
                            val stored = ready.repository.snapshot.collectAsState().value
                            val settings = stored.settings
                            val themeColor = settings.themeColor
                            val dark = ThemeMode.isDark(settings.themeMode, systemIsDark)
                            val scheme = remember(themeColor, systemColorScheme, dark) { ThemeColors.schemeFor(themeColor, systemColorScheme, dark) }
                            val light = remember(themeColor, systemColorScheme) { ThemeColors.schemeFor(themeColor, systemColorScheme, dark = false) }
                            val darkTheme by rememberUpdatedState(onDarkTheme)
                            LaunchedEffect(dark) { darkTheme(dark) }
                            // La langue du système est lue une fois, avant la première surcharge : `Locale.current` la
                            // reflèterait ensuite la langue choisie (docs/spec/i18n.md § Choix de la langue).
                            val system = remember(ready) { ready.systemLanguage ?: Locale.current.language }
                            val language = UiLanguage.resolve(settings.language, system)
                            // Posée pendant la composition, avant que l'interface (en dessous) lise la locale.
                            remember(language) { ready.applyLanguage(language) }
                            // Noms des niveaux choisis (docs/spec/planning-personnalise.md § Lot D) : affichage seulement, partout dans l'interface.
                            val levelNames = settings.levelNames ?: LevelNames()
                            CompositionLocalProvider(
                                LocalUiLanguage provides language,
                                LocalSystemColorScheme provides systemColorScheme?.invoke(dark),
                                LocalLevelNames provides levelNames,
                                // Noms des calendriers importés : l'origine d'un créneau (docs/spec/connexions-externes.md § Interface).
                                LocalCalendarNames provides remember(stored.calendarSources) { stored.calendarSources.associate { it.id to it.name } },
                            ) {
                                KairosTheme(scheme, dark, light) { KairosShell(platform, ready, language, initialDestination, initialTeamDestination, initialSecondary) }
                            }
                        }
                        failure != null -> Status(stringResource(Res.string.error_open_title), failure.toString())
                        else -> Status(stringResource(Res.string.loading), null, progress = true)
                    }
                }
                // Dans le thème océan par défaut, clair ou sombre comme le système, pas celui des réglages : même fond que les
                // relais natifs.
                if (!splashDone) StartupSplash(ready = ready != null || failure != null, onFinished = { splashDone = true })
            }
        }
    }
}

@Composable
private fun KairosShell(
    platform: Platform,
    services: AppServices,
    /** Langue de l'interface (`fr` ou `en`) : la coquille est recomposée quand elle change. */
    language: String,
    initialDestination: Destination,
    initialTeamDestination: TeamDestination,
    initialSecondary: Secondary?,
) {
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
    var teamDestination by rememberSaveable { mutableStateOf(initialTeamDestination) }
    var secondary by rememberSaveable { mutableStateOf(initialSecondary) }
    // « À propos » ouvert depuis les Réglages : la flèche retour y ramène.
    var secondaryParent by rememberSaveable { mutableStateOf<Secondary?>(null) }
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
            nav.month = null
            destination = Destination.WORK
            secondary = null
            secondaryParent = null
            persistSpace(Space.PERSONAL)
        }
    }

    BoxWithConstraints {
        // Largeur de la fenêtre pour les écrans qui choisissent plein écran ou dialogue (fiche membre).
        CompositionLocalProvider(LocalWindowWidth provides maxWidth) {
            // Les textes des ressources lisent la locale à la composition : changer de langue recrée la coquille (écran
            // courant conservé : la navigation est mémorisée au-dessus, seul l'état interne des écrans repart).
            key(language) {
                AppShell(
                    layout = NavigationLayout.choose(platform, maxWidth),
                    services = services,
                    space = space,
                    destination = destination,
                    teamDestination = teamDestination,
                    teamModeEnabled = teamEnabled,
                    teamName = team?.name.orEmpty(),
                    secondary = secondary,
                    nav = nav,
                    onNavigate = {
                        // Navigation à la main : une section demandée par le guide et pas encore montrée n'a plus lieu d'être (issue #95).
                        nav.guideSpot.pending = null
                        // La navigation principale ramène à aujourd'hui et à la semaine courante.
                        nav.day = null
                        nav.week = null
                        nav.month = null
                        destination = it
                        secondary = null
                        secondaryParent = null
                    },
                    onNavigateTeam = {
                        nav.guideSpot.pending = null
                        teamDestination = it
                        secondary = null
                        secondaryParent = null
                    },
                    onSpaceChange = {
                        nav.guideSpot.pending = null
                        // Changer d'espace ouvre l'écran de travail de l'autre espace (Jour ou Suivi), pas son Accueil.
                        nav.day = null
                        nav.week = null
                        nav.month = null
                        destination = Destination.WORK
                        teamDestination = TeamDestination.START
                        secondary = null
                        secondaryParent = null
                        chosenSpace = it
                        persistSpace(it)
                    },
                    onOpenDay = { day ->
                        nav.guideSpot.pending = null
                        nav.day = day
                        destination = Destination.DAY
                        secondary = null
                        secondaryParent = null
                    },
                    onOpenSettings = {
                        nav.guideSpot.pending = null
                        // La roue crantée ouvre toujours la liste des catégories (docs/spec/reglages.md).
                        nav.settings.category = null
                        secondary = Secondary.SETTINGS
                        secondaryParent = null
                    },
                    onOpenAbout = {
                        secondaryParent = secondary
                        secondary = Secondary.ABOUT
                    },
                    onCloseSecondary = {
                        secondary = secondaryParent
                        secondaryParent = null
                    },
                )
            }
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

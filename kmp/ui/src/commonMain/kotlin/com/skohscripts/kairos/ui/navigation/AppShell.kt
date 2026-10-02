package com.skohscripts.kairos.ui.navigation

import com.skohscripts.kairos.ui.theme.KairosFilterChip
import androidx.compose.foundation.Image
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.skohscripts.kairos.ui.app.AppServices
import com.skohscripts.kairos.ui.app.LocalMessages
import com.skohscripts.kairos.ui.app.WelcomeDialog
import com.skohscripts.kairos.ui.app.ShortcutBanner
import com.skohscripts.kairos.ui.app.UpdateBanner
import com.skohscripts.kairos.ui.app.UpdateWatcher
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.skohscripts.kairos.ui.generated.resources.Res
import com.skohscripts.kairos.ui.generated.resources.action_back
import com.skohscripts.kairos.ui.generated.resources.app_name
import com.skohscripts.kairos.ui.generated.resources.help_action
import com.skohscripts.kairos.ui.generated.resources.nav_navigation
import com.skohscripts.kairos.ui.generated.resources.title_home
import com.skohscripts.kairos.ui.generated.resources.title_about
import com.skohscripts.kairos.ui.icons.KairosIcons
import com.skohscripts.kairos.ui.icons.KairosLogo
import com.skohscripts.kairos.ui.guide.GuideDialog
import com.skohscripts.kairos.ui.guide.GuideKind
import com.skohscripts.kairos.ui.guide.GuideTarget
import com.skohscripts.kairos.ui.guide.HelpDialog
import com.skohscripts.kairos.ui.guide.HelpTopic
import com.skohscripts.kairos.ui.screens.AboutScreen
import com.skohscripts.kairos.ui.screens.HomeScreen
import com.skohscripts.kairos.ui.screens.DestinationScreen
import com.skohscripts.kairos.ui.screens.TeamDestinationScreen
import com.skohscripts.kairos.ui.team.SpaceSelector
import com.skohscripts.kairos.ui.generated.resources.title_team_named
import com.skohscripts.kairos.core.stats.TaskStats
import com.skohscripts.kairos.ui.day.Dates
import com.skohscripts.kairos.ui.generated.resources.title_day_other
import com.skohscripts.kairos.ui.generated.resources.title_week_of
import androidx.compose.ui.text.intl.Locale
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import com.skohscripts.kairos.ui.chrono.AlertBanners
import com.skohscripts.kairos.ui.chrono.ChronoWatcher
import org.jetbrains.compose.resources.stringResource

/**
 * Coquille commune : navigation principale (rail, barre basse ou barre haute
 * selon [layout]), barre d'application avec le titre de l'écran, contenu.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
fun AppShell(
    layout: NavigationLayout,
    services: AppServices,
    /** Espace courant ; [Space.TEAM] seulement si [teamModeEnabled]. */
    space: Space,
    destination: Destination,
    teamDestination: TeamDestination,
    /** Gestion d'équipe activée (valeur enregistrée) : seul cas où le sélecteur d'espace apparaît. */
    teamModeEnabled: Boolean,
    /** Nom de l'équipe (éventuellement vide), préfixe du titre dans l'espace Équipe. */
    teamName: String,
    /** Écran secondaire ouvert (Accueil ou « À propos et guide »), `null` = une destination. */
    secondary: Secondary?,
    nav: NavState,
    onNavigate: (Destination) -> Unit,
    onNavigateTeam: (TeamDestination) -> Unit,
    onSpaceChange: (Space) -> Unit,
    onOpenDay: (LocalDate?) -> Unit,
    onOpenHome: () -> Unit,
    onOpenAbout: () -> Unit,
    onCloseSecondary: () -> Unit,
) {
    // Retour système (Android) ou Échap (bureau, web) : ferme l'écran secondaire (Accueil ou « À propos »).
    BackHandler(enabled = secondary != null) { onCloseSecondary() }
    // Aide de l'écran courant (« ? ») et visite guidée en cours : états de la coquille, rien n'est mémorisé au-delà.
    var helpOpen by rememberSaveable { mutableStateOf(false) }
    var guide by rememberSaveable { mutableStateOf<GuideKind?>(null) }

    val snackbar = remember { SnackbarHostState() }
    // Veille du chrono pour toute l'application : les alertes jouent quel que soit l'écran.
    val alerts = remember { mutableStateListOf<String>() }
    ChronoWatcher(services, alerts)
    UpdateWatcher(services)
    val scope = rememberCoroutineScope()
    val showMessage: (String) -> Unit = { message -> scope.launch { snackbar.showSnackbar(message) } }
    // Hors des branches de mise en page : un redimensionnement ne rouvre pas l'accueil.
    CompositionLocalProvider(LocalMessages provides showMessage) { WelcomeDialog(services, onStartGuide = { guide = GuideKind.PERSONAL }) }

    val language = Locale.current.language
    val today = services.clock.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
    val day = nav.day
    // Destinations de l'espace courant : la coquille ne connaît que [NavEntry].
    val team = space == Space.TEAM
    val entries: List<NavEntry> = if (team) TeamDestination.entries else Destination.entries
    val current: NavEntry = if (team) teamDestination else destination
    val selected: NavEntry? = current.takeUnless { secondary != null }
    val onSelect: (NavEntry) -> Unit = {
        when (it) {
            is Destination -> onNavigate(it)
            is TeamDestination -> onNavigateTeam(it)
        }
    }
    // Où mène une cible de la visite ou de l'Accueil : l'espace change au besoin, puis la destination
    // (changer d'espace ouvre sa première destination, d'où l'ordre) ; une cible d'équipe sans gestion d'équipe est sans effet.
    val openTarget: (GuideTarget) -> Unit = { target ->
        when (target) {
            is GuideTarget.Personal -> {
                if (team) onSpaceChange(Space.PERSONAL)
                onNavigate(target.destination)
            }
            is GuideTarget.Team -> if (teamModeEnabled) {
                if (!team) onSpaceChange(Space.TEAM)
                nav.exchangesTab = target.exchanges
                onNavigateTeam(target.destination)
            }
        }
    }
    val selector: (@Composable () -> Unit)? = if (teamModeEnabled) {
        { SpaceSelector(space, onSpaceChange) }
    } else {
        null
    }
    // Titre fidèle à ce qui est affiché : « Aujourd'hui » seulement si c'est vrai (Kairos 2).
    val title = when {
        secondary == Secondary.HOME -> stringResource(Res.string.title_home)
        secondary == Secondary.ABOUT -> stringResource(Res.string.title_about)
        team && teamName.isNotBlank() -> stringResource(Res.string.title_team_named, teamName, stringResource(current.title))
        team -> stringResource(current.title)
        destination == Destination.DAY && day != null && day != today -> stringResource(Res.string.title_day_other, Dates.long(day, language))
        destination == Destination.WEEK -> stringResource(Res.string.title_week_of, Dates.long(TaskStats.monday(nav.week ?: today), language))
        else -> stringResource(destination.title)
    }
    val topBar: @Composable () -> Unit = {
        TopAppBar(
            // Avec le sélecteur d'espace à côté, le titre tient sur deux lignes au plus.
            title = { if (selector != null) Text(title, maxLines = 2, overflow = TextOverflow.Ellipsis) else Text(title) },
            navigationIcon = {
                if (secondary != null) {
                    IconButton(onClick = onCloseSecondary) {
                        Icon(KairosIcons.ArrowBack, contentDescription = stringResource(Res.string.action_back))
                    }
                } else if (layout != NavigationLayout.RAIL) {
                    // Sans rail, le logo porte l'identité en tête de la barre.
                    Image(KairosLogo, contentDescription = null, modifier = Modifier.padding(start = 16.dp).size(28.dp))
                }
            },
            // Sans rail, le sélecteur d'espace est à droite du titre ; avec un rail, il est dans son en-tête.
            actions = {
                if (layout != NavigationLayout.RAIL) selector?.invoke()
                // Sur l'Accueil et « À propos », l'aide est déjà la page elle-même.
                if (secondary == null) {
                    IconButton(onClick = { helpOpen = true }) {
                        Icon(KairosIcons.Help, contentDescription = stringResource(Res.string.help_action))
                    }
                }
            },
        )
    }
    val content: @Composable (Modifier) -> Unit = { modifier ->
        Column(modifier.fillMaxSize()) {
            UpdateBanner(services)
            CompositionLocalProvider(LocalMessages provides showMessage) { ShortcutBanner(services) }
            Box(Modifier.weight(1f).fillMaxWidth()) {
                CompositionLocalProvider(LocalMessages provides showMessage) {
                    when {
                        secondary == Secondary.HOME -> HomeScreen(teamModeEnabled, openTarget, { guide = it }, onOpenAbout)
                        secondary == Secondary.ABOUT -> AboutScreen()
                        team -> TeamDestinationScreen(teamDestination, services, nav, onOpenAbout, onTour = { guide = GuideKind.TEAM })
                        else -> DestinationScreen(destination, services, nav, onOpenDay, onNavigate, onOpenAbout)
                    }
                }
                AlertBanners(alerts, Modifier.align(Alignment.BottomCenter))
            }
        }
    }

    if (helpOpen) {
        HelpDialog(
            screen = current,
            topic = HelpTopic.of(current, nav.exchangesTab),
            team = team,
            onOpenHome = { helpOpen = false; onOpenHome() },
            onStartTour = { helpOpen = false; guide = if (team) GuideKind.TEAM else GuideKind.PERSONAL },
            onDismiss = { helpOpen = false },
        )
    }
    guide?.let { kind ->
        GuideDialog(kind, onOpen = { target -> guide = null; openTarget(target) }, onDismiss = { guide = null })
    }

    when (layout) {
        NavigationLayout.RAIL -> Row(Modifier.fillMaxSize()) {
            KairosNavigationRail(entries, selected, onSelect, selector)
            Scaffold(topBar = topBar, snackbarHost = { SnackbarHost(snackbar) }) { padding -> content(Modifier.padding(padding)) }
        }
        NavigationLayout.BOTTOM_BAR -> Scaffold(
            topBar = topBar,
            snackbarHost = { SnackbarHost(snackbar) },
            bottomBar = { KairosNavigationBar(entries, selected, onSelect) },
        ) { padding -> content(Modifier.padding(padding)) }
        NavigationLayout.TOP_BAR -> Scaffold(
            topBar = {
                Column {
                    topBar()
                    KairosTopNavigation(entries, selected, onSelect)
                }
            },
            snackbarHost = { SnackbarHost(snackbar) },
        ) { padding -> content(Modifier.padding(padding)) }
    }
}

@Composable
private fun KairosNavigationRail(
    entries: List<NavEntry>,
    selected: NavEntry?,
    onNavigate: (NavEntry) -> Unit,
    spaceSelector: (@Composable () -> Unit)?,
) {
    NavigationRail(
        header = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(vertical = 12.dp)) {
                Image(KairosLogo, contentDescription = null, modifier = Modifier.size(34.dp))
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(Res.string.app_name),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                )
                // Mode équipe activé seulement : en mode solo, l'en-tête reste celui d'avant.
                spaceSelector?.let {
                    Spacer(Modifier.height(12.dp))
                    Box(Modifier.padding(horizontal = 4.dp)) { it() }
                }
            }
        },
    ) {
        Spacer(Modifier.height(8.dp))
        entries.forEach { dest ->
            val isSelected = dest == selected
            NavigationRailItem(
                selected = isSelected,
                onClick = { onNavigate(dest) },
                icon = { Icon(if (isSelected) dest.selectedIcon() else dest.icon(), contentDescription = null) },
                label = { Text(stringResource(dest.label)) },
            )
        }
    }
}

@Composable
private fun KairosNavigationBar(entries: List<NavEntry>, selected: NavEntry?, onNavigate: (NavEntry) -> Unit) {
    NavigationBar {
        entries.forEach { dest ->
            val isSelected = dest == selected
            NavigationBarItem(
                selected = isSelected,
                onClick = { onNavigate(dest) },
                icon = { Icon(if (isSelected) dest.selectedIcon() else dest.icon(), contentDescription = null) },
                label = { Text(stringResource(dest.label)) },
            )
        }
    }
}

/** Barre horizontale compacte (bureau/web étroit) : pastilles, l'active en `secondaryContainer`. */
@Composable
private fun KairosTopNavigation(entries: List<NavEntry>, selected: NavEntry?, onNavigate: (NavEntry) -> Unit) {
    val description = stringResource(Res.string.nav_navigation)
    Surface(color = MaterialTheme.colorScheme.surface) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .semantics { contentDescription = description }
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            entries.forEach { dest ->
                val isSelected = dest == selected
                KairosFilterChip(
                    selected = isSelected,
                    onClick = { onNavigate(dest) },
                    label = { Text(stringResource(dest.label)) },
                    leadingIcon = {
                        Icon(
                            if (isSelected) dest.selectedIcon() else dest.icon(),
                            contentDescription = null,
                            modifier = Modifier.size(FilterChipDefaults.IconSize),
                        )
                    },
                )
            }
        }
    }
}

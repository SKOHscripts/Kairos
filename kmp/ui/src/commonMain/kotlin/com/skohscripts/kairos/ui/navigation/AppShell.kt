package com.skohscripts.kairos.ui.navigation

import com.skohscripts.kairos.ui.reminders.ReminderWatcher
import com.skohscripts.kairos.core.engine.TimeTracking
import com.skohscripts.kairos.ui.generated.resources.demo_hub_busy
import com.skohscripts.kairos.ui.generated.resources.demo_task_title
import com.skohscripts.kairos.ui.guide.demo.DemoBanner
import com.skohscripts.kairos.ui.guide.demo.DemoDoneDialog
import com.skohscripts.kairos.ui.guide.demo.DemoPanel
import com.skohscripts.kairos.ui.guide.demo.DemoTour
import com.skohscripts.kairos.ui.guide.demo.LocalDemoTour
import com.skohscripts.kairos.ui.team.TeamTaskCreateDialog
import com.skohscripts.kairos.ui.team.TeamTaskCreatedDialog
import androidx.compose.runtime.collectAsState
import com.skohscripts.kairos.core.KairosBuild
import com.skohscripts.kairos.ui.app.uiLanguage
import com.skohscripts.kairos.ui.theme.KairosFilterChip
import androidx.compose.foundation.Image
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
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
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.skohscripts.kairos.ui.app.AppServices
import com.skohscripts.kairos.ui.app.LocalMessages
import com.skohscripts.kairos.ui.app.LocalUndoMessages
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
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.skohscripts.kairos.ui.generated.resources.Res
import com.skohscripts.kairos.ui.generated.resources.action_back
import com.skohscripts.kairos.ui.generated.resources.app_name
import com.skohscripts.kairos.ui.generated.resources.donate_action
import com.skohscripts.kairos.ui.generated.resources.help_action
import com.skohscripts.kairos.ui.generated.resources.link_unavailable
import com.skohscripts.kairos.ui.generated.resources.nav_navigation
import com.skohscripts.kairos.ui.generated.resources.nav_settings
import com.skohscripts.kairos.ui.generated.resources.title_settings
import com.skohscripts.kairos.ui.generated.resources.title_about
import com.skohscripts.kairos.ui.generated.resources.features_list_title
import com.skohscripts.kairos.ui.icons.KairosIcons
import com.skohscripts.kairos.ui.icons.KairosLogo
import com.skohscripts.kairos.ui.guide.GuideDialog
import com.skohscripts.kairos.ui.guide.DiscoverDialog
import com.skohscripts.kairos.ui.guide.FeatureCatalog
import com.skohscripts.kairos.ui.guide.GuideChapter
import com.skohscripts.kairos.ui.guide.GuideKind
import com.skohscripts.kairos.ui.guide.GuideTarget
import com.skohscripts.kairos.ui.guide.LocalGuideSpot
import com.skohscripts.kairos.ui.guide.HelpDialog
import com.skohscripts.kairos.ui.guide.HelpTopic
import com.skohscripts.kairos.ui.screens.AboutScreen
import com.skohscripts.kairos.ui.screens.FeatureListScreen
import com.skohscripts.kairos.ui.screens.DONATE_URL
import com.skohscripts.kairos.ui.screens.HomeScreen
import com.skohscripts.kairos.ui.settings.SettingsScreen
import com.skohscripts.kairos.ui.screens.DestinationScreen
import com.skohscripts.kairos.ui.screens.TeamDestinationScreen
import com.skohscripts.kairos.ui.team.SpaceSelector
import com.skohscripts.kairos.ui.generated.resources.title_team_named
import com.skohscripts.kairos.core.stats.TaskStats
import com.skohscripts.kairos.ui.day.Dates
import com.skohscripts.kairos.ui.generated.resources.title_day_other
import com.skohscripts.kairos.ui.generated.resources.title_week_of
import com.skohscripts.kairos.ui.generated.resources.title_month_of
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
    /** Écran secondaire ouvert (Réglages ou « À propos et guide »), `null` = une destination. */
    secondary: Secondary?,
    nav: NavState,
    onNavigate: (Destination) -> Unit,
    onNavigateTeam: (TeamDestination) -> Unit,
    onSpaceChange: (Space) -> Unit,
    onOpenDay: (LocalDate?) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenAbout: () -> Unit,
    onCloseSecondary: () -> Unit,
    /** Ouvre la liste des fonctionnalités (bouton de l'Accueil, docs/spec/liste-fonctionnalites.md). */
    onOpenFeatures: () -> Unit = {},
    /**
     * Démonstration montrée par cette coquille (docs/spec/visite-guidee.md § Visite interactive) ; `null` : la coquille réelle,
     * rendu et comportement inchangés.
     */
    demo: DemoTour? = null,
    /** Lance une démonstration (entrée du hub, carte du catalogue) ; sans effet si la plateforme n'en fournit pas. */
    onStartDemo: () -> Unit = {},
    onExitDemo: () -> Unit = {},
    onRestartDemo: () -> Unit = {},
) {
    // Une catégorie des Réglages ouverte seule à l'écran (téléphone) : le retour ramène d'abord à la liste.
    val inSettingsCategory = secondary == Secondary.SETTINGS && nav.settings.inCategory
    val back: () -> Unit = { if (inSettingsCategory) nav.settings.category = null else onCloseSecondary() }
    // Retour système (Android) ou Échap (bureau, web) : ferme l'écran secondaire (Réglages ou « À propos »).
    BackHandler(enabled = secondary != null) { back() }
    // Démonstration : le retour système (ou Échap) la quitte, une fois les dialogues refermés.
    BackHandler(enabled = demo != null && secondary == null) { onExitDemo() }
    // Aide de l'écran courant (« ? ») et visite guidée en cours : états de la coquille, rien n'est mémorisé au-delà.
    var helpOpen by rememberSaveable { mutableStateOf(false) }
    val uriHandler = LocalUriHandler.current
    var guide by rememberSaveable { mutableStateOf<GuideKind?>(null) }
    // Hub « Découvrir Kairos » et chapitre en cours de lecture (docs/spec/visite-guidee.md) : états de la coquille, rien n'est mémorisé.
    var discover by rememberSaveable { mutableStateOf(false) }
    var chapter by rememberSaveable { mutableStateOf<GuideChapter?>(null) }
    // Première tâche d'équipe accompagnée (issue #97) : étape du coach (`null` = fiche fermée, -1 = coach passé) et tâche créée.
    var teamCoachStep by rememberSaveable { mutableStateOf<Int?>(null) }
    var teamCreated by rememberSaveable { mutableStateOf<Long?>(null) }

    val snackbar = remember { SnackbarHostState() }
    // Veille du chrono pour toute l'application : les alertes jouent quel que soit l'écran.
    val alerts = remember { mutableStateListOf<String>() }
    ChronoWatcher(services, alerts)
    // Rappels des tâches à heure fixe et de la tâche suivante (docs/spec/rappels.md) : mêmes bandeaux.
    ReminderWatcher(services, alerts)
    UpdateWatcher(services)
    val scope = rememberCoroutineScope()
    // Ce que le guide a retenu (docs/spec/visite-guidee.md § Persistance, issue #94).
    val stored by services.repository.snapshot.collectAsState()
    val memory = stored.settings.guide
    val newsKey = FeatureCatalog.newsKey(KairosBuild.VERSION_NAME)
    val showMessage: (String) -> Unit = { message -> scope.launch { snackbar.showSnackbar(message) } }
    // Message avec une action : l'action n'est jouée que si l'utilisateur la choisit avant la fin du message.
    val showUndo: (String, String, () -> Unit) -> Unit = { message, label, action ->
        scope.launch { if (snackbar.showSnackbar(message, actionLabel = label) == SnackbarResult.ActionPerformed) action() }
    }
    val linkFailure = stringResource(Res.string.link_unavailable)
    // Hors des branches de mise en page : un redimensionnement ne rouvre pas l'accueil.
    // L'accompagnement de la première tâche (docs/spec/premiere-tache.md) se passe sur la vue Jour de l'espace Perso : on y va, puis on le demande.
    val startTask: (Boolean) -> Unit = { recurring ->
        if (space == Space.TEAM) onSpaceChange(Space.PERSONAL)
        onNavigate(Destination.DAY)
        nav.firstTaskRecurring = recurring
        nav.firstTaskRequest = true
    }
    val startFirstTask: () -> Unit = { startTask(false) }
    if (demo == null) {
        CompositionLocalProvider(LocalMessages provides showMessage) {
            WelcomeDialog(services, onStartGuide = { guide = GuideKind.PERSONAL }, onFirstTask = startFirstTask)
        }
    }
    // Démonstration (docs/spec/visite-guidee.md § Visite interactive) : refusée pendant un chrono réel, la coquille réelle
    // quittant la composition (sa veille du chrono avec elle).
    val demoBusy = TimeTracking.runningSession(stored.workSessions) != null
    val demoBusyText = stringResource(Res.string.demo_hub_busy)
    val startDemo: () -> Unit = { if (demoBusy) showMessage(demoBusyText) else onStartDemo() }
    if (demo != null) {
        // L'étape avance quand son effet apparaît dans les données de la démonstration.
        LaunchedEffect(demo) { services.repository.snapshot.collect { demo.observe(it) } }
    }

    val language = uiLanguage()
    val today = services.clock.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
    val day = nav.day
    // Destinations de l'espace courant : la coquille ne connaît que [NavEntry].
    val team = space == Space.TEAM
    // Démonstration : ni Accueil, ni réglages, ni « ? » : rien n'y est enregistrable, l'aide reste dans l'application réelle.
    val entries: List<NavEntry> = when {
        team -> TeamDestination.entries
        demo != null -> Destination.entries - Destination.HOME
        else -> Destination.entries
    }
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
                // Après la navigation, qui efface toute demande en attente (KairosApp).
                nav.guideSpot.pending = target.section
            }
            is GuideTarget.Team -> if (teamModeEnabled) {
                if (!team) onSpaceChange(Space.TEAM)
                nav.exchangesTab = target.exchanges
                onNavigateTeam(target.destination)
            }
            is GuideTarget.Settings -> {
                onOpenSettings()
                // La roue crantée ouvre la liste : la section ouvre d'abord sa catégorie (docs/spec/reglages.md).
                target.section?.let { section ->
                    nav.settings.category = section.settingsCategory
                    nav.guideSpot.pending = section
                }
            }
            GuideTarget.FirstTask -> startFirstTask()
            GuideTarget.Demo -> if (services.demo != null) startDemo()
        }
    }
    // L'Accueil de l'espace courant (destination de tête, la même page dans les deux espaces).
    val openHome: () -> Unit = { if (team) onNavigateTeam(TeamDestination.HOME) else onNavigate(Destination.HOME) }
    val selector: (@Composable () -> Unit)? = if (teamModeEnabled) {
        { SpaceSelector(space, onSpaceChange) }
    } else {
        null
    }
    // Titre fidèle à ce qui est affiché : « Aujourd'hui » seulement si c'est vrai.
    val title = when {
        inSettingsCategory -> stringResource(nav.settings.category!!.title)
        secondary == Secondary.SETTINGS -> stringResource(Res.string.title_settings)
        secondary == Secondary.ABOUT -> stringResource(Res.string.title_about)
        secondary == Secondary.FEATURES -> stringResource(Res.string.features_list_title)
        team && teamName.isNotBlank() -> stringResource(Res.string.title_team_named, teamName, stringResource(current.title))
        team -> stringResource(current.title)
        // Sans rail (fenêtre étroite), date courte : « Semaine · du lundi 5 octobre 2026 » tenait sur trois lignes à 380 dp.
        destination == Destination.DAY && day != null && day != today ->
            stringResource(Res.string.title_day_other, if (layout == NavigationLayout.RAIL) Dates.long(day, language) else Dates.dayShort(day, language))
        destination == Destination.WEEK && nav.monthMode -> stringResource(Res.string.title_month_of, Dates.monthYear(nav.month ?: today, language))
        destination == Destination.WEEK -> TaskStats.monday(nav.week ?: today).let { monday ->
            stringResource(Res.string.title_week_of, if (layout == NavigationLayout.RAIL) Dates.long(monday, language) else Dates.short(monday, language))
        }
        else -> stringResource(destination.title)
    }
    // Sujet d'aide de ce qui est affiché ; `null` = pas de « ? » (Accueil, « À propos »).
    val helpTopic: HelpTopic? = when (secondary) {
        Secondary.SETTINGS -> HelpTopic.SETTINGS
        Secondary.ABOUT, Secondary.FEATURES -> null
        null -> HelpTopic.of(current, nav.exchangesTab)
    }
    val topBar: @Composable () -> Unit = {
        TopAppBar(
            // Deux lignes au plus (sélecteur d'espace à côté, fenêtre étroite) : la barre garde sa hauteur.
            // Avec le sélecteur d'espace dans la barre, le titre garde moins de 120 dp à 360 dp : corps réduit, sinon
            // « Aujourd’hui » et « Statistiques » se coupaient au milieu du mot.
            title = {
                val tight = layout != NavigationLayout.RAIL && selector != null
                Text(title, style = if (tight) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
            },
            navigationIcon = {
                if (secondary != null) {
                    IconButton(onClick = back) {
                        Icon(KairosIcons.ArrowBack, contentDescription = stringResource(Res.string.action_back))
                    }
                } else if (layout != NavigationLayout.RAIL) {
                    // Sans rail, le logo porte l'identité en tête de la barre.
                    Image(KairosLogo, contentDescription = null, modifier = Modifier.padding(start = 16.dp).size(28.dp))
                }
            },
            // Sans rail, le sélecteur d'espace est à droite du titre ; avec un rail, il est dans son en-tête.
            actions = actions@{
                if (demo != null) return@actions
                val selectorInBar = layout != NavigationLayout.RAIL && selector != null
                if (selectorInBar) selector?.invoke()
                // Sélecteur d'espace dans la barre : le cœur lui laisse la place (titre illisible sinon à 360 dp) ;
                // « Soutenir Kairos » reste dans « À propos et guide » (docs/spec/soutien.md).
                if (!selectorInBar) {
                    IconButton(onClick = { runCatching { uriHandler.openUri(DONATE_URL) }.onFailure { showMessage(linkFailure) } }) {
                        Icon(KairosIcons.Favorite, contentDescription = stringResource(Res.string.donate_action))
                    }
                }
                // Sur l'Accueil et « À propos », l'aide est déjà la page elle-même ; les Réglages gardent la leur.
                if (helpTopic != null) {
                    IconButton(onClick = { helpOpen = true }) {
                        Icon(KairosIcons.Help, contentDescription = stringResource(Res.string.help_action))
                    }
                }
                // Réglages : à droite, de partout sauf depuis eux-mêmes et « À propos » (qu'ils ouvrent).
                if (secondary == null) {
                    IconButton(onClick = onOpenSettings) {
                        Icon(KairosIcons.Settings, contentDescription = stringResource(Res.string.nav_settings))
                    }
                }
            },
        )
    }
    val content: @Composable (Modifier) -> Unit = { modifier ->
        Column(modifier.fillMaxSize()) {
            if (demo != null) DemoBanner(onExitDemo)
            CompositionLocalProvider(LocalMessages provides showMessage) { UpdateBanner(services) }
            CompositionLocalProvider(LocalMessages provides showMessage) { ShortcutBanner(services) }
            Box(Modifier.weight(1f).fillMaxWidth()) {
                CompositionLocalProvider(
                    LocalMessages provides showMessage, LocalUndoMessages provides showUndo, LocalGuideSpot provides nav.guideSpot,
                    LocalDemoTour provides demo,
                ) {
                    val home: @Composable () -> Unit = {
                        HomeScreen(
                            teamModeEnabled, openTarget, { guide = it }, onOpenAbout, onDiscover = { discover = true },
                            newsHidden = newsKey != null && memory?.newsHidden == newsKey,
                            onHideNews = { scope.launch { services.repository.updateGuide { it.copy(newsHidden = newsKey) } } },
                            onOpenFeatures = onOpenFeatures,
                        )
                    }
                    when {
                        secondary == Secondary.SETTINGS -> SettingsScreen(services, nav.settings, onDiscover = { discover = true }, onOpenAbout = onOpenAbout)
                        secondary == Secondary.ABOUT -> AboutScreen()
                        // Un toucher ferme la liste, puis ouvre la cible de la carte (qui peut être les Réglages).
                        secondary == Secondary.FEATURES -> FeatureListScreen(teamModeEnabled) { target -> onCloseSecondary(); openTarget(target) }
                        team -> TeamDestinationScreen(teamDestination, services, nav, onTour = { guide = GuideKind.TEAM }, home = home)
                        else -> DestinationScreen(destination, services, nav, onOpenDay, onNavigate, home, onDiscover = { discover = true })
                    }
                }
                AlertBanners(alerts, Modifier.align(Alignment.BottomCenter))
            }
        }
    }

    if (helpOpen && helpTopic != null) {
        HelpDialog(
            topic = helpTopic,
            team = team,
            onOpenHome = { helpOpen = false; openHome() },
            onStartTour = { helpOpen = false; guide = if (team) GuideKind.TEAM else GuideKind.PERSONAL },
            onDismiss = { helpOpen = false },
            onDiscover = { helpOpen = false; discover = true },
        )
    }
    if (discover && demo == null) {
        DiscoverDialog(
            teamEnabled = teamModeEnabled,
            onFirstTask = { discover = false; startFirstTask() },
            onChapter = { discover = false; chapter = it },
            onDismiss = { discover = false },
            read = memory?.readChapters.orEmpty().mapNotNull { name -> GuideChapter.entries.firstOrNull { it.name == name } }.toSet(),
            // La fiche s'ouvre dans l'espace Équipe, sur le Backlog où la tâche arrivera.
            onFirstTeamTask = {
                discover = false
                if (!team) onSpaceChange(Space.TEAM)
                onNavigateTeam(TeamDestination.BACKLOG)
                teamCoachStep = 0
            },
            // Première tâche récurrente et visite des Réglages (docs/spec/premiere-tache.md, issue #98).
            onFirstRecurring = { discover = false; startTask(true) },
            onSettingsTour = {
                discover = false
                onOpenSettings()
                nav.settings.tourStep = 0
            },
            onDemo = services.demo?.let { { discover = false; startDemo() } },
            demoBusy = demoBusy,
        )
    }
    teamCoachStep?.let { step ->
        TeamTaskCreateDialog(
            services, step,
            onStep = { teamCoachStep = it },
            onDismiss = { teamCoachStep = null },
            onCreated = { id -> teamCoachStep = null; teamCreated = id },
        )
    }
    teamCreated?.let { id ->
        TeamTaskCreatedDialog(services, id, onOpen = { teamCreated = null; onNavigateTeam(it) }, onDismiss = { teamCreated = null })
    }
    chapter?.let { picked ->
        GuideDialog(
            FeatureCatalog.chapter(picked), "chapter-${picked.name}",
            onOpen = { target -> chapter = null; openTarget(target) },
            onDismiss = { chapter = null },
            // Chapitre parcouru jusqu'au bout : coché dans le hub (issue #94).
            onFinish = { scope.launch { services.repository.updateGuide { it.copy(readChapters = (it.readChapters + picked.name).distinct()) } } },
        )
    }
    guide?.let { kind ->
        GuideDialog(kind, onOpen = { target -> guide = null; openTarget(target) }, onDismiss = { guide = null })
    }
    if (demo?.finished == true) DemoDoneDialog(onExit = onExitDemo, onRestart = onRestartDemo)
    // « Faire pour moi » : les mêmes méthodes du dépôt que l'interface ; « À moi de jouer » : la vue Jour, où sont les repères.
    val demoTitle = stringResource(Res.string.demo_task_title)
    val demoPanel: @Composable () -> Unit = {
        if (demo != null && !demo.finished) {
            DemoPanel(
                demo,
                onDoIt = { scope.launch { demo.step.act(demo.context(demoTitle)) } },
                onMyTurn = {
                    if (destination != Destination.DAY || secondary != null) onNavigate(Destination.DAY)
                    demo.askFocus()
                },
            )
        }
    }

    // Une seule arborescence pour les trois mises en page : le contenu garde sa place dans la composition quand la
    // fenêtre franchit 600 dp, donc l'état des écrans (dialogue d'édition ouvert, saisie en cours, défilement) survit
    // au redimensionnement. Trois branches `when` distinctes recréaient l'écran et fermaient ses dialogues.
    Row(Modifier.fillMaxSize()) {
        if (layout == NavigationLayout.RAIL) KairosNavigationRail(entries, selected, onSelect, selector)
        Scaffold(
            topBar = {
                Column {
                    topBar()
                    if (layout == NavigationLayout.TOP_BAR) KairosTopNavigation(entries, selected, onSelect)
                }
            },
            snackbarHost = { SnackbarHost(snackbar) },
            // Le panneau de la démonstration passe au-dessus de la barre basse : le contenu se réduit d'autant, le repère
            // n'est jamais caché.
            bottomBar = {
                Column {
                    demoPanel()
                    if (layout == NavigationLayout.BOTTOM_BAR) KairosNavigationBar(entries, selected, onSelect)
                }
            },
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
    NavigationRail {
        // En-tête et destinations défilent ensemble : sur une fenêtre basse (téléphone en paysage, 360 dp de haut),
        // les dernières destinations restaient hors d'atteinte sous le bord.
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.verticalScroll(rememberScrollState()),
        ) {
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
/** Pixels par cran de molette sur la rangée des destinations (un cran vaut ~1 unité de `scrollDelta`). */
private const val WHEEL_STEP = 60f

@Composable
private fun KairosTopNavigation(entries: List<NavEntry>, selected: NavEntry?, onNavigate: (NavEntry) -> Unit) {
    val description = stringResource(Res.string.nav_navigation)
    val scroll = rememberScrollState()
    val scope = rememberCoroutineScope()
    Surface(color = MaterialTheme.colorScheme.surface) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .semantics { contentDescription = description }
                // La molette de la souris (verticale) fait défiler la rangée : sans elle, une fenêtre de bureau étroite
                // ne montrait ni « Stats » ni (une fois Stats ouvert) « Accueil », faute de geste horizontal.
                .pointerInput(scroll) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            if (event.type == PointerEventType.Scroll) {
                                val delta = event.changes.sumOf { (it.scrollDelta.y + it.scrollDelta.x).toDouble() }.toFloat()
                                scope.launch { scroll.scrollTo((scroll.value + delta * WHEEL_STEP).toInt().coerceIn(0, scroll.maxValue)) }
                                event.changes.forEach { it.consume() }
                            }
                        }
                    }
                }
                .horizontalScroll(scroll)
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            entries.forEach { dest ->
                val isSelected = dest == selected
                // Bandeau plus large que l'écran (360 dp, cinq destinations) : l'onglet actif revient toujours en vue,
                // y compris quand on y arrive autrement que par lui (Accueil, visite guidée, raccourci).
                val bring = remember { BringIntoViewRequester() }
                LaunchedEffect(isSelected) { if (isSelected) bring.bringIntoView() }
                KairosFilterChip(
                    selected = isSelected,
                    onClick = { onNavigate(dest) },
                    modifier = Modifier.bringIntoViewRequester(bring),
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

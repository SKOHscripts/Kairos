package com.skohscripts.kairos.ui.navigation

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
import androidx.compose.material3.FilterChip
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
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.compose.ui.unit.dp
import com.skohscripts.kairos.ui.generated.resources.Res
import com.skohscripts.kairos.ui.generated.resources.action_back
import com.skohscripts.kairos.ui.generated.resources.app_name
import com.skohscripts.kairos.ui.generated.resources.nav_navigation
import com.skohscripts.kairos.ui.generated.resources.title_about
import com.skohscripts.kairos.ui.icons.KairosIcons
import com.skohscripts.kairos.ui.icons.KairosLogo
import com.skohscripts.kairos.ui.screens.AboutScreen
import com.skohscripts.kairos.ui.screens.DestinationScreen
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
    destination: Destination,
    aboutOpen: Boolean,
    nav: NavState,
    onNavigate: (Destination) -> Unit,
    onOpenDay: (LocalDate?) -> Unit,
    onOpenAbout: () -> Unit,
    onCloseAbout: () -> Unit,
) {
    // Retour système (Android) ou Échap (bureau, web) : ferme « À propos ».
    BackHandler(enabled = aboutOpen) { onCloseAbout() }

    val snackbar = remember { SnackbarHostState() }
    // Veille du chrono pour toute l'application : les alertes jouent quel que soit l'écran.
    val alerts = remember { mutableStateListOf<String>() }
    ChronoWatcher(services, alerts)
    UpdateWatcher(services)
    val scope = rememberCoroutineScope()
    val showMessage: (String) -> Unit = { message -> scope.launch { snackbar.showSnackbar(message) } }
    // Hors des branches de mise en page : un redimensionnement ne rouvre pas l'accueil.
    CompositionLocalProvider(LocalMessages provides showMessage) { WelcomeDialog(services, onOpenAbout) }

    val language = Locale.current.language
    val today = services.clock.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
    val day = nav.day
    // Titre fidèle à ce qui est affiché : « Aujourd'hui » seulement si c'est vrai (Kairos 2).
    val title = when {
        aboutOpen -> stringResource(Res.string.title_about)
        destination == Destination.DAY && day != null && day != today -> stringResource(Res.string.title_day_other, Dates.long(day, language))
        destination == Destination.WEEK -> stringResource(Res.string.title_week_of, Dates.long(TaskStats.monday(nav.week ?: today), language))
        else -> stringResource(destination.title)
    }
    val topBar: @Composable () -> Unit = {
        TopAppBar(
            title = { Text(title) },
            navigationIcon = {
                if (aboutOpen) {
                    IconButton(onClick = onCloseAbout) {
                        Icon(KairosIcons.ArrowBack, contentDescription = stringResource(Res.string.action_back))
                    }
                } else if (layout != NavigationLayout.RAIL) {
                    // Sans rail, le logo porte l'identité en tête de la barre.
                    Image(KairosLogo, contentDescription = null, modifier = Modifier.padding(start = 16.dp).size(28.dp))
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
                    if (aboutOpen) AboutScreen() else DestinationScreen(destination, services, nav, onOpenDay, onNavigate, onOpenAbout)
                }
                AlertBanners(alerts, Modifier.align(Alignment.BottomCenter))
            }
        }
    }

    when (layout) {
        NavigationLayout.RAIL -> Row(Modifier.fillMaxSize()) {
            KairosNavigationRail(destination.takeUnless { aboutOpen }, onNavigate)
            Scaffold(topBar = topBar, snackbarHost = { SnackbarHost(snackbar) }) { padding -> content(Modifier.padding(padding)) }
        }
        NavigationLayout.BOTTOM_BAR -> Scaffold(
            topBar = topBar,
            snackbarHost = { SnackbarHost(snackbar) },
            bottomBar = { KairosNavigationBar(destination.takeUnless { aboutOpen }, onNavigate) },
        ) { padding -> content(Modifier.padding(padding)) }
        NavigationLayout.TOP_BAR -> Scaffold(
            topBar = {
                Column {
                    topBar()
                    KairosTopNavigation(destination.takeUnless { aboutOpen }, onNavigate)
                }
            },
            snackbarHost = { SnackbarHost(snackbar) },
        ) { padding -> content(Modifier.padding(padding)) }
    }
}

@Composable
private fun KairosNavigationRail(selected: Destination?, onNavigate: (Destination) -> Unit) {
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
            }
        },
    ) {
        Spacer(Modifier.height(8.dp))
        Destination.entries.forEach { dest ->
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
private fun KairosNavigationBar(selected: Destination?, onNavigate: (Destination) -> Unit) {
    NavigationBar {
        Destination.entries.forEach { dest ->
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
private fun KairosTopNavigation(selected: Destination?, onNavigate: (Destination) -> Unit) {
    val description = stringResource(Res.string.nav_navigation)
    Surface(color = MaterialTheme.colorScheme.surface) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .semantics { contentDescription = description }
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Destination.entries.forEach { dest ->
                val isSelected = dest == selected
                FilterChip(
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

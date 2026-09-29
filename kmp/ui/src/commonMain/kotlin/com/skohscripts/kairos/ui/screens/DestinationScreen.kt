package com.skohscripts.kairos.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.skohscripts.kairos.ui.generated.resources.Res
import com.skohscripts.kairos.ui.generated.resources.placeholder_body
import com.skohscripts.kairos.ui.generated.resources.placeholder_title
import com.skohscripts.kairos.ui.icons.KairosIcons
import com.skohscripts.kairos.ui.app.AppServices
import com.skohscripts.kairos.ui.day.DayScreen
import com.skohscripts.kairos.ui.navigation.Destination
import com.skohscripts.kairos.ui.navigation.NavState
import com.skohscripts.kairos.ui.notes.NotesScreen
import com.skohscripts.kairos.ui.stats.StatsScreen
import com.skohscripts.kairos.ui.week.WeekScreen
import kotlinx.datetime.LocalDate
import com.skohscripts.kairos.ui.settings.SettingsScreen
import org.jetbrains.compose.resources.stringResource

/** Contenu d'une destination (les cinq sont portées depuis le jalon M4). */
@Composable
fun DestinationScreen(
    destination: Destination,
    services: AppServices,
    nav: NavState,
    onOpenDay: (LocalDate?) -> Unit,
    onNavigate: (Destination) -> Unit,
    onOpenAbout: () -> Unit,
) {
    when (destination) {
        Destination.NOTES -> NotesScreen(services, onOpenTasks = { onNavigate(Destination.DAY) })
        Destination.DAY -> DayScreen(services, nav.day, onBackToToday = { onOpenDay(null) })
        Destination.WEEK -> WeekScreen(services, nav, onOpenDay)
        Destination.STATS -> StatsScreen(services)
        Destination.SETTINGS -> SettingsScreen(services, onOpenAbout)
    }
}

/** Bloc « En construction » d'une partie pas encore portée (Réglages à venir, jalon M5). */
@Composable
fun UnderConstruction() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.widthIn(max = 480.dp).padding(top = 32.dp),
    ) {
        Icon(
            KairosIcons.Construction,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(48.dp),
        )
        Text(stringResource(Res.string.placeholder_title), style = MaterialTheme.typography.titleMedium)
        Text(
            stringResource(Res.string.placeholder_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

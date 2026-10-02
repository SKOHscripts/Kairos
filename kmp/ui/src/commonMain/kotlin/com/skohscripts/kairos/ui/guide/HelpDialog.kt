package com.skohscripts.kairos.ui.guide

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.skohscripts.kairos.ui.theme.KairosTextButton
import com.skohscripts.kairos.ui.app.heading
import com.skohscripts.kairos.ui.generated.resources.Res
import com.skohscripts.kairos.ui.generated.resources.help_close
import com.skohscripts.kairos.ui.generated.resources.help_open_home
import com.skohscripts.kairos.ui.generated.resources.help_open_home_hint
import com.skohscripts.kairos.ui.generated.resources.help_open_team_tour
import com.skohscripts.kairos.ui.generated.resources.help_open_team_tour_hint
import com.skohscripts.kairos.ui.generated.resources.help_open_tour
import com.skohscripts.kairos.ui.generated.resources.help_open_tour_hint
import com.skohscripts.kairos.ui.generated.resources.help_title
import com.skohscripts.kairos.ui.icons.KairosIcons
import com.skohscripts.kairos.ui.navigation.NavEntry
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * Aide de l'écran courant [screen] (bouton « ? » de la barre du haut, docs/spec/accueil.md § Le bouton « ? ») : le texte du
 * sujet [topic], puis l'accès à l'Accueil et à la visite de l'espace [team] courant. Choisir une entrée ferme l'aide
 * avant d'agir ([onDismiss] est appelé d'abord par l'appelant).
 */
@Composable
fun HelpDialog(
    screen: NavEntry,
    topic: HelpTopic,
    team: Boolean,
    onOpenHome: () -> Unit,
    onStartTour: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(KairosIcons.Help, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
        title = { Text(stringResource(Res.string.help_title, stringResource(screen.title)), modifier = Modifier.heading()) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(topic.body), style = MaterialTheme.typography.bodyMedium)
                HelpEntry(KairosIcons.Info, Res.string.help_open_home, Res.string.help_open_home_hint, onOpenHome)
                if (team) {
                    HelpEntry(KairosIcons.PlayArrow, Res.string.help_open_team_tour, Res.string.help_open_team_tour_hint, onStartTour)
                } else {
                    HelpEntry(KairosIcons.PlayArrow, Res.string.help_open_tour, Res.string.help_open_tour_hint, onStartTour)
                }
            }
        },
        confirmButton = { KairosTextButton(onClick = onDismiss) { Text(stringResource(Res.string.help_close)) } },
    )
}

/** Ligne cliquable (≥ 48 dp : `ListItem` à deux lignes), fond transparent pour garder celui du dialogue. */
@Composable
private fun HelpEntry(icon: ImageVector, title: StringResource, hint: StringResource, onClick: () -> Unit) {
    ListItem(
        leadingContent = { Icon(icon, contentDescription = null) },
        headlineContent = { Text(stringResource(title)) },
        supportingContent = { Text(stringResource(hint)) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.clickable(role = Role.Button, onClick = onClick),
    )
}

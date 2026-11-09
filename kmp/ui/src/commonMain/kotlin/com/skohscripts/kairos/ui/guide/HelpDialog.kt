package com.skohscripts.kairos.ui.guide

import com.skohscripts.kairos.ui.generated.resources.gtd_open_hint
import com.skohscripts.kairos.ui.generated.resources.gtd_open
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import com.skohscripts.kairos.ui.generated.resources.feedback_action
import com.skohscripts.kairos.ui.generated.resources.feedback_hint
import com.skohscripts.kairos.ui.generated.resources.help_close
import com.skohscripts.kairos.ui.generated.resources.help_open_home
import com.skohscripts.kairos.ui.generated.resources.help_open_home_hint
import com.skohscripts.kairos.ui.generated.resources.help_open_team_tour
import com.skohscripts.kairos.ui.generated.resources.help_open_team_tour_hint
import com.skohscripts.kairos.ui.generated.resources.help_open_tour
import com.skohscripts.kairos.ui.generated.resources.help_open_tour_hint
import com.skohscripts.kairos.ui.generated.resources.help_open_discover
import com.skohscripts.kairos.ui.generated.resources.help_open_discover_hint
import com.skohscripts.kairos.ui.generated.resources.help_title
import com.skohscripts.kairos.ui.icons.KairosIcons
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * Aide de l'écran courant (bouton « ? » de la barre du haut, docs/spec/accueil.md § Le bouton « ? ») : le texte du
 * sujet [topic], puis l'accès à l'Accueil, à la visite de l'espace [team] courant, au hub « Découvrir Kairos » et à « Donner
 * un avis » (docs/spec/retours.md). Choisir une entrée ferme l'aide
 * avant d'agir ([onDismiss] est appelé d'abord par l'appelant).
 */
@Composable
fun HelpDialog(
    topic: HelpTopic,
    team: Boolean,
    onOpenHome: () -> Unit,
    onStartTour: () -> Unit,
    onDismiss: () -> Unit,
    /** Ouvre le hub « Découvrir Kairos » (docs/spec/visite-guidee.md). */
    onDiscover: () -> Unit = {},
    /** Ouvre « Donner un avis » (docs/spec/retours.md). */
    onFeedback: () -> Unit = {},
    /** Ouvre « La méthode GTD » (docs/spec/methode-gtd.md), espace Perso seulement. */
    onGtd: () -> Unit = {},
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(KairosIcons.Help, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
        title = { Text(stringResource(Res.string.help_title, stringResource(topic.screen)), modifier = Modifier.heading()) },
        text = {
            // Défile : sur un écran bas (téléphone en paysage, fenêtre réduite), les entrées restent atteignables.
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(topic.body), style = MaterialTheme.typography.bodyMedium)
                HelpEntry(KairosIcons.Home, Res.string.help_open_home, Res.string.help_open_home_hint, onOpenHome)
                if (team) {
                    HelpEntry(KairosIcons.PlayArrow, Res.string.help_open_team_tour, Res.string.help_open_team_tour_hint, onStartTour)
                } else {
                    HelpEntry(KairosIcons.PlayArrow, Res.string.help_open_tour, Res.string.help_open_tour_hint, onStartTour)
                }
                HelpEntry(KairosIcons.Layers, Res.string.help_open_discover, Res.string.help_open_discover_hint, onDiscover)
                // Le cycle GTD est celui de l'espace Perso, pas celui du manager.
                if (!team) HelpEntry(KairosIcons.Help, Res.string.gtd_open, Res.string.gtd_open_hint, onGtd)
                HelpEntry(KairosIcons.Feedback, Res.string.feedback_action, Res.string.feedback_hint, onFeedback)
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

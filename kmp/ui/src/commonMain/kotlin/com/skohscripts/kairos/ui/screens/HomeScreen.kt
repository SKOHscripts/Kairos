package com.skohscripts.kairos.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.skohscripts.kairos.ui.app.heading
import com.skohscripts.kairos.ui.generated.resources.Res
import com.skohscripts.kairos.ui.generated.resources.app_name
import com.skohscripts.kairos.ui.generated.resources.app_tagline
import com.skohscripts.kairos.ui.generated.resources.home_about
import com.skohscripts.kairos.ui.generated.resources.home_feature_capture_body
import com.skohscripts.kairos.ui.generated.resources.home_feature_capture_title
import com.skohscripts.kairos.ui.generated.resources.home_feature_do_body
import com.skohscripts.kairos.ui.generated.resources.home_feature_do_title
import com.skohscripts.kairos.ui.generated.resources.home_feature_qualify_body
import com.skohscripts.kairos.ui.generated.resources.home_feature_qualify_title
import com.skohscripts.kairos.ui.generated.resources.home_feature_stats_body
import com.skohscripts.kairos.ui.generated.resources.home_feature_stats_title
import com.skohscripts.kairos.ui.generated.resources.home_feature_time_body
import com.skohscripts.kairos.ui.generated.resources.home_feature_time_title
import com.skohscripts.kairos.ui.generated.resources.home_feature_week_body
import com.skohscripts.kairos.ui.generated.resources.home_feature_week_title
import com.skohscripts.kairos.ui.generated.resources.home_features_title
import com.skohscripts.kairos.ui.generated.resources.home_team_manager
import com.skohscripts.kairos.ui.generated.resources.home_team_member
import com.skohscripts.kairos.ui.generated.resources.home_team_open_settings
import com.skohscripts.kairos.ui.generated.resources.home_team_open_team
import com.skohscripts.kairos.ui.generated.resources.home_team_title
import com.skohscripts.kairos.ui.generated.resources.home_team_tour
import com.skohscripts.kairos.ui.generated.resources.home_tour
import com.skohscripts.kairos.ui.generated.resources.home_why_body
import com.skohscripts.kairos.ui.generated.resources.home_why_title
import com.skohscripts.kairos.ui.guide.GuideKind
import com.skohscripts.kairos.ui.guide.GuideTarget
import com.skohscripts.kairos.ui.icons.KairosIcons
import com.skohscripts.kairos.ui.icons.KairosLogo
import com.skohscripts.kairos.ui.navigation.Destination
import com.skohscripts.kairos.ui.navigation.TeamDestination
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/** Une fonctionnalité de l'Accueil : icône, titre, phrase, écran qu'elle ouvre. */
private class Feature(val icon: () -> ImageVector, val title: StringResource, val body: StringResource, val target: GuideTarget)

private val FEATURES = listOf(
    Feature({ KairosIcons.Notes }, Res.string.home_feature_capture_title, Res.string.home_feature_capture_body, GuideTarget.Personal(Destination.NOTES)),
    Feature({ KairosIcons.Edit }, Res.string.home_feature_qualify_title, Res.string.home_feature_qualify_body, GuideTarget.Personal(Destination.DAY)),
    Feature({ KairosIcons.Today }, Res.string.home_feature_do_title, Res.string.home_feature_do_body, GuideTarget.Personal(Destination.DAY)),
    Feature({ KairosIcons.Schedule }, Res.string.home_feature_time_title, Res.string.home_feature_time_body, GuideTarget.Personal(Destination.DAY)),
    Feature({ KairosIcons.DateRange }, Res.string.home_feature_week_title, Res.string.home_feature_week_body, GuideTarget.Personal(Destination.WEEK)),
    Feature({ KairosIcons.BarChart }, Res.string.home_feature_stats_title, Res.string.home_feature_stats_body, GuideTarget.Personal(Destination.STATS)),
)

/**
 * Page « Accueil » (docs/spec/accueil.md § La page « Accueil ») : la raison d'être de Kairos (l'unique bloc teinté
 * de l'écran), une carte cliquable par fonctionnalité, la carte « Travailler en équipe » (les deux rôles, manager et
 * membre), puis la visite guidée. [onOpen] va à l'écran d'une cible ; [teamEnabled] : la gestion d'équipe est activée
 * (la carte ouvre alors l'espace Équipe, sinon les Réglages, et la visite de l'espace Équipe est proposée).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HomeScreen(teamEnabled: Boolean, onOpen: (GuideTarget) -> Unit, onTour: (GuideKind) -> Unit, onAbout: () -> Unit) {
    Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), contentAlignment = Alignment.TopCenter) {
        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.widthIn(max = 720.dp).fillMaxWidth().padding(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Image(KairosLogo, contentDescription = null, modifier = Modifier.size(56.dp))
                Column {
                    Text(stringResource(Res.string.app_name), style = MaterialTheme.typography.headlineSmall)
                    Text(stringResource(Res.string.app_tagline), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.onPrimaryContainer),
                elevation = CardDefaults.cardElevation(0.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(Res.string.home_why_title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.heading())
                    Text(stringResource(Res.string.home_why_body), style = MaterialTheme.typography.bodyMedium)
                }
            }
            Text(stringResource(Res.string.home_features_title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.heading())
            FEATURES.forEach { feature ->
                OutlinedCard(onClick = { onOpen(feature.target) }, modifier = Modifier.fillMaxWidth()) {
                    ListItem(
                        leadingContent = { Icon(feature.icon(), contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                        headlineContent = { Text(stringResource(feature.title)) },
                        supportingContent = { Text(stringResource(feature.body)) },
                        trailingContent = { Icon(KairosIcons.ChevronRight, contentDescription = null) },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    )
                }
            }
            OutlinedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(KairosIcons.Groups, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text(stringResource(Res.string.home_team_title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.heading())
                    }
                    Text(stringResource(Res.string.home_team_manager), style = MaterialTheme.typography.bodyMedium)
                    Text(stringResource(Res.string.home_team_member), style = MaterialTheme.typography.bodyMedium)
                    OutlinedButton(onClick = {
                        onOpen(if (teamEnabled) GuideTarget.Team(TeamDestination.START) else GuideTarget.Personal(Destination.SETTINGS))
                    }) {
                        Text(stringResource(if (teamEnabled) Res.string.home_team_open_team else Res.string.home_team_open_settings))
                    }
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Button(onClick = { onTour(GuideKind.PERSONAL) }) { Text(stringResource(Res.string.home_tour)) }
                if (teamEnabled) OutlinedButton(onClick = { onTour(GuideKind.TEAM) }) { Text(stringResource(Res.string.home_team_tour)) }
                TextButton(onClick = onAbout) { Text(stringResource(Res.string.home_about)) }
            }
        }
    }
}

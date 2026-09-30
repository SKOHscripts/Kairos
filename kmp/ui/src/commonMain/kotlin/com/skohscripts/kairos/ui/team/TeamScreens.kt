package com.skohscripts.kairos.ui.team

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.skohscripts.kairos.ui.app.heading
import com.skohscripts.kairos.ui.generated.resources.Res
import com.skohscripts.kairos.ui.generated.resources.team_backlog_empty_body
import com.skohscripts.kairos.ui.generated.resources.team_backlog_empty_title
import com.skohscripts.kairos.ui.generated.resources.team_board_empty_body
import com.skohscripts.kairos.ui.generated.resources.team_board_empty_title
import com.skohscripts.kairos.ui.generated.resources.team_forecast_empty_body
import com.skohscripts.kairos.ui.generated.resources.team_forecast_empty_title
import com.skohscripts.kairos.ui.generated.resources.team_members_empty_body
import com.skohscripts.kairos.ui.generated.resources.team_members_empty_title
import com.skohscripts.kairos.ui.generated.resources.team_soon
import com.skohscripts.kairos.ui.icons.KairosIcons
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/*
 * Écrans de l'espace Équipe au jalon E1 (docs/spec/equipe.md § Jalons) : la
 * coquille à deux espaces est en place, le contenu arrive aux jalons E2 à E5.
 * Chaque écran n'est qu'un état vide ; il sera remplacé sur place.
 */

/** Suivi : le tableau de l'équipe, par membre et par état (E3). */
@Composable
fun TeamBoardScreen() =
    TeamEmptyState(KairosIcons.ViewKanban, Res.string.team_board_empty_title, Res.string.team_board_empty_body)

/** Backlog : les tâches d'équipe non assignées, triées par score (E3). */
@Composable
fun TeamBacklogScreen() =
    TeamEmptyState(KairosIcons.Stacks, Res.string.team_backlog_empty_title, Res.string.team_backlog_empty_body)

/** Équipe : les membres, leur capacité et leur charge (E2, E4). */
@Composable
fun TeamMembersScreen() =
    TeamEmptyState(KairosIcons.Groups, Res.string.team_members_empty_title, Res.string.team_members_empty_body)

/** Prévisions : simulations et scénarios (E5). */
@Composable
fun ForecastScreen() =
    TeamEmptyState(KairosIcons.Monitoring, Res.string.team_forecast_empty_title, Res.string.team_forecast_empty_body)

/**
 * État vide centré : icône, titre, ce que l'écran fera, « Disponible dans une
 * prochaine version. ». Colonne de 720 dp au plus ; aucune couleur de marque
 * (icône en `primary`, textes neutres), pas de bloc teinté.
 */
@Composable
private fun TeamEmptyState(icon: ImageVector, title: StringResource, body: StringResource) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.widthIn(max = 720.dp).fillMaxWidth(),
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(48.dp))
            Text(
                stringResource(title),
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.heading(),
            )
            Text(stringResource(body), style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
            Text(
                stringResource(Res.string.team_soon),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

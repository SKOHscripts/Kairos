package com.skohscripts.kairos.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.skohscripts.kairos.core.settings.FieldError
import com.skohscripts.kairos.ui.app.AppServices
import com.skohscripts.kairos.ui.app.heading
import com.skohscripts.kairos.ui.generated.resources.Res
import com.skohscripts.kairos.ui.generated.resources.setting_team_enabled_help
import com.skohscripts.kairos.ui.generated.resources.settings_section_team
import com.skohscripts.kairos.ui.team.ClearTeamDataButton
import org.jetbrains.compose.resources.stringResource

/**
 * Carte Équipe des Réglages (docs/spec/equipe.md § Activation), après
 * Apparence : l'interrupteur « Gestion d'équipe » (champ `team.enabled` du
 * formulaire, pris en compte à « Enregistrer » comme le reste) et sa phrase.
 *
 * [savedEnabled] est la valeur **enregistrée** : le nom de l'équipe et celui
 * du manager ne sont montrés que lorsque le mode est activé et enregistré,
 * pas dès que l'interrupteur est touché. En mode solo, la carte se réduit
 * à l'interrupteur : c'est la seule trace visible de l'espace Équipe. Mode
 * enregistré activé, la carte ajoute les trois seuils du suivi (sans
 * avancement, ballottée, limite d'en-cours, docs/spec/equipe-backlog-suivi.md
 * § Signaux), les cinq réglages de la charge (horizon, taux de focus, heures par
 * point, alerte de charge, tolérance d'affinité, docs/spec/equipe-charge.md), les quatre
 * réglages des prévisions (tirages, historique, échantillon minimum, seuil d'échéance en danger,
 * docs/spec/equipe-simulation.md) et
 * « Supprimer les données d'équipe… » la ferme ([ClearTeamDataButton]).
 */
@Composable
internal fun TeamSettingsCard(
    values: Map<String, String>,
    errors: Map<String, FieldError>,
    savedEnabled: Boolean,
    services: AppServices,
    onChange: (String, String) -> Unit,
) {
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(Res.string.settings_section_team), style = MaterialTheme.typography.titleMedium, modifier = Modifier.heading())
            Text(stringResource(Res.string.setting_team_enabled_help), style = MaterialTheme.typography.bodyMedium)
            SettingInput("team.enabled", values["team.enabled"].orEmpty(), errors["team.enabled"]) { onChange("team.enabled", it) }
            if (savedEnabled) {
                SettingInput("team.name", values["team.name"].orEmpty(), errors["team.name"]) { onChange("team.name", it) }
                SettingInput("team.managerName", values["team.managerName"].orEmpty(), errors["team.managerName"]) { onChange("team.managerName", it) }
                listOf(
                    "team.staleProgressDays", "team.churnThreshold", "team.wipLimit",
                    "team.horizonWeeks", "team.focusFactor", "team.hoursPerPoint", "team.loadWarnPercent", "team.affinityDays",
                    "team.simulationRuns", "team.historyWeeks", "team.minSamples", "team.deadlineRiskPercent",
                ).forEach { key ->
                    SettingInput(key, values[key].orEmpty(), errors[key]) { onChange(key, it) }
                }
                ClearTeamDataButton(services)
            }
        }
    }
}

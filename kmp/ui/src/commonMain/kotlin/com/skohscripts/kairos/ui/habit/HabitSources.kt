package com.skohscripts.kairos.ui.habit

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.skohscripts.kairos.core.habit.Claim
import com.skohscripts.kairos.core.habit.EvidenceLevel
import com.skohscripts.kairos.core.habit.HabitScience
import com.skohscripts.kairos.ui.app.heading
import com.skohscripts.kairos.ui.app.rememberLinkOpener
import com.skohscripts.kairos.ui.day.verticalScrollWithThumb
import com.skohscripts.kairos.ui.generated.resources.*
import com.skohscripts.kairos.ui.icons.KairosIcons
import com.skohscripts.kairos.ui.theme.KairosTextButton
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * Texte de chaque affirmation de [HabitScience.CLAIMS] (docs/spec/habitudes.md § Base scientifique), dans les six langues.
 * `HabitStringsTest` garde la correspondance : une affirmation sans texte, ou un texte sans affirmation, fait échouer.
 */
val CLAIM_TEXTS: Map<String, StringResource> = mapOf(
    "H1" to Res.string.habit_claim_h1,
    "H2" to Res.string.habit_claim_h2,
    "H3" to Res.string.habit_claim_h3,
    "H4" to Res.string.habit_claim_h4,
    "H5" to Res.string.habit_claim_h5,
    "H6" to Res.string.habit_claim_h6,
    "H7" to Res.string.habit_claim_h7,
    "H8" to Res.string.habit_claim_h8,
    "H9" to Res.string.habit_claim_h9,
    "H10" to Res.string.habit_claim_h10,
    "H11" to Res.string.habit_claim_h11,
    "H12" to Res.string.habit_claim_h12,
    "H13" to Res.string.habit_claim_h13,
)

private fun EvidenceLevel.label(): StringResource = when (this) {
    EvidenceLevel.SOLID -> Res.string.habit_level_solid
    EvidenceLevel.MODERATE -> Res.string.habit_level_moderate
    EvidenceLevel.WEAK -> Res.string.habit_level_weak
}

/**
 * « Sources scientifiques » (docs/spec/habitudes.md § Réglages, issue #284) : chaque affirmation, son niveau de preuve et ses
 * références complètes ; le DOI s'ouvre dans le navigateur, tout le reste est hors ligne. Ouvert depuis la carte Habitudes.
 */
@Composable
fun HabitSourcesDialog(onDismiss: () -> Unit) {
    val openLink = rememberLinkOpener()
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("habit-sources"),
        icon = { Icon(KairosIcons.Science, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
        title = { Text(stringResource(Res.string.habit_sources_title), modifier = Modifier.heading()) },
        text = {
            Column(Modifier.verticalScrollWithThumb(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(Res.string.habit_sources_intro), style = MaterialTheme.typography.bodyMedium)
                HabitScience.CLAIMS.forEach { claim -> ClaimBlock(claim, openLink) }
                HorizontalDivider()
                Text(stringResource(Res.string.habit_sources_not_claimed), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(stringResource(Res.string.habit_sources_translation), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = { KairosTextButton(onClick = onDismiss) { Text(stringResource(Res.string.help_close)) } },
    )
}

@Composable
private fun ClaimBlock(claim: Claim, openLink: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(stringResource(CLAIM_TEXTS.getValue(claim.id)), style = MaterialTheme.typography.titleSmall, modifier = Modifier.heading())
        Text(
            stringResource(Res.string.habit_level, stringResource(claim.level.label())),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        claim.refs.mapNotNull(HabitScience::reference).forEach { ref ->
            Column(Modifier.padding(start = 8.dp)) {
                Text(ref.citation, style = MaterialTheme.typography.bodySmall)
                KairosTextButton(onClick = { openLink(HabitScience.doiUrl(ref.doi)) }) {
                    Text(stringResource(Res.string.habit_doi, ref.doi), style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

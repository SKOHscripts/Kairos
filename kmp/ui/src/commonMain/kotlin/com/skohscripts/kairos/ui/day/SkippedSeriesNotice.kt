package com.skohscripts.kairos.ui.day

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.skohscripts.kairos.core.engine.SkippedSeries
import com.skohscripts.kairos.ui.generated.resources.Res
import com.skohscripts.kairos.ui.generated.resources.skipped_ignore
import com.skohscripts.kairos.ui.generated.resources.skipped_ignore_for
import com.skohscripts.kairos.ui.generated.resources.skipped_recreate_for_many
import com.skohscripts.kairos.ui.generated.resources.skipped_recreate_for_one
import com.skohscripts.kairos.ui.generated.resources.skipped_recreate_many
import com.skohscripts.kairos.ui.generated.resources.skipped_recreate_one
import com.skohscripts.kairos.ui.generated.resources.skipped_series_many
import com.skohscripts.kairos.ui.generated.resources.skipped_series_one
import com.skohscripts.kairos.ui.icons.KairosIcons
import com.skohscripts.kairos.ui.theme.KairosSpacing
import com.skohscripts.kairos.ui.theme.KairosTextButton
import org.jetbrains.compose.resources.stringResource

/**
 * Avis des mois sautés d'une série « le N du mois » (docs/spec/recurrence.md § Avis des mois sautés, issue #111) : une ligne
 * discrète, contour sans remplissage (le bloc teinté de l'écran reste « Maintenant »). L'afficher n'écrit rien ; seuls les
 * deux boutons agissent. Chaque bouton est nommé « libellé : série » pour que plusieurs avis se distinguent au lecteur d'écran.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SkippedSeriesNotice(series: SkippedSeries, onRecreate: () -> Unit, onIgnore: () -> Unit) {
    val one = series.months.size == 1
    val text = if (one) {
        stringResource(Res.string.skipped_series_one, series.title)
    } else {
        stringResource(Res.string.skipped_series_many, series.months.size, series.title)
    }
    val recreate = stringResource(if (one) Res.string.skipped_recreate_one else Res.string.skipped_recreate_many)
    val recreateFor = stringResource(if (one) Res.string.skipped_recreate_for_one else Res.string.skipped_recreate_for_many, series.title)
    val ignoreFor = stringResource(Res.string.skipped_ignore_for, series.title)
    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(KairosSpacing.s),
            itemVerticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(start = KairosSpacing.l, end = KairosSpacing.s, top = KairosSpacing.xs, bottom = KairosSpacing.xs),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(KairosSpacing.m),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(vertical = KairosSpacing.s),
            ) {
                Icon(KairosIcons.Repeat, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                Text(text, style = MaterialTheme.typography.bodyMedium)
            }
            Row {
                KairosTextButton(onClick = onRecreate, modifier = Modifier.semantics { contentDescription = recreateFor }) { Text(recreate) }
                KairosTextButton(onClick = onIgnore, modifier = Modifier.semantics { contentDescription = ignoreFor }) {
                    Text(stringResource(Res.string.skipped_ignore))
                }
            }
        }
    }
}

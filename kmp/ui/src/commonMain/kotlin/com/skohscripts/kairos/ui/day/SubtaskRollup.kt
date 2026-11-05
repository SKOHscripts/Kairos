package com.skohscripts.kairos.ui.day

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.skohscripts.kairos.core.engine.TimeTracking
import com.skohscripts.kairos.core.model.KairosSnapshot
import com.skohscripts.kairos.core.model.TaskStatus
import com.skohscripts.kairos.core.model.TaskTree
import com.skohscripts.kairos.core.stats.SplitAdvice
import com.skohscripts.kairos.ui.generated.resources.Res
import com.skohscripts.kairos.ui.generated.resources.rollup_planned
import com.skohscripts.kairos.ui.generated.resources.rollup_spent
import com.skohscripts.kairos.ui.generated.resources.rollup_unestimated_many
import com.skohscripts.kairos.ui.generated.resources.rollup_unestimated_one
import com.skohscripts.kairos.ui.generated.resources.split_advice_action
import com.skohscripts.kairos.ui.generated.resources.split_advice_bias
import com.skohscripts.kairos.ui.generated.resources.split_advice_none
import com.skohscripts.kairos.ui.generated.resources.split_advice_spread
import com.skohscripts.kairos.ui.generated.resources.split_advice_unknown
import com.skohscripts.kairos.ui.icons.KairosIcons
import com.skohscripts.kairos.ui.theme.KairosButtonIconPadding
import com.skohscripts.kairos.ui.theme.KairosTextButton
import kotlin.math.round
import kotlin.time.Instant
import org.jetbrains.compose.resources.stringResource

/**
 * L'agrégat d'une mère en une ligne (docs/spec/sous-taches.md § Agrégation) : « 1 h 30 prévues · 1 sans durée · 45 min
 * passées », chaque partie absente quand elle est nulle ; `null` si tout l'est.
 */
@Composable
internal fun rollupText(rollup: TaskTree.Rollup): String? {
    val parts = buildList {
        if (rollup.plannedMinutes > 0) add(stringResource(Res.string.rollup_planned, duration(rollup.plannedMinutes)))
        when {
            rollup.unestimated == 1 -> add(stringResource(Res.string.rollup_unestimated_one))
            rollup.unestimated > 1 -> add(stringResource(Res.string.rollup_unestimated_many, rollup.unestimated))
        }
        if (rollup.spentMinutes > 0) add(stringResource(Res.string.rollup_spent, duration(rollup.spentMinutes)))
    }
    return parts.takeIf { it.isNotEmpty() }?.joinToString(" · ")
}

/**
 * Agrégat d'une tâche hors de la vue Jour (fiche d'équipe) : durées estimées, ou temps réel d'une faite ; pas de durée
 * apprise (celles de l'équipe ne valent que pour le backlog).
 */
internal fun teamRollup(taskId: Long, snapshot: KairosSnapshot, now: Instant): TaskTree.Rollup? {
    val live = snapshot.tasks.filter { it.status != TaskStatus.ARCHIVED }
    val spent = TimeTracking.spentMinutesByTask(snapshot.workSessions, now, live)
    return TaskTree.rollup(taskId, live, { t -> t.estimatedMinutes?.takeIf { it > 0 } ?: spent[t.id]?.takeIf { it > 0 && t.status == TaskStatus.DONE } }, spent)
}

/**
 * Conseil « découpe-la en sous-tâches » sous les points de la fiche (docs/spec/sous-taches.md § Conseil de découpage) : ce
 * que l'historique de ce niveau dit, chiffres écrits. Icône et texte neutres : un conseil, pas une erreur (charte).
 */
@Composable
internal fun SplitAdviceLine(advice: SplitAdvice.Advice, onSplit: (() -> Unit)? = null) {
    val text = when (advice.reason) {
        SplitAdvice.Reason.BIAS -> stringResource(Res.string.split_advice_bias, advice.points, ratio(advice.bias ?: 1.0), advice.samples)
        SplitAdvice.Reason.SPREAD -> stringResource(Res.string.split_advice_spread, advice.points, duration(advice.shortest ?: 0), duration(advice.longest ?: 0))
        SplitAdvice.Reason.NOT_ENOUGH_HISTORY ->
            if (advice.samples == 0) stringResource(Res.string.split_advice_none, advice.points)
            else stringResource(Res.string.split_advice_unknown, advice.samples, advice.points)
    }
    Column {
        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(KairosIcons.Layers, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
            Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        // Ouvre « Découpage et enchaînement » et place le curseur dans « Nouvelles sous-tâches ».
        if (onSplit != null) {
            KairosTextButton(onClick = onSplit, contentPadding = KairosButtonIconPadding) { Text(stringResource(Res.string.split_advice_action)) }
        }
    }
}

/** « 1.80 » : deux décimales, point décimal, comme le biais des statistiques. */
private fun ratio(x: Double): String {
    val r = round(x * 100) / 100
    val whole = r.toLong()
    val cents = round((r - whole) * 100).toInt()
    return "$whole.${cents.toString().padStart(2, '0')}"
}

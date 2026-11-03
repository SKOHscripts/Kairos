package com.skohscripts.kairos.ui.day

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.skohscripts.kairos.ui.app.expandedState
import com.skohscripts.kairos.ui.icons.KairosIcons
import com.skohscripts.kairos.ui.theme.KairosButtonIconPadding
import com.skohscripts.kairos.ui.theme.KairosTextButton
import com.skohscripts.kairos.ui.generated.resources.learned_model_neighbour
import com.skohscripts.kairos.ui.generated.resources.learned_model_neighbours
import com.skohscripts.kairos.ui.generated.resources.learned_model_use
import com.skohscripts.kairos.core.engine.DurationEstimator
import com.skohscripts.kairos.core.engine.model.ModelGate
import com.skohscripts.kairos.core.engine.model.ModelParams
import com.skohscripts.kairos.core.model.LearningSettings
import com.skohscripts.kairos.ui.generated.resources.Res
import com.skohscripts.kairos.ui.generated.resources.model_basis_points
import com.skohscripts.kairos.ui.generated.resources.model_basis_type
import com.skohscripts.kairos.ui.generated.resources.model_basis_type_points
import com.skohscripts.kairos.ui.generated.resources.why_learned_model
import com.skohscripts.kairos.ui.generated.resources.why_learned_model_few_neighbours
import com.skohscripts.kairos.ui.generated.resources.why_learned_model_few_tasks
import com.skohscripts.kairos.ui.generated.resources.why_learned_model_neighbour
import com.skohscripts.kairos.ui.generated.resources.why_learned_model_not_better
import com.skohscripts.kairos.ui.generated.resources.why_learned_model_regression
import org.jetbrains.compose.resources.stringResource

/**
 * La provenance d'une estimation du modèle, en phrase (docs/spec/estimateur-modele.md § Interface) : fiable, la durée,
 * l'intervalle et les tâches proches (trois au plus, avec leur durée réelle) ou la base de la régression ; peu fiable, la
 * raison (trop peu de tâches faites, de tâches proches, ou la garde). Jamais un chiffre sans sa preuve.
 */
@Composable
internal fun modelProvenance(estimate: DurationEstimator.Estimate, learning: LearningSettings): String {
    val evidence = estimate.evidence ?: return stringResource(Res.string.why_learned_model_few_tasks, 0, ModelParams.MIN_CORPUS)
    val low = duration(estimate.low ?: estimate.minutes)
    val high = duration(estimate.high ?: estimate.minutes)
    if (!estimate.reliable) {
        val minNeighbours = ModelParams.minNeighbours(learning.minSamples)
        return when {
            evidence.corpus < ModelParams.MIN_CORPUS -> stringResource(Res.string.why_learned_model_few_tasks, evidence.corpus, ModelParams.MIN_CORPUS)
            evidence.basis == DurationEstimator.Basis.NEIGHBOURS && evidence.neighbours.size < minNeighbours ->
                stringResource(Res.string.why_learned_model_few_neighbours, evidence.neighbours.size, minNeighbours)
            evidence.gate.reason == ModelGate.Reason.TOO_FEW_SAMPLES ->
                stringResource(Res.string.why_learned_model_few_tasks, evidence.gate.evaluated, ModelParams.GATE_MIN)
            else -> stringResource(Res.string.why_learned_model_not_better)
        }
    }
    return when (evidence.basis) {
        DurationEstimator.Basis.NEIGHBOURS -> {
            val names = evidence.neighbours.take(3).map { stringResource(Res.string.why_learned_model_neighbour, it.title, duration(it.minutes)) }
            stringResource(Res.string.why_learned_model, duration(estimate.minutes), low, high, estimate.samples, names.joinToString(", "))
        }
        DurationEstimator.Basis.REGRESSION ->
            stringResource(Res.string.why_learned_model_regression, duration(estimate.minutes), low, high, evidence.corpus, regressionBasis(evidence))
    }
}

/** « type « Rédaction », 5 points » : ce qu'a lu la régression. */
@Composable
internal fun regressionBasis(evidence: DurationEstimator.Evidence): String {
    val type = evidence.taskType
    val points = evidence.points
    return when {
        type != null && points != null -> stringResource(Res.string.model_basis_type_points, type, points)
        type != null -> stringResource(Res.string.model_basis_type, type)
        else -> stringResource(Res.string.model_basis_points, points ?: 0)
    }
}

/**
 * Dans la fiche, sous le champ des minutes vide (docs/spec/estimateur-modele.md § Interface) : fiable, la puce « Utiliser 45 min
 * (entre 30 et 60) », qui ne remplit le champ que sur action, et la section repliable « Tâches proches » (titre, durée réelle,
 * ressemblance) ou la phrase de la régression ; peu fiable, la raison, sans action.
 */
@Composable
internal fun ModelSuggestion(estimate: DurationEstimator.Estimate, learning: LearningSettings, onUse: (Int) -> Unit) {
    if (!estimate.reliable) {
        Text(modelProvenance(estimate, learning), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    KairosTextButton(onClick = { onUse(estimate.minutes) }, contentPadding = KairosButtonIconPadding) {
        Icon(KairosIcons.Model, contentDescription = null, modifier = Modifier.size(18.dp))
        Text(
            stringResource(Res.string.learned_model_use, duration(estimate.minutes), duration(estimate.low ?: estimate.minutes), duration(estimate.high ?: estimate.minutes)),
            modifier = Modifier.padding(start = 6.dp),
        )
    }
    val evidence = estimate.evidence ?: return
    if (evidence.basis == DurationEstimator.Basis.REGRESSION) {
        Text(modelProvenance(estimate, learning), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    var open by rememberSaveable { mutableStateOf(false) }
    KairosTextButton(onClick = { open = !open }, modifier = Modifier.expandedState(open), contentPadding = KairosButtonIconPadding) {
        Icon(if (open) KairosIcons.ExpandLess else KairosIcons.ExpandMore, contentDescription = null)
        Text(stringResource(Res.string.learned_model_neighbours, evidence.neighbours.size), modifier = Modifier.padding(start = 6.dp))
    }
    if (open) {
        Column(Modifier.padding(start = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            evidence.neighbours.forEach {
                Text(
                    stringResource(Res.string.learned_model_neighbour, it.title, duration(it.minutes), it.similarityPercent),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

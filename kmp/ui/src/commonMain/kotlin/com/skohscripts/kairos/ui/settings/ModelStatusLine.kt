package com.skohscripts.kairos.ui.settings

import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import kotlinx.datetime.TimeZone
import com.skohscripts.kairos.ui.generated.resources.settings_review_button
import com.skohscripts.kairos.ui.theme.KairosOutlinedButton
import com.skohscripts.kairos.ui.app.EstimateReviewDialog
import com.skohscripts.kairos.core.engine.model.EstimateChanges
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import com.skohscripts.kairos.core.engine.DurationEstimator
import com.skohscripts.kairos.core.engine.model.ModelGate
import com.skohscripts.kairos.core.engine.model.ModelParams
import com.skohscripts.kairos.ui.app.AppServices
import com.skohscripts.kairos.ui.generated.resources.Res
import com.skohscripts.kairos.ui.generated.resources.learning_model_status_few
import com.skohscripts.kairos.ui.generated.resources.learning_model_status_not_better
import com.skohscripts.kairos.ui.generated.resources.learning_model_status_ok
import com.skohscripts.kairos.ui.generated.resources.learning_model_status_unverified
import org.jetbrains.compose.resources.stringResource
import kotlin.math.floor

/**
 * L'état de la vérification du modèle, sous son interrupteur (docs/spec/estimateur-modele.md § Interface) : corpus trop court,
 * garde pas encore jouable, erreur médiane comparée à celle de la durée par défaut, ou « non appliqué ». Lu sur les données
 * **enregistrées** (l'espace Perso), comme le reste de l'application.
 */
@Composable
internal fun ModelStatusLine(services: AppServices) {
    val snapshot by services.repository.snapshot.collectAsState()
    val status = remember(snapshot) { DurationEstimator.modelStatus(snapshot, services.clock.now(), timeZone = TimeZone.currentSystemDefault()) } ?: return
    val gate = status.gate
    fun percent(x: Double) = floor(x * 100.0 + 0.5).toInt()
    val text = when {
        status.corpus < ModelParams.MIN_CORPUS -> stringResource(Res.string.learning_model_status_few, status.corpus, ModelParams.MIN_CORPUS)
        gate.reason == ModelGate.Reason.TOO_FEW_SAMPLES -> stringResource(Res.string.learning_model_status_unverified, gate.evaluated, ModelParams.GATE_MIN)
        gate.passed -> stringResource(Res.string.learning_model_status_ok, gate.evaluated, percent(gate.modelError), percent(gate.defaultError))
        else -> stringResource(Res.string.learning_model_status_not_better, gate.evaluated, percent(gate.modelError), percent(gate.defaultError))
    }
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    // Estimations saisies que le modèle conteste (besoin 13) : la revue, depuis la carte.
    val proposals = remember(snapshot) { EstimateChanges.proposals(snapshot, services.clock.now(), TimeZone.currentSystemDefault()) }
    var review by remember { mutableStateOf(false) }
    if (proposals.isNotEmpty()) {
        KairosOutlinedButton(onClick = { review = true }) { Text(stringResource(Res.string.settings_review_button, proposals.size)) }
    }
    val scope = rememberCoroutineScope()
    if (review) EstimateReviewDialog(services, emptyList(), scope) { review = false }
}

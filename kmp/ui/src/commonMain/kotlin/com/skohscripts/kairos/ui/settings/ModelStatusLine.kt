package com.skohscripts.kairos.ui.settings

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
import com.skohscripts.kairos.core.forecast.Survival
import com.skohscripts.kairos.ui.generated.resources.settings_linger_learned
import com.skohscripts.kairos.ui.generated.resources.settings_linger_pending
import com.skohscripts.kairos.ui.generated.resources.settings_linger_info
import com.skohscripts.kairos.core.model.Settings
import com.skohscripts.kairos.core.engine.model.ModelGate
import com.skohscripts.kairos.core.engine.model.ModelParams
import com.skohscripts.kairos.ui.app.AppServices
import com.skohscripts.kairos.ui.generated.resources.Res
import com.skohscripts.kairos.ui.generated.resources.learning_model_coverage
import com.skohscripts.kairos.ui.generated.resources.learning_model_coverage_pending
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
    val text = modelStatusText(status)
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    // Fourchette vérifiée (docs/spec/previsions-taches.md § Étape A) : dite dès que la garde a ses candidats.
    if (gate.reason != ModelGate.Reason.TOO_FEW_SAMPLES && status.corpus >= ModelParams.MIN_CORPUS) {
        Text(
            if (gate.calibrated) {
                stringResource(Res.string.learning_model_coverage, gate.evaluated, gate.covered)
            } else {
                stringResource(Res.string.learning_model_coverage_pending, gate.evaluated, ModelParams.CALIBRATION_MIN)
            },
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    // Estimations saisies que le modèle conteste (besoin 13) : la revue, depuis la carte.
    // L'espace Perso seulement, comme le dialogue : une tâche d'équipe n'y paraît jamais.
    val personal by services.repository.personalSnapshot.collectAsState()
    val proposals = remember(personal) { EstimateChanges.proposals(personal, services.clock.now(), TimeZone.currentSystemDefault()) }
    var review by remember { mutableStateOf(false) }
    if (proposals.isNotEmpty()) {
        KairosOutlinedButton(onClick = { review = true }) { Text(stringResource(Res.string.settings_review_button, proposals.size)) }
    }
    if (review) EstimateReviewDialog(services, emptyList()) { review = false }
}

/** Pourcentage arrondi d'une erreur relative (0,31 → 31). */
internal fun percent(x: Double): Int = floor(x * 100.0 + 0.5).toInt()

/**
 * La phrase d'état du modèle : ce qui manque, la vérification réussie ou non. Partagée par les Réglages et la carte « Ce que
 * Kairos apprend » du bilan (docs/spec/bilan.md) : les deux ne disent jamais autre chose.
 */
@Composable
internal fun modelStatusText(status: DurationEstimator.Companion.ModelStatus): String {
    val gate = status.gate
    return when {
        // Ce qui manque, en nombre de tâches (2026-11-03) : le compte restant d'abord, puis l'avancement.
        status.corpus < ModelParams.MIN_CORPUS ->
            stringResource(Res.string.learning_model_status_few, ModelParams.MIN_CORPUS - status.corpus, status.corpus, ModelParams.MIN_CORPUS)
        gate.reason == ModelGate.Reason.TOO_FEW_SAMPLES ->
            stringResource(Res.string.learning_model_status_unverified, ModelParams.GATE_MIN - gate.evaluated, gate.evaluated, ModelParams.GATE_MIN)
        gate.passed -> stringResource(Res.string.learning_model_status_ok, gate.evaluated, percent(gate.modelError), percent(gate.defaultError))
        else -> stringResource(Res.string.learning_model_status_not_better, gate.evaluated, percent(gate.modelError), percent(gate.defaultError))
    }
}

/**
 * L'échéance implicite calculée du rythme, sous son champ (docs/spec/taches-qui-trainent.md § Échéance apprise), pour info :
 * champ vide ([manual] faux), la valeur appliquée ou le repli faute d'historique ; valeur saisie, ce que le rythme
 * donnerait (rien sans historique). Calculée sur les données **enregistrées** de l'espace Perso.
 */
@Composable
internal fun LingerStatusLine(services: AppServices, manual: Boolean) {
    val personal by services.repository.personalSnapshot.collectAsState()
    val learned = remember(personal) { Survival.lingerDays(personal, services.clock.now(), TimeZone.currentSystemDefault()) }
    val text = when {
        manual -> learned?.let { stringResource(Res.string.settings_linger_info, it) } ?: return
        learned != null -> stringResource(Res.string.settings_linger_learned, learned)
        else -> stringResource(Res.string.settings_linger_pending, Settings.DEFAULT_IMPLICIT_DEADLINE_DAYS)
    }
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

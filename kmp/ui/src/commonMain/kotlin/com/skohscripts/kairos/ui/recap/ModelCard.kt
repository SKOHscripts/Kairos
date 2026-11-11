package com.skohscripts.kairos.ui.recap

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.skohscripts.kairos.core.engine.DurationEstimator
import com.skohscripts.kairos.core.engine.model.ModelGate
import com.skohscripts.kairos.core.engine.model.ModelParams
import com.skohscripts.kairos.core.model.KairosSnapshot
import com.skohscripts.kairos.core.recap.ModelReview
import com.skohscripts.kairos.ui.app.AppServices
import com.skohscripts.kairos.ui.app.EstimateReviewDialog
import com.skohscripts.kairos.ui.app.expandedState
import com.skohscripts.kairos.ui.day.Dates
import com.skohscripts.kairos.ui.generated.resources.Res
import com.skohscripts.kairos.ui.generated.resources.model_bias
import com.skohscripts.kairos.ui.generated.resources.model_card_computing
import com.skohscripts.kairos.ui.generated.resources.model_card_hint
import com.skohscripts.kairos.ui.generated.resources.model_card_off
import com.skohscripts.kairos.ui.generated.resources.model_card_title
import com.skohscripts.kairos.ui.generated.resources.model_contested
import com.skohscripts.kairos.ui.generated.resources.model_history_few
import com.skohscripts.kairos.ui.generated.resources.model_history_row
import com.skohscripts.kairos.ui.generated.resources.model_history_summary
import com.skohscripts.kairos.ui.generated.resources.model_history_title
import com.skohscripts.kairos.ui.generated.resources.model_kpi_corpus
import com.skohscripts.kairos.ui.generated.resources.model_kpi_corpus_value
import com.skohscripts.kairos.ui.generated.resources.model_kpi_coverage
import com.skohscripts.kairos.ui.generated.resources.model_kpi_error
import com.skohscripts.kairos.ui.generated.resources.model_kpi_period
import com.skohscripts.kairos.ui.generated.resources.model_source_model
import com.skohscripts.kairos.ui.generated.resources.model_source_series
import com.skohscripts.kairos.ui.generated.resources.model_source_title
import com.skohscripts.kairos.ui.generated.resources.model_sources
import com.skohscripts.kairos.ui.generated.resources.recap_detail
import com.skohscripts.kairos.ui.generated.resources.settings_review_button
import com.skohscripts.kairos.ui.generated.resources.stats_percent
import com.skohscripts.kairos.ui.generated.resources.stats_sample
import com.skohscripts.kairos.ui.generated.resources.stats_unreliable
import com.skohscripts.kairos.ui.icons.KairosIcons
import com.skohscripts.kairos.ui.settings.modelStatusText
import com.skohscripts.kairos.ui.settings.percent
import com.skohscripts.kairos.ui.stats.BarRow
import com.skohscripts.kairos.ui.stats.Muted
import com.skohscripts.kairos.ui.stats.Panel
import com.skohscripts.kairos.ui.team.rememberComputed
import com.skohscripts.kairos.ui.theme.KairosButtonIconPadding
import com.skohscripts.kairos.ui.theme.KairosOutlinedButton
import com.skohscripts.kairos.ui.theme.KairosSpacing
import com.skohscripts.kairos.ui.theme.KairosTextButton
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import org.jetbrains.compose.resources.stringResource
import kotlin.math.round

/**
 * Carte « Ce que Kairos apprend » du bilan (docs/spec/bilan.md § Ce que Kairos apprend) : où en est le modèle d'estimation
 * des durées, ce que la période lui a appris, et comment il a convergé. Le calcul (le modèle reconstruit à chaque fin de
 * semaine) se fait hors de l'affichage ; « Calcul… » en attendant.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ModelCard(services: AppServices, snapshot: KairosSnapshot, start: LocalDate, end: LocalDate, today: LocalDate, language: String) {
    val zone = remember { TimeZone.currentSystemDefault() }
    val computed = rememberComputed(snapshot, start, end, today) { ModelReview.build(snapshot, start, end, services.clock.now(), zone) }
    Panel(KairosIcons.Model, stringResource(Res.string.model_card_title), stringResource(Res.string.model_card_hint)) {
        val review = computed.value
        if (review == null) {
            Muted(stringResource(Res.string.model_card_computing))
            return@Panel
        }
        // Coupé dans les Réglages : une ligne qui dit où le rallumer, pas de chiffres.
        val status = review.status ?: run {
            Muted(stringResource(Res.string.model_card_off))
            return@Panel
        }
        val gate = status.gate
        val verified = status.corpus >= ModelParams.MIN_CORPUS && gate.reason != ModelGate.Reason.TOO_FEW_SAMPLES
        var open by remember(start) { mutableStateOf(false) }
        var reviewing by remember { mutableStateOf(false) }
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(KairosSpacing.m),
                verticalArrangement = Arrangement.spacedBy(KairosSpacing.s),
                maxItemsInEachRow = if (maxWidth >= 560.dp) 4 else 2,
            ) {
                val cell = Modifier.weight(1f)
                Figure(stringResource(Res.string.model_kpi_corpus_value, status.corpus, review.learnedInPeriod), stringResource(Res.string.model_kpi_corpus), cell)
                // Sans vérification jouable, pas de pourcentage : un chiffre seul tromperait.
                Figure(
                    if (verified) pct(percent(gate.modelError)) else "—",
                    stringResource(Res.string.model_kpi_error, if (verified) pct(percent(gate.defaultError)) else "—"),
                    cell, warn = verified && !gate.passed,
                )
                Figure(if (verified) "${gate.covered} / ${gate.evaluated}" else "—", stringResource(Res.string.model_kpi_coverage), cell)
                Figure(if (review.checked > 0) "${review.inside} / ${review.checked}" else "—", stringResource(Res.string.model_kpi_period), cell)
            }
        }
        Text(modelStatusText(status), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        KairosTextButton(onClick = { open = !open }, modifier = Modifier.expandedState(open), contentPadding = KairosButtonIconPadding) {
            Icon(if (open) KairosIcons.ExpandLess else KairosIcons.ExpandMore, contentDescription = null)
            Text(stringResource(Res.string.recap_detail), modifier = Modifier.padding(start = 4.dp))
        }
        if (!open) return@Panel
        History(review.history, language)
        review.bias?.let { b ->
            val sample = stringResource(if (b.reliable) Res.string.stats_sample else Res.string.stats_unreliable, b.count)
            Text(stringResource(Res.string.model_bias, twoDecimals(b.ratio), sample), style = MaterialTheme.typography.bodyMedium)
        }
        val sources = listOfNotNull(
            review.bySource[DurationEstimator.Source.MODEL]?.let { stringResource(Res.string.model_source_model, it) },
            review.bySource[DurationEstimator.Source.SERIES]?.let { stringResource(Res.string.model_source_series, it) },
            review.bySource[DurationEstimator.Source.TITLE]?.let { stringResource(Res.string.model_source_title, it) },
        )
        if (sources.isNotEmpty()) Text(stringResource(Res.string.model_sources, sources.joinToString(" · ")), style = MaterialTheme.typography.bodyMedium)
        if (review.contested > 0) {
            Text(stringResource(Res.string.model_contested, review.contested), style = MaterialTheme.typography.bodyMedium)
            KairosOutlinedButton(onClick = { reviewing = true }) { Text(stringResource(Res.string.settings_review_button, review.contested)) }
        }
        if (reviewing) EstimateReviewDialog(services, emptyList()) { reviewing = false }
    }
}

/**
 * La convergence : une barre par fin de semaine, l'erreur du modèle telle qu'elle aurait été vérifiée ce jour-là, avec
 * celle de la durée par défaut et le nombre de tâches apprises ; « pas encore assez de tâches » sous le seuil.
 */
@Composable
private fun History(points: List<ModelReview.Point>, language: String) {
    if (points.isEmpty()) return
    Section(stringResource(Res.string.model_history_title), points.size, counted = false) {
        val measured = points.filter { it.corpus >= ModelParams.MIN_CORPUS && it.gate.reason != ModelGate.Reason.TOO_FEW_SAMPLES }
        if (measured.size >= 2) {
            val first = measured.first()
            val last = measured.last()
            val weeks = ((last.date.toEpochDays() - first.date.toEpochDays()) / 7).toInt()
            Muted(
                stringResource(
                    Res.string.model_history_summary, weeks,
                    pct(percent(first.gate.modelError)),
                    pct(percent(last.gate.modelError)),
                    pct(percent(last.gate.defaultError)),
                ),
            )
        }
        points.forEach { p ->
            val label = Dates.dayMonth(p.date, language)
            if (p in measured) {
                BarRow(
                    label, p.gate.modelError.toFloat(),
                    stringResource(Res.string.model_history_row, pct(percent(p.gate.modelError)), pct(percent(p.gate.defaultError)), p.corpus),
                    warn = !p.gate.passed,
                    // Étroit : la date passe au-dessus, la barre garde de la place à côté de sa valeur.
                    stackedWhenNarrow = true,
                )
            } else {
                BarRow(label, 0f, stringResource(Res.string.model_history_few, p.corpus), stackedWhenNarrow = true)
            }
        }
    }
}

/** ×1.30 : point décimal, comme le biais des statistiques. */
private fun twoDecimals(x: Double): String {
    val cents = round(x * 100).toLong()
    return "${cents / 100}.${(cents % 100).toString().padStart(2, '0')}"
}

/** « 31 % » avec une espace insécable : « 52 » et « % » ne se séparent jamais en fin de ligne. */
@Composable
private fun pct(value: Int): String = stringResource(Res.string.stats_percent, value).replace(' ', '\u00A0')

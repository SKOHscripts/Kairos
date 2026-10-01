package com.skohscripts.kairos.ui.team.forecast

import com.skohscripts.kairos.ui.theme.KairosButtonIconPadding
import com.skohscripts.kairos.ui.theme.KairosOutlinedButton
import com.skohscripts.kairos.ui.theme.KairosTextButton
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.skohscripts.kairos.core.model.KairosSnapshot
import com.skohscripts.kairos.core.team.forecast.DeadlineOutlook
import com.skohscripts.kairos.core.team.forecast.ForecastModel
import com.skohscripts.kairos.core.team.forecast.ForecastResult
import com.skohscripts.kairos.core.team.forecast.ForecastScope
import com.skohscripts.kairos.core.team.forecast.LateSet
import com.skohscripts.kairos.ui.day.Dates
import com.skohscripts.kairos.ui.generated.resources.Res
import com.skohscripts.kairos.ui.generated.resources.action_cancel
import com.skohscripts.kairos.ui.generated.resources.action_ok
import com.skohscripts.kairos.ui.generated.resources.forecast_bottleneck_empty
import com.skohscripts.kairos.ui.generated.resources.forecast_bottleneck_hint
import com.skohscripts.kairos.ui.generated.resources.forecast_bottleneck_share
import com.skohscripts.kairos.ui.generated.resources.forecast_bottleneck_title
import com.skohscripts.kairos.ui.generated.resources.forecast_by_answer
import com.skohscripts.kairos.ui.generated.resources.forecast_by_date
import com.skohscripts.kairos.ui.generated.resources.forecast_by_hint
import com.skohscripts.kairos.ui.generated.resources.forecast_by_throughput_note
import com.skohscripts.kairos.ui.generated.resources.forecast_by_title
import com.skohscripts.kairos.ui.generated.resources.forecast_by_unavailable
import com.skohscripts.kairos.ui.generated.resources.forecast_computed_at
import com.skohscripts.kairos.ui.generated.resources.forecast_criticality_empty
import com.skohscripts.kairos.ui.generated.resources.forecast_criticality_hint
import com.skohscripts.kairos.ui.generated.resources.forecast_criticality_title
import com.skohscripts.kairos.ui.generated.resources.forecast_criticality_value
import com.skohscripts.kairos.ui.generated.resources.forecast_deadline
import com.skohscripts.kairos.ui.generated.resources.forecast_deadline_risk
import com.skohscripts.kairos.ui.generated.resources.forecast_deadlines_empty
import com.skohscripts.kairos.ui.generated.resources.forecast_deadlines_hint
import com.skohscripts.kairos.ui.generated.resources.forecast_deadlines_more
import com.skohscripts.kairos.ui.generated.resources.forecast_deadlines_title
import com.skohscripts.kairos.ui.generated.resources.forecast_finish_hint
import com.skohscripts.kairos.ui.generated.resources.forecast_finish_title
import com.skohscripts.kairos.ui.generated.resources.forecast_histogram_description
import com.skohscripts.kairos.ui.generated.resources.forecast_histogram_week
import com.skohscripts.kairos.ui.generated.resources.forecast_honesty_draws
import com.skohscripts.kairos.ui.generated.resources.forecast_honesty_seed
import com.skohscripts.kairos.ui.generated.resources.forecast_interrupted
import com.skohscripts.kairos.ui.generated.resources.forecast_masked
import com.skohscripts.kairos.ui.generated.resources.forecast_nothing
import com.skohscripts.kairos.ui.generated.resources.forecast_nothing_qualify
import com.skohscripts.kairos.ui.generated.resources.forecast_outcome_hint
import com.skohscripts.kairos.ui.generated.resources.forecast_outcome_late_0
import com.skohscripts.kairos.ui.generated.resources.forecast_outcome_late_1
import com.skohscripts.kairos.ui.generated.resources.forecast_outcome_late_2
import com.skohscripts.kairos.ui.generated.resources.forecast_outcome_late_3
import com.skohscripts.kairos.ui.generated.resources.forecast_outcome_many
import com.skohscripts.kairos.ui.generated.resources.forecast_outcome_none
import com.skohscripts.kairos.ui.generated.resources.forecast_outcome_one
import com.skohscripts.kairos.ui.generated.resources.forecast_outcome_title
import com.skohscripts.kairos.ui.generated.resources.forecast_out_of_horizon
import com.skohscripts.kairos.ui.generated.resources.forecast_outcome_no_deadline
import com.skohscripts.kairos.ui.generated.resources.forecast_percentile
import com.skohscripts.kairos.ui.generated.resources.forecast_percentile_out
import com.skohscripts.kairos.ui.generated.resources.forecast_stale
import com.skohscripts.kairos.ui.generated.resources.forecast_task_gone
import com.skohscripts.kairos.ui.generated.resources.forecast_unreliable_samples
import com.skohscripts.kairos.ui.generated.resources.forecast_unreliable_weeks
import com.skohscripts.kairos.ui.generated.resources.forecast_deadlines_less
import com.skohscripts.kairos.ui.generated.resources.forecast_quoted
import com.skohscripts.kairos.ui.generated.resources.member_unknown
import com.skohscripts.kairos.ui.icons.KairosIcons
import com.skohscripts.kairos.ui.stats.BarRow
import com.skohscripts.kairos.ui.stats.BarTrack
import com.skohscripts.kairos.ui.stats.Panel
import com.skohscripts.kairos.ui.team.Flag
import com.skohscripts.kairos.ui.team.toPickerDate
import com.skohscripts.kairos.ui.team.toPickerMillis
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.stringResource

/*
 * Panneaux de résultat des Prévisions (docs/spec/equipe-simulation.md § Interface). Tous lisent un ForecastResult ;
 * aucun ne calcule : les chiffres viennent du moteur. Un résultat n'est jamais une date seule (P50 · P85 · P95), et
 * ce qui n'est pas certain n'est jamais écrit « 100 % ».
 */

/** Noms des tâches et des membres d'un résultat, lus dans la base courante (une tâche supprimée depuis est dite telle). */
internal class ForecastNames(private val snapshot: KairosSnapshot) {
    private val tasks = snapshot.tasks.associateBy { it.id }

    @Composable
    fun task(id: Long): String = tasks[id]?.title ?: stringResource(Res.string.forecast_task_gone)

    @Composable
    fun member(id: Long): String = snapshot.members.firstOrNull { it.id == id }?.name ?: stringResource(Res.string.member_unknown)
}

/** Une mention à contour avec une icône : la forme dit l'état, jamais une teinte (pas d'ambre). */
@Composable
internal fun Notice(text: String, icon: ImageVector, modifier: Modifier = Modifier) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier.border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.small).padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun Muted(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** « périmé » et « interrompu » : les deux états d'un résultat qui n'est plus (ou pas encore) celui des données. */
@Composable
internal fun ResultBanners(result: ForecastResult, stale: Boolean, language: String) {
    if (!stale && !result.interrupted) return
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        if (stale) Notice(stringResource(Res.string.forecast_stale), KairosIcons.Refresh)
        if (result.interrupted) {
            Notice(
                stringResource(Res.string.forecast_interrupted, groupedNumber(result.runs, language), groupedNumber(result.requestedRuns, language)),
                KairosIcons.Stop,
            )
        }
    }
}

/**
 * Honnêteté (docs/spec/equipe-simulation.md § Honnêteté) : nombre de tirages, graine, source des données, « peu
 * fiable » sous le minimum, date du calcul. Sans elle, un chiffre n'est pas un résultat.
 */
@Composable
internal fun HonestyNote(result: ForecastResult, computedAt: kotlin.time.Instant, zone: TimeZone, language: String, modifier: Modifier = Modifier) {
    val local = computedAt.toLocalDateTime(zone)
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        HorizontalDivider()
        Text(
            stringResource(Res.string.forecast_honesty_draws, groupedNumber(result.runs, language)) + " · " +
                stringResource(Res.string.forecast_honesty_seed, result.seed.toString()),
            style = MaterialTheme.typography.bodySmall,
        )
        Text(sourceText(result.source, result.model), style = MaterialTheme.typography.bodySmall)
        if (!result.reliable) {
            val throughput = result.model == ForecastModel.THROUGHPUT
            val required = if (throughput) minOf(result.source.minimum, result.source.windowWeeks) else result.source.minimum
            Flag(
                stringResource(if (throughput) Res.string.forecast_unreliable_weeks else Res.string.forecast_unreliable_samples, required),
                flagged = true,
            )
        }
        Muted(stringResource(Res.string.forecast_computed_at, dateLabel(local.date, local.date, language), Dates.time(local, language)))
    }
}

/** Date de fin : P50, P85, P95 en phrases, histogramme par semaine, honnêteté. */
@Composable
internal fun FinishPanel(run: ForecastRun, zone: TimeZone, language: String) {
    val result = run.result
    Panel(KairosIcons.Schedule, stringResource(Res.string.forecast_finish_title), stringResource(Res.string.forecast_finish_hint)) {
        val finish = result.finish
        if (finish == null) {
            NothingToForecast(run)
        } else {
            val dates = listOf(50 to finish.p50, 85 to finish.p85, 95 to finish.p95)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                dates.forEach { (percent, date) ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.heightIn(min = 32.dp)) {
                        Text("P$percent", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(36.dp))
                        Text(percentileSentence(percent, date, result.day, language), style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
            if (finish.outOfHorizon > 0) {
                Muted(stringResource(Res.string.forecast_out_of_horizon, groupedNumber(finish.outOfHorizon, language), groupedNumber(result.runs, language)))
            }
            if (finish.histogram.isNotEmpty()) {
                val description = buildString {
                    append(stringResource(Res.string.forecast_histogram_description))
                    append(' ')
                    finish.histogram.forEach {
                        append(stringResource(Res.string.forecast_histogram_week, dateLabel(it.weekStart, result.day, language), percentLabel(percentOf(it.draws, result.runs))))
                        append(". ")
                    }
                    dates.forEach { (percent, date) -> append(percentileSentence(percent, date, result.day, language)).append(". ") }
                }
                Histogram(
                    finish.histogram, result.runs,
                    dates.map { (percent, date) -> HistogramMark("P$percent", date) },
                    language, description, Modifier.fillMaxWidth().padding(top = 8.dp),
                )
            }
        }
        HonestyNote(result, run.computedAt, zone, language)
    }
}

@Composable
private fun percentileSentence(percent: Int, date: LocalDate?, day: LocalDate, language: String): String =
    if (date == null) {
        stringResource(Res.string.forecast_percentile_out, percentLabel(percent))
    } else {
        stringResource(Res.string.forecast_percentile, percentLabel(percent), dateLabel(date, day, language))
    }

/** « Rien à prévoir » (périmètre vide), avec les tâches à qualifier si le backlog était demandé. */
@Composable
private fun NothingToForecast(run: ForecastRun) {
    Text(stringResource(Res.string.forecast_nothing), style = MaterialTheme.typography.bodyLarge)
    val spec = run.spec
    val backlog = spec.scope == ForecastScope.AssignedAndBacklog || spec.options.includeBacklog || spec.scope is ForecastScope.Tasks
    if (backlog && run.result.toQualify > 0) {
        Muted(stringResource(Res.string.forecast_nothing_qualify, run.result.toQualify))
    }
}

/** « Combien d'ici… » : une date au `DatePicker`, la réponse en « au moins N tâches (p %) ». */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FinishedByPanel(result: ForecastResult, language: String) {
    Panel(KairosIcons.DateRange, stringResource(Res.string.forecast_by_title), stringResource(Res.string.forecast_by_hint)) {
        var chosen by remember { mutableStateOf<LocalDate?>(null) }
        var picking by remember { mutableStateOf(false) }
        val date = chosen ?: result.finishDate(50) ?: result.day.plus(DatePeriod(days = 28))
        KairosOutlinedButton(onClick = { picking = true }, contentPadding = KairosButtonIconPadding) {
            Icon(KairosIcons.DateRange, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(
                stringResource(Res.string.forecast_by_date, dateLabel(date, result.day, language)),
                modifier = Modifier.padding(start = 8.dp),
            )
        }
        val answer = result.finishedBy(date)
        if (answer == null) {
            Muted(stringResource(Res.string.forecast_by_unavailable))
        } else {
            Text(
                stringResource(
                    Res.string.forecast_by_answer,
                    dateLabel(date, result.day, language),
                    atLeastText(listOf(answer.atLeast95 to 95, answer.atLeast85 to 85, answer.atLeast50 to 50)),
                    answer.total,
                ),
                style = MaterialTheme.typography.bodyLarge,
            )
        }
        if (result.model == ForecastModel.THROUGHPUT) Muted(stringResource(Res.string.forecast_by_throughput_note))
        if (picking) {
            DateChoice(date, onPick = { chosen = it; picking = false }, onDismiss = { picking = false })
        }
    }
}

/** Le sélecteur de date Material 3 (une date), en dialogue : « OK » / « Annuler ». */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DateChoice(initial: LocalDate, onPick: (LocalDate) -> Unit, onDismiss: () -> Unit) {
    val state = rememberDatePickerState(initialSelectedDateMillis = initial.toPickerMillis(), initialDisplayedMonthMillis = initial.toPickerMillis())
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            KairosTextButton(enabled = state.selectedDateMillis != null, onClick = { state.selectedDateMillis?.let { onPick(it.toPickerDate()) } }) {
                Text(stringResource(Res.string.action_ok))
            }
        },
        dismissButton = { KairosTextButton(onClick = onDismiss) { Text(stringResource(Res.string.action_cancel)) } },
    ) {
        DatePicker(state)
    }
}

/** Échéances : probabilité de tenir chacune, les plus fragiles d'abord ; « en danger » sous le seuil (contour + icône). */
@Composable
internal fun DeadlinesPanel(result: ForecastResult, names: ForecastNames, riskPercent: Int, language: String) {
    Panel(KairosIcons.Schedule, stringResource(Res.string.forecast_deadlines_title), stringResource(Res.string.forecast_deadlines_hint, riskPercent)) {
        if (result.deadlines.isEmpty()) {
            Muted(stringResource(Res.string.forecast_deadlines_empty))
            return@Panel
        }
        var all by remember { mutableStateOf(false) }
        val shown = if (all) result.deadlines else result.deadlines.take(DEADLINES_SHOWN)
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            shown.forEach { DeadlineRow(it, names.task(it.taskId), result.day, language) }
        }
        if (result.deadlines.size > DEADLINES_SHOWN) {
            KairosTextButton(onClick = { all = !all }) {
                Text(stringResource(if (all) Res.string.forecast_deadlines_less else Res.string.forecast_deadlines_more, result.deadlines.size - DEADLINES_SHOWN))
            }
        }
    }
}

private const val DEADLINES_SHOWN = 8

@Composable
private fun DeadlineRow(d: DeadlineOutlook, title: String, day: LocalDate, language: String) {
    val percent = percentLabel(onTimePercent(d.lateDraws, d.draws))
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Muted(stringResource(Res.string.forecast_deadline, dateLabel(d.deadline, day, language)))
        }
        Flag(
            if (d.atRisk) stringResource(Res.string.forecast_deadline_risk, percent) else percent,
            flagged = d.atRisk,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

/** Une ligne « libellé, barre, valeur » : la valeur est toujours écrite, la barre ne la porte pas seule. */
@Composable
private fun ProportionRow(label: String, fraction: Float, value: String) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            BarTrack(fraction, Modifier.weight(1f))
            Text(value, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.End, modifier = Modifier.widthIn(min = 56.dp))
        }
    }
}

/** Criticité : les 10 tâches les plus souvent en retard. */
@Composable
internal fun CriticalityPanel(result: ForecastResult, names: ForecastNames) {
    Panel(KairosIcons.Warning, stringResource(Res.string.forecast_criticality_title), stringResource(Res.string.forecast_criticality_hint)) {
        val critical = result.mostCritical(10)
        if (critical.isEmpty()) {
            Muted(stringResource(if (result.deadlines.isEmpty()) Res.string.forecast_deadlines_empty else Res.string.forecast_criticality_empty))
            return@Panel
        }
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            critical.forEach { d ->
                val percent = latePercent(d.lateDraws, d.draws)
                ProportionRow(names.task(d.taskId), percent / 100f, stringResource(Res.string.forecast_criticality_value, percentLabel(percent)))
            }
        }
    }
}

/** Goulot : les membres qui finissent le dernier, et la part des tirages où ils le font. */
@Composable
internal fun BottleneckPanel(result: ForecastResult, names: ForecastNames) {
    Panel(KairosIcons.Person, stringResource(Res.string.forecast_bottleneck_title), stringResource(Res.string.forecast_bottleneck_hint)) {
        if (result.bottleneck.isEmpty()) {
            Muted(stringResource(Res.string.forecast_bottleneck_empty))
            return@Panel
        }
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            result.bottleneck.forEach { share ->
                val percent = percentOf(share.draws, share.total)
                ProportionRow(names.member(share.memberId), percent / 100f, stringResource(Res.string.forecast_bottleneck_share, percentLabel(percent)))
            }
        }
    }
}

/** Issue la plus probable : 0 / 1 / 2 / 3 retards ou plus, et l'ensemble de retards le plus fréquent. */
@Composable
internal fun OutcomePanel(result: ForecastResult, names: ForecastNames) {
    Panel(KairosIcons.Balance, stringResource(Res.string.forecast_outcome_title), stringResource(Res.string.forecast_outcome_hint)) {
        if (result.deadlines.isEmpty() || result.lateDistribution.isEmpty()) {
            Muted(stringResource(Res.string.forecast_outcome_no_deadline))
            return@Panel
        }
        val labels = listOf(
            Res.string.forecast_outcome_late_0, Res.string.forecast_outcome_late_1, Res.string.forecast_outcome_late_2, Res.string.forecast_outcome_late_3,
        )
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            result.lateDistribution.forEachIndexed { index, draws ->
                val percent = percentOf(draws, result.runs)
                BarRow(stringResource(labels[index]), percent / 100f, percentLabel(percent), stackedWhenNarrow = true)
            }
        }
        result.mostLikelyLateSet?.let { set ->
            Text(outcomeSentence(set, result.runs, names), style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Composable
private fun outcomeSentence(set: LateSet, runs: Int, names: ForecastNames): String {
    val share = percentLabel(percentOf(set.draws, runs))
    val titles = set.taskIds.map { stringResource(Res.string.forecast_quoted, names.task(it)) }
    return when {
        set.taskIds.isEmpty() -> stringResource(Res.string.forecast_outcome_none, share)
        set.taskIds.size == 1 -> stringResource(Res.string.forecast_outcome_one, titles.first(), share)
        else -> {
            val listed = titles.take(3).joinToString(", ") + if (titles.size > 3) " +${titles.size - 3}" else ""
            stringResource(Res.string.forecast_outcome_many, titles.size, listed, share)
        }
    }
}

/** Modèle par débit : les quatre panneaux par tâche sont remplacés par la phrase qui dit pourquoi. */
@Composable
internal fun MaskedPanels() {
    OutlinedCard(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
            Icon(KairosIcons.Info, contentDescription = null, modifier = Modifier.size(20.dp))
            Text(stringResource(Res.string.forecast_masked), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

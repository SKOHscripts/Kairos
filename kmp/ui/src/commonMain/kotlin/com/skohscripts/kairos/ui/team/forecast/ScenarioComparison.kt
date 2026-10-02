package com.skohscripts.kairos.ui.team.forecast

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import com.skohscripts.kairos.core.team.forecast.ForecastResult
import com.skohscripts.kairos.ui.generated.resources.Res
import com.skohscripts.kairos.ui.generated.resources.cmp_best
import com.skohscripts.kairos.ui.generated.resources.cmp_hint
import com.skohscripts.kairos.ui.generated.resources.cmp_no_deadlines
import com.skohscripts.kairos.ui.generated.resources.cmp_row_all
import com.skohscripts.kairos.ui.generated.resources.cmp_row_late
import com.skohscripts.kairos.ui.generated.resources.cmp_row_load
import com.skohscripts.kairos.ui.generated.resources.cmp_row_p50
import com.skohscripts.kairos.ui.generated.resources.cmp_row_p85
import com.skohscripts.kairos.ui.generated.resources.cmp_skipped
import com.skohscripts.kairos.ui.generated.resources.cmp_title
import com.skohscripts.kairos.ui.generated.resources.forecast_out_of_horizon_short
import com.skohscripts.kairos.ui.icons.KairosIcons
import com.skohscripts.kairos.ui.stats.Panel
import com.skohscripts.kairos.ui.team.Flag
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.stringResource
import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * Comparaison (docs/spec/equipe-simulation.md § Scénarios) : la situation réelle et les scénarios cochés côte à côte,
 * **mêmes tirages** pour toutes les colonnes. Le meilleur de chaque ligne porte une étoile (jamais la couleur seule) ;
 * à égalité de toutes les colonnes, aucune n'est marquée. Sous 600 dp le tableau défile horizontalement **dans** sa
 * carte (les libellés de ligne restent en place), jamais la page.
 */
@Composable
internal fun ScenarioComparison(run: ComparisonRun, stale: Boolean, realLabel: String, zone: TimeZone, language: String) {
    val columns = run.columns
    val first = run.columns.first().result
    val hasDeadlines = columns.any { it.result.deadlines.isNotEmpty() }
    val day = first.day
    val dateText = @Composable { date: kotlinx.datetime.LocalDate? ->
        if (date == null) stringResource(Res.string.forecast_out_of_horizon_short) else dateLabel(date, day, language)
    }

    // Les lignes : libellé, texte de chaque colonne, et les colonnes marquées « meilleur ».
    val p50 = columns.map { it.result.finishDate(50) }
    val p85 = columns.map { it.result.finishDate(85) }
    val late = columns.map { it.result.expectedLate }
    val all = columns.map { onTimeAllPercent(it.result) }
    val load = columns.map { it.loadRatePercent }
    val rows = buildList {
        add(CompareRow(stringResource(Res.string.cmp_row_p50), p50.map { dateText(it) }, ComparisonBest.lowest(p50.map { it?.toEpochDays()?.toDouble() })))
        add(CompareRow(stringResource(Res.string.cmp_row_p85), p85.map { dateText(it) }, ComparisonBest.lowest(p85.map { it?.toEpochDays()?.toDouble() })))
        if (hasDeadlines) {
            add(CompareRow(stringResource(Res.string.cmp_row_late), late.map { decimal1(it, language) }, ComparisonBest.lowest(late.map { (it * 10).roundToInt().toDouble() })))
            add(CompareRow(stringResource(Res.string.cmp_row_all), all.map { percentLabel(it) }, ComparisonBest.highest(all.map { it.toDouble() })))
        }
        add(
            CompareRow(
                stringResource(Res.string.cmp_row_load),
                load.map { it?.let { rate -> percentLabel(rate.roundToInt()) } ?: "—" },
                ComparisonBest.lowest(load.map { it?.roundToInt()?.toDouble() }),
            ),
        )
    }
    val best = stringResource(Res.string.cmp_best)

    Panel(KairosIcons.CompareArrows, stringResource(Res.string.cmp_title), stringResource(Res.string.cmp_hint)) {
        ResultBanners(first, stale, language)
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val labelWidth = 120.dp
            val cell = max(112.dp, (maxWidth - labelWidth) / columns.size)
            Row(Modifier.fillMaxWidth()) {
                // Colonne des libellés : fixe.
                Column(Modifier.width(labelWidth)) {
                    Spacer48()
                    rows.forEach { row ->
                        HorizontalDivider()
                        Row(Modifier.height(ROW_HEIGHT), verticalAlignment = Alignment.CenterVertically) {
                            Text(row.label, style = MaterialTheme.typography.labelLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
                // Colonnes de données : défilent ensemble, dans la carte.
                Column(Modifier.weight(1f).horizontalScroll(rememberScrollState())) {
                    Row(Modifier.height(ROW_HEIGHT), verticalAlignment = Alignment.CenterVertically) {
                        columns.forEach { column ->
                            Text(
                                column.name ?: realLabel, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.width(cell).padding(end = 8.dp),
                            )
                        }
                    }
                    rows.forEach { row ->
                        HorizontalDivider(Modifier.width(cell * columns.size))
                        Row(Modifier.height(ROW_HEIGHT), verticalAlignment = Alignment.CenterVertically) {
                            row.cells.forEachIndexed { index, text ->
                                Row(Modifier.width(cell).padding(end = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(text, style = MaterialTheme.typography.bodyMedium, fontWeight = if (index in row.best) FontWeight.Medium else FontWeight.Normal)
                                    if (index in row.best) Icon(KairosIcons.StarFilled, contentDescription = best, modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
        if (!hasDeadlines) Text(stringResource(Res.string.cmp_no_deadlines), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        columns.filter { it.skipped > 0 }.forEach { column ->
            Flag(stringResource(Res.string.cmp_skipped, column.name ?: realLabel, column.skipped), flagged = true)
        }
        HonestyNote(first, run.computedAt, zone, language)
    }
}

private val ROW_HEIGHT = 52.dp

@Composable
private fun Spacer48() {
    androidx.compose.foundation.layout.Spacer(Modifier.height(ROW_HEIGHT))
}

private class CompareRow(val label: String, val cells: List<String>, val best: Set<Int>)

/** Probabilité de tout tenir, en pour cent entier arrondi vers le bas (jamais 100 % s'il existe un tirage en retard). */
internal fun onTimeAllPercent(result: ForecastResult): Int {
    if (result.runs == 0) return 100
    val percent = floor(result.allOnTimeProbability * 100 + 1e-9).toInt()
    return if (result.allOnTimeProbability < 1.0) percent.coerceAtMost(99) else percent
}

/** Les colonnes « meilleures » d'une ligne : voir [lowest] et [highest]. */
internal object ComparisonBest {
    /** Indices des plus petites valeurs ; `null` = pire que tout ; vide si toutes les colonnes sont à égalité. */
    fun lowest(values: List<Double?>): Set<Int> {
        val best = values.filterNotNull().minOrNull() ?: return emptySet()
        return marked(values, best)
    }

    /** Indices des plus grandes valeurs ; `null` = pire que tout ; vide si toutes les colonnes sont à égalité. */
    fun highest(values: List<Double?>): Set<Int> {
        val best = values.filterNotNull().maxOrNull() ?: return emptySet()
        return marked(values, best)
    }

    private fun marked(values: List<Double?>, best: Double): Set<Int> {
        val indices = values.indices.filter { values[it] == best }.toSet()
        return if (indices.size == values.size) emptySet() else indices
    }
}

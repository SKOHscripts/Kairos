package com.skohscripts.kairos.ui.team.forecast

import androidx.compose.runtime.Composable
import androidx.compose.ui.text.intl.Locale
import com.skohscripts.kairos.core.team.forecast.DataSourceKind
import com.skohscripts.kairos.core.team.forecast.ForecastModel
import com.skohscripts.kairos.core.team.forecast.SourceInfo
import com.skohscripts.kairos.ui.day.Dates
import com.skohscripts.kairos.ui.generated.resources.Res
import com.skohscripts.kairos.ui.generated.resources.forecast_by_first_many
import com.skohscripts.kairos.ui.generated.resources.forecast_by_first_one
import com.skohscripts.kairos.ui.generated.resources.forecast_by_next
import com.skohscripts.kairos.ui.generated.resources.forecast_percent
import com.skohscripts.kairos.ui.generated.resources.forecast_source_default
import com.skohscripts.kairos.ui.generated.resources.forecast_source_history
import com.skohscripts.kairos.ui.generated.resources.forecast_source_mixed
import com.skohscripts.kairos.ui.generated.resources.forecast_throughput_default
import com.skohscripts.kairos.ui.generated.resources.forecast_throughput_history
import com.skohscripts.kairos.ui.generated.resources.forecast_throughput_mixed
import com.skohscripts.kairos.ui.team.dateSpan
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.resources.stringResource
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

/*
 * Mise en texte des prévisions : pourcentages entiers (jamais « 100 % » pour ce qui n'est pas certain), nombres
 * groupés (« 3 200 » en français, « 3,200 » en anglais), dates courtes (année seulement si elle diffère de celle du
 * jour de calcul), phrases de source des données.
 */

private fun english(language: String) = language.lowercase().startsWith("en")

/** « 3 200 » (espace insécable, que la ligne ne coupe pas) ou « 3,200 ». */
internal fun groupedNumber(value: Int, language: String): String {
    val digits = kotlin.math.abs(value).toString()
    val separator = if (english(language)) "," else ' '.toString()
    val grouped = digits.reversed().chunked(3).joinToString(separator).reversed()
    return if (value < 0) "-$grouped" else grouped
}

/** Part de [part] sur [total] en pour cent, arrondie au plus proche (0 si [total] est nul). */
internal fun percentOf(part: Int, total: Int): Int = if (total <= 0) 0 else (100.0 * part / total).roundToInt()

/** Probabilité de tenir : arrondie **vers le bas**, et jamais 100 % s'il existe un tirage en retard. */
internal fun onTimePercent(lateDraws: Int, draws: Int): Int {
    if (draws <= 0) return 100
    val percent = floor(100.0 * (draws - lateDraws) / draws).toInt()
    return if (lateDraws > 0) percent.coerceAtMost(99) else percent
}

/** Part des tirages en retard : arrondie **vers le haut**, et jamais 0 % s'il existe un tirage en retard. */
internal fun latePercent(lateDraws: Int, draws: Int): Int {
    if (draws <= 0 || lateDraws <= 0) return 0
    return ceil(100.0 * lateDraws / draws).toInt().coerceAtMost(100)
}

/** « 38 % » (« 38% » en anglais). */
@Composable
internal fun percentLabel(percent: Int): String = stringResource(Res.string.forecast_percent, percent)

/** « 9 oct. » (« Oct 9 »), avec l'année si elle diffère de celle de [today]. */
internal fun dateLabel(date: LocalDate, today: LocalDate, language: String): String = dateSpan(date, date, today, language).first

/** Nombre moyen (« 0,8 » ou « 0.8 ») à une décimale. */
internal fun decimal1(value: Double, language: String): String {
    val rounded = (value * 10).roundToInt() / 10.0
    val text = Dates.number(rounded)
    return if (english(language)) text else text.replace('.', ',')
}

/**
 * La source des données d'un résultat, en une phrase (docs/spec/equipe-simulation.md § Honnêteté) :
 * « historique : 38 tâches faites sur 12 semaines », « hypothèse par défaut : 0,8 à 2 fois l’estimation, le plus
 * souvent 1 », ou le mélange des deux sous le minimum. Le modèle par débit compte des tâches finies par semaine.
 */
@Composable
internal fun sourceText(source: SourceInfo, model: ForecastModel): String {
    val throughput = model == ForecastModel.THROUGHPUT
    return when (source.kind) {
        DataSourceKind.HISTORY -> stringResource(
            if (throughput) Res.string.forecast_throughput_history else Res.string.forecast_source_history,
            source.samples, source.windowWeeks,
        )
        DataSourceKind.MIXED -> stringResource(
            if (throughput) Res.string.forecast_throughput_mixed else Res.string.forecast_source_mixed,
            source.samples, source.windowWeeks, source.minimum,
        )
        DataSourceKind.DEFAULT -> stringResource(if (throughput) Res.string.forecast_throughput_default else Res.string.forecast_source_default)
    }
}

/**
 * « au moins 14 tâches (85 %), 18 (50 %) » : la réponse à « combien d'ici… ». Les [counts] vont du plus sûr au moins
 * sûr, avec leur pourcentage de chances.
 */
@Composable
internal fun atLeastText(counts: List<Pair<Int, Int>>): String {
    val (firstCount, firstPercent) = counts.first()
    // « 0 tâche » et « 1 tâche » en français, « 0 tasks » et « 1 task » en anglais.
    val singular = if (english(Locale.current.language)) firstCount == 1 else firstCount <= 1
    val first = stringResource(
        if (singular) Res.string.forecast_by_first_one else Res.string.forecast_by_first_many,
        firstCount, percentLabel(firstPercent),
    )
    val rest = counts.drop(1).map { (count, percent) -> stringResource(Res.string.forecast_by_next, count, percentLabel(percent)) }
    return (listOf(first) + rest).joinToString(", ")
}

package com.skohscripts.kairos.ui.team

import androidx.compose.runtime.Composable
import androidx.compose.ui.text.intl.Locale
import com.skohscripts.kairos.ui.day.Dates
import com.skohscripts.kairos.ui.generated.resources.Res
import com.skohscripts.kairos.ui.generated.resources.backlog_no_category
import com.skohscripts.kairos.ui.generated.resources.load_horizon_many
import com.skohscripts.kairos.ui.generated.resources.load_horizon_one
import com.skohscripts.kairos.ui.generated.resources.load_hours
import com.skohscripts.kairos.ui.generated.resources.load_percent
import com.skohscripts.kairos.ui.generated.resources.load_unestimated_many
import com.skohscripts.kairos.ui.generated.resources.load_unestimated_one
import com.skohscripts.kairos.ui.generated.resources.load_weeks_plural
import com.skohscripts.kairos.ui.generated.resources.load_weeks_singular
import org.jetbrains.compose.resources.stringResource
import kotlin.math.round
import kotlin.math.roundToInt

/*
 * Mise en texte des chiffres de charge (docs/spec/equipe-charge.md § Interface) : heures à une décimale au plus
 * (« 96 h », « 13,4 h »), taux entier (« 117 % »), virgule décimale en français. Le code commun n'a pas de
 * formatage localisé : la langue vient de `Locale.current`, comme pour les dates.
 */

private fun english(language: String) = language.lowercase().startsWith("en")

/** « 96 », « 13,4 » (français) ou « 13.4 » (anglais) : une décimale au plus. */
internal fun decimalText(value: Double, language: String): String {
    val text = Dates.number(value)
    return if (english(language)) text else text.replace('.', ',')
}

/** « 96 h », « 13,4 h ». */
@Composable
internal fun hoursText(hours: Double): String = stringResource(Res.string.load_hours, decimalText(hours, Locale.current.language))

/** « 117 % » ; « — » quand le taux est indéfini (capacité nulle). */
@Composable
internal fun percentText(percent: Double?): String = if (percent == null) "—" else stringResource(Res.string.load_percent, percent.roundToInt())

/** « 1 semaine », « 4 semaines ». */
@Composable
internal fun weeksText(weeks: Int): String =
    if (weeks == 1) stringResource(Res.string.load_horizon_one) else stringResource(Res.string.load_horizon_many, weeks)

/** « ≈ 1,5 semaine » : le singulier jusqu'à 2 en français, à 1 exactement en anglais. */
@Composable
internal fun teamWeeksText(weeks: Double): String {
    val language = Locale.current.language
    val rounded = round(weeks * 10) / 10
    val singular = if (english(language)) rounded == 1.0 else rounded < 2.0
    return stringResource(if (singular) Res.string.load_weeks_singular else Res.string.load_weeks_plural, decimalText(weeks, language))
}

/** « + 1 non estimée », « + 3 non estimées ». */
@Composable
internal fun unestimatedText(count: Int): String =
    if (count == 1) stringResource(Res.string.load_unestimated_one) else stringResource(Res.string.load_unestimated_many, count)

/** Nom d'une catégorie (`Task.taskType`), « Sans catégorie » pour la chaîne vide. */
@Composable
internal fun categoryName(category: String): String = category.ifEmpty { stringResource(Res.string.backlog_no_category) }

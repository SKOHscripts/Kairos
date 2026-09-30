package com.skohscripts.kairos.ui.team

import androidx.compose.runtime.Composable
import androidx.compose.ui.text.intl.Locale
import com.skohscripts.kairos.core.team.MemberAbsence
import com.skohscripts.kairos.ui.day.Dates
import com.skohscripts.kairos.ui.generated.resources.Res
import com.skohscripts.kairos.ui.generated.resources.absence_range
import com.skohscripts.kairos.ui.generated.resources.member_absent_day
import com.skohscripts.kairos.ui.generated.resources.member_absent_label
import com.skohscripts.kairos.ui.generated.resources.member_absent_range
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.resources.stringResource

/**
 * Une plage de dates lisible en une ou deux moitiés : « 12 oct. » seul pour un
 * jour, sinon « 12 » + « 16 oct. » (même mois, français) ou « Oct 12 » + « 16 »
 * (même mois, anglais) ; l'année n'est écrite que si elle diffère de celle de
 * [today]. Noms de mois et ordre par langue : le code commun n'a pas de
 * formatage localisé (`Dates`).
 */
internal data class DateSpan(val first: String, val second: String?)

internal fun dateSpan(start: LocalDate, end: LocalDate, today: LocalDate, language: String): DateSpan {
    val english = language.lowercase().startsWith("en")
    fun withYear(date: LocalDate, text: String) =
        if (date.year == today.year) text else if (english) "$text, ${date.year}" else "$text ${date.year}"
    if (start == end) return DateSpan(withYear(end, Dates.short(end, language)), null)
    val sameMonth = start.year == end.year && start.month == end.month
    return when {
        sameMonth && english -> DateSpan(Dates.short(start, language), withYear(end, end.day.toString()))
        sameMonth -> DateSpan(start.day.toString(), withYear(end, Dates.short(end, language)))
        else -> DateSpan(withYear(start, Dates.short(start, language)), withYear(end, Dates.short(end, language)))
    }
}

/** « 12 au 16 oct. » ou « 12 oct. » : les dates d'une absence, sans préfixe (liste de la fiche). */
@Composable
internal fun absenceDates(absence: MemberAbsence, today: LocalDate): String {
    val span = dateSpan(absence.start, absence.end, today, Locale.current.language)
    return if (span.second == null) span.first else stringResource(Res.string.absence_range, span.first, span.second)
}

/** « Absent du 12 au 16 oct. · Congés » : la ligne de la carte d'un membre. */
@Composable
internal fun absenceSummary(absence: MemberAbsence, today: LocalDate): String {
    val span = dateSpan(absence.start, absence.end, today, Locale.current.language)
    val base = if (span.second == null) {
        stringResource(Res.string.member_absent_day, span.first)
    } else {
        stringResource(Res.string.member_absent_range, span.first, span.second)
    }
    return if (absence.label.isBlank()) base else stringResource(Res.string.member_absent_label, base, absence.label)
}

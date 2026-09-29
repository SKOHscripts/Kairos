package com.skohscripts.kairos.ui.day

import androidx.compose.runtime.Composable
import com.skohscripts.kairos.ui.generated.resources.Res
import com.skohscripts.kairos.ui.generated.resources.duration_hours
import com.skohscripts.kairos.ui.generated.resources.duration_hours_minutes
import com.skohscripts.kairos.ui.generated.resources.duration_minutes
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import org.jetbrains.compose.resources.stringResource
import kotlin.math.round

/**
 * Dates, heures et nombres lisibles (« 30 sept. », « 09h15 », « 1 h 30 »).
 * Noms de mois et formats fixes par langue : le code commun n'a pas de
 * formatage localisé. Le français est la langue par défaut.
 */
internal object Dates {
    private val FR = listOf("janv.", "févr.", "mars", "avr.", "mai", "juin", "juil.", "août", "sept.", "oct.", "nov.", "déc.")
    private val EN = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")

    private fun english(language: String) = language.lowercase().startsWith("en")

    fun short(date: LocalDate, language: String): String {
        val month = date.month.ordinal
        return if (english(language)) "${EN[month]} ${date.day}" else "${date.day} ${FR[month]}"
    }

    private val FR_LONG = listOf("janvier", "février", "mars", "avril", "mai", "juin", "juillet", "août", "septembre", "octobre", "novembre", "décembre")
    private val EN_LONG = listOf("January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December")
    private val FR_DAYS = listOf("lundi", "mardi", "mercredi", "jeudi", "vendredi", "samedi", "dimanche")
    private val EN_DAYS = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")

    /** « mardi 30 septembre 2026 », « 1er » le premier du mois (`date_longue` de Kairos 2) ; « Tuesday, September 30, 2026 ». */
    fun long(date: LocalDate, language: String): String {
        val dow = date.dayOfWeek.ordinal
        val month = date.month.ordinal
        if (english(language)) return "${EN_DAYS[dow]}, ${EN_LONG[month]} ${date.day}, ${date.year}"
        val day = if (date.day == 1) "1er" else date.day.toString()
        return "${FR_DAYS[dow]} $day ${FR_LONG[month]} ${date.year}"
    }

    /** « mar. 30/09 » (`jour_court` de Kairos 2) ; « Tue 9/30 ». */
    fun dayShort(date: LocalDate, language: String): String {
        val dow = date.dayOfWeek.ordinal
        return if (english(language)) {
            "${EN_DAYS[dow].take(3)} ${date.month.ordinal + 1}/${date.day}"
        } else {
            "${FR_DAYS[dow].take(3)}. ${date.day.toString().padStart(2, '0')}/${(date.month.ordinal + 1).toString().padStart(2, '0')}"
        }
    }

    /** « 28/09 » ; « 9/28 ». */
    fun dayMonth(date: LocalDate, language: String): String =
        if (english(language)) "${date.month.ordinal + 1}/${date.day}"
        else "${date.day.toString().padStart(2, '0')}/${(date.month.ordinal + 1).toString().padStart(2, '0')}"

    /** « 09h15 » (Kairos 2) en français, « 09:15 » en anglais. */
    fun time(t: LocalTime, language: String): String {
        val h = t.hour.toString().padStart(2, '0')
        val m = t.minute.toString().padStart(2, '0')
        return if (english(language)) "$h:$m" else "${h}h$m"
    }

    fun time(t: LocalDateTime, language: String): String = time(t.time, language)

    /** Graduation de la frise : « 9h », « 9:00 ». */
    fun hour(h: Int, language: String): String = if (english(language)) "$h:00" else "${h}h"

    /** « HH:MM » pour un champ de saisie. */
    fun field(t: LocalTime): String = "${t.hour.toString().padStart(2, '0')}:${t.minute.toString().padStart(2, '0')}"

    /** Lit « 9:30 », « 09:30 » ou « 9h30 » ; `null` si invalide. */
    fun parseTime(text: String): LocalTime? {
        val parts = text.trim().lowercase().split(':', 'h')
        if (parts.size != 2) return null
        val h = parts[0].toIntOrNull() ?: return null
        val m = parts[1].ifEmpty { "0" }.toIntOrNull() ?: return null
        return if (h in 0..23 && m in 0..59) LocalTime(h, m) else null
    }

    fun parseDate(text: String): LocalDate? = text.trim().takeIf { it.isNotEmpty() }?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

    /** Une décimale au plus, sans « .0 » (filtre `nombre` de Kairos 2) : « 16 », « 6.2 ». */
    fun number(value: Double): String {
        val rounded = round(value * 10) / 10
        val whole = rounded.toLong()
        return if (rounded == whole.toDouble()) whole.toString() else rounded.toString()
    }
}

/** Durée lisible (`_fmt_minutes` de Kairos 2) : « 1 h 30 », « 2 h », « 45 min ». */
@Composable
internal fun duration(minutes: Int): String {
    val m = maxOf(0, minutes)
    val hours = m / 60
    val rest = m % 60
    return when {
        hours > 0 && rest > 0 -> stringResource(Res.string.duration_hours_minutes, hours, rest.toString().padStart(2, '0'))
        hours > 0 -> stringResource(Res.string.duration_hours, hours)
        else -> stringResource(Res.string.duration_minutes, rest)
    }
}

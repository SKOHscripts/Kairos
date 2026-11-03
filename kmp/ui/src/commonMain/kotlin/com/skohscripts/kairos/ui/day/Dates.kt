package com.skohscripts.kairos.ui.day

import org.jetbrains.compose.resources.getString
import androidx.compose.runtime.Composable
import com.skohscripts.kairos.core.model.Settings
import com.skohscripts.kairos.core.settings.UiLanguage
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
 * formatage localisé. Six langues (docs/spec/i18n.md) ; l'anglais est le repli.
 */
internal object Dates {
    /** Les noms et séparateurs d'une langue ; l'ordre des champs et la ponctuation propres à chaque format sont dans les fonctions. */
    private class Names(
        /** Mois abrégés, joints au numéro du jour (génitif en russe : « 30 сент. »). */
        val months: List<String>,
        /** Mois seuls, au nominatif : « novembre 2026 ». */
        val monthsLong: List<String>,
        /** Mois d'une date complète (génitif en russe), sinon comme [monthsLong]. */
        val monthsFull: List<String> = monthsLong,
        /** Jours, du lundi au dimanche. */
        val days: List<String>,
        /** Jours abrégés des en-têtes de grille et de « mar. 30/09 ». */
        val abbrev: List<String>,
        /** Entre jour et mois numériques (« 30/09 », « 30.09 ») et ce qui les suit. */
        val numSep: String = "/",
        val numEnd: String = "",
    )

    private val FR = Names(
        months = listOf("janv.", "févr.", "mars", "avr.", "mai", "juin", "juil.", "août", "sept.", "oct.", "nov.", "déc."),
        monthsLong = listOf("janvier", "février", "mars", "avril", "mai", "juin", "juillet", "août", "septembre", "octobre", "novembre", "décembre"),
        days = listOf("lundi", "mardi", "mercredi", "jeudi", "vendredi", "samedi", "dimanche"),
        abbrev = listOf("lun.", "mar.", "mer.", "jeu.", "ven.", "sam.", "dim."),
    )
    private val EN = Names(
        months = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"),
        monthsLong = listOf("January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December"),
        days = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"),
        abbrev = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"),
    )
    private val ES = Names(
        months = listOf("ene", "feb", "mar", "abr", "may", "jun", "jul", "ago", "sep", "oct", "nov", "dic"),
        monthsLong = listOf("enero", "febrero", "marzo", "abril", "mayo", "junio", "julio", "agosto", "septiembre", "octubre", "noviembre", "diciembre"),
        days = listOf("lunes", "martes", "miércoles", "jueves", "viernes", "sábado", "domingo"),
        abbrev = listOf("lun", "mar", "mié", "jue", "vie", "sáb", "dom"),
    )
    private val DE = Names(
        months = listOf("Jan", "Feb", "Mär", "Apr", "Mai", "Jun", "Jul", "Aug", "Sep", "Okt", "Nov", "Dez"),
        monthsLong = listOf("Januar", "Februar", "März", "April", "Mai", "Juni", "Juli", "August", "September", "Oktober", "November", "Dezember"),
        days = listOf("Montag", "Dienstag", "Mittwoch", "Donnerstag", "Freitag", "Samstag", "Sonntag"),
        abbrev = listOf("Mo", "Di", "Mi", "Do", "Fr", "Sa", "So"),
        numSep = ".",
        numEnd = ".",
    )
    private val RU = Names(
        months = listOf("янв.", "февр.", "марта", "апр.", "мая", "июня", "июля", "авг.", "сент.", "окт.", "нояб.", "дек."),
        monthsLong = listOf("январь", "февраль", "март", "апрель", "май", "июнь", "июль", "август", "сентябрь", "октябрь", "ноябрь", "декабрь"),
        monthsFull = listOf("января", "февраля", "марта", "апреля", "мая", "июня", "июля", "августа", "сентября", "октября", "ноября", "декабря"),
        days = listOf("понедельник", "вторник", "среда", "четверг", "пятница", "суббота", "воскресенье"),
        abbrev = listOf("пн", "вт", "ср", "чт", "пт", "сб", "вс"),
        numSep = ".",
    )
    private val IT = Names(
        months = listOf("gen", "feb", "mar", "apr", "mag", "giu", "lug", "ago", "set", "ott", "nov", "dic"),
        monthsLong = listOf("gennaio", "febbraio", "marzo", "aprile", "maggio", "giugno", "luglio", "agosto", "settembre", "ottobre", "novembre", "dicembre"),
        days = listOf("lunedì", "martedì", "mercoledì", "giovedì", "venerdì", "sabato", "domenica"),
        abbrev = listOf("lun", "mar", "mer", "gio", "ven", "sab", "dom"),
    )

    private fun names(language: String): Names = when (UiLanguage.lang(language)) {
        Settings.LANGUAGE_FR -> FR
        Settings.LANGUAGE_ES -> ES
        Settings.LANGUAGE_DE -> DE
        Settings.LANGUAGE_RU -> RU
        Settings.LANGUAGE_IT -> IT
        else -> EN
    }

    private fun english(language: String) = !UiLanguage.dayFirst(language)

    private fun two(n: Int) = n.toString().padStart(2, '0')

    /** « 30 sept. », « 30 sep », « 30. Sep », « 30 сент. » ; « Sep 30 » en anglais. */
    fun short(date: LocalDate, language: String): String {
        val month = names(language).months[date.month.ordinal]
        return when {
            english(language) -> "$month ${date.day}"
            UiLanguage.lang(language) == Settings.LANGUAGE_DE -> "${date.day}. $month"
            else -> "${date.day} $month"
        }
    }

    /** Comme [short], avec l'année (« 29 févr. 2028 », « Feb 29, 2028 ») si elle diffère de celle de [reference]. */
    fun short(date: LocalDate, language: String, reference: LocalDate): String {
        val text = short(date, language)
        return when {
            date.year == reference.year -> text
            english(language) -> "$text, ${date.year}"
            else -> "$text ${date.year}"
        }
    }

    /**
     * Date complète : « mardi 30 septembre 2026 » (« 1er » le premier du mois), « martes 30 de septiembre de 2026 »,
     * « Dienstag, 30. September 2026 », « вторник, 29 сентября 2026 », « martedì 30 settembre 2026 » ;
     * « Tuesday, September 30, 2026 » en anglais.
     */
    fun long(date: LocalDate, language: String): String {
        val n = names(language)
        val day = n.days[date.dayOfWeek.ordinal]
        val month = n.monthsFull[date.month.ordinal]
        return when (UiLanguage.lang(language)) {
            Settings.LANGUAGE_FR -> "$day ${dayOfMonth(date, language)} $month ${date.year}"
            Settings.LANGUAGE_ES -> "$day ${date.day} de $month de ${date.year}"
            Settings.LANGUAGE_DE -> "$day, ${date.day}. $month ${date.year}"
            Settings.LANGUAGE_RU -> "$day, ${date.day} $month ${date.year}"
            Settings.LANGUAGE_IT -> "$day ${date.day} $month ${date.year}"
            else -> "$day, $month ${date.day}, ${date.year}"
        }
    }

    /** Mois et année : « novembre 2026 », « November 2026 », « noviembre de 2026 », « ноябрь 2026 ». */
    fun monthYear(date: LocalDate, language: String): String {
        val month = names(language).monthsLong[date.month.ordinal]
        return if (UiLanguage.lang(language) == Settings.LANGUAGE_ES) "$month de ${date.year}" else "$month ${date.year}"
    }

    /** Abréviation du jour de la semaine (lundi = 0) pour un en-tête de grille : « lun. », « Mon », « Mo », « пн ». */
    fun weekdayAbbrev(weekday: Int, language: String): String = names(language).abbrev[weekday]

    /** Nom du jour de la semaine : « mardi », « Tuesday ». */
    fun weekdayName(date: LocalDate, language: String): String = names(language).days[date.dayOfWeek.ordinal]

    /** Nom du jour de la semaine (lundi = 0) : « mardi », « Tuesday ». */
    fun weekdayName(weekday: Int, language: String): String = names(language).days[weekday]

    /** Jour du mois seul : « 1er » (français seulement), « 15 » ; « 1 », « 15 ». */
    fun dayOfMonth(date: LocalDate, language: String): String =
        if (UiLanguage.isFrench(language) && date.day == 1) "1er" else date.day.toString()

    /** « mar. 30/09 », « Di 30.09. » ; « Tue 9/30 ». */
    fun dayShort(date: LocalDate, language: String): String {
        val day = names(language).abbrev[date.dayOfWeek.ordinal]
        return "$day ${dayMonth(date, language)}"
    }

    /** « 28/09 », « 28.09. » (allemand), « 28.09 » (russe) ; « 9/28 » en anglais. */
    fun dayMonth(date: LocalDate, language: String): String {
        if (english(language)) return "${date.month.ordinal + 1}/${date.day}"
        val n = names(language)
        return "${two(date.day)}${n.numSep}${two(date.month.ordinal + 1)}${n.numEnd}"
    }

    /** « 09h15 » en français, « 09:15 » ailleurs. */
    fun time(t: LocalTime, language: String): String {
        val h = two(t.hour)
        val m = two(t.minute)
        return if (UiLanguage.isFrench(language)) "${h}h$m" else "$h:$m"
    }

    fun time(t: LocalDateTime, language: String): String = time(t.time, language)

    /** Graduation de la frise : « 9h » en français, « 9:00 » ailleurs. */
    fun hour(h: Int, language: String): String = if (UiLanguage.isFrench(language)) "${h}h" else "$h:00"

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

    /**
     * Saisie souple d'une date : `2026-09-30` (ISO), `2026-9-30` ou `2026/9/30` (zéros facultatifs, `-`, `/` ou `.`)
     * et, si [dayFirst] (toute langue sauf l'anglais), `30/09/2026` ou `30.09.2026` (jour, mois, année). L'ordre
     * mois/jour de l'anglais n'est pas accepté : il serait ambigu avec les autres langues (docs/spec/planning-personnalise.md § Lot A, 1). `null` si illisible ou impossible.
     */
    fun parseDate(text: String, dayFirst: Boolean = false): LocalDate? {
        val t = text.trim().trimEnd('.')
        if (t.isEmpty()) return null
        runCatching { LocalDate.parse(t) }.getOrNull()?.let { return it }
        val parts = t.split('-', '/', '.').map { it.trim() }
        if (parts.size != 3 || parts.any { it.isEmpty() || !it.all(Char::isDigit) }) return null
        val n = parts.map { it.toInt() }
        val (year, month, day) = when {
            parts[0].length == 4 -> Triple(n[0], n[1], n[2])
            dayFirst && parts[2].length == 4 -> Triple(n[2], n[1], n[0])
            else -> return null
        }
        return runCatching { LocalDate(year, month, day) }.getOrNull()
    }

    /**
     * Une décimale au plus, sans « .0 » : « 16 », « 6.2 ». Une valeur positive qui
     * s'arrondirait à 0 garde deux décimales (« 0.05 » : un score de 1 / 21 n'est pas nul).
     */
    fun number(value: Double, language: String): String =
        number(value).let { if (UiLanguage.dayFirst(language)) it.replace('.', ',') else it }

    fun number(value: Double): String {
        if (value > 0 && value < 0.05) return (round(value * 100) / 100).takeIf { it > 0 }?.toString() ?: "0.01"
        val rounded = round(value * 10) / 10
        val whole = rounded.toLong()
        return if (rounded == whole.toDouble()) whole.toString() else rounded.toString()
    }
}

/** [duration] hors composition (texte d'un message). */
internal suspend fun durationText(minutes: Int): String {
    val m = maxOf(0, minutes)
    val hours = m / 60
    val rest = m % 60
    return when {
        hours > 0 && rest > 0 -> getString(Res.string.duration_hours_minutes, hours, rest.toString().padStart(2, '0'))
        hours > 0 -> getString(Res.string.duration_hours, hours)
        else -> getString(Res.string.duration_minutes, rest)
    }
}

/** Durée lisible : « 1 h 30 », « 2 h », « 45 min ». */
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

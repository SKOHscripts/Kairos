package com.skohscripts.kairos.core.settings

import com.skohscripts.kairos.core.model.Settings
import kotlinx.datetime.LocalDate

/** Nature d'un champ de réglage : ce qu'on y saisit et comment on le valide. */
enum class FieldKind { INT, DECIMAL, BOOL, TEXT, DATES }

/**
 * Un réglage éditable (docs/spec-v3/reglages.md) : clé stable (nom du champ
 * de [Settings]), nature, bornes **incluses** de Kairos 2 (`ge`/`le`), ou
 * borne basse exclue (`gt`) si [minExclusive]. La valeur circule en texte :
 * celui du formulaire.
 */
class SettingField internal constructor(
    val key: String,
    val kind: FieldKind,
    val min: Double? = null,
    val max: Double? = null,
    val minExclusive: Boolean = false,
    internal val read: (Settings) -> String,
    internal val write: (Settings, String) -> Settings,
)

enum class FieldErrorKind { REQUIRED, NOT_INTEGER, NOT_NUMBER, TOO_SMALL, TOO_LARGE, INVALID_DATE }

/** Erreur d'un champ ; [bound] est la borne franchie (« 0 », « 23 ») ou la date illisible. */
data class FieldError(val kind: FieldErrorKind, val bound: String? = null)

/** Règles entre champs (clé `_general` de Kairos 2), vérifiées seulement si chaque champ est valide. */
enum class GeneralError { WORKDAY_ORDER, DIP_ORDER }

data class SettingsValidation(
    /** Les réglages à enregistrer, `null` s'il y a la moindre erreur (jamais d'état partiel). */
    val settings: Settings?,
    val fieldErrors: Map<String, FieldError>,
    val general: GeneralError?,
)

/**
 * Formulaire des Réglages, pur : valeurs affichées ([values]) et validation
 * par champ ([validate]), mêmes bornes et mêmes règles inter-champs que
 * `app/config.py` (Kairos 2). Un nombre décimal accepte la virgule.
 */
object SettingsForm {
    private fun int(key: String, min: Int?, max: Int? = null, get: (Settings) -> Int, set: (Settings, Int) -> Settings) =
        SettingField(key, FieldKind.INT, min?.toDouble(), max?.toDouble(), read = { get(it).toString() }, write = { s, v -> set(s, v.trim().toInt()) })

    private fun decimal(key: String, min: Double, exclusive: Boolean, get: (Settings) -> Double, set: (Settings, Double) -> Settings) =
        SettingField(key, FieldKind.DECIMAL, min, null, exclusive, read = { formatDecimal(get(it)) }, write = { s, v -> set(s, parseDecimal(v)!!) })

    private fun bool(key: String, get: (Settings) -> Boolean, set: (Settings, Boolean) -> Settings) =
        SettingField(key, FieldKind.BOOL, read = { get(it).toString() }, write = { s, v -> set(s, v == "true") })

    private fun text(key: String, kind: FieldKind, get: (Settings) -> String, set: (Settings, String) -> Settings) =
        SettingField(key, kind, read = get, write = { s, v -> set(s, v.trim()) })

    val FIELDS: List<SettingField> = listOf(
        int("defaultTaskDurationMinutes", 1, get = { it.defaultTaskDurationMinutes }) { s, v -> s.copy(defaultTaskDurationMinutes = v) },
        int("meetingBufferMinutes", 0, get = { it.meetingBufferMinutes }) { s, v -> s.copy(meetingBufferMinutes = v) },
        int("workdayStartHour", 0, 23, { it.workdayStartHour }) { s, v -> s.copy(workdayStartHour = v) },
        int("workdayEndHour", 0, 23, { it.workdayEndHour }) { s, v -> s.copy(workdayEndHour = v) },
        decimal("priorityValueBase", 0.0, true, { it.priorityValueBase }) { s, v -> s.copy(priorityValueBase = v) },
        int("urgencyHorizonDays", 0, get = { it.urgencyHorizonDays }) { s, v -> s.copy(urgencyHorizonDays = v) },
        decimal("urgencyPeak", 0.0, false, { it.urgencyPeak }) { s, v -> s.copy(urgencyPeak = v) },
        int("defaultFibonacciPoints", 1, get = { it.defaultFibonacciPoints }) { s, v -> s.copy(defaultFibonacciPoints = v) },
        bool("cognitiveDipEnabled", { it.cognitiveDipEnabled }) { s, v -> s.copy(cognitiveDipEnabled = v) },
        int("cognitiveDipStartHour", 0, 23, { it.cognitiveDipStartHour }) { s, v -> s.copy(cognitiveDipStartHour = v) },
        int("cognitiveDipTroughHour", 0, 23, { it.cognitiveDipTroughHour }) { s, v -> s.copy(cognitiveDipTroughHour = v) },
        int("cognitiveDipEndHour", 0, 23, { it.cognitiveDipEndHour }) { s, v -> s.copy(cognitiveDipEndHour = v) },
        decimal("cognitiveDipPenalty", 0.0, false, { it.cognitiveDipPenalty }) { s, v -> s.copy(cognitiveDipPenalty = v) },
        int("staleOverdueDays", 0, get = { it.staleOverdueDays }) { s, v -> s.copy(staleOverdueDays = v) },
        int("staleUntouchedDays", 0, get = { it.staleUntouchedDays }) { s, v -> s.copy(staleUntouchedDays = v) },
        int("priorityOverloadThreshold", 0, get = { it.priorityOverloadThreshold }) { s, v -> s.copy(priorityOverloadThreshold = v) },
        text("taskTypes", FieldKind.TEXT, { it.taskTypes }) { s, v -> s.copy(taskTypes = v) },
        int("statsWindowWeeks", 1, get = { it.statsWindowWeeks }) { s, v -> s.copy(statsWindowWeeks = v) },
        int("timerIdleAlertMinutes", 0, get = { it.timerIdleAlertMinutes }) { s, v -> s.copy(timerIdleAlertMinutes = v) },
        int("pomodoroFocusMinutes", 0, get = { it.pomodoroFocusMinutes }) { s, v -> s.copy(pomodoroFocusMinutes = v) },
        bool("timerAlertSound", { it.timerAlertSound }) { s, v -> s.copy(timerAlertSound = v) },
        bool("holidaysFr", { it.holidaysFr }) { s, v -> s.copy(holidaysFr = v) },
        text("extraHolidays", FieldKind.DATES, { it.extraHolidays }) { s, v -> s.copy(extraHolidays = normalizeDates(v)) },
        bool("updateCheckEnabled", { it.updateCheckEnabled }) { s, v -> s.copy(updateCheckEnabled = v) },
    )

    private val byKey = FIELDS.associateBy { it.key }

    fun field(key: String): SettingField = byKey.getValue(key)

    /** Texte de chaque champ pour [settings] (booléens : « true » / « false »). */
    fun values(settings: Settings): Map<String, String> = FIELDS.associate { it.key to it.read(settings) }

    /**
     * Valide [values] (clé → texte ; une clé absente garde la valeur de
     * [base]) et rend les réglages complets, ou les erreurs par champ, ou la
     * règle inter-champs violée.
     */
    fun validate(values: Map<String, String>, base: Settings): SettingsValidation {
        val errors = LinkedHashMap<String, FieldError>()
        var result = base
        for (f in FIELDS) {
            val raw = values[f.key] ?: continue
            val error = check(f, raw)
            if (error != null) errors[f.key] = error else result = f.write(result, raw)
        }
        if (errors.isNotEmpty()) return SettingsValidation(null, errors, null)
        val general = when {
            result.workdayStartHour >= result.workdayEndHour -> GeneralError.WORKDAY_ORDER
            !(result.cognitiveDipStartHour <= result.cognitiveDipTroughHour && result.cognitiveDipTroughHour <= result.cognitiveDipEndHour) -> GeneralError.DIP_ORDER
            else -> null
        }
        return SettingsValidation(if (general == null) result else null, emptyMap(), general)
    }

    private fun check(f: SettingField, raw: String): FieldError? {
        val text = raw.trim()
        return when (f.kind) {
            FieldKind.BOOL, FieldKind.TEXT -> null
            FieldKind.DATES -> text.split(',').map { it.trim() }.firstOrNull { it.isNotEmpty() && parseDate(it) == null }
                ?.let { FieldError(FieldErrorKind.INVALID_DATE, it) }
            FieldKind.INT, FieldKind.DECIMAL -> {
                if (text.isEmpty()) return FieldError(FieldErrorKind.REQUIRED)
                val number = if (f.kind == FieldKind.INT) {
                    text.toIntOrNull()?.toDouble() ?: return FieldError(FieldErrorKind.NOT_INTEGER)
                } else {
                    parseDecimal(text) ?: return FieldError(FieldErrorKind.NOT_NUMBER)
                }
                val min = f.min
                val max = f.max
                when {
                    min != null && (number < min || (f.minExclusive && number == min)) -> FieldError(FieldErrorKind.TOO_SMALL, bound(f, min))
                    max != null && number > max -> FieldError(FieldErrorKind.TOO_LARGE, bound(f, max))
                    else -> null
                }
            }
        }
    }

    private fun bound(f: SettingField, value: Double) = if (f.kind == FieldKind.INT) value.toInt().toString() else formatDecimal(value)

    private fun parseDate(text: String): LocalDate? = runCatching { LocalDate.parse(text) }.getOrNull()

    /** Dates nettoyées : espaces retirés, vides écartés, séparées par « , ». */
    private fun normalizeDates(text: String): String = text.split(',').map { it.trim() }.filter { it.isNotEmpty() }.joinToString(",")

    /** « 1,5 » ou « 1.5 » ; `null` si ce n'est pas un nombre fini. */
    internal fun parseDecimal(text: String): Double? = text.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it.isFinite() }

    /** « 4 » plutôt que « 4.0 » ; sinon la forme la plus courte. */
    internal fun formatDecimal(value: Double): String =
        if (value == kotlin.math.floor(value) && kotlin.math.abs(value) < 1e15) value.toLong().toString() else value.toString()
}

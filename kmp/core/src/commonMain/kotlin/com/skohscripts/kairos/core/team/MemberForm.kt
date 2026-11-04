package com.skohscripts.kairos.core.team

import com.skohscripts.kairos.core.model.Settings
import com.skohscripts.kairos.core.settings.FieldError
import com.skohscripts.kairos.core.settings.FieldErrorKind
import com.skohscripts.kairos.core.settings.SettingsForm
import kotlinx.datetime.LocalDate

/**
 * Formulaire de la fiche membre, pur (docs/spec/equipe.md § Membres) : valeurs
 * en texte et validation par champ, sur le modèle de [SettingsForm]. Les
 * erreurs réutilisent [FieldError] ; les clés sont celles de [NAME],
 * [AVAILABILITY] et [HOURS]. Un nombre décimal accepte la virgule.
 */
object MemberForm {
    const val NAME = "name"
    const val AVAILABILITY = "availabilityPercent"
    const val HOURS = "hoursPerDay"

    /** Longueur maximale d'un nom : au-delà, la carte du membre et les menus d'assignation ne le montrent plus. */
    const val MAX_NAME = 60

    const val MIN_AVAILABILITY = 1
    const val MAX_AVAILABILITY = 100
    const val MIN_HOURS = 1.0
    const val MAX_HOURS = 24.0

    /** Textes saisis (ou à afficher) d'une fiche. */
    data class Input(
        val name: String = "",
        val role: String = "",
        val availabilityPercent: String = MAX_AVAILABILITY.toString(),
        val hoursPerDay: String = "",
        /** « Heures différentes selon le jour » (issue #104) : la semaine type [weekHours] remplace les heures par jour. */
        val weekly: Boolean = false,
        /** Heures du lundi au vendredi ; vide = 0, jour libre. */
        val weekHours: List<String> = List(TeamMember.WEEK_DAYS) { "" },
    )

    /** Valeurs validées, prêtes pour le dépôt. */
    data class Valid(
        val name: String,
        val role: String,
        val availabilityPercent: Int,
        val hoursPerDay: Double,
        /** Semaine type ; `null` = heures par jour chaque jour ouvré. */
        val weekHours: List<Double>? = null,
    )

    /** Clé du champ des heures du jour [day] de la semaine type (0 = lundi). */
    fun weekKey(day: Int): String = "weekHours.$day"

    /** [valid] est `null` s'il y a la moindre erreur (jamais d'état partiel). */
    data class Result(val valid: Valid?, val errors: Map<String, FieldError>)

    /**
     * Heures par jour d'un nouveau membre : la journée de travail des
     * Réglages moins une heure (pause), bornée à 1…24.
     */
    fun defaultHoursPerDay(settings: Settings): Double =
        (settings.workdayEndHour - settings.workdayStartHour - 1).coerceIn(MIN_HOURS.toInt(), MAX_HOURS.toInt()).toDouble()

    /** Fiche d'un nouveau membre : quotité 100, heures par jour par défaut des Réglages. */
    fun newInput(settings: Settings) = Input(hoursPerDay = formatHours(defaultHoursPerDay(settings)))

    /** Textes d'un membre existant. */
    fun inputOf(member: TeamMember) = Input(
        name = member.name,
        role = member.role,
        availabilityPercent = member.availabilityPercent.toString(),
        hoursPerDay = formatHours(member.hoursPerDay),
        weekly = member.weekHours != null,
        weekHours = member.weekHours?.map(::formatHours) ?: List(TeamMember.WEEK_DAYS) { "" },
    )

    /** Activer la semaine type : des jours encore tous vides partent des heures par jour, chaque jour. */
    fun withWeekly(input: Input, weekly: Boolean): Input =
        if (weekly && input.weekHours.all { it.isBlank() }) input.copy(weekly = true, weekHours = List(TeamMember.WEEK_DAYS) { input.hoursPerDay })
        else input.copy(weekly = weekly)

    private val SPACES = Regex("\\s+")

    /** Nom enregistré : sans espaces autour, chaque suite d'espaces réduite à une seule (« Alice  Martin » → « Alice Martin »). */
    fun cleanName(name: String): String = name.trim().replace(SPACES, " ")

    /**
     * Clé de comparaison de deux noms de membre : [cleanName] sans la casse. Les accents comptent (« Hélène » ≠ « Helene ») :
     * deux noms qui ne diffèrent que par un accent se distinguent à l'écran (décision du 2026-11-04).
     */
    fun nameKey(name: String): String = cleanName(name).lowercase()

    /** « 7 » plutôt que « 7.0 », « 7.5 » sinon. */
    fun formatHours(hours: Double): String = SettingsForm.formatDecimal(hours)

    /**
     * [takenNames] : les noms des **autres** membres actifs ; [archivedNames] : ceux des membres archivés. Deux membres de
     * même nom ([nameKey] : sans casse ni espaces en trop) ne se distinguent plus dans les menus d'assignation, la charge ni
     * le journal : refusé, [FieldErrorKind.DUPLICATE] (actif) ou [FieldErrorKind.DUPLICATE_ARCHIVED] (archivé : le message
     * invite à le réactiver). Le nom validé est [cleanName].
     */
    fun validate(input: Input, takenNames: Collection<String> = emptyList(), archivedNames: Collection<String> = emptyList()): Result {
        val errors = LinkedHashMap<String, FieldError>()
        val name = cleanName(input.name)
        val key = nameKey(name)
        when {
            name.isEmpty() -> errors[NAME] = FieldError(FieldErrorKind.REQUIRED)
            name.length > MAX_NAME -> errors[NAME] = FieldError(FieldErrorKind.TOO_LARGE, MAX_NAME.toString())
            takenNames.any { nameKey(it) == key } -> errors[NAME] = FieldError(FieldErrorKind.DUPLICATE, name)
            archivedNames.any { nameKey(it) == key } -> errors[NAME] = FieldError(FieldErrorKind.DUPLICATE_ARCHIVED, name)
        }

        var availability = 0
        val availabilityText = input.availabilityPercent.trim()
        when {
            availabilityText.isEmpty() -> errors[AVAILABILITY] = FieldError(FieldErrorKind.REQUIRED)
            availabilityText.toIntOrNull() == null -> errors[AVAILABILITY] = FieldError(FieldErrorKind.NOT_INTEGER)
            else -> {
                availability = availabilityText.toInt()
                when {
                    availability < MIN_AVAILABILITY -> errors[AVAILABILITY] = FieldError(FieldErrorKind.TOO_SMALL, MIN_AVAILABILITY.toString())
                    availability > MAX_AVAILABILITY -> errors[AVAILABILITY] = FieldError(FieldErrorKind.TOO_LARGE, MAX_AVAILABILITY.toString())
                }
            }
        }

        var hours = 0.0
        val hoursText = input.hoursPerDay.trim()
        if (input.weekly) {
            // Champ masqué par la semaine type : sa valeur valide est gardée, sinon la journée la plus longue (bornée) la remplace.
            hours = SettingsForm.parseDecimal(hoursText)?.takeIf { it in MIN_HOURS..MAX_HOURS }
                ?: (input.weekHours.mapNotNull { SettingsForm.parseDecimal(it.trim()) }.maxOrNull() ?: 0.0).coerceIn(MIN_HOURS, MAX_HOURS)
        } else if (hoursText.isEmpty()) {
            errors[HOURS] = FieldError(FieldErrorKind.REQUIRED)
        } else {
            val parsed = SettingsForm.parseDecimal(hoursText)
            when {
                parsed == null -> errors[HOURS] = FieldError(FieldErrorKind.NOT_NUMBER)
                parsed < MIN_HOURS -> errors[HOURS] = FieldError(FieldErrorKind.TOO_SMALL, formatHours(MIN_HOURS))
                parsed > MAX_HOURS -> errors[HOURS] = FieldError(FieldErrorKind.TOO_LARGE, formatHours(MAX_HOURS))
                else -> hours = parsed
            }
        }

        // Semaine type (issue #104) : 0 à 24 par jour, vide = jour libre, au moins un jour travaillé.
        var week: List<Double>? = null
        if (input.weekly) {
            val days = input.weekHours.take(TeamMember.WEEK_DAYS).mapIndexed { day, text ->
                val t = text.trim()
                val parsed = if (t.isEmpty()) 0.0 else SettingsForm.parseDecimal(t)
                when {
                    parsed == null -> { errors[weekKey(day)] = FieldError(FieldErrorKind.NOT_NUMBER); 0.0 }
                    parsed < 0.0 -> { errors[weekKey(day)] = FieldError(FieldErrorKind.TOO_SMALL, "0"); 0.0 }
                    parsed > MAX_HOURS -> { errors[weekKey(day)] = FieldError(FieldErrorKind.TOO_LARGE, formatHours(MAX_HOURS)); 0.0 }
                    else -> parsed
                }
            }
            if (days.size == TeamMember.WEEK_DAYS && days.none { it > 0.0 } && (0 until TeamMember.WEEK_DAYS).none { weekKey(it) in errors }) {
                errors[weekKey(0)] = FieldError(FieldErrorKind.REQUIRED)
            }
            week = days
        }

        if (errors.isNotEmpty()) return Result(null, errors)
        return Result(Valid(name, input.role.trim(), availability, hours, week), emptyMap())
    }

    /** Une absence est valide si sa fin ne précède pas son début (les deux jours sont inclus). */
    fun isValidAbsence(start: LocalDate, end: LocalDate): Boolean = end >= start
}

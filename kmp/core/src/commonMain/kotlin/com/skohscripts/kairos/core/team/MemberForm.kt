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
    )

    /** Valeurs validées, prêtes pour le dépôt. */
    data class Valid(
        val name: String,
        val role: String,
        val availabilityPercent: Int,
        val hoursPerDay: Double,
    )

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
    )

    /** « 7 » plutôt que « 7.0 », « 7.5 » sinon. */
    fun formatHours(hours: Double): String = SettingsForm.formatDecimal(hours)

    fun validate(input: Input): Result {
        val errors = LinkedHashMap<String, FieldError>()
        val name = input.name.trim()
        if (name.isEmpty()) errors[NAME] = FieldError(FieldErrorKind.REQUIRED)

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
        if (hoursText.isEmpty()) {
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

        if (errors.isNotEmpty()) return Result(null, errors)
        return Result(Valid(name, input.role.trim(), availability, hours), emptyMap())
    }

    /** Une absence est valide si sa fin ne précède pas son début (les deux jours sont inclus). */
    fun isValidAbsence(start: LocalDate, end: LocalDate): Boolean = end >= start
}

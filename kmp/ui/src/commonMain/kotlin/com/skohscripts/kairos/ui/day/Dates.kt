package com.skohscripts.kairos.ui.day

import kotlinx.datetime.LocalDate

/**
 * Date courte lisible (« 30 sept. », « Sep 30 ») pour les étiquettes de
 * tâche. Noms de mois fixes par langue : le code commun n'a pas de
 * formatage localisé des dates.
 */
internal object Dates {
    private val FR = listOf("janv.", "févr.", "mars", "avr.", "mai", "juin", "juil.", "août", "sept.", "oct.", "nov.", "déc.")
    private val EN = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")

    fun short(date: LocalDate, language: String): String {
        val month = date.month.ordinal
        return if (language.lowercase().startsWith("en")) "${EN[month]} ${date.day}" else "${date.day} ${FR[month]}"
    }
}

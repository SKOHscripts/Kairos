package com.skohscripts.kairos.core.engine

import kotlinx.datetime.LocalDate

/**
 * Fériés nationaux des pays de Kairos (docs/spec/jours-feries.md), lus dans [HolidayTable], générée depuis python-holidays par
 * `kmp/tools/make_holidays.py` et recalculée à chaque version. Pur, sans réseau.
 */
object PublicHolidays {
    /** Codes ISO 3166-1 alpha-2 pris en charge (les pays des six langues de l'interface). */
    val COUNTRIES: List<String> = HolidayTable.DATA.keys.sorted()

    /** Version de python-holidays dont vient la table. */
    const val SOURCE: String = HolidayTable.SOURCE

    const val FIRST_YEAR: Int = HolidayTable.FIRST_YEAR
    const val LAST_YEAR: Int = HolidayTable.LAST_YEAR

    /** Chaque pays analysé une fois, à la première demande (`lazy` : sûr entre fils sur le JVM). */
    private val parsed: Map<String, Lazy<Map<Int, Set<LocalDate>>>> = HolidayTable.DATA.mapValues { (_, text) -> lazy { parse(text) } }

    fun isSupported(code: String?): Boolean = code != null && code in HolidayTable.DATA

    /** Les fériés de [country] en [year] ; vide hors de la table ou pour un code inconnu. */
    fun dates(country: String, year: Int): Set<LocalDate> {
        if (year < FIRST_YEAR || year > LAST_YEAR) return emptySet()
        return table(country)?.get(year).orEmpty()
    }

    /**
     * Pays par défaut d'une nouvelle installation : la région du système ([region], « DE », « be ») si elle est prise en charge,
     * `""` (aucun) sinon. `UK` est lu comme `GB`.
     */
    fun defaultFor(region: String?): String {
        val code = region?.trim()?.uppercase()?.let { if (it == "UK") "GB" else it }
        return if (isSupported(code)) code!! else ""
    }

    private fun table(country: String): Map<Int, Set<LocalDate>>? = parsed[country]?.value

    private fun parse(text: String): Map<Int, Set<LocalDate>> =
        text.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.associate { line ->
            val parts = line.split(' ')
            val year = parts[0].toInt()
            year to parts.drop(1).mapTo(HashSet()) { LocalDate(year, it.substring(0, 2).toInt(), it.substring(2, 4).toInt()) }
        }
}

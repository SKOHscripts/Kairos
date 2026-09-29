package com.skohscripts.kairos.core.engine

import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime

/**
 * Arithmétique d'heures **naïves** (heure locale sans fuseau, convention de
 * Kairos 2 pour les créneaux et l'épinglage) : calculée en UTC pour ne jamais
 * subir de changement d'heure.
 */
internal fun LocalDateTime.plusMinutes(minutes: Long): LocalDateTime =
    (toInstant(TimeZone.UTC) + kotlin.time.Duration.parse("${minutes}m")).toLocalDateTime(TimeZone.UTC)

internal fun LocalDateTime.plusMinutes(minutes: Int): LocalDateTime = plusMinutes(minutes.toLong())

/** Minutes entières (arrondi vers le bas, comme `total_seconds() // 60`) de [a] à [b]. */
internal fun minutesBetween(a: LocalDateTime, b: LocalDateTime): Long {
    val seconds = (b.toInstant(TimeZone.UTC) - a.toInstant(TimeZone.UTC)).inWholeSeconds
    return seconds.floorDiv(60L)
}

package com.skohscripts.kairos.core.team

import kotlinx.datetime.LocalDate
import kotlin.time.Instant

/**
 * Absence d'un membre (docs/spec/equipe.md § Modèle) : une plage de dates
 * locales, **début et fin incluses** ([end] ≥ [start]), avec un [label]
 * facultatif (« Congés », « Formation »…). [memberId] est `TeamMember.id`
 * (référence « molle », comme `Task.assigneeId`).
 */
data class MemberAbsence(
    val id: Long,
    val memberId: Long,
    val start: LocalDate,
    val end: LocalDate,
    val label: String = "",
    val createdAt: Instant,
)

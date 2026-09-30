package com.skohscripts.kairos.core.team

import kotlin.time.Instant

/**
 * Membre de l'équipe (docs/spec/equipe.md § Modèle) : une fiche dans la base
 * du manager, pas un compte. [uid] est l'identité stable utilisée par les
 * échanges de fichiers (les [id] entiers ne sortent jamais d'une base) ;
 * [isSelf] désigne « C'est moi » (au plus un membre).
 */
data class TeamMember(
    val id: Long,
    val uid: String,
    val name: String,
    val role: String = "",
    /** Quotité en % (1-100). */
    val availabilityPercent: Int = 100,
    val hoursPerDay: Double,
    val isSelf: Boolean = false,
    val archived: Boolean = false,
    val createdAt: Instant,
    val updatedAt: Instant,
)

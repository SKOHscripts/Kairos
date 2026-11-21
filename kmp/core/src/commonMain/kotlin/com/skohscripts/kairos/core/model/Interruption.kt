package com.skohscripts.kairos.core.model

import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/**
 * Un dérangement signalé après coup (docs/spec/derangements.md, issue #272) : il couvre `[at − minutes ; at]`, [at] étant l'instant
 * du signalement. [source] facultative ; [taskId] la tâche dont le chrono tournait, [noteId] la note créée avec lui (références
 * « molles », jamais contraintes). Donnée personnelle : jamais lue par l'espace Équipe.
 */
data class Interruption(
    val id: Long,
    val at: Instant,
    val minutes: Int,
    val source: InterruptionSource? = null,
    val taskId: Long? = null,
    val noteId: Long? = null,
    val createdAt: Instant,
) {
    /** Début du dérangement. */
    val start: Instant get() = at - minutes.minutes

    companion object {
        /** Bornes d'une durée de dérangement, en minutes. */
        const val MIN_MINUTES = 1
        const val MAX_MINUTES = 240
    }
}

/** D'où vient un dérangement ; [code] = valeur stockée, figée. */
enum class InterruptionSource(val code: String) {
    PERSON("person"),
    CALL("call"),
    MESSAGE("message"),
    SELF("self"),
    ;

    companion object {
        /** Un code inconnu (version plus récente) se lit « non précisée ». */
        fun fromCode(code: String?): InterruptionSource? = entries.firstOrNull { it.code == code }
    }
}

package com.skohscripts.kairos.core.alerts

/** Cloche d'une alerte du chrono (docs/spec/temps-reel-chrono.md § Cloches, issue #234). */
enum class Bell {
    SINGLE,
    DOUBLE;

    companion object {
        /** Deux cloches pour le chrono oublié, une pour la pause et le dépassement : on reconnaît l'oubli à l'oreille. */
        fun of(kind: ChronoAlertKind): Bell = if (kind == ChronoAlertKind.IDLE) DOUBLE else SINGLE

        /** L'arrêt automatique du garde-fou. */
        val AUTO_STOP = SINGLE
    }
}

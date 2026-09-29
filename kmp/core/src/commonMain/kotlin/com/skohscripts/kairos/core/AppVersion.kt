package com.skohscripts.kairos.core

/**
 * Version de Kairos au format `X.Y.Z` ou `X.Y.Z-alpha.N` / `X.Y.Z-beta.N`
 * (docs/spec/distribution.md § Versionnage).
 *
 * Sert à deux choses : calculer le `versionCode` Android attendu (la CI vérifie
 * que `gradle.properties` le respecte) et comparer deux versions (vérification
 * des mises à jour sur le bureau, `updates/UpdateCheck`).
 */
data class AppVersion(
    val major: Int,
    val minor: Int,
    val patch: Int,
    val stage: Stage = Stage.RELEASE,
    val stageNumber: Int = 0,
) : Comparable<AppVersion> {

    /** Ordre croissant : alpha < beta < version finale, pour un même X.Y.Z. */
    enum class Stage { ALPHA, BETA, RELEASE }

    val isPrerelease: Boolean get() = stage != Stage.RELEASE

    /**
     * Code de version Android : `X*10000 + Y*100 + Z` pour une version finale,
     * décalé de −1000 (alpha) ou −500 (beta) plus le numéro de préversion. Une
     * préversion de 3.0.0 reste ainsi sous 30000 et au-dessus de 2.x (≤ 29999
     * n'est jamais atteint par une 2.x, dont le maximum publié est 20600).
     */
    val versionCode: Int
        get() {
            val base = major * 10_000 + minor * 100 + patch
            return when (stage) {
                Stage.RELEASE -> base
                Stage.BETA -> base - 500 + stageNumber
                Stage.ALPHA -> base - 1000 + stageNumber
            }
        }

    override fun compareTo(other: AppVersion): Int =
        compareValuesBy(this, other, { it.major }, { it.minor }, { it.patch }, { it.stage }, { it.stageNumber })

    override fun toString(): String = buildString {
        append("$major.$minor.$patch")
        when (stage) {
            Stage.ALPHA -> append("-alpha.$stageNumber")
            Stage.BETA -> append("-beta.$stageNumber")
            Stage.RELEASE -> Unit
        }
    }

    companion object {
        private val PATTERN = Regex("""^v?(\d+)\.(\d+)\.(\d+)(?:-(alpha|beta)\.(\d+))?$""")

        /** Analyse `3.0.0`, `v3.0.0`, `3.0.0-alpha.2`… ; `null` pour tout autre format. */
        fun parse(text: String): AppVersion? {
            val m = PATTERN.matchEntire(text.trim()) ?: return null
            val (major, minor, patch, stage, number) = m.destructured
            // Numéros de préversion bornés : au-delà, le code déborderait sur l'étape suivante.
            val n = number.toIntOrNull() ?: 0
            if (stage.isNotEmpty() && n !in 1..499) return null
            if (minor.toInt() > 99 || patch.toInt() > 99) return null
            return AppVersion(
                major.toInt(), minor.toInt(), patch.toInt(),
                when (stage) {
                    "alpha" -> Stage.ALPHA
                    "beta" -> Stage.BETA
                    else -> Stage.RELEASE
                },
                n,
            )
        }

        /** Version de ce build (`gradle.properties`). */
        val current: AppVersion by lazy {
            requireNotNull(parse(KairosBuild.VERSION_NAME)) { "kairos.versionName invalide : ${KairosBuild.VERSION_NAME}" }
        }
    }
}

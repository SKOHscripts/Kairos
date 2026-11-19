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

    /** Ce que la version ajoute au dernier chiffre : le numéro d'une beta, [FINAL_STEP] pour une finale (formule de la 4.2.0). */
    private val step: Int
        get() = when (stage) {
            Stage.RELEASE -> FINAL_STEP
            Stage.BETA -> stageNumber
            Stage.ALPHA -> error("une alpha n'a pas de code à partir de la $SINGLE_APP_MAJOR.$SINGLE_APP_MINOR.0 : $this")
        }

    /** La version suit la formule d'une seule app pour la beta et la stable (docs/spec/distribution.md § Versionnage). */
    private val singleApp: Boolean get() = major * 100 + minor >= SINGLE_APP_MAJOR * 100 + SINGLE_APP_MINOR

    /**
     * Code de version Android (docs/spec/distribution.md § Versionnage). À partir de la 4.2.0 :
     * `X*1 000 000 + Y*10 000 + Z*100 + s`, `s` = numéro de beta (1 à [MAX_PRERELEASE]) ou [FINAL_STEP] pour une finale :
     * une beta passe au-dessus de la finale précédente et sous la sienne, ce qui permet d'installer beta et stable dans la
     * même app (décision du 2026-11-15). Jusqu'à la 4.1.x, codes publiés et inchangés : `X*10000 + Y*100 + Z`, décalé de
     * −1000 (alpha) ou −500 (beta) plus le numéro de préversion (une préversion de 3.0.0 reste sous 30000 et au-dessus de
     * la dernière 2.x, 20600). Tout code de la 4.2.0 dépasse donc tout code d'avant (4.1.0 = 40100).
     */
    val versionCode: Int
        get() {
            if (singleApp) return major * 1_000_000 + minor * 10_000 + patch * 100 + step
            val base = major * 10_000 + minor * 100 + patch
            return when (stage) {
                Stage.RELEASE -> base
                Stage.BETA -> base - 500 + stageNumber
                Stage.ALPHA -> base - 1000 + stageNumber
            }
        }

    /**
     * Version des paquets de bureau (MSI, DMG, `.deb`), qui n'acceptent que trois nombres et doivent croître d'une beta à
     * sa finale : `X.Y.(Z*100 + s)` à partir de la 4.2.0 (`4.2.0-beta.1` → 4.2.1, `4.2.0` → 4.2.99). Jusqu'à la 4.1.x :
     * `X.Y.Z` pour une finale, `1.0.<versionCode>` pour une préversion (produit « Kairos Preview »). Le script de
     * construction du bureau (`desktopApp/build.gradle.kts`) applique la même règle.
     */
    val packageVersion: String
        get() = when {
            singleApp -> "$major.$minor.${patch * 100 + step}"
            isPrerelease -> "1.0.$versionCode"
            else -> "$major.$minor.$patch"
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
        /**
         * Numéro de préversion le plus haut que Kairos publie (docs/spec/distribution.md § Cycle des versions) : 99 est le
         * dernier chiffre d'une finale dans la formule de la 4.2.0 ([FINAL_STEP]). [parse] accepte plus large (anciens tags).
         */
        const val MAX_PRERELEASE = 98

        /** Dernier chiffre d'une finale dans la formule de la 4.2.0 : au-dessus de toute beta de la même version. */
        const val FINAL_STEP = 99

        /** Première version d'une seule app pour la beta et la stable (décision du 2026-11-15). */
        const val SINGLE_APP_MAJOR = 4
        const val SINGLE_APP_MINOR = 2

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

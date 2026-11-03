package com.skohscripts.kairos.core.engine.model

import com.skohscripts.kairos.core.engine.DurationEstimator
import com.skohscripts.kairos.core.model.TaskSpace
import kotlin.random.Random

/**
 * Historiques synthétiques à graine fixe dont la vérité est connue (docs/spec/estimateur-modele.md § Évaluation par
 * rétro-test) ; identifiants croissants = ordre de réalisation.
 *
 * - [Profile.RECURRING] : 6 titres répétés, chacun sa durée de base ;
 * - [Profile.NOVEL] : chaque titre est nouveau, mais chaque type a son lexique ; durée = base du type × facteur du palier de
 *   points × bruit triangulaire 0,7-1,5 ;
 * - [Profile.NOISE] : durées indépendantes de toute caractéristique ;
 * - [Profile.SPARSE] : 12 tâches faites, comme `NOVEL`.
 */
object SimulatedHistory {
    enum class Profile { RECURRING, NOVEL, NOISE, SPARSE }

    private class Kind(val type: String, val base: Double, val lexicon: List<String>)

    private val KINDS = listOf(
        Kind("Rédaction", 50.0, listOf("rédiger", "rapport", "bilan", "synthèse", "note", "compte", "rendu", "article", "trimestriel", "mensuel")),
        Kind("Revue", 25.0, listOf("revue", "relire", "relecture", "code", "correction", "vérifier", "commentaires", "pull", "request")),
        Kind("Réunion", 60.0, listOf("réunion", "point", "équipe", "comité", "atelier", "client", "préparer", "ordre", "jour")),
        Kind("Admin", 15.0, listOf("facture", "courrier", "classer", "remboursement", "formulaire", "banque", "assurance", "dossier")),
        Kind("Dev", 120.0, listOf("développer", "module", "fonction", "écran", "migration", "api", "refonte", "tests", "intégration")),
    )

    private val POINTS = listOf(1, 2, 3, 5, 8)
    private val POINT_FACTOR = mapOf(1 to 0.5, 2 to 0.7, 3 to 1.0, 5 to 1.4, 8 to 2.0)

    /** Un mot inventé de 6 lettres : rend chaque titre de `NOVEL` nouveau. */
    private fun pseudoWord(r: Random): String {
        val consonants = "bcdfgklmnprstvz"
        val vowels = "aeiou"
        return buildString { repeat(3) { append(consonants[r.nextInt(consonants.length)]); append(vowels[r.nextInt(vowels.length)]) } }
    }

    /** Bruit triangulaire entre 0,7 et 1,5 (moyenne de deux uniformes). */
    private fun noise(r: Random): Double = 0.7 + 0.8 * (r.nextDouble() + r.nextDouble()) / 2.0

    fun generate(seed: Int, profile: Profile, n: Int = 200): List<DurationEstimator.Sample> {
        val r = Random(seed)
        val count = if (profile == Profile.SPARSE) 12 else n
        val recurring = List(6) { k ->
            val kind = KINDS[k % KINDS.size]
            Triple("${kind.lexicon[0]} ${kind.lexicon[1 + k % (kind.lexicon.size - 1)]} ${pseudoWord(r)}", kind, POINTS[k % POINTS.size])
        }
        return List(count) { i ->
            val id = (i + 1).toLong()
            when (profile) {
                Profile.RECURRING -> {
                    val (title, kind, points) = recurring[r.nextInt(recurring.size)]
                    val minutes = kind.base * POINT_FACTOR.getValue(points) * noise(r)
                    sample(id, title, kind.type, points, minutes)
                }
                Profile.NOVEL, Profile.SPARSE -> {
                    val kind = KINDS[r.nextInt(KINDS.size)]
                    val points = POINTS[r.nextInt(POINTS.size)]
                    val words = List(2) { kind.lexicon[r.nextInt(kind.lexicon.size)] }.distinct()
                    val title = (words + pseudoWord(r)).joinToString(" ")
                    sample(id, title, kind.type, points, kind.base * POINT_FACTOR.getValue(points) * noise(r))
                }
                Profile.NOISE -> {
                    val kind = KINDS[r.nextInt(KINDS.size)]
                    val title = (List(2) { kind.lexicon[r.nextInt(kind.lexicon.size)] } + pseudoWord(r)).joinToString(" ")
                    sample(id, title, kind.type, POINTS[r.nextInt(POINTS.size)], 30.0 * (0.5 + r.nextDouble()))
                }
            }
        }
    }

    private fun sample(id: Long, title: String, type: String, points: Int, minutes: Double) =
        DurationEstimator.Sample(id, null, title, TaskSpace.PERSONAL, maxOf(2, minutes.toInt()), taskType = type, points = points, priority = 2)
}

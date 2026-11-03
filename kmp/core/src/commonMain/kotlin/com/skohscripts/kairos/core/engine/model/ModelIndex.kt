package com.skohscripts.kairos.core.engine.model

import com.skohscripts.kairos.core.engine.DurationEstimator
import kotlin.math.sqrt

/**
 * L'index du modèle (docs/spec/estimateur-modele.md § Le modèle), calculé de l'historique, jamais stocké : les échantillons
 * **triés par `taskId`** (l'ordre d'entrée est sans effet), et pour chacun ses vecteurs de mots et de trigrammes pondérés par
 * l'idf `sqrt((N + 1) / (df + 1))` puis normalisés (L2). Les identifiants de caractéristiques sont attribués dans l'ordre
 * d'apparition en parcourant les échantillons ; un vecteur est un tableau trié par identifiant, et le produit scalaire une
 * fusion de deux tableaux triés : ordre de sommation fixe, résultat identique au bit près sur toutes les cibles.
 */
class ModelIndex private constructor(
    /** Le corpus, trié par `taskId` croissant. */
    val samples: List<DurationEstimator.Sample>,
    private val words: Family,
    private val trigrams: Family,
) {
    /** Un vecteur creux normalisé : identifiants croissants et poids. */
    class Vector(val ids: IntArray, val weights: DoubleArray) {
        val isEmpty: Boolean get() = ids.isEmpty()

        /** Produit scalaire par fusion de deux tableaux triés. */
        fun dot(other: Vector): Double {
            var i = 0
            var j = 0
            var sum = 0.0
            while (i < ids.size && j < other.ids.size) {
                when {
                    ids[i] == other.ids[j] -> { sum += weights[i] * other.weights[j]; i++; j++ }
                    ids[i] < other.ids[j] -> i++
                    else -> j++
                }
            }
            return sum
        }

        companion object {
            val EMPTY = Vector(IntArray(0), DoubleArray(0))
        }
    }

    /** Une famille de caractéristiques (mots ou trigrammes) : identifiants, idf et vecteurs du corpus. */
    private class Family(val ids: Map<String, Int>, val idf: DoubleArray, val vectors: List<Vector>, val maxIdf: Double) {
        /**
         * Le vecteur de [features] : les caractéristiques connues avec leur idf ; une caractéristique **absente du corpus**
         * garde le poids maximal et compte dans la norme sans jamais pouvoir s'apparier (un mot jamais vu baisse la ressemblance).
         */
        fun vector(features: List<String>): Vector {
            val known = ArrayList<Int>()
            var unknown = 0
            for (f in features) {
                val id = ids[f]
                if (id == null) unknown++ else known.add(id)
            }
            known.sort()
            return normalized(known.toIntArray(), DoubleArray(known.size) { idf[known[it]] }, unknown * maxIdf * maxIdf)
        }
    }

    /** Une tâche à estimer, vue par l'index. */
    class Query(val words: Vector, val trigrams: Vector, val hasText: Boolean)

    fun query(title: String): Query {
        val f = TextFeatures.of(title)
        return Query(words.vector(f.words), trigrams.vector(f.trigrams), hasText = !f.isEmpty)
    }

    /** `0,5 × cosinus(mots) + 0,5 × cosinus(trigrammes)` entre [query] et l'échantillon d'indice [index]. */
    fun text(query: Query, index: Int): Double =
        0.5 * query.words.dot(words.vectors[index]) + 0.5 * query.trigrams.dot(trigrams.vectors[index])

    companion object {
        /** L'index de [samples] (déjà réduits au corpus : même espace, au plus `MAX_CORPUS`). */
        fun build(samples: List<DurationEstimator.Sample>): ModelIndex {
            val sorted = samples.sortedBy { it.taskId }
            val features = sorted.map { TextFeatures.of(it.title) }
            return ModelIndex(sorted, family(features.map { it.words }), family(features.map { it.trigrams }))
        }

        private fun family(perSample: List<List<String>>): Family {
            val ids = LinkedHashMap<String, Int>()
            val df = ArrayList<Int>()
            for (features in perSample) {
                for (f in features) {
                    val id = ids.getOrPut(f) { df.add(0); df.size - 1 }
                    df[id] = df[id] + 1
                }
            }
            val n = perSample.size.toDouble()
            val idf = DoubleArray(df.size) { sqrt((n + 1.0) / (df[it] + 1.0)) }
            val vectors = perSample.map { features ->
                val sortedIds = features.map { ids.getValue(it) }.sorted()
                normalized(sortedIds.toIntArray(), DoubleArray(sortedIds.size) { idf[sortedIds[it]] }, 0.0)
            }
            return Family(ids, idf, vectors, sqrt(n + 1.0))
        }

        /** Divise [weights] par la norme L2 (somme dans l'ordre des identifiants, puis [extraSquares]) ; vide si nulle. */
        private fun normalized(ids: IntArray, weights: DoubleArray, extraSquares: Double): Vector {
            var squares = 0.0
            for (w in weights) squares += w * w
            squares += extraSquares
            if (ids.isEmpty() || squares <= 0.0) return Vector.EMPTY
            val norm = sqrt(squares)
            return Vector(ids, DoubleArray(weights.size) { weights[it] / norm })
        }

        private const val FNV_OFFSET = -0x340d631b7bdddcdbL
        private const val FNV_PRIME = 0x100000001b3L

        /**
         * Empreinte FNV-1a 64 bits (écrite à la main, jamais `hashCode`) des échantillons triés par `taskId` : la clé du cache
         * ([ModelCache]). Couvre tout ce que le modèle lit : identifiant, minutes, type, points, priorité, étiquette,
         * récurrence, titre.
         */
        fun fingerprint(samples: List<DurationEstimator.Sample>): Long {
            var h = FNV_OFFSET
            fun mix(value: Long) {
                var v = value
                repeat(8) {
                    h = (h xor (v and 0xFF)) * FNV_PRIME
                    v = v ushr 8
                }
            }
            fun mix(text: String) {
                mix(text.length.toLong())
                for (c in text) mix(c.code.toLong())
            }
            for (s in samples.sortedBy { it.taskId }) {
                mix(s.taskId)
                mix(s.minutes.toLong())
                mix(s.taskType)
                mix((s.points ?: -1).toLong())
                mix((s.priority ?: -1).toLong())
                mix(s.projectTag)
                mix(if (s.recurring) 1L else 0L)
                mix(s.title)
            }
            return h
        }
    }
}

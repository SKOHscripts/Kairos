package com.skohscripts.kairos.core.team.forecast

import kotlin.random.Random

/**
 * Hasard des prévisions (docs/spec/equipe-simulation.md § Moteur, skill
 * `kairos-monte-carlo`). Pur et **reproductible au bit près** sur JVM, Android
 * et wasm : la seule source est `kotlin.random.Random(graine)` (même suite sur
 * toutes les cibles), et les graines des sous-générateurs sont dérivées
 * d'identités **stables** (graine, numéro de tirage, domaine, clé, indice) par
 * un mélange 64 bits écrit à la main ([mix], SplitMix64) et un hachage de texte
 * écrit à la main ([fnv1a64], FNV-1a 64 bits sur l'UTF-8). Jamais
 * `String.hashCode()`, jamais l'ordre de traitement : c'est ce qui donne les
 * **tirages communs** entre la situation réelle et ses scénarios (une tâche, un
 * membre-semaine voit le même aléa dans les deux).
 *
 * Aucune fonction transcendante (`exp`, `ln`, `pow`) dans le tirage : leur
 * dernier bit peut différer d'une cible à l'autre ; seules `+ - * /` et `sqrt`
 * (correctement arrondie par la norme IEEE 754) sont utilisées.
 */
object ForecastRandom {
    /** Constante de SplitMix64 : 2^64 / φ, en entier signé (0x9E3779B97F4A7C15). */
    const val GOLDEN: Long = -7046029254386353131L

    private const val MIX_1: Long = -4658895280553007687L // 0xBF58476D1CE4E5B9
    private const val MIX_2: Long = -7723592293110705685L // 0x94D049BB133111EB

    private const val FNV_OFFSET: Long = -3750763034362895579L // 0xCBF29CE484222325
    private const val FNV_PRIME: Long = 1099511628211L // 0x100000001B3

    /** Domaine d'un facteur d'erreur d'estimation (clé : hachage du `teamUid` de la tâche). */
    const val DOMAIN_ERROR: Long = 1L

    /** Domaine d'un facteur de capacité (clé : identifiant du membre ; indice : numéro de semaine). */
    const val DOMAIN_CAPACITY: Long = 2L

    /** Domaine d'un débit hebdomadaire (clé : identifiant du membre, 0 pour l'équipe ; indice : numéro de semaine). */
    const val DOMAIN_THROUGHPUT: Long = 3L

    /** Finaliseur de SplitMix64 : bijection de `Long` qui diffuse chaque bit sur tous les autres. */
    fun mix(x: Long): Long {
        var z = x
        z = (z xor (z ushr 30)) * MIX_1
        z = (z xor (z ushr 27)) * MIX_2
        return z xor (z ushr 31)
    }

    /** Sortie suivante de SplitMix64 pour l'état [state] (`mix(state + GOLDEN)`). */
    fun splitMix64(state: Long): Long = mix(state + GOLDEN)

    /** FNV-1a 64 bits des octets UTF-8 de [text] : hachage de texte stable, indépendant de la JVM. */
    fun fnv1a64(text: String): Long {
        var h = FNV_OFFSET
        for (b in text.encodeToByteArray()) {
            h = (h xor (b.toLong() and 0xFF)) * FNV_PRIME
        }
        return h
    }

    /** Graine de base du tirage numéro [draw] (à partir de 0) : dépend de la graine et du numéro, de rien d'autre. */
    fun drawBase(seed: Long, draw: Int): Long = splitMix64(splitMix64(seed) xor draw.toLong())

    /** Graine du sous-générateur (domaine, clé, indice) du tirage de base [drawBase]. */
    fun streamSeed(drawBase: Long, domain: Long, key: Long, index: Long): Long =
        splitMix64(splitMix64(splitMix64(drawBase xor domain) xor key) xor index)

    /** Sous-générateur `kotlin.random.Random` de (domaine, clé, indice) pour le tirage de base [drawBase]. */
    fun stream(drawBase: Long, domain: Long, key: Long, index: Long = 0L): Random =
        Random(streamSeed(drawBase, domain, key, index))
}

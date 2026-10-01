package com.skohscripts.kairos.core.team.forecast

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

/**
 * Valeurs **écrites en dur**, calculées hors de Kotlin (Python : SplitMix64, FNV-1a et
 * XorWow de `kotlin.random.Random` réécrits indépendamment) : un test de `commonTest`
 * tourne sur JVM, Android et wasm, et signale toute divergence du hasard entre cibles.
 */
class ForecastRandomTest {
    @Test
    fun splitMix64MatchesTheReferenceSequence() {
        // Suite de référence de SplitMix64 pour la graine 0 (Vigna) : états successifs 0, G, 2G…
        assertEquals(0xE220A8397B1DCDAFuL.toLong(), ForecastRandom.splitMix64(0L))
        assertEquals(0x6E789E6AA1B965F4uL.toLong(), ForecastRandom.splitMix64(ForecastRandom.GOLDEN))
        assertEquals(0x06C45D188009454FuL.toLong(), ForecastRandom.splitMix64(ForecastRandom.GOLDEN * 2))
        assertEquals(0L, ForecastRandom.mix(0L))
    }

    @Test
    fun fnv1aMatchesTheReferenceVectors() {
        assertEquals(0xCBF29CE484222325uL.toLong(), ForecastRandom.fnv1a64(""))
        assertEquals(0xAF63DC4C8601EC8CuL.toLong(), ForecastRandom.fnv1a64("a"))
        assertEquals(0x85944171F73967E8uL.toLong(), ForecastRandom.fnv1a64("foobar"))
        // Les octets sont ceux de l'UTF-8 : « é » = C3 A9, quelle que soit la plateforme.
        assertEquals(775207407765167617L, ForecastRandom.fnv1a64("é"))
        assertEquals(-8472660522307775933L, ForecastRandom.fnv1a64("uid-1"))
    }

    @Test
    fun derivedSeedsAreFixed() {
        assertEquals(6332618229526065668L, ForecastRandom.drawBase(42L, 0))
        assertEquals(-914255856146365723L, ForecastRandom.drawBase(42L, 1))
        val key = ForecastRandom.fnv1a64("uid-1")
        assertEquals(7081222375932805331L, ForecastRandom.streamSeed(ForecastRandom.drawBase(42L, 0), ForecastRandom.DOMAIN_ERROR, key, 0))
    }

    @Test
    fun kotlinRandomGivesTheSameSequenceOnEveryTarget() {
        val r = ForecastRandom.stream(ForecastRandom.drawBase(42L, 0), ForecastRandom.DOMAIN_ERROR, ForecastRandom.fnv1a64("uid-1"))
        assertEquals(0.7166763093610753, r.nextDouble())
        assertEquals(0.6124772592049574, r.nextDouble())
        assertEquals(5, r.nextInt(10))
        val s = Random(123456789L)
        assertEquals(listOf(630, 550, 934, 544, 539), List(5) { s.nextInt(1000) })
        val t = Random(123456789L)
        assertEquals(listOf(0.6504457469476764, 0.33694362964714153, 0.23977669693942294), List(3) { t.nextDouble() })
    }

    @Test
    fun aStreamDependsOnItsIdentityOnlyNotOnTheOrderOfCreation() {
        val base = ForecastRandom.drawBase(7L, 3)
        val a1 = ForecastRandom.stream(base, ForecastRandom.DOMAIN_ERROR, 11L).nextDouble()
        val b = ForecastRandom.stream(base, ForecastRandom.DOMAIN_ERROR, 12L).nextDouble()
        val a2 = ForecastRandom.stream(base, ForecastRandom.DOMAIN_ERROR, 11L).nextDouble()
        assertEquals(a1, a2)
        assertNotEquals(a1, b)
        // Domaine, indice, numéro de tirage et graine séparent les flux.
        val c = ForecastRandom.stream(base, ForecastRandom.DOMAIN_CAPACITY, 11L).nextDouble()
        val d = ForecastRandom.stream(base, ForecastRandom.DOMAIN_ERROR, 11L, 1L).nextDouble()
        val e = ForecastRandom.stream(ForecastRandom.drawBase(7L, 4), ForecastRandom.DOMAIN_ERROR, 11L).nextDouble()
        val f = ForecastRandom.stream(ForecastRandom.drawBase(8L, 3), ForecastRandom.DOMAIN_ERROR, 11L).nextDouble()
        assertEquals(5, setOf(a1, c, d, e, f).size)
    }
}

package com.skohscripts.kairos.core.sync

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Horloge hybride et marques (docs/spec/synchronisation.md § Horloge hybride et marques). */
class HlcTest {
    @Test
    fun tickIsMonotonicEvenWhenTheWallClockGoesBack() {
        var h = Hlc.ZERO
        val walls = listOf(1_000L, 1_000L, 999L, 500L, 2_000L, 1_500L)
        val out = walls.map { now -> Hlc.tick(h, now).also { h = it } }
        assertEquals(out.sorted(), out)
        assertEquals(out.size, out.toSet().size, "jamais deux fois la même horloge")
        assertEquals(Hlc(1_000, 0), out[0])
        assertEquals(Hlc(1_000, 1), out[1])
        assertEquals(Hlc(1_000, 3), out[3], "une horloge murale qui recule fait avancer le compteur")
        assertEquals(Hlc(2_000, 0), out[4])
        assertEquals(Hlc(2_000, 1), out[5])
    }

    @Test
    fun counterOverflowAdvancesTheWallByOneMillisecond() {
        assertEquals(Hlc(11, 0), Hlc.tick(Hlc(10, Hlc.MAX_COUNTER), 5))
    }

    @Test
    fun receiveNeverGoesBelowWhatItHasSeen() {
        // Une horloge locale très en retard (jours) qui lit une marque distante : la suivante la dépasse.
        val local = Hlc(1_000, 0)
        val remote = Hlc(9_000_000, 4)
        val received = Hlc.receive(local, remote, nowMillis = 500)
        assertTrue(received > remote)
        assertTrue(Hlc.tick(received, 500) > remote)
        // En avance : l'heure courante l'emporte.
        assertEquals(Hlc(10_000_000, 0), Hlc.receive(local, remote, 10_000_000))
    }

    @Test
    fun stampsHaveATotalOrderAndRoundTrip() {
        val a = Stamp(Hlc(5, 1), "aaa")
        val b = Stamp(Hlc(5, 1), "bbb")
        val c = Stamp(Hlc(5, 2), "aaa")
        assertEquals(listOf(a, b, c), listOf(c, b, a).sorted())
        for (s in listOf(a, b, c, Stamp.ORIGIN, Stamp(Hlc(1_761_000_000_000, 65_535), Stamp.LEGACY_DEVICE))) {
            assertEquals(s, Stamp.parse(s.toString()))
        }
        assertEquals("5.1@aaa", a.toString())
        assertEquals("0.0@", Stamp.ORIGIN.toString())
    }

    @Test
    fun malformedTextIsRejected() {
        for (bad in listOf("", "5", "5.", ".1", "-5.1", "5.-1", "5.70000", "a.1", "5.1x", "1e3.0")) assertNull(Hlc.parse(bad), bad)
        assertNull(Stamp.parse("5.1"))
        assertNull(Stamp.parse("5.1@" + "x".repeat(Stamp.MAX_DEVICE + 1)))
    }

    @Test
    fun contextCoversOriginAlwaysAndLegacyNever() {
        val seen = mapOf("dev1" to Hlc(10, 0))
        assertTrue(Context.covers(seen, Stamp.ORIGIN))
        assertTrue(Context.covers(emptyMap(), Stamp.ORIGIN))
        assertFalse(Context.covers(seen, Stamp(Hlc(0, 0), Stamp.LEGACY_DEVICE)))
        assertTrue(Context.covers(seen, Stamp(Hlc(10, 0), "dev1")))
        assertFalse(Context.covers(seen, Stamp(Hlc(10, 1), "dev1")))
        assertFalse(Context.covers(seen, Stamp(Hlc(1, 0), "dev2")))
    }

    @Test
    fun joinTakesThePointwiseMaximum() {
        val a = mapOf("x" to Hlc(5, 0), "y" to Hlc(1, 0))
        val b = mapOf("x" to Hlc(3, 0), "z" to Hlc(2, 0))
        assertEquals(mapOf("x" to Hlc(5, 0), "y" to Hlc(1, 0), "z" to Hlc(2, 0)), Context.join(a, b))
        assertEquals(Context.join(a, b), Context.join(b, a))
    }
}

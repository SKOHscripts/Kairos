package com.skohscripts.kairos.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AppVersionTest {
    @Test
    fun parsesReleasesAndPrereleases() {
        assertEquals(AppVersion(3, 0, 0), AppVersion.parse("3.0.0"))
        assertEquals(AppVersion(3, 0, 0), AppVersion.parse("v3.0.0"))
        assertEquals(AppVersion(3, 0, 0, AppVersion.Stage.ALPHA, 2), AppVersion.parse("3.0.0-alpha.2"))
        assertEquals(AppVersion(3, 1, 4, AppVersion.Stage.BETA, 1), AppVersion.parse("v3.1.4-beta.1"))
    }

    @Test
    fun rejectsOtherFormats() {
        for (bad in listOf("", "3.0", "3.0.0-rc1", "3.0.0-alpha", "3.0.0-alpha.0", "3.100.0", "x3.0.0")) {
            assertNull(AppVersion.parse(bad), bad)
        }
    }

    @Test
    fun versionCodeFollowsTheFormula() {
        assertEquals(30000, AppVersion.parse("3.0.0")!!.versionCode)
        assertEquals(29001, AppVersion.parse("3.0.0-alpha.1")!!.versionCode)
        assertEquals(29501, AppVersion.parse("3.0.0-beta.1")!!.versionCode)
        assertEquals(30102, AppVersion.parse("3.1.2")!!.versionCode)
    }

    @Test
    fun versionCodesIncreaseWithVersions() {
        val ordered = listOf("3.0.0-alpha.1", "3.0.0-alpha.7", "3.0.0-beta.1", "3.0.0-beta.3", "3.0.0", "3.0.1", "3.1.0")
            .map { AppVersion.parse(it)!! }
        assertEquals(ordered, ordered.sorted())
        assertEquals(ordered.map { it.versionCode }, ordered.map { it.versionCode }.sorted())
        // Toute version 3 passe au-dessus de la dernière version Python (2.6.0 = 20600).
        assertTrue(ordered.first().versionCode > 20600)
    }

    @Test
    fun buildVersionIsConsistent() {
        // gradle.properties : versionName et versionCode doivent suivre la formule.
        val current = AppVersion.current
        assertEquals(current.versionCode, KairosBuild.VERSION_CODE)
        assertEquals(KairosBuild.VERSION_NAME, current.toString())
        // Préversions (docs/spec/distribution.md § Cycle des versions) : seulement d'une version en .0, numéro de 1 à 98 (99
        // est le dernier chiffre d'une finale) ; une beta de correctif passerait sous la finale qu'elle précède.
        if (current.isPrerelease) {
            assertEquals(0, current.patch, "une préversion annonce une version X.Y.0")
            assertTrue(current.stageNumber in 1..AppVersion.MAX_PRERELEASE, "numéro de préversion de 1 à ${AppVersion.MAX_PRERELEASE}")
        }
    }

    @Test
    fun prereleaseCodesGrowFromOneMinorToTheNext() {
        // Jusqu'à la 4.1 (codes publiés, inchangés) : chaque canal (Preview, stable) croît.
        val old = listOf("4.0.0", "4.1.0-beta.1", "4.1.0-beta.99", "4.1.0").map { AppVersion.parse(it)!!.versionCode }
        assertEquals(listOf(40000, 39601, 39699, 40100), old)
    }

    @Test
    fun fromFourTwoBetasAndFinalsShareOneGrowingLine() {
        // Une seule app pour la beta et la stable (docs/spec/distribution.md § Versionnage, décision du 2026-11-15) : chaque
        // beta passe au-dessus de la finale précédente et sous la sienne.
        val names = listOf("4.1.0", "4.2.0-beta.1", "4.2.0-beta.98", "4.2.0", "4.2.1", "4.3.0-beta.1", "5.0.0-beta.1", "5.0.0")
        val codes = names.map { AppVersion.parse(it)!!.versionCode }
        assertEquals(listOf(40100, 4_020_001, 4_020_098, 4_020_099, 4_020_199, 4_030_001, 5_000_001, 5_000_099), codes)
        assertEquals(codes.sorted(), codes)
        assertEquals(names.map { AppVersion.parse(it)!! }.sorted(), names.map { AppVersion.parse(it)!! })
    }

    @Test
    fun desktopPackageVersionsGrowToo() {
        // MSI, DMG et .deb : trois nombres, croissants d'une beta à sa finale ; avant la 4.2, l'ancien produit Preview.
        assertEquals("4.1.0", AppVersion.parse("4.1.0")!!.packageVersion)
        assertEquals("1.0.39609", AppVersion.parse("4.1.0-beta.9")!!.packageVersion)
        assertEquals("4.2.1", AppVersion.parse("4.2.0-beta.1")!!.packageVersion)
        assertEquals("4.2.98", AppVersion.parse("4.2.0-beta.98")!!.packageVersion)
        assertEquals("4.2.99", AppVersion.parse("4.2.0")!!.packageVersion)
        assertEquals("4.2.199", AppVersion.parse("4.2.1")!!.packageVersion)
        assertEquals("4.3.1", AppVersion.parse("4.3.0-beta.1")!!.packageVersion)
    }
}

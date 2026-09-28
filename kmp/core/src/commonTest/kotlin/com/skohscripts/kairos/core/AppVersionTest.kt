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
    }
}

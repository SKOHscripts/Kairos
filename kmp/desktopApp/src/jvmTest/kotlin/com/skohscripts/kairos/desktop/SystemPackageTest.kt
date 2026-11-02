package com.skohscripts.kairos.desktop

import kotlinx.coroutines.runBlocking
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Installation par un gestionnaire de paquets (docs/spec/distribution.md § Paquet Arch Linux, docs/spec/mises-a-jour.md) :
 * le marqueur `kairos-managed` du paquet AUR coupe la vérification des mises à jour.
 */
class SystemPackageTest {
    private val tmp = createTempDirectory("managed").toFile()

    @AfterTest
    fun clean() {
        tmp.deleteRecursively()
    }

    /** Image jpackage Linux comme le paquet l'installe (`/opt/kairos/bin/Kairos`, `lib/app/`), avec ou sans marqueur. */
    private fun linuxImage(dir: String, marker: Boolean): String {
        val root = File(tmp, dir)
        File(root, "bin").mkdirs()
        File(root, "lib/app").mkdirs()
        if (marker) File(root, "lib/app/${SystemPackage.MARKER}").writeText("")
        return File(root, "bin/Kairos").path
    }

    @Test
    fun onlyAMarkedImageIsManagedByAPackageManager() {
        assertTrue(SystemPackage.detect(linuxImage("opt/kairos", marker = true), "Linux"))
        // Le même .deb installé à la main, ou une copie portable : pas de marqueur.
        assertFalse(SystemPackage.detect(linuxImage("deb/kairos", marker = false), "Linux"))
        // Hors image jpackage (gradle run, tests) ou sur macOS.
        assertFalse(SystemPackage.detect(null, "Linux"))
        assertFalse(SystemPackage.detect("", "Linux"))
        assertFalse(SystemPackage.detect(linuxImage("mac/kairos", marker = true), "Mac OS X"))
        // Le marqueur portable ne vaut pas installation gérée.
        val portable = linuxImage("portable/kairos", marker = false)
        File(File(portable).parentFile.parentFile, "lib/app/${PortableCopy.MARKER}").writeText("")
        assertFalse(SystemPackage.detect(portable, "Linux"))
    }

    @Test
    fun aManagedInstallHasNoUpdateServiceAndAnOtherOneKeepsIt() = runBlocking<Unit> {
        val managed = DesktopServices.open(File(tmp, "data-managed"), managed = true)
        assertNull(managed.updates)
        val byHand = DesktopServices.open(File(tmp, "data-by-hand"))
        assertNotNull(byHand.updates)
    }
}

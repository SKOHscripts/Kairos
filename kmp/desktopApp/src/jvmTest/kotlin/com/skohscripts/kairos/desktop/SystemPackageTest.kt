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
 * Installation par un gestionnaire de paquets ou un magasin (docs/spec/distribution.md § Paquet Arch Linux, § Microsoft
 * Store, docs/spec/mises-a-jour.md) : le marqueur `kairos-managed` du paquet AUR ou du paquet MSIX coupe la vérification
 * des mises à jour.
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

    /** Image jpackage Windows comme le paquet MSIX la contient (`Kairos.exe`, `app\`), avec ou sans marqueur. */
    private fun windowsImage(dir: String, marker: String?): String {
        val root = File(tmp, dir)
        File(root, "app").mkdirs()
        if (marker != null) File(root, "app/$marker").writeText("")
        return File(root, "Kairos.exe").path
    }

    @Test
    fun theMicrosoftStorePackageIsManagedButNotTheMsiNorAPortableCopy() {
        assertTrue(SystemPackage.detect(windowsImage("WindowsApps/Kairos", SystemPackage.MARKER), "Windows 11"))
        assertFalse(SystemPackage.detect(windowsImage("msi/Kairos", null), "Windows 11"))
        assertFalse(SystemPackage.detect(windowsImage("zip/Kairos", PortableCopy.MARKER), "Windows 10"))
    }

    @Test
    fun aManagedInstallHasNoUpdateServiceAndAnOtherOneKeepsIt() = runBlocking<Unit> {
        val managed = DesktopServices.open(File(tmp, "data-managed"), managed = true)
        assertNull(managed.updates)
        val byHand = DesktopServices.open(File(tmp, "data-by-hand"))
        assertNotNull(byHand.updates)
    }
}

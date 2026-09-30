package com.skohscripts.kairos.desktop

import com.skohscripts.kairos.ui.app.ShortcutStatus
import com.skohscripts.kairos.ui.app.ShortcutTarget
import kotlinx.coroutines.runBlocking
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** docs/spec/raccourci-portable.md */
class PortableShortcutsTest {
    private val tmp = createTempDirectory("portable").toFile()

    /** Image jpackage Linux (`bin/`, `lib/app/`) sous [dir], avec ou sans marqueur. */
    private fun linuxImage(dir: String, name: String = "Kairos", marker: Boolean = true): File {
        val root = File(tmp, dir)
        File(root, "bin").mkdirs()
        File(root, "lib/app").mkdirs()
        if (marker) File(root, "lib/app/${PortableCopy.MARKER}").writeText("")
        return File(root, "bin/$name")
    }

    @Test
    fun onlyAMarkedJpackageCopyOnLinuxOrWindowsIsPortable() {
        val launcher = linuxImage("Kairos").path
        val copy = assertNotNull(PortableCopy.detect(launcher, "Linux", "Kairos"))
        assertEquals(File(tmp, "Kairos"), copy.root)
        assertEquals(File(tmp, "Kairos/lib/Kairos.png"), copy.icon)
        assertEquals("kairos", copy.packageName)
        assertFalse(copy.windows)

        // Version installée (pas de marqueur), sources (pas de lanceur), macOS.
        assertNull(PortableCopy.detect(linuxImage("Installed", marker = false).path, "Linux", "Kairos"))
        assertNull(PortableCopy.detect(null, "Linux", "Kairos"))
        assertNull(PortableCopy.detect(launcher, "Mac OS X", "Kairos"))

        val win = File(tmp, "Win/Kairos Preview").apply { File(this, "app").mkdirs() }
        File(win, "app/${PortableCopy.MARKER}").writeText("")
        val windows = assertNotNull(PortableCopy.detect(File(win, "Kairos Preview.exe").path, "Windows 11", "Kairos Preview"))
        assertEquals(win, windows.root)
        assertTrue(windows.windows)
        assertEquals("kairos-preview", windows.packageName)
    }

    @Test
    fun execQuotesAndEscapesThePathAsTheDesktopEntrySpecAsks() {
        assertEquals("\"/opt/Kairos Preview/bin/Kairos Preview\"", PortableShortcuts.execArgument("/opt/Kairos Preview/bin/Kairos Preview"))
        // `$` et `"` : échappés par `\`, lui-même doublé par la règle des chaînes ; `%` doublé.
        assertEquals("\"/a\\\\\$b/c\\\\\"d/100%%\"", PortableShortcuts.execArgument("/a\$b/c\"d/100%"))
    }

    @Test
    fun theLinuxMenuEntryIsCreatedPointedElsewhereAndRemoved() = runBlocking<Unit> {
        val dataHome = File(tmp, "share")
        val state = File(tmp, "data/shortcuts.json")
        val env = mapOf("XDG_DATA_HOME" to dataHome.path)
        val copy = PortableCopy.detect(linuxImage("Kairos Preview", "Kairos Preview").path, "Linux", "Kairos Preview")!!
        val shortcuts = PortableShortcuts(copy, state, env)
        assertEquals(ShortcutTarget.APP_MENU, shortcuts.target)
        assertEquals(ShortcutStatus.MISSING, shortcuts.status.value)

        shortcuts.create()
        val entry = File(dataHome, "applications/kairos-preview.desktop")
        val text = entry.readText()
        assertTrue("Name=Kairos Preview" in text)
        assertTrue("Exec=\"${copy.launcher.absolutePath}\"" in text)
        assertTrue("Icon=${File(tmp, "Kairos Preview/lib/Kairos Preview.png").absolutePath}" in text)
        assertTrue("StartupWMClass=${WindowClass.NAME}" in text)
        assertTrue(entry.canExecute())
        assertEquals(ShortcutStatus.CREATED, shortcuts.status.value)

        // Une autre copie (nouvelle version dézippée ailleurs) voit un raccourci qui ne l'ouvre pas.
        val other = PortableCopy.detect(linuxImage("New/Kairos Preview", "Kairos Preview").path, "Linux", "Kairos Preview")!!
        val fromOther = PortableShortcuts(other, state, env)
        assertEquals(ShortcutStatus.ELSEWHERE, fromOther.status.value)
        fromOther.create()
        assertTrue("Exec=\"${other.launcher.absolutePath}\"" in entry.readText())
        assertEquals(ShortcutStatus.CREATED, PortableShortcuts(other, state, env).status.value)

        fromOther.remove()
        assertFalse(entry.exists())
        assertEquals(ShortcutStatus.MISSING, fromOther.status.value)
    }

    @Test
    fun noThanksIsRemembered() {
        val state = File(tmp, "data/shortcuts.json")
        val copy = PortableCopy.detect(linuxImage("Kairos").path, "Linux", "Kairos")!!
        val shortcuts = PortableShortcuts(copy, state, mapOf("XDG_DATA_HOME" to File(tmp, "share").path))
        assertFalse(shortcuts.declined.value)
        shortcuts.decline()
        assertTrue(shortcuts.declined.value)
        assertTrue(PortableShortcuts(copy, state).declined.value)
    }

    @Test
    fun windowsShortcutsAreMadeByPowerShellAndOnlyThoseAreRemoved() = runBlocking<Unit> {
        val root = File(tmp, "C'est/Kairos").apply { File(this, "app").mkdirs() }
        File(root, "app/${PortableCopy.MARKER}").writeText("")
        val copy = PortableCopy.detect(File(root, "Kairos.exe").path, "Windows 10", "Kairos")!!
        val start = File(tmp, "Programs/Kairos.lnk")
        val desktop = File(tmp, "Desktop/Kairos.lnk")
        var script = ""
        val shortcuts = PortableShortcuts(copy, File(tmp, "data/shortcuts.json"), powershell = {
            script = it
            listOf(start, desktop).onEach { f -> f.parentFile.mkdirs(); f.writeText("lnk") }.map { f -> f.path + "\r" }
        })
        assertEquals(ShortcutTarget.START_MENU_AND_DESKTOP, shortcuts.target)
        shortcuts.create()
        // Chemins entre apostrophes, l'apostrophe doublée ; icône = le lanceur.
        assertTrue("TargetPath = '${copy.launcher.absolutePath.replace("'", "''")}'" in script)
        assertTrue("IconLocation = '${copy.launcher.absolutePath.replace("'", "''")},0'" in script)
        assertTrue("@('Programs', 'Desktop')" in script)
        assertEquals(ShortcutStatus.CREATED, shortcuts.status.value)

        val unrelated = File(tmp, "Desktop/Other.lnk").apply { writeText("lnk") }
        shortcuts.remove()
        assertFalse(start.exists())
        assertFalse(desktop.exists())
        assertTrue(unrelated.exists())
    }
}

package com.skohscripts.kairos.desktop

import com.skohscripts.kairos.ui.app.NotifyState
import kotlinx.coroutines.runBlocking
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Notifications du bureau (docs/spec/temps-reel-chrono.md § Bureau, issue #219) : `notify-send` sous Linux, même sans plateau. */
class DesktopNotifierTest {
    @Test
    fun linuxWithNotifySendNotifiesWithoutATray() = runBlocking {
        val calls = mutableListOf<List<String>>()
        val notifier = DesktopNotifier(tray = null, osName = "Linux", notifySend = "/usr/bin/notify-send", run = { calls += it; true })
        assertEquals(NotifyState.ACTIVE, notifier.state.value)
        assertTrue(notifier.notify("Kairos · Report", "time for a short break?", "POMODORO"))
        assertEquals(listOf(listOf("/usr/bin/notify-send", "-a", "Kairos", "Kairos · Report", "time for a short break?")), calls)
        // La voie du chrono passe par la même commande (le système ne programme rien sur le bureau).
        assertTrue(notifier.notifyChrono("t", "b", "IDLE"))
        assertEquals(2, calls.size)
    }

    @Test
    fun aFailingCommandWithoutTrayReportsFailure() = runBlocking {
        val notifier = DesktopNotifier(tray = null, osName = "Linux", notifySend = "/usr/bin/notify-send", run = { false })
        assertFalse(notifier.notify("t", "b", "IDLE"))
    }

    @Test
    fun noNotifySendAndNoTrayIsUnavailable() {
        assertEquals(NotifyState.UNAVAILABLE, DesktopNotifier(tray = null, osName = "Linux", notifySend = null).state.value)
        // notify-send n'est utilisé que sous Linux.
        assertEquals(NotifyState.UNAVAILABLE, DesktopNotifier(tray = null, osName = "Windows 11", notifySend = "C:/notify-send").state.value)
    }

    @Test
    fun findOnPathFindsAnExecutable() {
        val dir = Files.createTempDirectory("kairos-path").toFile()
        val exe = java.io.File(dir, "notify-send").apply { writeText("#!/bin/sh\n"); setExecutable(true) }
        assertEquals(exe.path, DesktopNotifier.findOnPath("notify-send", dir.path))
        assertNull(DesktopNotifier.findOnPath("absent-tool", dir.path))
        assertNull(DesktopNotifier.findOnPath("notify-send", ""))
    }
}

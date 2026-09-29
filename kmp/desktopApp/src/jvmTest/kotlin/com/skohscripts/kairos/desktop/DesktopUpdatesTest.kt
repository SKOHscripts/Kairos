package com.skohscripts.kairos.desktop

import com.skohscripts.kairos.core.AppVersion
import com.skohscripts.kairos.ui.app.UpdateStatus
import kotlinx.coroutines.runBlocking
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

class DesktopUpdatesTest {
    private val clock = object : Clock {
        var now = Instant.parse("2026-09-29T08:00:00Z")
        override fun now() = now
    }
    private val installed = AppVersion.parse("3.0.0")!!
    private val file = File(createTempDirectory("updates").toFile(), "updates.json")
    private var calls = 0
    private var answer: () -> Pair<Int, String> = {
        200 to """{"tag_name": "v3.0.1", "html_url": "https://github.com/SKOHscripts/Kairos/releases/tag/v3.0.1", "prerelease": false}"""
    }

    private fun updates() = DesktopUpdates(file, clock, installed) { calls++; answer() }

    @Test
    fun aNewerReleaseIsRememberedAndCheckedAtMostEverySixHours() = runBlocking<Unit> {
        val u = updates()
        assertEquals(UpdateStatus.Never, u.status.value)
        u.check(force = false)
        val available = assertIs<UpdateStatus.Available>(u.status.value)
        assertEquals("3.0.1", available.version)
        assertEquals(1, calls)

        // Relancé 5 h plus tard : rien ne part, l'état revient du fichier.
        clock.now += 5.hours
        val again = updates()
        assertIs<UpdateStatus.Available>(again.status.value)
        again.check(force = false)
        assertEquals(1, calls)
        again.check(force = true)
        assertEquals(2, calls)
        clock.now += 6.hours
        again.check(force = false)
        assertEquals(3, calls)
    }

    @Test
    fun noReleaseAnOlderOneOrAFailureAreReportedAsSuch() = runBlocking<Unit> {
        val u = updates()
        answer = { 404 to "" }
        u.check(force = true)
        assertIs<UpdateStatus.UpToDate>(u.status.value)
        answer = { 200 to """{"tag_name": "v2.6.0", "html_url": "https://github.com/SKOHscripts/Kairos/releases/tag/v2.6.0"}""" }
        u.check(force = true)
        assertIs<UpdateStatus.UpToDate>(u.status.value)
        answer = { 503 to "" }
        u.check(force = true)
        assertIs<UpdateStatus.Failed>(u.status.value)
        answer = { throw java.io.IOException("offline") }
        u.check(force = true)
        assertIs<UpdateStatus.Failed>(u.status.value)
    }

    @Test
    fun laterHidesThisVersionAcrossLaunches() = runBlocking<Unit> {
        val u = updates()
        u.check(force = true)
        u.dismiss("3.0.1")
        assertEquals("3.0.1", updates().dismissed.value)
    }
}

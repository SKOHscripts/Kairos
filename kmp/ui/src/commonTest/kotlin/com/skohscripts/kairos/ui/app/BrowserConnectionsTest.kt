package com.skohscripts.kairos.ui.app

import com.skohscripts.kairos.core.connections.CalendarConnection
import com.skohscripts.kairos.core.connections.ConnectionError
import com.skohscripts.kairos.core.connections.ConnectionKind
import com.skohscripts.kairos.core.connections.SyncInterval
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

/** Fichier des connexions de la version web (docs/spec/connexions-en-ligne.md § Web) : commun, donc vérifié aussi en wasm. */
class BrowserConnectionsTest {
    private val full = BrowserConnections.Entry(
        "Work", "https://calendar.example/private.ics",
        CalendarConnection(
            0, ConnectionKind.URL, host = "calendar.example", syncEnabled = false, interval = SyncInterval.HOURLY,
            lastAttemptAt = Instant.parse("2026-11-21T09:00:00Z"), lastSyncAt = Instant.parse("2026-11-21T08:00:00Z"),
            lastError = ConnectionError.OFFLINE, failures = 2, nextAttemptAt = Instant.parse("2026-11-21T09:15:00Z"),
            plannedOn = "2026-11-21", redirectHost = "cdn.example",
        ),
    )

    @Test fun anEntryRoundTripsWithoutEtag() {
        val withEtag = full.copy(connection = full.connection.copy(etag = "\"abc\""))
        val text = BrowserConnections.encode(listOf(withEtag, full.copy(name = "Perso")))
        assertTrue("etag" !in text)
        assertEquals(listOf(full, full.copy(name = "Perso")), BrowserConnections.decode(text))
    }

    @Test fun anUnreadableOrForeignFileGivesNothing() {
        for (text in listOf(null, "", "not json", "[]", """{"version":2,"connections":[]}""", """{"version":1}""")) {
            assertEquals(emptyList(), BrowserConnections.decode(text), text)
        }
    }

    @Test fun anIncompleteEntryIsSkippedAndUnknownValuesFallBack() {
        val text = """{"version":1,"connections":[{"name":"","url":"https://a.example/x.ics"},{"name":"A"},
            {"name":"B","url":"https://b.example/x.ics","interval":"WEEKLY","lastError":"NEW","failures":-3,"lastSyncAt":"soon"}]}"""
        val entry = BrowserConnections.decode(text).single()
        assertEquals("B", entry.name)
        assertEquals(SyncInterval.ON_OPEN, entry.connection.interval)
        assertNull(entry.connection.lastError)
        assertEquals(0, entry.connection.failures)
        assertNull(entry.connection.lastSyncAt)
        assertTrue(entry.connection.syncEnabled)
    }

    @Test fun browserSecretsAreUnprotectedAndCountTheirChanges() {
        val secrets = BrowserSecrets()
        assertEquals(SecretLevel.NONE, secrets.level)
        secrets.put("url:1", "https://a.example")
        secrets.put("url:1", "https://a.example")
        assertEquals(1, secrets.version.value)
        secrets.delete("url:2")
        secrets.delete("url:1")
        assertEquals(2, secrets.version.value)
        assertEquals(emptySet(), secrets.keys())
    }
}

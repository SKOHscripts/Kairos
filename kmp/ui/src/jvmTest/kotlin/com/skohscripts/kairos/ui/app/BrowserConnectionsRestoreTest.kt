package com.skohscripts.kairos.ui.app

import com.skohscripts.kairos.core.calendar.CalendarImport
import com.skohscripts.kairos.core.connections.SyncInterval
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Rechargement de la version web (docs/spec/connexions-en-ligne.md § Web) : la base revient de l'instantané sans ses connexions,
 * puis le fichier `kairos-connections.json` les rattache par le nom du calendrier.
 */
class BrowserConnectionsRestoreTest {
    @Test fun aReloadReattachesByNameAndSkipsTheRest() = runBlocking {
        val before = onlineRepository()
        val secrets = BrowserSecrets()
        val online = DefaultOnlineConnections(before, BackupStore { _, _ -> }, FakeHttp(), secrets, this, { onlineNow }, browser = true)
        val ready = online.previewIcs("https://calendar.example/work.ics", false, null) as IcsPreviewResult.Ready
        val id = online.addIcs(ready, "Work", false)!!
        online.setInterval(id, SyncInterval.HOURLY)
        val text = BrowserConnections.encode(before, secrets)

        // Rechargement : un autre identifiant pour « Work », et un calendrier « Perso » qui n'est pas dans le fichier.
        val after = onlineRepository()
        after.importCalendar("Perso", false, CalendarImport.plan(onlineIcs, onlineToday, TimeZone.UTC), onlineToday)
        val workId = after.importCalendar("Work", false, CalendarImport.plan(onlineIcs, onlineToday, TimeZone.UTC), onlineToday)!!
        val restoredSecrets = BrowserSecrets()
        val extra = BrowserConnections.decode(text).single().let { it.copy(name = "Gone") }
        val withMissing = BrowserConnections.encode(BrowserConnections.decode(text) + extra)
        assertEquals(1, BrowserConnections.restore(after, restoredSecrets, withMissing))
        val row = after.connections.value.single()
        assertEquals(workId, row.sourceId)
        assertEquals(SyncInterval.HOURLY, row.interval)
        assertEquals("calendar.example", row.host)
        assertEquals("https://calendar.example/work.ics", restoredSecrets.get("url:$workId"))
        // Une seconde restauration ne touche pas une connexion déjà là.
        assertEquals(0, BrowserConnections.restore(after, restoredSecrets, withMissing))
    }

    @Test fun keepRewritesOnlyWhenTheTextChanges() = runBlocking {
        val repository = onlineRepository()
        val secrets = BrowserSecrets()
        val writes = mutableListOf<String>()
        val job = BrowserConnections.keep(this, repository, secrets) { writes += it }
        val online = DefaultOnlineConnections(repository, BackupStore { _, _ -> }, FakeHttp(), secrets, this, { onlineNow }, browser = true)
        suspend fun settle(count: Int) = withTimeout(5_000) { while (writes.size < count) delay(10) }
        settle(1)
        assertEquals(emptyList(), BrowserConnections.decode(writes.last()))
        val ready = online.previewIcs("https://calendar.example/work.ics", false, null) as IcsPreviewResult.Ready
        val id = online.addIcs(ready, "Work", false)!!
        settle(2)
        assertEquals("Work", BrowserConnections.decode(writes.last()).single().name)
        val count = writes.size
        secrets.put("url:$id", "https://calendar.example/work.ics")
        delay(100)
        assertEquals(count, writes.size)
        online.disconnect(id)
        settle(count + 1)
        assertTrue(BrowserConnections.decode(writes.last()).isEmpty())
        job.cancelAndJoin()
    }
}

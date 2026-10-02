package com.skohscripts.kairos.core.team.exchange

import com.skohscripts.kairos.core.model.KairosSnapshot
import com.skohscripts.kairos.core.team.Workspaces
import com.skohscripts.kairos.core.team.exchange.ExchangeFixtures.personal
import com.skohscripts.kairos.core.team.exchange.ExchangeFixtures.received
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class TeamOriginTest {
    @Test
    fun anOriginSurvivesEncodingEvenWithOddNames() {
        val origin = TeamOrigin("team-1", "Équipe | « Plateforme »", "Corentin\u001F", "member-1", "Léa")
        val parsed = TeamOrigin.parse(origin.encode())!!
        assertEquals(origin.copy(managerName = "Corentin"), parsed)
        assertEquals("team-1", parsed.key)
    }

    @Test
    fun unreadableOriginsAreNotOrigins() {
        assertNull(TeamOrigin.parse(null))
        assertNull(TeamOrigin.parse(""))
        assertNull(TeamOrigin.parse("n'importe quoi"))
        assertNull(TeamOrigin.parse("\u001F\u001F\u001F\u001F"))
        assertFalse(ReceivedTasks.isReceived(personal(1).copy(origin = "abc")))
        assertTrue(ReceivedTasks.isReceived(received(1, "a")))
    }

    @Test
    fun originsListsOneEntryPerManagerWithAtLeastOneLiveTask() {
        val other = ExchangeFixtures.origin.copy(teamUid = "team-2", managerName = "Albane", teamName = "Autre")
        val renamed = ExchangeFixtures.origin.copy(teamName = "Nouveau nom")
        val s = KairosSnapshot(
            tasks = listOf(
                received(1, "a", origin = ExchangeFixtures.origin), received(2, "b", origin = renamed), // même équipe : la plus récente donne les noms
                received(3, "c", origin = other),
                received(4, "d", origin = ExchangeFixtures.origin.copy(teamUid = "team-3", managerName = "Zoé"), removed = true), // que des retirées
                personal(5),
            ),
        )
        val origins = ReceivedTasks.origins(s)
        assertEquals(listOf("Albane", "Corentin"), origins.map { it.managerName })
        assertEquals("Nouveau nom", origins.last().teamName)
        assertTrue(ReceivedTasks.origins(KairosSnapshot(tasks = listOf(personal(1)))).isEmpty())
    }

    @Test
    fun aReceivedTaskIsTeamDataButStaysInThePersonalView() {
        val solo = KairosSnapshot(tasks = listOf(personal(1)))
        assertFalse(Workspaces.hasTeamData(solo))
        val receiver = KairosSnapshot(tasks = listOf(personal(1), received(2, "a")))
        assertTrue(Workspaces.hasTeamData(receiver))
        // Une tâche reçue est personnelle : le filtre de l'espace Perso rend la base telle quelle.
        assertSame(receiver, Workspaces.personalView(receiver))
    }
}

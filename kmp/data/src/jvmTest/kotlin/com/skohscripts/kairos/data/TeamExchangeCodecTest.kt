package com.skohscripts.kairos.data

import com.skohscripts.kairos.core.model.KairosSnapshot
import com.skohscripts.kairos.core.model.Note
import com.skohscripts.kairos.core.model.Task
import com.skohscripts.kairos.core.model.TaskStatus
import com.skohscripts.kairos.core.model.TimeBlock
import com.skohscripts.kairos.core.model.WorkSession
import com.skohscripts.kairos.core.team.exchange.ExternalBlocker
import com.skohscripts.kairos.core.team.exchange.PackDependency
import com.skohscripts.kairos.core.team.exchange.PackMember
import com.skohscripts.kairos.core.team.exchange.PackTask
import com.skohscripts.kairos.core.team.exchange.PackTeam
import com.skohscripts.kairos.core.team.exchange.ReceivedTasks
import com.skohscripts.kairos.core.team.exchange.ReportBuilder
import com.skohscripts.kairos.core.team.exchange.ReportSubtask
import com.skohscripts.kairos.core.team.exchange.ReportTask
import com.skohscripts.kairos.core.team.exchange.TeamOrigin
import com.skohscripts.kairos.core.team.exchange.TeamPack
import com.skohscripts.kairos.core.team.exchange.TeamReport
import com.skohscripts.kairos.data.TeamExchangeCodec.Kind
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Instant

/** Formats des paquets et des rapports (docs/spec/equipe-echanges.md § Formats). */
class TeamExchangeCodecTest {
    private val at = Instant.parse("2026-09-30T07:00:00Z")
    private val later = Instant.parse("2026-10-03T16:00:00Z")

    private val pack = TeamPack(
        packId = "pack-1",
        exportedAt = at,
        team = PackTeam("team-1", "Équipe Plateforme", "Corentin"),
        member = PackMember("member-lea", "Léa"),
        tasks = listOf(
            PackTask("t1", null, "Migration API", "Détail « accentué »", 1, 5, "Développement", LocalDate(2026, 10, 10), 480, 40),
            PackTask("t2", "t1", "Sous-tâche", priority = null, fibonacciPoints = null),
        ),
        dependencies = listOf(PackDependency("t2", "t1")),
        externalBlockers = listOf(ExternalBlocker("t1", "API v2", "Marc"), ExternalBlocker("t2", "Au backlog", null)),
    )

    private val report = TeamReport(
        reportedAt = later,
        teamUid = "team-1",
        member = PackMember("member-lea", "Léa"),
        tasks = listOf(
            ReportTask("t1", TaskStatus.TODO, null, LocalDate(2026, 10, 1), 70, 360),
            ReportTask("t2", TaskStatus.DONE, LocalDate(2026, 10, 2), null, 100, 0),
        ),
        newSubtasks = listOf(ReportSubtask("s1", "t1", "Écrire les tests", TaskStatus.DONE), ReportSubtask("s2", "s1", "Cas limites")),
    )

    private fun reason(block: () -> Unit) = assertFailsWith<ImportException> { block() }.reason

    private fun export(): String = ExportCodec.encode(KairosSnapshot(), "3.0.0", at)

    // --- Aller-retour ------------------------------------------------------------------------------------------------

    @Test
    fun aPackSurvivesTheRoundTrip() {
        val text = TeamExchangeCodec.encodePack(pack, "3.1.0")
        assertEquals(pack, TeamExchangeCodec.decodePack(text))
        assertTrue("\"format\": \"kairos-team-pack\"" in text)
        assertTrue("\"formatVersion\": 1" in text)
        assertTrue("\"appVersion\": \"3.1.0\"" in text)
        assertEquals("pack-1", Json.parseToJsonElement(text).jsonObject.getValue("packId").toString().trim('"'))
    }

    @Test
    fun aReportSurvivesTheRoundTrip() {
        val text = TeamExchangeCodec.encodeReport(report, "3.1.0")
        assertEquals(report, TeamExchangeCodec.decodeReport(text))
        assertTrue("\"format\": \"kairos-team-report\"" in text)
        assertTrue("\"status\": \"done\"" in text)
    }

    @Test
    fun theFieldsAreExactlyThoseOfTheSpec() {
        val packJson = Json.parseToJsonElement(TeamExchangeCodec.encodePack(pack, "3.1.0")).jsonObject
        assertEquals(
            setOf("format", "formatVersion", "appVersion", "packId", "exportedAt", "team", "member", "tasks", "dependencies", "externalBlockers"),
            packJson.keys,
        )
        assertEquals(setOf("uid", "name", "manager"), packJson.getValue("team").jsonObject.keys)
        assertEquals(setOf("uid", "name"), packJson.getValue("member").jsonObject.keys)
        val tasks = packJson.getValue("tasks") as JsonArray
        assertEquals(
            setOf("uid", "title", "description", "priority", "fibonacciPoints", "taskType", "deadline", "estimatedMinutes", "progressPercent"),
            tasks[0].jsonObject.keys,
        )
        // Les champs nuls ou vides sont omis.
        assertEquals(setOf("uid", "parentUid", "title"), tasks[1].jsonObject.keys)
        assertEquals(setOf("taskUid", "blockerUid"), (packJson.getValue("dependencies") as JsonArray)[0].jsonObject.keys)
        assertEquals(setOf("taskUid", "title", "assignee"), (packJson.getValue("externalBlockers") as JsonArray)[0].jsonObject.keys)
        assertEquals(setOf("taskUid", "title"), (packJson.getValue("externalBlockers") as JsonArray)[1].jsonObject.keys)

        val reportJson = Json.parseToJsonElement(TeamExchangeCodec.encodeReport(report, "3.1.0")).jsonObject
        assertEquals(setOf("format", "formatVersion", "appVersion", "reportedAt", "team", "member", "tasks", "newSubtasks"), reportJson.keys)
        assertEquals(setOf("uid"), reportJson.getValue("team").jsonObject.keys)
        val lines = reportJson.getValue("tasks") as JsonArray
        assertEquals(setOf("uid", "status", "startedOn", "progressPercent", "spentMinutes"), lines[0].jsonObject.keys)
        assertEquals(setOf("uid", "status", "doneOn", "progressPercent", "spentMinutes"), lines[1].jsonObject.keys)
        assertEquals(setOf("uid", "parentUid", "title", "status"), (reportJson.getValue("newSubtasks") as JsonArray)[0].jsonObject.keys)
        assertFalse("null" in TeamExchangeCodec.encodeReport(report, "3.1.0"))
    }

    @Test
    fun unknownFieldsAreIgnored() {
        val text = TeamExchangeCodec.encodePack(pack, "3.1.0").replace("\"packId\"", "\"futur\": {\"a\": 1},\n  \"packId\"")
        assertEquals(pack, TeamExchangeCodec.decodePack(text))
        val r = TeamExchangeCodec.encodeReport(report, "3.1.0").replace("\"uid\": \"t1\"", "\"uid\": \"t1\", \"nouveau\": true")
        assertEquals(report, TeamExchangeCodec.decodeReport(r))
    }

    // --- Aiguillage et refus croisés ---------------------------------------------------------------------------------

    @Test
    fun detectReadsTheFormatFieldFirst() {
        assertEquals(Kind.EXPORT, TeamExchangeCodec.detect(export()))
        assertEquals(Kind.PACK, TeamExchangeCodec.detect(TeamExchangeCodec.encodePack(pack, "3")))
        assertEquals(Kind.REPORT, TeamExchangeCodec.detect(TeamExchangeCodec.encodeReport(report, "3")))
        // Le reste du fichier n'est pas lu : un fichier d'une version future s'aiguille quand même.
        assertEquals(Kind.PACK, TeamExchangeCodec.detect("""{"format":"kairos-team-pack","formatVersion":9,"tout":"autre"}"""))
        for (bad in listOf("", "pas du json", "[]", "42", "{}", """{"format":42}""", """{"format":"kairos-export-2"}""", """{"format":null}""", """{"format":"Kairos-Team-Pack"}""")) {
            assertEquals(ImportException.Reason.NOT_AN_EXPORT, reason { TeamExchangeCodec.detect(bad) }, bad)
        }
    }

    @Test
    fun anExportIsNeverTakenForAPackOrAReportAndConversely() {
        val packText = TeamExchangeCodec.encodePack(pack, "3")
        val reportText = TeamExchangeCodec.encodeReport(report, "3")
        val exportText = export()
        val notAnExport = ImportException.Reason.NOT_AN_EXPORT
        assertEquals(notAnExport, reason { TeamExchangeCodec.decodePack(exportText) })
        assertEquals(notAnExport, reason { TeamExchangeCodec.decodeReport(exportText) })
        assertEquals(notAnExport, reason { TeamExchangeCodec.decodePack(reportText) })
        assertEquals(notAnExport, reason { TeamExchangeCodec.decodeReport(packText) })
        assertEquals(notAnExport, reason { ExportCodec.decode(packText) })
        assertEquals(notAnExport, reason { ExportCodec.decode(reportText) })
        // Même un paquet vide (qui a la forme d'un export vide) n'est pas un export.
        assertEquals(notAnExport, reason { ExportCodec.decode(TeamExchangeCodec.encodePack(pack.copy(tasks = emptyList(), dependencies = emptyList(), externalBlockers = emptyList()), "3")) })
        assertEquals(notAnExport, reason { TeamExchangeCodec.decodePack("pas du json") })
        assertEquals(notAnExport, reason { TeamExchangeCodec.decodeReport("[]") })
    }

    @Test
    fun aNewerFormatVersionIsTooNew() {
        val pack2 = TeamExchangeCodec.encodePack(pack, "3").replace("\"formatVersion\": 1", "\"formatVersion\": 2")
        assertEquals(ImportException.Reason.TOO_NEW, reason { TeamExchangeCodec.decodePack(pack2) })
        val report2 = TeamExchangeCodec.encodeReport(report, "3").replace("\"formatVersion\": 1", "\"formatVersion\": 2")
        assertEquals(ImportException.Reason.TOO_NEW, reason { TeamExchangeCodec.decodeReport(report2) })
        // Même si la structure a changé dans la version future.
        assertEquals(ImportException.Reason.TOO_NEW, reason { TeamExchangeCodec.decodePack("""{"format":"kairos-team-pack","formatVersion":3,"autre":1}""") })
    }

    @Test
    fun unreadableValuesAreCorrupted() {
        val packText = TeamExchangeCodec.encodePack(pack, "3")
        val corrupted = ImportException.Reason.CORRUPTED
        assertEquals(corrupted, reason { TeamExchangeCodec.decodePack(packText.replace("2026-10-10", "pas-une-date")) })
        assertEquals(corrupted, reason { TeamExchangeCodec.decodePack(packText.replace("2026-09-30T07:00:00Z", "hier")) })
        assertEquals(corrupted, reason { TeamExchangeCodec.decodePack(packText.replace("\"pack-1\"", "\"\"")) })
        assertEquals(corrupted, reason { TeamExchangeCodec.decodePack(packText.replace("\"member-lea\"", "\" \"")) })
        // Un champ obligatoire manquant ou mal typé est lui aussi illisible (le format et la version, eux, sont bons).
        assertEquals(corrupted, reason { TeamExchangeCodec.decodePack("""{"format":"kairos-team-pack","formatVersion":1}""") })
        val reportText = TeamExchangeCodec.encodeReport(report, "3")
        assertEquals(corrupted, reason { TeamExchangeCodec.decodeReport(reportText.replace("2026-10-02", "n'importe quoi")) })
        assertEquals(corrupted, reason { TeamExchangeCodec.decodeReport(reportText.replace("2026-10-03T16:00:00Z", "demain")) })
        assertEquals(corrupted, reason { TeamExchangeCodec.decodeReport(reportText.replace("\"spentMinutes\": 360", "\"spentMinutes\": \"beaucoup\"")) })
    }

    @Test
    fun anUnknownStatusReadsAsTodo() {
        val text = TeamExchangeCodec.encodeReport(report, "3").replace("\"status\": \"done\"", "\"status\": \"archived\"")
        assertTrue(TeamExchangeCodec.decodeReport(text).tasks.all { it.status == TaskStatus.TODO })
    }

    // --- Rien de personnel dans un rapport ---------------------------------------------------------------------------------

    @Test
    fun reportLeaksNothingPersonal() {
        val stamp = at
        val origin = TeamOrigin("team-1", "Équipe Plateforme", "Corentin", "member-lea", "Léa")
        val received = Task(
            1, "Migration API", description = "Brief confidentiel du manager", projectTag = "Projet X", createdAt = stamp, updatedAt = stamp,
            teamUid = "t1", origin = origin.encode(), manualTimeSpentMinutes = 30, priority = 1, fibonacciPoints = 3,
        )
        val snapshot = KairosSnapshot(
            tasks = listOf(
                received,
                Task(2, "Rendez-vous médecin", description = "Mon secret", createdAt = stamp, updatedAt = stamp),
                Task(3, "Ma sous-tâche", createdAt = stamp, updatedAt = stamp, parentId = 1, teamUid = "s1"),
                Task(4, "Cadeau d'anniversaire", createdAt = stamp, updatedAt = stamp, parentId = 2, teamUid = "s9"),
            ),
            notes = listOf(Note(1, "Idée de démission", createdAt = stamp, updatedAt = stamp)),
            timeBlocks = listOf(TimeBlock(1, "Thérapie", LocalDateTime(2026, 10, 3, 9, 0), LocalDateTime(2026, 10, 3, 10, 0), createdAt = stamp)),
            workSessions = listOf(
                WorkSession(1, 1, Instant.parse("2026-10-01T09:00:00Z"), Instant.parse("2026-10-01T10:00:00Z"), stamp),
                WorkSession(2, 2, Instant.parse("2026-10-01T11:00:00Z"), Instant.parse("2026-10-01T12:00:00Z"), stamp),
            ),
        )
        val text = TeamExchangeCodec.encodeReport(ReportBuilder.build(snapshot, "team-1", later, TimeZone.UTC)!!, "3.1.0")
        for (secret in listOf("Rendez-vous médecin", "Mon secret", "Idée de démission", "Thérapie", "Brief confidentiel", "Projet X", "Cadeau d'anniversaire", "2026-10-01T09", "2026-10-01T11")) {
            assertFalse(secret in text, secret)
        }
        val json = Json.parseToJsonElement(text).jsonObject
        // Une seule tâche rapportée (la reçue) : 90 min = 60 de session + 30 saisies ; une sous-tâche du membre sous elle.
        val tasks = json.getValue("tasks") as JsonArray
        assertEquals(1, tasks.size)
        assertEquals("90", tasks[0].jsonObject.getValue("spentMinutes").toString())
        assertEquals(listOf("Ma sous-tâche"), (json.getValue("newSubtasks") as JsonArray).map { it.jsonObject.getValue("title").toString().trim('"') })
        // Pas de tâche reçue, pas de rapport.
        assertEquals(null, ReportBuilder.build(snapshot.copy(tasks = snapshot.tasks.drop(1)), "team-1", later, TimeZone.UTC))
        assertTrue(ReceivedTasks.isReceived(received))
    }

    // --- Export complet : champs ajoutés par le jalon ----------------------------------------------------------------------

    @Test
    fun theFullExportCarriesTheExchangeFields() {
        val stamp = at
        val member = com.skohscripts.kairos.core.team.TeamMember(
            1, "member-lea", "Léa", hoursPerDay = 7.0, createdAt = stamp, updatedAt = stamp, lastReportAt = later,
        )
        val team = Task(1, "Équipe", createdAt = stamp, updatedAt = stamp, space = com.skohscripts.kairos.core.model.TaskSpace.TEAM, assigneeId = 1, teamUid = "t1", reportedMinutes = 360)
        val received = Task(2, "Reçue", createdAt = stamp, updatedAt = stamp, teamUid = "t9", origin = "o", originRemoved = true)
        val snapshot = KairosSnapshot(tasks = listOf(team, received, Task(3, "Perso", createdAt = stamp, updatedAt = stamp)), members = listOf(member))
        val text = ExportCodec.encode(snapshot, "3.1.0", stamp)
        assertEquals(snapshot, ExportCodec.decode(text))
        assertTrue("\"formatVersion\": 2" in text)
        // Une tâche reçue suffit à passer en version 2, et n'écrit « originRemoved » que si elle est retirée.
        val onlyReceived = KairosSnapshot(tasks = listOf(Task(1, "Reçue", createdAt = stamp, updatedAt = stamp, teamUid = "t9", origin = "o")))
        val v2 = ExportCodec.encode(onlyReceived, "3.1.0", stamp)
        assertTrue("\"formatVersion\": 2" in v2)
        assertFalse("originRemoved" in v2)
        assertFalse("reportedMinutes" in v2)
        assertEquals(onlyReceived, ExportCodec.decode(v2))
    }
}

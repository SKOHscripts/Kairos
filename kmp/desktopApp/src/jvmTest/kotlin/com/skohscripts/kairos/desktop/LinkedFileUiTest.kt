package com.skohscripts.kairos.desktop

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.runComposeUiTest
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.skohscripts.kairos.core.model.KairosSnapshot
import com.skohscripts.kairos.data.ExportCodec
import com.skohscripts.kairos.data.KairosRepository
import com.skohscripts.kairos.data.KairosStore
import com.skohscripts.kairos.ui.KairosApp
import com.skohscripts.kairos.ui.Platform
import com.skohscripts.kairos.ui.app.AppServices
import com.skohscripts.kairos.ui.app.BackupStore
import com.skohscripts.kairos.ui.app.FileService
import com.skohscripts.kairos.ui.app.LinkedFileIo
import com.skohscripts.kairos.ui.app.LinkedFileState
import com.skohscripts.kairos.ui.app.LinkedFileSync
import com.skohscripts.kairos.ui.app.SavedLink
import com.skohscripts.kairos.ui.navigation.Destination
import com.skohscripts.kairos.ui.navigation.TeamDestination
import com.skohscripts.kairos.ui.settings.SettingsCategory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import java.util.Locale
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

/**
 * Fichier lié du bureau (issue #137, docs/spec/export-import.md § Fichier lié du bureau et d'Android) : proposé dans la
 * carte Données seulement, « Créer un fichier », « Délier » ; un fichier écrit par un autre appareil met un bandeau dans la
 * vue Jour et se fusionne après confirmation (docs/spec/synchronisation.md § Premier rapprochement) ; ouvrir un fichier sur
 * un appareil qui a des données propose « Fusionner » ou « Remplacer ».
 */
@OptIn(ExperimentalTestApi::class)
class LinkedFileUiTest {
    private val now = Instant.parse("2026-10-25T08:00:00Z")
    private val clock = object : Clock {
        override fun now() = now
    }
    private val saved = Locale.getDefault()

    @BeforeTest
    fun english() = Locale.setDefault(Locale.ENGLISH)

    @AfterTest
    fun restore() = Locale.setDefault(saved)

    private class FakeIo : LinkedFileIo {
        val files = mutableMapOf<String, String>()
        var link: SavedLink? = null
        override suspend fun pickCreate(suggestedName: String) = "/sync/$suggestedName"
        override suspend fun pickOpen() = "/sync/kairos.json"
        override suspend fun read(target: String) = files[target] ?: error("absent")
        override suspend fun write(target: String, text: String) {
            files[target] = text
        }
        override fun displayName(target: String) = target.substringAfterLast('/')
        override suspend fun loadLink() = link
        override suspend fun saveLink(link: SavedLink?) {
            this.link = link
        }
    }

    private val backups = mutableMapOf<String, String>()

    private fun setUp(io: FakeIo): Pair<AppServices, LinkedFileSync> = runBlocking {
        val store = BackupStore { name, text -> backups[name] = text }
        val sync = LinkedFileSync(io, CoroutineScope(SupervisorJob() + Dispatchers.Default), clock, "Test", store)
        val repo = KairosRepository.open(KairosStore.open(JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)), { KairosSnapshot() }, clock, onChanged = { sync.changed(it) })
        sync.start(repo)
        val files = object : FileService {
            override suspend fun saveText(suggestedName: String, text: String) = true
            override suspend fun openText(): String? = null
        }
        AppServices(repo, files, store, linkedStorage = sync, clock = clock) to sync
    }

    private fun ComposeUiTest.waitText(text: String, substring: Boolean = false) =
        waitUntil(conditionDescription = text, timeoutMillis = 10_000) { onAllNodesWithText(text, substring = substring).fetchSemanticsNodes().isNotEmpty() }

    @Test
    fun theDataCardOffersALinkedFileWithoutNaggingInTheDayView() = runComposeUiTest {
        val io = FakeIo()
        val (services, sync) = setUp(io)
        setContent { KairosApp(Platform.DESKTOP, Destination.DAY, TeamDestination.BOARD) { services } }
        waitText("Capture")
        // Rien de lié : aucun bandeau dans la vue Jour.
        assertTrue(onAllNodesWithText("up-to-date copy", substring = true).fetchSemanticsNodes().isEmpty())
        openSettings(SettingsCategory.DATA)
        waitText("up-to-date copy", substring = true)
        onNodeWithText("Create a file").performScrollTo().performClick()
        waitText("Also saved in the file “kairos.json”.")
        assertEquals(LinkedFileState.Linked("kairos.json"), sync.state.value)
        assertTrue(ExportCodec.decode(io.files.getValue("/sync/kairos.json")).tasks.isEmpty())
        onNodeWithText("Unlink").performScrollTo().performClick()
        waitText("Create a file")
        assertEquals(LinkedFileState.NotLinked, sync.state.value)
    }

    /**
     * Le fichier d'un autre appareil d'avant la 4.0 (format 1) : deux tâches, créées une heure plus tôt (sinon la tâche 1 des deux
     * appareils, née à la même milliseconde, aurait le même `uid` de migration).
     */
    private suspend fun otherDevicesFile(): String {
        val earlier = object : Clock {
            override fun now() = now - 1.hours
        }
        val other = KairosRepository.open(KairosStore.open(JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)), { KairosSnapshot() }, earlier)
        other.createTask("Venue d'ailleurs")
        other.createTask("Elle aussi")
        return ExportCodec.encode(other.snapshot.value, "3.10.0", now)
    }

    @Test
    fun aFileWrittenByAnotherDeviceAsksBeforeMergingThenKeepsBothSides() = runComposeUiTest {
        val io = FakeIo()
        val (services, sync) = setUp(io)
        runBlocking {
            services.repository.createTask("Locale")
            sync.createFile()
            io.files["/sync/kairos.json"] = otherDevicesFile()
            sync.mergeNow()
        }
        assertTrue(sync.state.value is LinkedFileState.NeedsConfirmation)
        setContent { KairosApp(Platform.DESKTOP, Destination.DAY, TeamDestination.BOARD) { services } }
        waitText("was written by another device", substring = true)
        waitText("Tasks only here: 1 · only in the file: 2 · different: 0", substring = true)
        onNodeWithText("Merge").performScrollTo().performClick()
        waitUntil(timeoutMillis = 10_000) { services.repository.snapshot.value.tasks.map { it.title }.toSet() == setOf("Locale", "Venue d'ailleurs", "Elle aussi") }
        waitUntil(timeoutMillis = 10_000) { sync.state.value == LinkedFileState.Linked("kairos.json") }
        // Le fichier a reçu la tâche d'ici, au format synchronisé.
        assertEquals(3, ExportCodec.decodeSynced(io.files.getValue("/sync/kairos.json")).snapshot.tasks.size)
    }

    @Test
    fun openingAFileOnADeviceWithDataOffersMergeOrReplace() = runComposeUiTest {
        val io = FakeIo()
        val (services, _) = setUp(io)
        runBlocking {
            services.repository.createTask("Locale")
            io.files["/sync/kairos.json"] = otherDevicesFile()
        }
        setContent { KairosApp(Platform.DESKTOP, Destination.DAY, TeamDestination.BOARD) { services } }
        openSettings(SettingsCategory.DATA)
        onNodeWithText("Open a Kairos file").performScrollTo().performClick()
        waitText("Open “kairos.json”")
        onAllNodesWithText("Merge").onLast().performClick()
        waitUntil(timeoutMillis = 10_000) { services.repository.snapshot.value.tasks.map { it.title }.toSet() == setOf("Locale", "Venue d'ailleurs", "Elle aussi") }
    }

    @Test
    fun replacingWithAnOpenedFileBacksUpFirst() = runComposeUiTest {
        val io = FakeIo()
        val (services, sync) = setUp(io)
        runBlocking {
            services.repository.createTask("Locale")
            io.files["/sync/kairos.json"] = otherDevicesFile()
        }
        setContent { KairosApp(Platform.DESKTOP, Destination.DAY, TeamDestination.BOARD) { services } }
        openSettings(SettingsCategory.DATA)
        onNodeWithText("Open a Kairos file").performScrollTo().performClick()
        waitText("Open “kairos.json”")
        onAllNodesWithText("Replace").onLast().performClick()
        waitUntil(timeoutMillis = 10_000) { services.repository.snapshot.value.tasks.map { it.title }.toSet() == setOf("Venue d'ailleurs", "Elle aussi") }
        waitUntil(timeoutMillis = 10_000) { sync.state.value == LinkedFileState.Linked("kairos.json") }
        assertTrue(backups.keys.any { it.startsWith("avant-import-") })
    }
}

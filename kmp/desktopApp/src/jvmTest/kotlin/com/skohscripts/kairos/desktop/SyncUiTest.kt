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
import com.skohscripts.kairos.ui.navigation.Destination
import com.skohscripts.kairos.ui.navigation.TeamDestination
import com.skohscripts.kairos.ui.settings.SettingsCategory
import kotlinx.coroutines.runBlocking
import java.util.Locale
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/**
 * Interface de la synchronisation (docs/spec/synchronisation.md § Interface) : bandeau et dialogue « À revoir », « Prendre
 * l'autre », « Fusionner un fichier Kairos… » avec son aperçu et le message de fusion, nom de l'appareil dans la carte Données.
 */
@OptIn(ExperimentalTestApi::class)
class SyncUiTest {
    private var now = Instant.parse("2026-11-04T08:00:00Z")
    private val clock = object : Clock {
        override fun now(): Instant = now.also { now += 1.minutes }
    }
    private val saved = Locale.getDefault()
    private val backups = mutableMapOf<String, String>()

    @BeforeTest
    fun english() = Locale.setDefault(Locale.ENGLISH)

    @AfterTest
    fun restore() = Locale.setDefault(saved)

    private suspend fun repo(): KairosRepository =
        KairosRepository.open(KairosStore.open(JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)), { KairosSnapshot() }, clock)

    private fun KairosRepository.export(): String = ExportCodec.encode(snapshot.value, "4.0.0", now, sync.value)

    private suspend fun KairosRepository.pull(from: KairosRepository) = merge(ExportCodec.decodeSynced(from.export())) { true }

    private fun services(repository: KairosRepository, opened: String? = null) = AppServices(
        repository,
        object : FileService {
            override suspend fun saveText(suggestedName: String, text: String) = true
            override suspend fun openText(): String? = opened
        },
        BackupStore { name, text -> backups[name] = text },
        clock = clock,
        deviceLabel = "Test",
    )

    private fun ComposeUiTest.waitText(text: String, substring: Boolean = false) =
        waitUntil(conditionDescription = text, timeoutMillis = 10_000) { onAllNodesWithText(text, substring = substring).fetchSemanticsNodes().isNotEmpty() }

    @Test
    fun aConflictIsReviewedFromTheDayViewAndTheOtherValueCanBeTaken() = runComposeUiTest {
        val a = runBlocking { repo() }
        runBlocking {
            a.enableSync("PC")
            a.createTask("Rapport")
            val b = repo()
            b.enableSync("Phone")
            b.pull(a)
            a.pull(b)
            // Chacun change la priorité sans avoir vu l'autre : la plus récente (B) s'applique, celle de A va dans « À revoir ».
            a.setPriority(a.snapshot.value.tasks.single().id, 0)
            b.setPriority(b.snapshot.value.tasks.single().id, 2)
            a.pull(b)
        }
        assertEquals(2, a.snapshot.value.tasks.single().priority)
        assertEquals(1, a.syncReview.value.size)
        setContent { KairosApp(Platform.DESKTOP, Destination.DAY, TeamDestination.BOARD) { services(a) } }
        waitText("To review after a merge: 1.")
        onNodeWithText("See").performScrollTo().performClick()
        waitText("To review")
        waitText("Priority")
        waitText("Applied: 2 (Phone, ", substring = true)
        waitText("Other value: 0 (PC, ", substring = true)
        onNodeWithText("Take the other").performClick()
        waitUntil(timeoutMillis = 10_000) { a.snapshot.value.tasks.single().priority == 0 }
        waitUntil(timeoutMillis = 10_000) { a.syncReview.value.isEmpty() }
        waitText("Nothing to review.")
    }

    @Test
    fun aKairosFileIsMergedByHandAfterItsPreview() = runComposeUiTest {
        val a = runBlocking { repo() }
        val file = runBlocking {
            a.createTask("Ici")
            val other = repo()
            other.enableSync("Phone")
            other.createTask("Venue d'ailleurs")
            other.export()
        }
        setContent { KairosApp(Platform.DESKTOP, Destination.DAY, TeamDestination.BOARD) { services(a, opened = file) } }
        openSettings(SettingsCategory.DATA)
        onNodeWithText("Merge a Kairos file…").performScrollTo().performClick()
        waitText("Merge this file?")
        waitText("Tasks added: 1 · changed: 0 · removed: 0.", substring = true)
        onAllNodesWithText("Merge").onLast().performClick()
        waitUntil(timeoutMillis = 10_000) { a.snapshot.value.tasks.map { it.title }.toSet() == setOf("Ici", "Venue d'ailleurs") }
        waitText("Merged. Added: 1", substring = true)
        // La base synchronise désormais : la carte Données nomme l'appareil et dit la dernière fusion.
        waitText("This device: Test · ", substring = true)
        waitText("Last merge:", substring = true)
        assertTrue(a.sync.value!!.label.startsWith("Test · "))
    }
}

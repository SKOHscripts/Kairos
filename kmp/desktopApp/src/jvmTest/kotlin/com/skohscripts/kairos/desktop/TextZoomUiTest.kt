package com.skohscripts.kairos.desktop

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.height
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.skohscripts.kairos.core.model.KairosSnapshot
import com.skohscripts.kairos.data.KairosRepository
import com.skohscripts.kairos.data.KairosStore
import com.skohscripts.kairos.ui.KairosApp
import com.skohscripts.kairos.ui.Platform
import com.skohscripts.kairos.ui.app.AppServices
import com.skohscripts.kairos.ui.app.FileService
import com.skohscripts.kairos.ui.navigation.Destination
import com.skohscripts.kairos.ui.settings.SettingsCategory
import kotlinx.coroutines.runBlocking
import java.util.Locale
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant

/** Zoom du texte (docs/spec/apparence.md § Taille du texte, issue #229) : enregistré à « Save », il agrandit le texte, pas les dp. */
@OptIn(ExperimentalTestApi::class)
class TextZoomUiTest {
    private val clock = object : Clock {
        override fun now() = Instant.parse("2026-11-09T07:00:00Z")
    }
    private val saved = Locale.getDefault()

    @BeforeTest
    fun english() = Locale.setDefault(Locale.ENGLISH)

    @AfterTest
    fun restore() = Locale.setDefault(saved)

    private fun services(): AppServices = runBlocking {
        val repository = KairosRepository.open(KairosStore.open(JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)), { KairosSnapshot() }, clock)
        val files = object : FileService {
            override suspend fun saveText(suggestedName: String, text: String) = false
            override suspend fun openText(): String? = null
        }
        AppServices(repository, files, { _, _ -> }, clock = clock)
    }

    private fun ComposeUiTest.waitText(text: String) =
        waitUntil(conditionDescription = "texte « $text »", timeoutMillis = 15_000) { onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }

    @Test
    fun choosingAStepAndSavingGrowsTheTextButNotTheTouchTargets() = runComposeUiTest {
        val services = services()
        setContent { KairosApp(Platform.DESKTOP, Destination.DAY, systemDark = false) { services } }
        openSettings(SettingsCategory.GENERAL)
        waitText("Text size")
        val labelBefore = onNodeWithText("Text size").getUnclippedBoundsInRoot().height
        val segmentBefore = onNodeWithText("100 %").getUnclippedBoundsInRoot().height
        assertEquals(100, services.repository.snapshot.value.settings.textZoom)

        onNodeWithText("150 %").performScrollTo().performClick()
        // Rien ne change avant « Save ».
        assertEquals(100, services.repository.snapshot.value.settings.textZoom)
        onNodeWithText("Save").performClick()
        waitUntil(timeoutMillis = 15_000) { services.repository.snapshot.value.settings.textZoom == 150 }
        waitUntil(timeoutMillis = 15_000) { onNodeWithText("Text size").getUnclippedBoundsInRoot().height > labelBefore }

        val ratio = onNodeWithText("Text size").getUnclippedBoundsInRoot().height / labelBefore
        assertTrue(ratio in 1.3f..1.7f, "le texte grandit d'environ 50 % : $ratio")
        assertTrue(onNodeWithText("100 %").getUnclippedBoundsInRoot().height >= segmentBefore, "le segment ne rétrécit pas")
    }
}

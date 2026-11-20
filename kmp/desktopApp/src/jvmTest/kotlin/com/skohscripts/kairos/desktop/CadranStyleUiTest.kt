package com.skohscripts.kairos.desktop

import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.runComposeUiTest
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.skohscripts.kairos.core.model.KairosSnapshot
import com.skohscripts.kairos.core.model.Settings
import com.skohscripts.kairos.data.KairosRepository
import com.skohscripts.kairos.data.KairosStore
import com.skohscripts.kairos.ui.KairosApp
import com.skohscripts.kairos.ui.Platform
import com.skohscripts.kairos.ui.app.AppServices
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
import kotlin.time.Clock
import kotlin.time.Instant

/** Style Cadran de bout en bout (docs/spec/style-cadran.md) : choisi dans Apparence, appliqué à « Enregistrer ». */
@OptIn(ExperimentalTestApi::class)
class CadranStyleUiTest {
    private val clock = object : Clock {
        override fun now() = Instant.parse("2026-09-29T07:00:00Z")
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

    private fun ComposeUiTest.screenColors(): Set<Int> {
        val pixels = onRoot().captureToImage().toPixelMap()
        return buildSet { for (x in 0 until pixels.width) for (y in 0 until pixels.height) add(pixels[x, y].toArgb()) }
    }

    @Test
    fun choosingCadranRepaintsTheScreenOnceSaved() = runComposeUiTest {
        val services = services()
        val repository = services.repository
        val iosPage = 0xFFF2F2F7.toInt()
        // Thème du système forcé en clair : un bureau en sombre ferait échouer le test.
        setContent { KairosApp(Platform.DESKTOP, Destination.DAY, TeamDestination.BOARD, systemDark = false) { services } }
        openSettings(SettingsCategory.GENERAL)
        waitUntil(timeoutMillis = 15_000) { onAllNodesWithText("Cadran").fetchSemanticsNodes().isNotEmpty() }
        assert(iosPage !in screenColors())

        // Le segment ne change rien avant « Enregistrer ».
        onNodeWithText("Cadran").performScrollTo().performClick()
        waitUntil(timeoutMillis = 15_000) { onAllNodesWithText("Unsaved changes").fetchSemanticsNodes().isNotEmpty() }
        assertEquals(Settings.UI_STYLE_MATERIAL, repository.snapshot.value.settings.uiStyle)

        onNodeWithText("Save").performClick()
        waitUntil(timeoutMillis = 15_000) { repository.snapshot.value.settings.uiStyle == Settings.UI_STYLE_CADRAN }
        // Le fond groupé d'iOS, sans quitter l'écran ouvert : le segment est toujours là.
        waitUntil(timeoutMillis = 15_000) { iosPage in screenColors() }
        onNodeWithText("Material").performScrollTo()
    }
}

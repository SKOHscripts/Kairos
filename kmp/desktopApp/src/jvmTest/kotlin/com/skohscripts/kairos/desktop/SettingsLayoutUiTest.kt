package com.skohscripts.kairos.desktop

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.skohscripts.kairos.core.model.KairosSnapshot
import com.skohscripts.kairos.data.KairosRepository
import com.skohscripts.kairos.data.KairosStore
import com.skohscripts.kairos.ui.app.AppServices
import com.skohscripts.kairos.ui.app.FileService
import com.skohscripts.kairos.ui.settings.SettingsScreen
import com.skohscripts.kairos.ui.theme.KairosTheme
import kotlinx.coroutines.runBlocking
import java.util.Locale
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * Largeur des champs des Réglages (docs/spec/densite.md § Réglages) : un champ
 * numérique est borné à 320 dp (son aide suit la même largeur) et ne déborde
 * jamais d'une fenêtre de téléphone (360 dp) ; un champ de texte libre garde
 * la pleine largeur de la carte.
 */
@OptIn(ExperimentalTestApi::class)
class SettingsLayoutUiTest {
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

    private fun ComposeUiTest.settingsAt(width: Dp) {
        val services = services()
        setContent {
            KairosTheme {
                Box(Modifier.width(width).height(2000.dp)) { SettingsScreen(services) {} }
            }
        }
        waitUntil(timeoutMillis = 15_000) { onAllNodesWithText("Working day").fetchSemanticsNodes().isNotEmpty() }
    }

    /** Compose n'offre que `assertWidthIsAtLeast` et `assertWidthIsEqualTo` : la borne haute se lit sur les limites du nœud. */
    private fun SemanticsNodeInteraction.assertWidthIsAtMost(max: Dp) {
        val bounds = getUnclippedBoundsInRoot()
        val width = bounds.right - bounds.left
        assertTrue(width <= max, "largeur $width au-delà de $max")
    }

    private fun ComposeUiTest.field(label: String): SemanticsNodeInteraction = onNode(hasSetTextAction() and hasText(label))

    @Test
    fun aNumberFieldNeverOverflowsA360dpWindow() = runComposeUiTest {
        settingsAt(360.dp)
        val bounds = field("Default task duration (min)").getUnclippedBoundsInRoot()
        assertTrue(bounds.left >= 0.dp && bounds.right <= 360.dp, "champ numérique hors de la fenêtre de 360 dp : $bounds")
        // Marge d'écran de 16 dp et marge intérieure de carte de 16 dp de chaque côté.
        field("Default task duration (min)").assertWidthIsAtMost(360.dp - 64.dp)
    }

    @Test
    fun aNumberFieldAndItsHelpStopAt320dpInAWideWindow() = runComposeUiTest {
        settingsAt(1000.dp)
        field("Default task duration (min)").assertWidthIsAtMost(320.dp)
        onNodeWithText("Given to a task without an estimate to place it in the day (never saved).").assertWidthIsAtMost(320.dp)
    }

    @Test
    fun aFreeTextFieldKeepsTheFullWidthOfItsCard() = runComposeUiTest {
        settingsAt(1000.dp)
        field("Types offered when editing").assertWidthIsAtLeast(400.dp)
    }
}

package com.skohscripts.kairos.desktop

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.runComposeUiTest
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.skohscripts.kairos.core.model.KairosSnapshot
import com.skohscripts.kairos.core.model.Settings
import com.skohscripts.kairos.core.model.TeamSettings
import com.skohscripts.kairos.data.KairosRepository
import com.skohscripts.kairos.data.KairosStore
import com.skohscripts.kairos.ui.KairosApp
import com.skohscripts.kairos.ui.Platform
import com.skohscripts.kairos.ui.app.AppServices
import com.skohscripts.kairos.ui.app.BackupStore
import com.skohscripts.kairos.ui.app.FileService
import kotlinx.coroutines.runBlocking
import java.util.Locale
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * Liste des fonctionnalités (docs/spec/liste-fonctionnalites.md, issue #153) : bouton de l'Accueil, chapitres (Équipe seulement
 * avec l'espace Équipe), recherche sans accents, filtre « depuis », toucher qui ouvre la cible. Base en mémoire, horloge figée.
 */
@OptIn(ExperimentalTestApi::class)
class FeatureListUiTest {
    private val now = Instant.parse("2026-09-29T07:00:00Z")
    private val clock = object : Clock {
        override fun now() = now
    }
    private val saved = Locale.getDefault()

    @AfterTest
    fun restore() = Locale.setDefault(saved)

    private object NoFiles : FileService {
        override suspend fun saveText(suggestedName: String, text: String) = false
        override suspend fun openText(): String? = null
    }

    private fun services(settings: Settings = Settings()): AppServices = runBlocking {
        val repository = KairosRepository.open(KairosStore.open(JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)), { KairosSnapshot(settings = settings) }, clock)
        AppServices(repository, NoFiles, BackupStore { _, _ -> }, clock = clock)
    }

    private fun ComposeUiTest.waitText(text: String, substring: Boolean = false) =
        waitUntil(conditionDescription = "texte « $text »", timeoutMillis = 15_000) {
            onAllNodesWithText(text, substring = substring).fetchSemanticsNodes().isNotEmpty()
        }

    private fun ComposeUiTest.count(text: String, substring: Boolean = false) = onAllNodesWithText(text, substring = substring).fetchSemanticsNodes().size

    /** Défile la liste jusqu'à [text] : vrai s'il y est (la liste est paresseuse). */
    private fun ComposeUiTest.listHas(text: String): Boolean =
        runCatching { onNodeWithTag("feature-list").performScrollToNode(hasText(text)) }.isSuccess

    /** Revient en haut de la liste, là où sont la recherche et le filtre. */
    private fun ComposeUiTest.top() {
        onNodeWithTag("feature-list").performScrollToIndex(0)
        waitForIdle()
    }

    private fun ComposeUiTest.openList(button: String) {
        waitText(button)
        onNodeWithText(button).performScrollTo().performClick()
        waitUntil(timeoutMillis = 15_000) { runCatching { onNodeWithTag("feature-list").fetchSemanticsNode() }.isSuccess }
    }

    @Test
    fun theHomeButtonOpensTheWholeListGroupedByChapterWithoutTheTeamChapter() = runComposeUiTest {
        Locale.setDefault(Locale.ENGLISH)
        val services = services()
        setContent { KairosApp(Platform.DESKTOP) { services } }
        openList("Feature list")
        assertTrue(listHas("First steps"))
        assertTrue(listHas("Keyboard shortcuts"))
        // « depuis » et la pastille de plateforme d'une fonctionnalité qui n'existe pas partout.
        assertTrue(listHas("since 3.0.0 · Desktop, Web"))
        assertTrue(listHas("Settings"))
        // Espace Équipe désactivé : pas de chapitre Équipe.
        assertTrue(!listHas("Guided first team task"))

        // Filtre « depuis 3.10.0 » : seules les cartes de la 3.10.0 et après.
        top()
        onNodeWithText("All versions").performClick()
        waitText("since 3.10.0")
        onAllNodesWithText("since 3.10.0")[0].performClick()
        waitUntil(timeoutMillis = 15_000) { !listHas("Keyboard shortcuts") }
        assertTrue(listHas("Languages"))
        assertTrue(listHas("Phone calendars"))

        // Aucun résultat, puis « Effacer » : tout revient.
        top()
        onNodeWithText("Search a feature").performTextInput("zzzz")
        waitText("No feature matches")
        onNodeWithText("Clear").performClick()
        waitUntil(timeoutMillis = 15_000) { listHas("Keyboard shortcuts") }

        // Un toucher ouvre la cible et ferme la liste : « Thème sombre » ouvre les Réglages, catégorie Général.
        assertTrue(listHas("Dark theme"))
        onNodeWithText("Dark theme").performClick()
        waitText("Appearance")
        assertEquals(0, count("Search a feature"))
    }

    @Test
    fun theSearchIgnoresAccentsAndTheTeamChapterShowsWithTheTeamSpace() = runComposeUiTest {
        Locale.setDefault(Locale.FRENCH)
        val team = TeamSettings(enabled = true, name = "Plateforme", managerName = "Corentin", identity = "id-1", lastSpace = TeamSettings.SPACE_PERSONAL)
        val services = services(Settings(language = Settings.LANGUAGE_FR, team = team))
        setContent { KairosApp(Platform.DESKTOP) { services } }
        openList("Liste de fonctionnalités")
        assertTrue(listHas("Première tâche d’équipe guidée"))
        top()
        onNodeWithText("Rechercher une fonctionnalité").performTextReplacement("reunion")
        // « Créneaux occupés » parle de « réunions » ; les raccourcis clavier, non.
        waitUntil(timeoutMillis = 15_000) { listHas("Créneaux occupés") }
        assertTrue(!listHas("Raccourcis clavier"))
    }
}

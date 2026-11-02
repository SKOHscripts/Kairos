package com.skohscripts.kairos.desktop

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.runComposeUiTest
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.skohscripts.kairos.core.model.KairosSnapshot
import com.skohscripts.kairos.core.model.Task
import com.skohscripts.kairos.data.KairosRepository
import com.skohscripts.kairos.data.KairosStore
import com.skohscripts.kairos.ui.KairosApp
import com.skohscripts.kairos.ui.Platform
import com.skohscripts.kairos.ui.app.AppServices
import com.skohscripts.kairos.ui.app.FileService
import com.skohscripts.kairos.ui.navigation.Destination
import com.skohscripts.kairos.ui.navigation.TeamDestination
import kotlinx.coroutines.runBlocking
import java.util.Locale
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * Facette « Incomplètes » des filtres de la vue Jour (docs/spec/vue-jour.md § Filtres, issue #166) : cocher « Sans type »
 * ne laisse que les tâches sans type, les cases se lisent comme telles, et décocher rend toutes les tâches.
 */
@OptIn(ExperimentalTestApi::class)
class IncompleteFilterUiTest {
    private val now = Instant.parse("2026-09-29T07:00:00Z")
    private val clock = object : Clock {
        override fun now() = now
    }
    private val saved = Locale.getDefault()

    @BeforeTest
    fun english() = Locale.setDefault(Locale.ENGLISH)

    @AfterTest
    fun restore() = Locale.setDefault(saved)

    private object NoFiles : FileService {
        override suspend fun saveText(suggestedName: String, text: String) = false
        override suspend fun openText(): String? = null
    }

    private fun task(id: Long, title: String, type: String) =
        Task(id, title, priority = null, fibonacciPoints = 3, estimatedMinutes = 30, taskType = type, createdAt = now, updatedAt = now)

    private fun services(): AppServices = runBlocking {
        val repository = KairosRepository.open(KairosStore.open(JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)), { KairosSnapshot() }, clock)
        repository.replaceAll(KairosSnapshot(tasks = listOf(task(1, "Typed report", "Admin"), task(2, "Untyped chore", ""))))
        AppServices(repository, NoFiles, { _, _ -> }, clock = clock)
    }

    // Sous-chaîne : une ligne de l'agenda précède le titre de son heure.
    private fun ComposeUiTest.waitText(text: String) =
        waitUntil(conditionDescription = "texte « $text »", timeoutMillis = 15_000) { onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty() }

    private fun ComposeUiTest.waitGone(text: String) =
        waitUntil(conditionDescription = "plus de « $text »", timeoutMillis = 15_000) { onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isEmpty() }

    /** La liste du jour est paresseuse : on y défile jusqu'au titre attendu (le filtre actif remonte au-dessus des listes). */
    private fun ComposeUiTest.waitInList(text: String) =
        waitUntil(conditionDescription = "« $text » dans la liste", timeoutMillis = 15_000) {
            runCatching { onNode(hasScrollToIndexAction()).performScrollToNode(hasText(text)) }.isSuccess
        }

    /** L'entrée cochable du menu (le champ de la facette affiche aussi les choix). */
    private fun ComposeUiTest.item(text: String) = onNode(hasText(text) and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Checkbox))

    @Test
    fun noTypeKeepsOnlyUntypedTasksAndUncheckingBringsThemAllBack() = runComposeUiTest {
        val services = services()
        setContent { KairosApp(Platform.DESKTOP, Destination.DAY, TeamDestination.BOARD) { services } }
        // Deux tâches à qualifier : « À traiter » n'est ni dans « Maintenant » ni dans la frise, que le filtre ne touche pas.
        waitInList("To process (2)")
        onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Search / filter"))
        onNodeWithText("Search / filter").performClick()
        waitText("Incomplete")
        // Plusieurs facettes affichent « All » : on vise celle dont l'étiquette est « Incomplete ».
        onNode(hasText("Incomplete") and SemanticsMatcher.keyIsDefined(SemanticsProperties.EditableText)).performScrollTo().performClick()
        waitText("No type")
        item("No duration").assertIsOff()
        item("No type").performClick()
        item("No type").assertIsOn()
        waitInList("To process (1)")
        waitText("Untyped chore")
        item("No type").performClick()
        waitInList("To process (2)")
    }
}

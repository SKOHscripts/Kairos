package com.skohscripts.kairos.desktop

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onRoot
import com.skohscripts.kairos.core.model.Settings
import com.skohscripts.kairos.ui.theme.ThemeColors
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runComposeUiTest
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.skohscripts.kairos.core.model.KairosSnapshot
import com.skohscripts.kairos.data.KairosRepository
import com.skohscripts.kairos.data.KairosStore
import com.skohscripts.kairos.ui.KairosApp
import com.skohscripts.kairos.ui.Platform
import com.skohscripts.kairos.ui.app.AppServices
import com.skohscripts.kairos.ui.app.FileService
import com.skohscripts.kairos.ui.app.LegacyImport
import com.skohscripts.kairos.ui.app.UpdateService
import com.skohscripts.kairos.ui.app.UpdateStatus
import kotlinx.coroutines.flow.MutableStateFlow
import com.skohscripts.kairos.core.legacy.LegacyDatabase
import java.io.File
import kotlinx.coroutines.runBlocking
import java.util.Locale
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * Réglages de bout en bout (docs/spec/reglages.md) : base SQLite en
 * mémoire, horloge figée (mardi 29 septembre 2026, 7 h UTC).
 */
@OptIn(ExperimentalTestApi::class)
class M5ScreensUiTest {
    private val clock = object : Clock {
        override fun now() = Instant.parse("2026-09-29T07:00:00Z")
    }
    private val saved = Locale.getDefault()

    @BeforeTest
    fun english() = Locale.setDefault(Locale.ENGLISH)

    @AfterTest
    fun restore() = Locale.setDefault(saved)

    private val backups = mutableListOf<String>()

    private fun services(firstLaunch: Boolean = false, legacy: LegacyImport? = null): AppServices = runBlocking {
        val repository = KairosRepository.open(KairosStore.open(JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)), { KairosSnapshot() }, clock)
        val files = object : FileService {
            override suspend fun saveText(suggestedName: String, text: String) = false
            override suspend fun openText(): String? = null
        }
        AppServices(repository, files, { name, _ -> backups += name }, clock = clock, legacy = legacy, firstLaunch = firstLaunch)
    }

    /** La vraie base Kairos 2 de `gen_legacy_db.py` (schéma final), trouvée « à son emplacement habituel ». */
    private val kairos2 = object : LegacyImport {
        override val found = "/home/moi/.local/share/Kairos/tasks.db"

        override suspend fun readFound(): LegacyDatabase {
            fun copy(name: String) = File.createTempFile("kairos2", name).also { f ->
                javaClass.getResourceAsStream("/legacy/current/$name")!!.use { input -> f.outputStream().use { input.copyTo(it) } }
            }
            return LegacyFiles.read(copy("tasks.db"), copy("settings.json"))
        }

        override suspend fun pick(): LegacyDatabase = readFound()
    }

    private fun ComposeUiTest.waitText(text: String, substring: Boolean = false) =
        waitUntil(timeoutMillis = 5_000) { onAllNodesWithText(text, substring = substring).fetchSemanticsNodes().isNotEmpty() }

    private fun ComposeUiTest.type(label: String, text: String) {
        val field = onNode(hasSetTextAction() and hasText(label)).performScrollTo()
        field.performTextClearance()
        field.performTextInput(text)
    }

    @Test
    fun anInvalidFieldBlocksSavingThenAValidFormIsSaved() = runComposeUiTest {
        val services = services()
        val repository = services.repository
        setContent { KairosApp(Platform.DESKTOP) { services } }
        waitText("Settings")
        onAllNodesWithText("Settings").onFirst().performClick()
        waitText("Working day")

        // Un champ hors bornes : message sous le champ, rien d'enregistré.
        type("Start of day (hour, 0-23)", "25")
        onNodeWithText("Save").performClick()
        waitText("At most 23.")
        assertEquals(9, repository.snapshot.value.settings.workdayStartHour)

        // Règle inter-champs : début après la fin.
        type("Start of day (hour, 0-23)", "19")
        onNodeWithText("Save").performClick()
        waitText("The start of the day must be before its end.")
        assertEquals(9, repository.snapshot.value.settings.workdayStartHour)

        // Valeurs valides, décimale à la virgule, interrupteur : tout est enregistré d'un coup.
        type("Start of day (hour, 0-23)", "8")
        type("Value base per priority", "2,5")
        onNodeWithText("Enable the afternoon dip").performScrollTo().performClick()
        onNodeWithText("Save").performClick()
        waitUntil(timeoutMillis = 5_000) { repository.snapshot.value.settings.workdayStartHour == 8 }
        val settings = repository.snapshot.value.settings
        assertEquals(2.5, settings.priorityValueBase)
        assertEquals(false, settings.cognitiveDipEnabled)
        waitUntil(timeoutMillis = 5_000) { onAllNodesWithText("Unsaved changes").fetchSemanticsNodes().isEmpty() }
    }

    /** Couleurs présentes à l'écran (image de la racine). */
    private fun ComposeUiTest.screenColors(): Set<Int> {
        val pixels = onRoot().captureToImage().toPixelMap()
        return buildSet { for (x in 0 until pixels.width) for (y in 0 until pixels.height) add(pixels[x, y].toArgb()) }
    }

    @Test
    fun aChosenColorThemesTheWholeInterfaceOnceSaved() = runComposeUiTest {
        val services = services()
        val repository = services.repository
        setContent { KairosApp(Platform.DESKTOP) { services } }
        waitText("Settings")
        onAllNodesWithText("Settings").onFirst().performClick()
        waitText("Appearance")
        // Le fond de l'écran est la surface du schéma : celle du miel, puis celle de la graine choisie.
        val honey = ThemeColors.schemeFor(Settings.DEFAULT_THEME_COLOR, null)
        val ocean = ThemeColors.seedScheme(0x2F6FED)
        assert(honey.surface.toArgb() in screenColors())

        // Une pastille ne change rien avant « Enregistrer ».
        onNodeWithContentDescription("Ocean").performScrollTo().performClick()
        waitText("Unsaved changes")
        assertEquals(Settings.DEFAULT_THEME_COLOR, repository.snapshot.value.settings.themeColor)

        onNodeWithText("Save").performClick()
        waitUntil(timeoutMillis = 5_000) { repository.snapshot.value.settings.themeColor == "#2F6FED" }
        waitUntil(timeoutMillis = 5_000) { ocean.surface.toArgb() in screenColors() }
        assert(honey.surface.toArgb() !in screenColors())

        // Couleur illisible : erreur sous le champ, rien d'enregistré.
        type("Custom colour", "bleu")
        onNodeWithText("Save").performClick()
        waitText("Unreadable colour: “bleu” (format #RRGGBB).")
        assertEquals("#2F6FED", repository.snapshot.value.settings.themeColor)
        // Pas de « Couleurs du système » sur le bureau.
        assertEquals(0, onAllNodesWithContentDescription("System colours").fetchSemanticsNodes().size)
    }

    @Test
    fun theWelcomeShowsOnceOnFirstLaunch() = runComposeUiTest {
        val services = services(firstLaunch = true)
        setContent { KairosApp(Platform.DESKTOP) { services } }
        waitText("Welcome to Kairos")
        onNodeWithText("[Example]", substring = true).assertExists()
        onNodeWithText("Get started").performClick()
        waitUntil(timeoutMillis = 5_000) { onAllNodesWithText("Welcome to Kairos").fetchSemanticsNodes().isEmpty() }
    }

    @Test
    fun aKairos2DatabaseFoundAtFirstLaunchIsImportedAfterABackup() = runComposeUiTest {
        val services = services(firstLaunch = true, legacy = kairos2)
        setContent { KairosApp(Platform.DESKTOP) { services } }
        waitText("A Kairos 2 database was found", substring = true)
        onNodeWithText("Import the Kairos 2 database").performClick()
        waitText("Import the Kairos 2 database?")
        onNodeWithText("5 task(s), 2 note(s), 2 timer session(s) and 1 time slot(s)", substring = true).assertExists()
        onNodeWithText("Import").performClick()
        waitUntil(timeoutMillis = 5_000) { services.repository.snapshot.value.tasks.size == 5 }
        assertEquals(1, backups.size)
        assertEquals(8, services.repository.snapshot.value.settings.workdayStartHour)
        waitText("Kairos 2 database imported: 5 task(s), 2 note(s), 2 session(s).")
    }

    @Test
    fun settingsImportAKairos2Database() = runComposeUiTest {
        val services = services(legacy = kairos2)
        setContent { KairosApp(Platform.DESKTOP) { services } }
        waitText("Settings")
        onAllNodesWithText("Settings").onFirst().performClick()
        waitText("Import a Kairos 2.x database")
        onNodeWithText("Import a Kairos 2.x database").performScrollTo().performClick()
        waitText("Import the Kairos 2 database?")
        // « Import » de l'export JSON reste à l'écran derrière le dialogue : le bouton du dialogue est le dernier.
        onAllNodesWithText("Import").onLast().performClick()
        waitUntil(timeoutMillis = 5_000) { services.repository.snapshot.value.notes.size == 2 }
    }

    @Test
    fun anAvailableVersionShowsABannerUntilLater() = runComposeUiTest {
        val updates = object : UpdateService {
            override val status = MutableStateFlow<UpdateStatus>(
                UpdateStatus.Available("3.0.1", "https://github.com/SKOHscripts/Kairos/releases/tag/v3.0.1", clock.now()),
            )
            override val dismissed = MutableStateFlow<String?>(null)
            override suspend fun check(force: Boolean) = Unit
            override fun dismiss(version: String) { dismissed.value = version }
        }
        val services = services().let { s -> AppServices(s.repository, s.files, s.backups, clock = clock, updates = updates) }
        setContent { KairosApp(Platform.DESKTOP) { services } }
        waitText("Kairos 3.0.1 is available", substring = true)
        onNodeWithText("Later").performClick()
        waitUntil(timeoutMillis = 5_000) { onAllNodesWithText("Kairos 3.0.1 is available", substring = true).fetchSemanticsNodes().isEmpty() }
        assertEquals("3.0.1", updates.dismissed.value)
    }

    @Test
    fun disclosuresAreSpokenAsButtonsWithTheirState() = runComposeUiTest {
        val services = services()
        setContent { KairosApp(Platform.DESKTOP) { services } }
        waitText("Search / filter")
        val header = hasText("Search / filter") and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button)
        onNode(header and SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "collapsed")).assertExists().performClick()
        waitUntil(timeoutMillis = 5_000) {
            onAllNodes(header and SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "expanded")).fetchSemanticsNodes().isNotEmpty()
        }
    }
}

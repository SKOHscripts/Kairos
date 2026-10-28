package com.skohscripts.kairos.desktop

import com.skohscripts.kairos.ui.settings.SettingsCategory
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
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.performScrollToNode
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
import com.skohscripts.kairos.ui.navigation.TeamDestination
import com.skohscripts.kairos.ui.navigation.Destination
import com.skohscripts.kairos.ui.Platform
import com.skohscripts.kairos.ui.app.AppServices
import com.skohscripts.kairos.ui.app.FileService
import com.skohscripts.kairos.ui.app.ShortcutService
import com.skohscripts.kairos.ui.app.ShortcutStatus
import com.skohscripts.kairos.ui.app.ShortcutTarget
import com.skohscripts.kairos.ui.app.UpdateService
import com.skohscripts.kairos.ui.app.UpdateStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import java.util.Locale
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
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

    private fun services(firstLaunch: Boolean = false): AppServices = runBlocking {
        val repository = KairosRepository.open(KairosStore.open(JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)), { KairosSnapshot() }, clock)
        val files = object : FileService {
            override suspend fun saveText(suggestedName: String, text: String) = false
            override suspend fun openText(): String? = null
        }
        AppServices(repository, files, { name, _ -> backups += name }, clock = clock, firstLaunch = firstLaunch)
    }

    // L'attente nomme le texte attendu : un dépassement de délai en CI dit alors quelle étape a manqué.
    // 15 s comme les tests de l'espace Équipe : le délai est en temps réel, et sur un runner de CI à
    // deux cœurs où les modules se testent en parallèle, 5 s ont manqué par intermittence.
    private fun ComposeUiTest.waitText(text: String, substring: Boolean = false) =
        waitUntil(conditionDescription = "texte « $text »", timeoutMillis = 15_000) {
            onAllNodesWithText(text, substring = substring).fetchSemanticsNodes().isNotEmpty()
        }

    private fun ComposeUiTest.type(label: String, text: String) {
        val field = onNode(hasSetTextAction() and hasText(label)).performScrollTo()
        field.performTextClearance()
        field.performTextInput(text)
    }

    @Test
    fun aCustomDayAndADayOffAreSavedAsTheSchedule() = runComposeUiTest {
        val services = services()
        val repository = services.repository
        setContent { KairosApp(Platform.DESKTOP, Destination.DAY, TeamDestination.BOARD) { services } }
        openSettings(SettingsCategory.DAY)
        waitText("Hours by day of the week")
        // Mardi (le 2e jour) : horaire propre ; dimanche (le 7e) : jour libre.
        onAllNodesWithText("Custom")[1].performScrollTo().performClick()
        type("From (hour, 0-23)", "10")
        type("To (hour, 0-23)", "12")
        onAllNodesWithText("Day off")[6].performScrollTo().performClick()
        onNodeWithText("Save").performClick()
        waitUntil(timeoutMillis = 5_000) { repository.snapshot.value.settings.schedule != null }
        val days = repository.snapshot.value.settings.schedule!!.weekdays
        assertEquals(setOf(1, 6), days.keys)
        assertEquals(10 to 12, days.getValue(1).startHour to days.getValue(1).endHour)
        assertTrue(days.getValue(6).free)
    }

    @Test
    fun anEndBeforeTheStartIsRefusedOnItsField() = runComposeUiTest {
        val services = services()
        setContent { KairosApp(Platform.DESKTOP, Destination.DAY, TeamDestination.BOARD) { services } }
        openSettings(SettingsCategory.DAY)
        waitText("Hours by day of the week")
        onAllNodesWithText("Custom")[0].performScrollTo().performClick()
        type("From (hour, 0-23)", "15")
        type("To (hour, 0-23)", "15")
        onNodeWithText("Save").performClick()
        waitText("At least 16.")
        assertEquals(null, services.repository.snapshot.value.settings.schedule)
    }

    @Test
    fun restoringTheDefaultsFillsTheFormAndStillNeedsASave() = runComposeUiTest {
        val services = services()
        val repository = services.repository
        runBlocking { repository.updateSettings(repository.snapshot.value.settings.copy(workdayStartHour = 7)) }
        setContent { KairosApp(Platform.DESKTOP, Destination.DAY, TeamDestination.BOARD) { services } }
        openSettings(SettingsCategory.DATA)
        waitText("Restore default settings")
        onNodeWithText("Restore default settings").performScrollTo().performClick()
        // Le formulaire montre la valeur par défaut (dans sa catégorie), mais rien n'est enregistré avant « Save ».
        openSettingsCategory(SettingsCategory.DAY)
        waitText("Working day")
        onNode(hasSetTextAction() and hasText("9")).assertExists()
        assertEquals(7, repository.snapshot.value.settings.workdayStartHour)
        onNodeWithText("Save").performClick()
        waitUntil(timeoutMillis = 5_000) { repository.snapshot.value.settings.workdayStartHour == 9 }
    }

    @Test
    fun anInvalidFieldBlocksSavingThenAValidFormIsSaved() = runComposeUiTest {
        val services = services()
        val repository = services.repository
        setContent { KairosApp(Platform.DESKTOP, Destination.DAY, TeamDestination.BOARD) { services } }
        openSettings(SettingsCategory.DAY)
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

        // Valeurs valides, décimale à la virgule, interrupteur : tout est enregistré d'un coup. Le score et le creux sont
        // dans le groupe « Avancé » de la catégorie Planification, replié : on l'ouvre. Le début de journée tapé dans
        // « Day and hours » reste en attente pendant le passage d'une catégorie à l'autre.
        type("Start of day (hour, 0-23)", "8")
        openSettingsCategory(SettingsCategory.PLANNING)
        onNodeWithText("Advanced: how tasks are ordered").performScrollTo().performClick()
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
        setContent { KairosApp(Platform.DESKTOP, Destination.DAY, TeamDestination.BOARD) { services } }
        openSettings(SettingsCategory.GENERAL)
        waitText("Appearance")
        // Le fond de l'écran est la surface du schéma : celle de la graine par défaut (l'océan), puis celle de la graine choisie.
        val ocean = ThemeColors.schemeFor(Settings.DEFAULT_THEME_COLOR, null)
        val honey = ThemeColors.seedScheme(0xC28417)
        assert(ocean.surface.toArgb() in screenColors())

        // Une pastille ne change rien avant « Enregistrer ».
        onNodeWithContentDescription("Honey").performScrollTo().performClick()
        waitText("Unsaved changes")
        assertEquals(Settings.DEFAULT_THEME_COLOR, repository.snapshot.value.settings.themeColor)

        onNodeWithText("Save").performClick()
        waitUntil(timeoutMillis = 5_000) { repository.snapshot.value.settings.themeColor == "#C28417" }
        waitUntil(timeoutMillis = 5_000) { honey.surface.toArgb() in screenColors() }
        assert(ocean.surface.toArgb() !in screenColors())

        // Couleur illisible : erreur sous le champ, rien d'enregistré.
        type("Custom color", "bleu")
        onNodeWithText("Save").performClick()
        waitText("Unreadable color: “bleu” (format #RRGGBB).")
        assertEquals("#C28417", repository.snapshot.value.settings.themeColor)
        // Pas de « Couleurs du système » sur le bureau.
        assertEquals(0, onAllNodesWithContentDescription("System colors").fetchSemanticsNodes().size)
    }

    @Test
    fun theWelcomeShowsOnceOnFirstLaunch() = runComposeUiTest {
        val services = services(firstLaunch = true)
        setContent { KairosApp(Platform.DESKTOP, Destination.DAY, TeamDestination.BOARD) { services } }
        waitText("Welcome to Kairos")
        onNodeWithText("[Example]", substring = true).assertExists()
        onNodeWithText("Get started").performClick()
        waitUntil(timeoutMillis = 5_000) { onAllNodesWithText("Welcome to Kairos").fetchSemanticsNodes().isEmpty() }
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
        setContent { KairosApp(Platform.DESKTOP, Destination.DAY, TeamDestination.BOARD) { services } }
        waitText("Kairos 3.0.1 is available", substring = true)
        onNodeWithText("Later").performClick()
        waitUntil(timeoutMillis = 5_000) { onAllNodesWithText("Kairos 3.0.1 is available", substring = true).fetchSemanticsNodes().isEmpty() }
        assertEquals("3.0.1", updates.dismissed.value)
    }

    /** Raccourci d'une copie portable (docs/spec/raccourci-portable.md), sans toucher au système. */
    private class FakeShortcuts : ShortcutService {
        override val target = ShortcutTarget.APP_MENU
        override val status = MutableStateFlow(ShortcutStatus.MISSING)
        override val declined = MutableStateFlow(false)
        override suspend fun create() { status.value = ShortcutStatus.CREATED }
        override suspend fun remove() { status.value = ShortcutStatus.MISSING }
        override fun decline() { declined.value = true }
    }

    @Test
    fun aPortableCopyProposesItsShortcutUntilCreatedOrDeclined() = runComposeUiTest {
        val shortcuts = FakeShortcuts()
        val services = services().let { s -> AppServices(s.repository, s.files, s.backups, clock = clock, shortcuts = shortcuts) }
        setContent { KairosApp(Platform.DESKTOP, Destination.DAY, TeamDestination.BOARD) { services } }
        waitText("Add Kairos to the applications menu, with its icon?")
        // Le bandeau est en tête, avant le « Add » de la capture de la vue Jour.
        onAllNodesWithText("Add").onFirst().performClick()
        waitText("Shortcut created.")
        waitUntil(timeoutMillis = 15_000) { onAllNodesWithText("Add Kairos to the applications menu", substring = true).fetchSemanticsNodes().isEmpty() }

        // Réglages → Raccourci : l'état, puis « Retirer » ; le bandeau revient.
        openSettings(SettingsCategory.GENERAL)
        waitText("Shortcut created for this copy.")
        onNodeWithText("Remove the shortcut").performScrollTo().performClick()
        waitText("No shortcut.")
        assertEquals(ShortcutStatus.MISSING, shortcuts.status.value)
        waitText("Add Kairos to the applications menu, with its icon?")

        // « Non merci » : le bandeau disparaît, la carte reste.
        onNodeWithText("No thanks").performClick()
        waitUntil(timeoutMillis = 15_000) { onAllNodesWithText("Add Kairos to the applications menu", substring = true).fetchSemanticsNodes().isEmpty() }
        assertEquals(true, shortcuts.declined.value)
        onNodeWithText("Create the shortcut").performScrollTo().assertExists()
    }

    @Test
    fun anInstalledCopyHasNoShortcutCard() = runComposeUiTest {
        val services = services()
        setContent { KairosApp(Platform.DESKTOP, Destination.DAY, TeamDestination.BOARD) { services } }
        openSettings(SettingsCategory.GENERAL)
        waitText("Appearance")
        assertEquals(0, onAllNodesWithText("Shortcut").fetchSemanticsNodes().size)
    }

    @Test
    fun disclosuresAreSpokenAsButtonsWithTheirState() = runComposeUiTest {
        val services = services()
        setContent { KairosApp(Platform.DESKTOP, Destination.DAY, TeamDestination.BOARD) { services } }
        // Liste paresseuse : sur une base vide l'invitation « Première tâche » pousse la carte de recherche hors de l'écran.
        waitUntil(timeoutMillis = 15_000) { onAllNodes(hasScrollToIndexAction()).fetchSemanticsNodes().isNotEmpty() }
        onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Search / filter"))
        waitText("Search / filter")
        val header = hasText("Search / filter") and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button)
        onNode(header and SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "collapsed")).assertExists().performClick()
        waitUntil(timeoutMillis = 5_000) {
            onAllNodes(header and SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "expanded")).fetchSemanticsNodes().isNotEmpty()
        }
    }
}

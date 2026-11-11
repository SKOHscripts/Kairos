package com.skohscripts.kairos.desktop

import com.skohscripts.kairos.ui.guide.FeatureCatalog
import com.skohscripts.kairos.ui.guide.GuideChapter
import com.skohscripts.kairos.ui.settings.SettingsCategory
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.runComposeUiTest
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.skohscripts.kairos.core.model.KairosSnapshot
import com.skohscripts.kairos.core.model.Settings
import com.skohscripts.kairos.core.model.TeamSettings
import com.skohscripts.kairos.data.KairosRepository
import com.skohscripts.kairos.data.KairosStore
import com.skohscripts.kairos.data.Reassignment
import com.skohscripts.kairos.data.TaskEdit
import com.skohscripts.kairos.ui.KairosApp
import com.skohscripts.kairos.ui.navigation.TeamDestination
import com.skohscripts.kairos.ui.navigation.Destination
import com.skohscripts.kairos.ui.Platform
import com.skohscripts.kairos.ui.app.AppServices
import com.skohscripts.kairos.ui.app.BackupStore
import com.skohscripts.kairos.ui.app.FileService
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import java.util.Locale
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * Accueil, visite guidée, aide et onglet « Échanges » (docs/spec/accueil.md, docs/spec/equipe-echanges.md § Onglet
 * « Échanges »). Base SQLite en mémoire, horloge figée (mardi 29 septembre 2026), interface en anglais.
 */
@OptIn(ExperimentalTestApi::class)
class M6GuideUiTest {
    private val now = Instant.parse("2026-09-29T07:00:00Z")
    private val clock = object : Clock {
        override fun now() = now
    }
    private val saved = Locale.getDefault()

    @BeforeTest
    fun english() = Locale.setDefault(Locale.ENGLISH)

    @AfterTest
    fun restore() = Locale.setDefault(saved)

    private class Files(var toOpen: String? = null) : FileService {
        val written = mutableListOf<Pair<String, String>>()
        override suspend fun saveText(suggestedName: String, text: String): Boolean {
            written += suggestedName to text
            return true
        }

        override suspend fun openText(): String? = toOpen
    }

    private fun repository(snapshot: KairosSnapshot = KairosSnapshot()): KairosRepository = runBlocking {
        KairosRepository.open(KairosStore.open(JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)), { snapshot }, clock)
    }

    private fun services(repository: KairosRepository, files: Files = Files(), firstLaunch: Boolean = false): AppServices =
        AppServices(repository, files, BackupStore { _, _ -> }, clock = clock, firstLaunch = firstLaunch)

    private val team = TeamSettings(enabled = true, name = "Platform", managerName = "Corentin", identity = "id-1", lastSpace = TeamSettings.SPACE_TEAM)

    private fun ComposeUiTest.waitText(text: String, substring: Boolean = false) =
        waitUntil(conditionDescription = "texte « $text »", timeoutMillis = 15_000) {
            onAllNodesWithText(text, substring = substring).fetchSemanticsNodes().isNotEmpty()
        }

    private fun ComposeUiTest.waitGone(text: String, substring: Boolean = false) =
        waitUntil(conditionDescription = "disparition de « $text »", timeoutMillis = 15_000) {
            onAllNodesWithText(text, substring = substring).fetchSemanticsNodes().isEmpty()
        }

    private fun ComposeUiTest.count(text: String, substring: Boolean = false) =
        onAllNodesWithText(text, substring = substring).fetchSemanticsNodes().size

    private fun ComposeUiTest.pressHelp() {
        waitUntil(timeoutMillis = 15_000) { onAllNodesWithContentDescription("Help").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithContentDescription("Help").performClick()
    }

    // --- Aide et Accueil ------------------------------------------------------------------------------------------------

    @Test
    fun helpExplainsTheCurrentScreenAndOpensTheHomePage() = runComposeUiTest {
        val services = services(repository())
        setContent { KairosApp(Platform.DESKTOP, Destination.DAY, TeamDestination.BOARD) { services } }
        pressHelp()
        waitText("Help: Today")
        waitText("“Up next” tells you what to do.", substring = true)
        onNodeWithText("Kairos home").performClick()

        // L'Accueil : raison d'être, fonctionnalités, équipe ; plus de « ? » (la page tient lieu d'aide).
        waitText("What is Kairos for?")
        waitText("What you can do")
        waitText("Work as a team")
        assertEquals(0, onAllNodesWithContentDescription("Help").fetchSemanticsNodes().size)
        // Sans gestion d'équipe : les Réglages, pas de visite d'équipe.
        waitText("Open Settings")
        assertEquals(0, count("Team space tour"))

        // Une carte ouvre son écran et referme l'Accueil.
        onNodeWithText("Measure").performScrollTo().performClick()
        waitGone("What is Kairos for?")
        waitText("Statistics")

        // Le « ? » est de retour et parle des statistiques.
        pressHelp()
        waitText("Help: Statistics")
    }

    @Test
    fun theAppOpensOnTheHomePageAndSettingsAreInTheTopBar() = runComposeUiTest {
        val services = services(repository())
        // Ouverture réelle (sans destination imposée) : l'Accueil, première destination, sans « ? ».
        setContent { KairosApp(Platform.DESKTOP) { services } }
        waitText("What is Kairos for?")
        assertEquals(0, onAllNodesWithContentDescription("Help").fetchSemanticsNodes().size)
        listOf("Home", "Notes", "Day", "Recap", "Stats").forEach { assertTrue(count(it) >= 1, it) }

        // Les Réglages : roue crantée, écran secondaire avec son aide et sa flèche retour.
        openSettings()
        waitText("Appearance")
        assertEquals(0, onAllNodesWithContentDescription("Settings").fetchSemanticsNodes().size)
        pressHelp()
        waitText("Help: Settings")
        onNodeWithText("Close").performClick()
        waitGone("Help: Settings")

        // « À propos et guide » ouvert depuis les Réglages (catégorie À propos) : la flèche y ramène, puis à l'Accueil.
        openSettingsCategory(SettingsCategory.ABOUT)
        onNodeWithText("About & guide").performScrollTo().performClick()
        waitText("Source code")
        onNodeWithContentDescription("Back").performClick()
        waitText("Discover Kairos")
        onNodeWithContentDescription("Back").performClick()
        waitText("What is Kairos for?")
    }

    @Test
    fun theTeamSpaceAlsoOpensOnTheHomePage() = runComposeUiTest {
        val (repo, _) = managerWithLea()
        val services = services(repo)
        setContent { KairosApp(Platform.DESKTOP) { services } }
        // Quitté dans l'espace Équipe : rouvert dans l'espace Équipe, sur l'Accueil, avec la barre d'équipe.
        waitText("What is Kairos for?")
        waitText("Team space tour")
        onAllNodesWithText("Tracking").onFirst().performClick()
        waitText("Platform · Tracking")
        // Revenir à l'Accueil par la barre.
        onAllNodesWithText("Home").onFirst().performClick()
        waitText("What is Kairos for?")
    }

    // --- Découvrir Kairos (hub des chapitres, docs/spec/visite-guidee.md) ---------------------------------------------------

    @Test
    fun theDiscoverHubOpensAChapterFromTheHelpAndGoesToItsScreen() = runComposeUiTest {
        val services = services(repository())
        setContent { KairosApp(Platform.DESKTOP, Destination.DAY, TeamDestination.BOARD) { services } }
        pressHelp()
        onNodeWithText("Discover Kairos").performClick()
        waitText("Create my first task")
        // Sans espace Équipe : cinq chapitres, pas celui de l'équipe. Comptes tirés du catalogue : une carte ajoutée à une
        // version ne casse pas ce test.
        waitText("First steps")
        val firstSteps = FeatureCatalog.chapter(GuideChapter.FIRST_STEPS).size
        val sameCount = FeatureCatalog.chapters(teamEnabled = false).count { FeatureCatalog.chapter(it).size == firstSteps }
        assertEquals(sameCount, count("$firstSteps cards"))
        onNodeWithText("Plan").performClick()
        val plan = FeatureCatalog.chapter(GuideChapter.PLAN).size
        waitText("Step 1 of $plan")
        waitText("Priority, points and estimate")
        repeat(plan - 1) { onNodeWithText("Next").performClick() }
        waitText("Step $plan of $plan")
        // La dernière carte du chapitre (horaires par jour) ouvre les Réglages, sur sa carte (issue #95).
        onNodeWithText("Show me the screen").performClick()
        waitGone("Step $plan of $plan")
        waitText("Hours by day of the week")
    }

    @Test
    fun theDiscoverHubIsInTheSettingsAndOnHomeAndOffersTheTeamChapterWhenTheTeamSpaceIsOn() = runComposeUiTest {
        val (repo, _) = managerWithLea()
        val services = services(repo)
        setContent { KairosApp(Platform.DESKTOP) { services } }
        waitText("What is Kairos for?")
        // Depuis la 3.4 l'Accueil montre aussi la carte « Découvrir Kairos » dans « Nouveau dans cette version » : le bouton est le dernier.
        onAllNodesWithText("Discover Kairos").onLast().performScrollTo().performClick()
        // Le chapitre de l'équipe s'ajoute : un chapitre de plus avec son nombre de cartes (tiré du catalogue).
        val team = FeatureCatalog.chapter(GuideChapter.TEAM).size
        val withTeam = FeatureCatalog.chapters(teamEnabled = true).count { FeatureCatalog.chapter(it).size == team }
        waitUntil(timeoutMillis = 15_000) { count("$team cards") == withTeam }
        onNodeWithText("Close").performClick()
        waitGone("$team cards")
        openSettings(SettingsCategory.ABOUT)
        waitText("Discover Kairos")
        onNodeWithText("Discover Kairos").performScrollTo().performClick()
        waitUntil(timeoutMillis = 15_000) { count("$team cards") == withTeam }
    }

    // --- Visite guidée ------------------------------------------------------------------------------------------------

    @Test
    fun theTourWalksThroughAndOpensTheScreen() = runComposeUiTest {
        val services = services(repository())
        setContent { KairosApp(Platform.DESKTOP, Destination.DAY, TeamDestination.BOARD) { services } }
        pressHelp()
        onNodeWithText("Guided tour").performClick()
        waitText("Step 1 of 6")
        waitText("Capture without thinking")
        // Première étape : « Passer la visite » ; pas de « Précédent ».
        assertEquals(0, count("Previous"))
        onNodeWithText("Next").performClick()
        waitText("Step 2 of 6")
        waitText("Qualify in one click")
        onNodeWithText("Previous").performClick()
        waitText("Step 1 of 6")
        // Jusqu'à l'étape des statistiques, dont « Voir l'écran » ouvre l'écran et ferme la visite.
        repeat(4) { onNodeWithText("Next").performClick() }
        waitText("Step 5 of 6")
        onNodeWithText("Show me the screen").performClick()
        waitGone("Step 5 of 6")
        waitText("Statistics")
    }

    @Test
    fun theTourEndsOnFinishAndLeavesNoTrace() = runComposeUiTest {
        val repository = repository()
        val services = services(repository)
        setContent { KairosApp(Platform.DESKTOP, Destination.DAY, TeamDestination.BOARD) { services } }
        val before = repository.snapshot.value.settings
        pressHelp()
        onNodeWithText("Guided tour").performClick()
        repeat(5) { onNodeWithText("Next").performClick() }
        waitText("Step 6 of 6")
        onNodeWithText("Finish").performClick()
        waitGone("Step 6 of 6")
        // Rien n'est mémorisé : aucun réglage ne change.
        assertEquals(before, repository.snapshot.value.settings)
    }

    @Test
    fun theFirstLaunchDialogStartsTheTour() = runComposeUiTest {
        val services = services(repository(), firstLaunch = true)
        setContent { KairosApp(Platform.DESKTOP, Destination.DAY, TeamDestination.BOARD) { services } }
        waitText("Welcome to Kairos")
        onNodeWithText("Guided tour").performClick()
        waitGone("Welcome to Kairos")
        waitText("Step 1 of 6")
    }

    @Test
    fun quittingTheTourFromTheFirstStepClosesIt() = runComposeUiTest {
        val services = services(repository())
        setContent { KairosApp(Platform.DESKTOP, Destination.DAY, TeamDestination.BOARD) { services } }
        pressHelp()
        onNodeWithText("Guided tour").performClick()
        waitText("Step 1 of 6")
        onNodeWithText("Skip the tour").performClick()
        waitGone("Step 1 of 6")
    }

    // --- Espace Équipe ------------------------------------------------------------------------------------------------

    private fun managerWithLea(): Pair<KairosRepository, Long> = runBlocking {
        val repo = repository()
        repo.updateSettings(Settings(team = team))
        val lea = repo.createMember("Léa", "Dev", 100, 7.0, false)!!
        repo.createMember("Marc", "Dev", 100, 7.0, false)!!
        val migration = repo.createTeamTask("Migration API")!!
        repo.updateTask(migration, TaskEdit(title = "Migration API", priority = 1, points = 5, estimatedMinutes = 480, pinDay = LocalDate(2026, 9, 29), reassign = Reassignment(lea)))
        repo to lea
    }

    @Test
    fun theTeamTourIsOfferedInTheTeamSpaceAndItsLastStepOpensTheExchangesTab() = runComposeUiTest {
        val (repo, _) = managerWithLea()
        val services = services(repo)
        setContent { KairosApp(Platform.DESKTOP, Destination.DAY, TeamDestination.BOARD) { services } }
        waitText("Tracking")
        pressHelp()
        waitText("Help: Tracking")
        // Dans l'espace Équipe, l'aide propose la visite de l'espace Équipe (et non celle de Kairos).
        assertEquals(0, count("Six steps to get started with Kairos"))
        onNodeWithText("Team space tour").performClick()
        waitText("Step 1 of 6")
        waitText("The members")
        repeat(5) { onNodeWithText("Next").performClick() }
        waitText("Step 6 of 6")
        onNodeWithText("Show me the screen").performClick()
        waitText("How to share progress")
        // L'aide de l'onglet parle des échanges.
        pressHelp()
        waitText("A member who uses Kairos receives their tasks in a file", substring = true)
    }

    @Test
    fun theHomePageOffersTheTeamTourAndSpaceOnlyWhenTeamManagementIsOn() = runComposeUiTest {
        val (repo, _) = managerWithLea()
        val services = services(repo)
        setContent { KairosApp(Platform.DESKTOP, Destination.DAY, TeamDestination.BOARD) { services } }
        waitText("Tracking")
        pressHelp()
        onNodeWithText("Kairos home").performClick()
        waitText("What is Kairos for?")
        waitText("Team space tour")
        onNodeWithText("Open the Team space").performScrollTo().performClick()
        waitText("Tracking")
        assertEquals(0, count("What is Kairos for?"))
    }

    // --- Onglet « Échanges » ------------------------------------------------------------------------------------------

    private fun ComposeUiTest.openExchanges() {
        waitText("Tracking")
        onAllNodesWithText("Team").onFirst().performClick()
        waitText("Exchanges")
        onNodeWithText("Exchanges").performClick()
        waitText("How to share progress")
    }

    @Test
    fun theTabsOnlyAppearOnceThereIsAMember() = runComposeUiTest {
        val repo = runBlocking { repository().also { it.updateSettings(Settings(team = team)) } }
        val services = services(repo)
        setContent { KairosApp(Platform.DESKTOP, Destination.DAY, TeamDestination.BOARD) { services } }
        waitText("Tracking")
        onAllNodesWithText("Team").onFirst().performClick()
        waitText("Add a member")
        assertEquals(0, count("Exchanges"))
    }

    @Test
    fun theExchangesTabListsMembersSendsAPackAndWaitsForTheReport() = runComposeUiTest {
        val (repo, lea) = managerWithLea()
        val files = Files()
        val services = services(repo, files)
        setContent { KairosApp(Platform.DESKTOP, Destination.DAY, TeamDestination.BOARD) { services } }
        openExchanges()
        // Une ligne par membre actif : jamais envoyé, rien d'attendu.
        waitText("Léa")
        waitText("Marc")
        assertEquals(2, count("No pack sent"))
        assertEquals(2, count("No report received"))
        assertEquals(0, count("Waiting for their report"))

        // « Envoyer ses tâches… » de Léa (la première ligne) : le fichier est enregistré, l'envoi journalisé, la ligne attend le rapport.
        onAllNodesWithText("Send their tasks…").onFirst().performScrollTo().performClick()
        waitUntil(timeoutMillis = 15_000) { files.written.size == 1 }
        assertTrue(files.written.single().first.startsWith("kairos-paquet-léa-"))
        waitText("Waiting for their report")
        waitText("Pack sent on", substring = true)
        assertEquals(1, count("No pack sent"))
        assertTrue(repo.snapshot.value.teamEvents.any { it.memberId == lea })
    }

    @Test
    fun receivingAReportFromTheTabIntegratesItAndClearsTheWait() = runComposeUiTest {
        val (repo, lea) = managerWithLea()
        val pack = runBlocking { repo.exportPack(lea, "pack-1")!! }
        // Léa reçoit le paquet, avance et renvoie son rapport.
        val reportText = runBlocking {
            val member = repository()
            member.receivePack(pack)
            val task = member.snapshot.value.tasks.single { it.title == "Migration API" }
            member.setReceivedProgress(task.id, 70)
            member.buildReport(member.origins().single().key)!!
        }
        val files = Files(toOpen = reportText)
        val services = services(repo, files)
        setContent { KairosApp(Platform.DESKTOP, Destination.DAY, TeamDestination.BOARD) { services } }
        openExchanges()
        waitText("Waiting for their report")

        onNodeWithText("Receive a report…").performClick()
        // Même aperçu que « Importer » des Réglages.
        waitText("Integrate")
        onNodeWithText("Integrate").performClick()
        waitUntil(timeoutMillis = 15_000) { repo.snapshot.value.members.single { it.id == lea }.lastReportAt != null }
        waitText("Report received on", substring = true)
        waitGone("Waiting for their report")
        assertEquals(70, repo.snapshot.value.tasks.single { it.title == "Migration API" }.progressPercent)
    }

    @Test
    fun aFullBackupIsNotAppliedFromTheExchangesTab() = runComposeUiTest {
        val (repo, _) = managerWithLea()
        val backup = com.skohscripts.kairos.data.ExportCodec.encode(repo.snapshot.value, "3.0.0", now)
        val services = services(repo, Files(toOpen = backup))
        setContent { KairosApp(Platform.DESKTOP, Destination.DAY, TeamDestination.BOARD) { services } }
        openExchanges()
        val tasksBefore = repo.snapshot.value.tasks.size
        onNodeWithText("Receive a report…").performClick()
        waitText("This file is a full backup, not a team exchange.", substring = true)
        assertEquals(0, count("Replace all data?"))
        assertEquals(tasksBefore, repo.snapshot.value.tasks.size)
    }
}

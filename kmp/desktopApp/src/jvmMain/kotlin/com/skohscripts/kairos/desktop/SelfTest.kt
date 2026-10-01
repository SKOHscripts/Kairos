package com.skohscripts.kairos.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.ImageComposeScene
import com.skohscripts.kairos.ui.navigation.NavState
import com.skohscripts.kairos.ui.notes.NotesScreen
import com.skohscripts.kairos.ui.screens.AboutScreen
import com.skohscripts.kairos.ui.stats.StatsScreen
import com.skohscripts.kairos.ui.week.WeekScreen
import com.skohscripts.kairos.ui.settings.SettingsScreen
import kotlinx.coroutines.runBlocking
import com.skohscripts.kairos.ui.theme.KairosTheme
import androidx.compose.ui.unit.Density
import com.skohscripts.kairos.core.AppVersion
import com.skohscripts.kairos.core.KairosBuild
import com.skohscripts.kairos.core.model.TeamSettings
import com.skohscripts.kairos.ui.KairosApp
import com.skohscripts.kairos.ui.Platform
import com.skohscripts.kairos.ui.team.AbsenceEditorContent
import com.skohscripts.kairos.ui.team.MemberSheetContent
import com.skohscripts.kairos.ui.team.TeamBacklogScreen
import com.skohscripts.kairos.ui.team.TeamBoardScreen
import com.skohscripts.kairos.ui.team.TeamMembersScreen
import com.skohscripts.kairos.ui.team.TeamTaskDialog
import com.skohscripts.kairos.ui.team.ExchangePreview
import com.skohscripts.kairos.ui.team.ExchangePreviewCard
import com.skohscripts.kairos.ui.team.forecast.ComparisonRun
import com.skohscripts.kairos.ui.team.forecast.ForecastRun
import com.skohscripts.kairos.ui.team.forecast.ForecastScreen
import com.skohscripts.kairos.ui.team.forecast.ForecastSpec
import com.skohscripts.kairos.ui.team.forecast.ScenarioEditorContent
import com.skohscripts.kairos.ui.team.forecast.TeamUiState
import com.skohscripts.kairos.ui.app.AppServices
import com.skohscripts.kairos.core.team.forecast.ForecastOptions
import com.skohscripts.kairos.core.team.forecast.ForecastScope
import com.skohscripts.kairos.ui.navigation.LocalWindowWidth
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.todayIn
import kotlin.time.Clock
import org.jetbrains.skia.EncodedImageFormat
import java.io.File

/**
 * `--self-test[=dossier]` (docs/spec/distribution.md § Auto-test) : vérifie,
 * SANS fenêtre, que l'application empaquetée démarre réellement : version
 * cohérente, dossier de données inscriptible, et rendu hors écran de
 * l'interface complète (thème, polices, chaînes, icônes) en largeur bureau et
 * en largeur étroite. Code de sortie 0 si tout va bien. Avec un dossier, les
 * rendus y sont écrits en PNG (artefacts de CI).
 */
object SelfTest {
    fun run(args: List<String>, dataDir: File): Int = try {
        val version = requireNotNull(AppVersion.parse(KairosBuild.VERSION_NAME)) { "version invalide" }
        check(version.versionCode == KairosBuild.VERSION_CODE) { "versionCode incohérent" }
        dataDir.mkdirs()
        File(dataDir, "self-test.tmp").apply { writeText("ok") }.delete()
        val output = args.firstOrNull { it.startsWith("--self-test=") }?.substringAfter('=')?.let(::File)
        // Base en mémoire avec les exemples : le rendu montre une vraie journée.
        val services = runBlocking { DesktopServices.open(dataDir, inMemory = true) }
        val app: @Composable () -> Unit = { KairosApp(Platform.DESKTOP) { services } }
        val settings: @Composable () -> Unit = { KairosTheme { Surface { SettingsScreen(services) {} } } }
        val about: @Composable () -> Unit = { KairosTheme { Surface { AboutScreen() } } }
        // Seconde base avec un chrono en marche sur la première tâche qualifiée.
        val timed = runBlocking {
            DesktopServices.open(dataDir, inMemory = true).also { s ->
                s.repository.snapshot.value.tasks.firstOrNull { !it.needsProcessing }?.let { s.repository.startTimer(it.id) }
            }
        }
        val chrono: @Composable () -> Unit = { KairosApp(Platform.DESKTOP) { timed } }
        // Troisième base : gestion d'équipe activée, ouverte dans l'espace Équipe (docs/spec/equipe.md).
        val team = runBlocking {
            DesktopServices.open(dataDir, inMemory = true).also { s ->
                val settings = s.repository.snapshot.value.settings
                s.repository.updateSettings(
                    settings.copy(team = TeamSettings(enabled = true, name = "Équipe Plateforme", managerName = "Claire", lastSpace = TeamSettings.SPACE_TEAM)),
                )
            }
        }
        // Quatrième base : l'écran Équipe (jalon E2) avec trois membres, dont « moi », un absent bientôt et un archivé.
        val today = Clock.System.todayIn(TimeZone.currentSystemDefault())
        val members = runBlocking {
            DesktopServices.open(dataDir, inMemory = true).also { s ->
                val repository = s.repository
                val current = repository.snapshot.value.settings
                repository.updateSettings(
                    current.copy(team = TeamSettings(enabled = true, name = "Équipe Plateforme", managerName = "Claire", lastSpace = TeamSettings.SPACE_TEAM)),
                )
                repository.createMember("Claire Martin", "Responsable d’équipe", 100, 7.0, true)
                val alex = repository.createMember("Alex Dupont", "Développeur back-end", 80, 7.0, false)!!
                val sam = repository.createMember("Sam Bernard", "Designer", 60, 6.5, false)!!
                val leave = today.plus(10, DateTimeUnit.DAY)
                repository.addAbsence(alex, leave, leave.plus(4, DateTimeUnit.DAY), "Congés")
                repository.addAbsence(alex, today.plus(60, DateTimeUnit.DAY), today.plus(60, DateTimeUnit.DAY), "Formation")
                repository.archiveMember(sam)
            }
        }
        val membersScreen: @Composable () -> Unit = { KairosTheme { Surface { TeamMembersScreen(members, initialFormerExpanded = true) } } }
        val alexSnapshot = members.repository.snapshot.value
        val alexMember = alexSnapshot.members.first { it.name == "Alex Dupont" }
        val alexAbsences = alexSnapshot.absences.filter { it.memberId == alexMember.id }
        fun sheet(compact: Boolean): @Composable () -> Unit = {
            KairosTheme {
                // Fond : l'écran voilé par le rideau du dialogue (32 % de la couleur « scrim »).
                Box(
                    Modifier.fillMaxSize().background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.32f).compositeOver(MaterialTheme.colorScheme.surface)),
                    contentAlignment = Alignment.Center,
                ) {
                    MemberSheetContent(alexMember, alexAbsences, alexSnapshot.settings, today, openTaskCount = 3, canDelete = true, compact = compact)
                }
            }
        }
        fun absenceEditor(compact: Boolean): @Composable () -> Unit = {
            KairosTheme {
                Box(
                    Modifier.fillMaxSize().background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.32f).compositeOver(MaterialTheme.colorScheme.surface)),
                    contentAlignment = Alignment.Center,
                ) { AbsenceEditorContent(alexAbsences.first(), today, compact) }
            }
        }
        // Cinquième base : l'espace Équipe du jalon E3 (docs/spec/equipe-backlog-suivi.md) avec trois membres et une vingtaine de tâches.
        val seeded = runBlocking { TeamSeed.open(english = java.util.Locale.getDefault().language == "en") }
        val board = seeded.services
        val billing = seeded.ids.getValue("billing")
        val alexId = board.repository.snapshot.value.members.first { it.name.startsWith("Alex") }.id
        fun atWidth(width: Dp, content: @Composable () -> Unit): @Composable () -> Unit = {
            CompositionLocalProvider(LocalWindowWidth provides width) { KairosTheme { Surface { content() } } }
        }
        fun taskSheet(width: Dp, history: Boolean = true): @Composable () -> Unit = {
            CompositionLocalProvider(LocalWindowWidth provides width) {
                KairosTheme {
                    Box(
                        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.32f).compositeOver(MaterialTheme.colorScheme.surface)),
                        contentAlignment = Alignment.Center,
                    ) { TeamTaskDialog(board, billing, onDismiss = {}, initialHistory = history) }
                }
            }
        }
        // Jalon E5 (docs/spec/equipe-simulation.md) : mêmes données, état d'interface à part (la sélection du Backlog et les
        // résultats des Prévisions ne débordent pas sur les captures précédentes), graine fixe et calcul d'un trait.
        fun withOwnUi() = AppServices(board.repository, board.files, board.backups, clock = board.clock, teamUi = TeamUiState(seed = { FORECAST_SEED }))
        val selectionServices = withOwnUi()
        val zone = TimeZone.currentSystemDefault()
        val forecastSnapshot = board.repository.snapshot.value
        val forecastSpec = ForecastSpec(ForecastScope.Assigned)
        val forecastServices = withOwnUi().also {
            it.teamUi.forecast = ForecastRun.computeNow(forecastSnapshot, board.clock.now(), zone, forecastSpec, FORECAST_SEED)
        }
        val comparisonServices = withOwnUi().also { s ->
            val scenarios = forecastSnapshot.teamScenarios
            s.teamUi.compared = scenarios.mapTo(HashSet()) { it.id }
            s.teamUi.spec = ForecastSpec(ForecastScope.AssignedAndBacklog, options = ForecastOptions())
            s.teamUi.comparison = ComparisonRun.computeNow(forecastSnapshot, board.clock.now(), zone, s.teamUi.spec, scenarios, FORECAST_SEED)
        }
        val reinforcement = forecastSnapshot.teamScenarios.first { it.id == seeded.ids.getValue("scenario-reinforcement") }
        fun scenarioEditor(compact: Boolean): @Composable () -> Unit = {
            KairosTheme {
                Box(
                    Modifier.fillMaxSize().background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.32f).compositeOver(MaterialTheme.colorScheme.surface)),
                    contentAlignment = Alignment.Center,
                ) { ScenarioEditorContent(forecastSnapshot, reinforcement, seeded.today, compact) }
            }
        }
        // Jalon E6 (docs/spec/equipe-echanges.md) : aperçus de réception et d'intégration, fiche membre avec ses échanges, vue Jour
        // d'un membre qui a reçu des tâches (marque « de Claire », « retirée par Claire »), Réglages avec « Renvoyer l'avancement… ».
        val exchange = runBlocking { exchangeSeed(dataDir, english = java.util.Locale.getDefault().language == "en") }
        fun exchangeCard(preview: ExchangePreview): @Composable () -> Unit = {
            KairosTheme {
                Box(
                    Modifier.fillMaxSize().background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.32f).compositeOver(MaterialTheme.colorScheme.surface)),
                    contentAlignment = Alignment.Center,
                ) { ExchangePreviewCard(preview, onConfirm = {}, onDismiss = {}) }
            }
        }
        val memberApp: @Composable () -> Unit = { KairosApp(Platform.DESKTOP) { exchange.member } }
        val memberSettings: @Composable () -> Unit = { KairosTheme { Surface { SettingsScreen(exchange.member) {} } } }
        val teamApp: @Composable () -> Unit = { KairosApp(Platform.DESKTOP) { team } }
        val teamSettings: @Composable () -> Unit = { KairosTheme { Surface { SettingsScreen(team) {} } } }
        val notes: @Composable () -> Unit = { KairosTheme { Surface { NotesScreen(services) {} } } }
        val week: @Composable () -> Unit = { KairosTheme { Surface { WeekScreen(services, NavState()) {} } } }
        val stats: @Composable () -> Unit = { KairosTheme { Surface { StatsScreen(timed) } } }
        // Les deux dernières, très hautes, montrent toute la vue Jour (agenda, sections, frise).
        val shots = listOf(
            Shot("wide", 1200, 1000, app),
            Shot("narrow", 420, 1000, app),
            Shot("settings", 900, 1000, settings),
            Shot("settings-full", 900, 4800, settings),
            Shot("about", 900, 1000, about),
            Shot("day-full", 1200, 3000, app),
            Shot("day-full-narrow", 420, 5000, app),
            Shot("day-chrono", 1200, 1000, chrono),
            Shot("notes", 900, 1000, notes),
            Shot("week", 1200, 1000, week),
            Shot("week-narrow", 420, 2400, week),
            Shot("stats", 1200, 1600, stats),
            // Espace Équipe (état vide du jalon E1) : large, puis 360 dp (téléphone) avec le sélecteur dans la barre.
            Shot("team", 1200, 1000, teamApp),
            Shot("team-narrow", 360, 800, teamApp),
            Shot("team-settings", 900, 5600, teamSettings),
            // Jalon E6 : échanges par fichier (aperçu d'un paquet, d'un rapport ; fiche membre ; vue Jour du membre ; Réglages du membre).
            Shot("team-exchange-pack", 900, 1100, exchangeCard(exchange.packPreview)),
            Shot("team-exchange-pack-narrow", 360, 1300, exchangeCard(exchange.packPreview)),
            Shot("team-exchange-report", 900, 1000, exchangeCard(exchange.reportPreview)),
            Shot("team-exchange-report-narrow", 360, 1300, exchangeCard(exchange.reportPreview)),
            Shot("team-exchange-sheet", 900, 1950, atWidth(900.dp) { TeamMembersScreen(exchange.manager, initialSheetMemberId = exchange.alexId) }),
            Shot("team-exchange-sheet-narrow", 360, 2100, atWidth(360.dp) { TeamMembersScreen(exchange.manager, initialSheetMemberId = exchange.alexId) }),
            Shot("exchange-day", 1200, 1700, memberApp),
            Shot("exchange-day-narrow", 420, 2600, memberApp),
            Shot("exchange-settings", 900, 4900, memberSettings),
            // Écran Équipe du jalon E2 : large, puis 360 dp ; fiche membre (dialogue, plein écran) et éditeur d'absence.
            Shot("team-members", 1200, 1000, membersScreen),
            Shot("team-members-narrow", 360, 1000, membersScreen),
            Shot("team-member-sheet", 900, 1100, sheet(compact = false)),
            Shot("team-member-sheet-narrow", 360, 1100, sheet(compact = true)),
            Shot("team-absence", 900, 900, absenceEditor(compact = false)),
            Shot("team-absence-narrow", 360, 900, absenceEditor(compact = true)),
            // Jalon E3 : Backlog, Suivi (colonnes puis sections empilées), sélection multiple, fiche d'une tâche avec historique.
            Shot("team-backlog", 1200, 1300, atWidth(1200.dp) { TeamBacklogScreen(board) }),
            Shot("team-backlog-narrow", 360, 2000, atWidth(360.dp) { TeamBacklogScreen(board) }),
            Shot("team-backlog-selection", 1200, 900, atWidth(1200.dp) { TeamBacklogScreen(selectionServices, initialSelection = setOf(seeded.ids.getValue("vat"), seeded.ids.getValue("demo"))) }),
            Shot("team-board", 1200, 1700, atWidth(1200.dp) { TeamBoardScreen(board) }),
            Shot("team-board-narrow", 360, 3000, atWidth(360.dp) { TeamBoardScreen(board) }),
            Shot("team-member-activity", 900, 1400, atWidth(900.dp) { TeamMembersScreen(board, initialSheetMemberId = board.repository.snapshot.value.members.first { it.name.startsWith("Alex") }.id) }),
            Shot("team-member-activity-narrow", 360, 1800, atWidth(360.dp) { TeamMembersScreen(board, initialSheetMemberId = board.repository.snapshot.value.members.first { it.name.startsWith("Alex") }.id) }),
            // Jalon E4 : la destination Équipe avec la charge (tuiles, barres, panneaux), la fiche d'un membre avec sa charge, la suggestion.
            Shot("team-load", 1200, 1350, atWidth(1200.dp) { TeamMembersScreen(board) }),
            Shot("team-load-narrow", 360, 1900, atWidth(360.dp) { TeamMembersScreen(board) }),
            Shot("team-member-load", 900, 1950, atWidth(900.dp) { TeamMembersScreen(board, initialSheetMemberId = alexId) }),
            Shot("team-member-load-narrow", 360, 1950, atWidth(360.dp) { TeamMembersScreen(board, initialSheetMemberId = alexId) }),
            Shot("team-suggestion", 1200, 1300, atWidth(1200.dp) { TeamBacklogScreen(board, initialSuggestion = true) }),
            Shot("team-suggestion-narrow", 360, 900, atWidth(360.dp) { TeamBacklogScreen(board, initialSuggestion = true) }),
            Shot("team-task-sheet", 900, 1100, taskSheet(900.dp)),
            Shot("team-task-sheet-narrow", 360, 1100, taskSheet(360.dp)),
            Shot("team-task-sheet-details", 900, 1500, taskSheet(900.dp, history = false)),
            Shot("team-task-sheet-details-narrow", 360, 1800, taskSheet(360.dp, history = false)),
            // Jalon E5 : Prévisions avec un résultat (périmètre, modèle, options, panneaux, scénarios), éditeur de scénario, comparaison.
            Shot("team-forecast", 1200, 2700, atWidth(1200.dp) { ForecastScreen(forecastServices) }),
            Shot("team-forecast-narrow", 360, 3000, atWidth(360.dp) { ForecastScreen(forecastServices) }),
            Shot("team-scenario-editor", 900, 1000, atWidth(900.dp) { scenarioEditor(compact = false)() }),
            Shot("team-scenario-editor-narrow", 360, 1000, atWidth(360.dp) { scenarioEditor(compact = true)() }),
            Shot("team-comparison", 1200, 1700, atWidth(1200.dp) { ForecastScreen(comparisonServices) }),
            Shot("team-comparison-narrow", 360, 2000, atWidth(360.dp) { ForecastScreen(comparisonServices) }),
        )
        for ((name, width, height, content) in shots) {
            val png = render(width, height, content)
            output?.let { it.mkdirs(); File(it, "desktop-$name.png").writeBytes(png) }
        }
        println("Kairos ${KairosBuild.VERSION_NAME} : auto-test réussi")
        0
    } catch (e: Throwable) {
        System.err.println("Kairos : auto-test en échec")
        e.printStackTrace()
        1
    }

    /** Graine fixe des captures de Prévisions : mêmes chiffres à chaque exécution (docs/spec/equipe-simulation.md). */
    private const val FORECAST_SEED = 20_261_001L

    /** Nombre maximal d'images (de 100 ms) attendues après le rendu initial pour que les calculs asynchrones aboutissent. */
    private const val MAX_SETTLE_FRAMES = 80

    private data class Shot(val name: String, val width: Int, val height: Int, val content: @Composable () -> Unit)

    private fun render(width: Int, height: Int, content: @Composable () -> Unit): ByteArray {
        val scene = ImageComposeScene(width, height, Density(1f), content = content)
        try {
            // Plusieurs images : laisse le temps aux ressources (polices, chaînes) de se charger.
            var image = scene.render(0)
            for (frame in 1..10) image = scene.render(frame * 100_000_000L)
            var png = requireNotNull(image.encodeToData(EncodedImageFormat.PNG)) { "encodage PNG" }.bytes
            // Les calculs lourds de l'espace Équipe (charge, suggestion) se font hors composition, en temps réel :
            // on attend que l'image ne change plus (l'indicateur de progression, animé, la fait changer tant que ça calcule).
            var stable = 0
            var frame = 11
            while (stable < 2 && frame < 11 + MAX_SETTLE_FRAMES) {
                Thread.sleep(100)
                val next = requireNotNull(scene.render(frame++ * 100_000_000L).encodeToData(EncodedImageFormat.PNG)) { "encodage PNG" }.bytes
                stable = if (next.contentEquals(png)) stable + 1 else 0
                png = next
            }
            return png
        } finally {
            scene.close()
        }
    }
}

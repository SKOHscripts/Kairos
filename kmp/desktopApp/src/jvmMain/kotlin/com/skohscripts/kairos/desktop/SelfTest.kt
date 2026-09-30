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
import com.skohscripts.kairos.ui.team.TeamMembersScreen
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
            Shot("team-settings", 900, 4800, teamSettings),
            // Écran Équipe du jalon E2 : large, puis 360 dp ; fiche membre (dialogue, plein écran) et éditeur d'absence.
            Shot("team-members", 1200, 1000, membersScreen),
            Shot("team-members-narrow", 360, 1000, membersScreen),
            Shot("team-member-sheet", 900, 1100, sheet(compact = false)),
            Shot("team-member-sheet-narrow", 360, 1100, sheet(compact = true)),
            Shot("team-absence", 900, 900, absenceEditor(compact = false)),
            Shot("team-absence-narrow", 360, 900, absenceEditor(compact = true)),
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

    private data class Shot(val name: String, val width: Int, val height: Int, val content: @Composable () -> Unit)

    private fun render(width: Int, height: Int, content: @Composable () -> Unit): ByteArray {
        val scene = ImageComposeScene(width, height, Density(1f), content = content)
        try {
            // Plusieurs images : laisse le temps aux ressources (polices, chaînes) de se charger.
            var image = scene.render(0)
            for (frame in 1..10) image = scene.render(frame * 100_000_000L)
            return requireNotNull(image.encodeToData(EncodedImageFormat.PNG)) { "encodage PNG" }.bytes
        } finally {
            scene.close()
        }
    }
}

package com.skohscripts.kairos.desktop

import androidx.compose.runtime.Composable
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.skohscripts.kairos.core.model.BlockKind
import com.skohscripts.kairos.core.model.BlockRecurrence
import com.skohscripts.kairos.core.model.KairosSnapshot
import com.skohscripts.kairos.core.model.Note
import com.skohscripts.kairos.core.model.NoteStatus
import com.skohscripts.kairos.core.model.Settings
import com.skohscripts.kairos.core.model.Task
import com.skohscripts.kairos.core.model.TaskDependency
import com.skohscripts.kairos.core.model.TaskRecurrence
import com.skohscripts.kairos.core.model.TaskStatus
import com.skohscripts.kairos.core.model.TimeBlock
import com.skohscripts.kairos.core.model.WorkSession
import com.skohscripts.kairos.data.KairosRepository
import com.skohscripts.kairos.data.KairosStore
import com.skohscripts.kairos.ui.KairosApp
import com.skohscripts.kairos.ui.Platform
import com.skohscripts.kairos.ui.app.AppServices
import com.skohscripts.kairos.ui.app.ChronoNotifier
import com.skohscripts.kairos.ui.app.FileService
import com.skohscripts.kairos.ui.app.NotifyState
import com.skohscripts.kairos.ui.navigation.Destination
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import org.jetbrains.skia.EncodedImageFormat
import java.io.File
import java.util.Locale
import kotlin.random.Random
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/**
 * Captures des fiches de magasin (docs/spec-v3/publication.md § Captures) :
 * `--store-screenshots=<dossier fastlane/metadata/android>` rend hors écran,
 * en français et en anglais, l'interface **Android** en largeur téléphone
 * (1080 × 2400 px, densité 2,625) sur un jeu de données réaliste, et écrit
 * `<langue>/images/phoneScreenshots/1.png`… Rien de ce jeu n'est livré dans
 * l'application : c'est une vitrine, pas des exemples.
 */
object StoreScreenshots {
    private const val WIDTH = 1080
    private const val HEIGHT = 2400
    private val today = LocalDate(2026, 10, 6) // un mardi
    private val now: Instant = LocalDateTime(today, LocalTime(10, 12)).toInstant(TimeZone.UTC)

    private val shots = listOf(Destination.DAY, Destination.WEEK, Destination.STATS, Destination.NOTES, Destination.SETTINGS)

    fun run(metadataDir: File): Int = try {
        // Heure et fuseau figés : les captures ne dépendent ni du jour ni de la machine.
        java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("UTC"))
        for ((folder, locale) in listOf("fr-FR" to Locale.FRANCE, "en-US" to Locale.US)) {
            Locale.setDefault(locale)
            val out = File(metadataDir, "$folder/images/phoneScreenshots").apply { mkdirs() }
            shots.forEachIndexed { i, destination ->
                val services = runBlocking { services(locale.language) }
                val png = render { KairosApp(Platform.ANDROID, destination) { services } }
                File(out, "${i + 1}.png").writeBytes(png)
            }
        }
        println("Captures des magasins écrites dans $metadataDir")
        0
    } catch (e: Throwable) {
        e.printStackTrace()
        1
    }

    private suspend fun services(language: String): AppServices {
        val clock = object : Clock {
            override fun now() = now
        }
        val repository = KairosRepository.open(
            KairosStore.open(JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)),
            examples = { snapshot(language) },
            clock = clock,
            timeZone = TimeZone.UTC,
        )
        val files = object : FileService {
            override suspend fun saveText(suggestedName: String, text: String) = false
            override suspend fun openText(): String? = null
        }
        val notifier = object : ChronoNotifier {
            override val state = MutableStateFlow(NotifyState.ACTIVE)
        }
        return AppServices(repository, files, { _, _ -> }, clock = clock, notifier = notifier)
    }

    private fun render(content: @Composable () -> Unit): ByteArray {
        val scene = ImageComposeScene(WIDTH, HEIGHT, Density(2.625f), content = content)
        try {
            var image = scene.render(0)
            for (frame in 1..12) image = scene.render(frame * 100_000_000L)
            return requireNotNull(image.encodeToData(EncodedImageFormat.PNG)) { "encodage PNG" }.bytes
        } finally {
            scene.close()
        }
    }

    /** Textes du jeu de données, par langue. */
    private class Words(
        val types: String,
        val dev: String, val review: String, val meeting: String, val doc: String, val admin: String,
        val leak: String, val demo: String, val mr: String, val standup: String, val install: String,
        val survey: String, val deploy: String, val report: String,
        val project: String, val focus: String, val lunch: String, val sync: String,
        val notes: List<String>, val history: List<String>,
    )

    private val FR = Words(
        types = Settings.DEFAULT_TASK_TYPES_FR,
        dev = "Développement", review = "Revue de code", meeting = "Réunion", doc = "Documentation", admin = "Administratif",
        leak = "Corriger la fuite mémoire du tableau de bord", demo = "Préparer la démo client",
        mr = "Relire la merge request du module paiement", standup = "Point d’équipe",
        install = "Mettre à jour la documentation d’installation", survey = "Répondre au questionnaire sécurité",
        deploy = "Déployer la version 2.4", report = "Envoyer le compte rendu du comité",
        project = "Atlas", focus = "Deep work", lunch = "Déjeuner", sync = "Réunion de suivi",
        notes = listOf(
            "Demander à l’équipe réseau les droits sur le serveur de préproduction",
            "Idée : un tableau de bord des temps de build\nÀ creuser avant le prochain sprint",
            "Rappeler le fournisseur pour le renouvellement des licences",
        ),
        history = listOf(
            "Analyser les journaux d’erreur", "Mettre à jour les dépendances", "Écrire les tests du module export",
            "Revue de l’architecture", "Préparer la rétrospective", "Nettoyer le backlog", "Corriger l’affichage mobile",
            "Rédiger la note de version", "Configurer la supervision", "Former la nouvelle recrue",
        ),
    )

    private val EN = Words(
        types = Settings.DEFAULT_TASK_TYPES_EN,
        dev = "Development", review = "Code review", meeting = "Meeting", doc = "Documentation", admin = "Administration",
        leak = "Fix the dashboard memory leak", demo = "Prepare the client demo",
        mr = "Review the payment module merge request", standup = "Team stand-up",
        install = "Update the installation guide", survey = "Answer the security questionnaire",
        deploy = "Deploy version 2.4", report = "Send the steering committee minutes",
        project = "Atlas", focus = "Deep work", lunch = "Lunch", sync = "Follow-up meeting",
        notes = listOf(
            "Ask the network team for access to the staging server",
            "Idea: a build-time dashboard\nTo explore before the next sprint",
            "Call the supplier back about the licence renewal",
        ),
        history = listOf(
            "Analyse the error logs", "Update the dependencies", "Write the export module tests",
            "Architecture review", "Prepare the retrospective", "Clean up the backlog", "Fix the mobile layout",
            "Write the release notes", "Set up monitoring", "Onboard the new hire",
        ),
    )

    private fun snapshot(language: String): KairosSnapshot {
        val w = if (language == "fr") FR else EN
        fun at(day: LocalDate, h: Int, m: Int = 0) = LocalDateTime(day, LocalTime(h, m))
        fun instant(day: LocalDate, h: Int, m: Int = 0) = at(day, h, m).toInstant(TimeZone.UTC)
        val created = instant(today.minus(DatePeriod(days = 3)), 9)
        val tasks = mutableListOf<Task>()
        fun task(
            title: String, priority: Int?, points: Int?, minutes: Int? = null, type: String = "",
            deadline: LocalDate? = null, project: String = "", pinned: LocalDateTime? = null,
            recurrence: TaskRecurrence = TaskRecurrence.NONE, status: TaskStatus = TaskStatus.TODO,
            updated: Instant = created, createdAt: Instant = created,
        ): Long {
            val id = tasks.size + 1L
            tasks += Task(
                id = id, title = title, priority = priority, fibonacciPoints = points, estimatedMinutes = minutes,
                taskType = type, deadline = deadline, projectTag = project, pinnedStart = pinned, recurrence = recurrence,
                status = status, createdAt = createdAt, updatedAt = updated,
            )
            return id
        }
        val leak = task(w.leak, 0, 5, 60, w.dev, today.plus(DatePeriod(days = 1)), w.project)
        task(w.demo, 1, 3, 45, w.meeting, today.plus(DatePeriod(days = 2)), w.project)
        task(w.mr, 1, 2, 30, w.review, project = w.project)
        task(w.standup, 1, 1, 15, w.meeting, pinned = at(today, 9, 30), recurrence = TaskRecurrence.WEEKDAYS)
        task(w.install, 2, 3, 45, w.doc)
        task(w.survey, 2, 2, 30, w.admin, today.plus(DatePeriod(days = 7)))
        val deploy = task(w.deploy, 0, 3, 30, w.dev, today.plus(DatePeriod(days = 3)), w.project)
        task(w.report, 1, 1, 20, w.admin, status = TaskStatus.DONE, updated = instant(today, 9, 5))
        // Boîte de réception vide : la première capture montre « Maintenant » et l'agenda.

        // Six semaines d'historique : de quoi remplir débit, calibration et temps par type.
        val random = Random(42)
        val sessions = mutableListOf<WorkSession>()
        val types = listOf(w.dev, w.review, w.meeting, w.doc, w.admin)
        val scale = listOf(1, 2, 3, 5, 8)
        for (week in 1..6) {
            repeat(3 + random.nextInt(3)) { k ->
                val day = today.minus(DatePeriod(days = 7 * week - k))
                val points = scale[random.nextInt(scale.size)]
                // Échéance la plupart du temps tenue, parfois dépassée d'un jour.
                val due = day.plus(DatePeriod(days = random.nextInt(-1, 3)))
                val id = task(
                    w.history[(week * 3 + k) % w.history.size], random.nextInt(3), points, points * 20, types[random.nextInt(types.size)],
                    deadline = due, status = TaskStatus.DONE, updated = instant(day, 17),
                    createdAt = instant(day.minus(DatePeriod(days = 2 + random.nextInt(6))), 9),
                )
                val start = instant(day, 9 + random.nextInt(5))
                val minutes = points * (14 + random.nextInt(14))
                sessions += WorkSession(sessions.size + 1L, id, start, start + minutes.minutes, start)
            }
        }
        // Le compte rendu fait ce matin, et le chrono en cours sur la fuite mémoire.
        sessions += WorkSession(sessions.size + 1L, 8, instant(today, 8, 40), instant(today, 9, 5), instant(today, 8, 40))
        sessions += WorkSession(sessions.size + 1L, leak, instant(today, 9, 42), null, instant(today, 9, 42))

        val notes = w.notes.mapIndexed { i, body ->
            val t = instant(today.minus(DatePeriod(days = i)), 8 + i)
            Note(i + 1L, body, NoteStatus.OPEN, null, t, t)
        }
        return KairosSnapshot(
            tasks = tasks,
            dependencies = listOf(TaskDependency(1, taskId = deploy, blockerId = leak, createdAt = created)),
            timeBlocks = listOf(
                TimeBlock(1, w.focus, at(today, 10, 30), at(today, 12, 0), BlockKind.DEEPWORK, createdAt = created),
                TimeBlock(2, w.lunch, at(today, 12, 0), at(today, 13, 0), BlockKind.BUSY, BlockRecurrence.DAILY, created),
                TimeBlock(3, w.sync, at(today, 14, 0), at(today, 15, 0), BlockKind.BUSY, createdAt = created),
            ),
            workSessions = sessions,
            notes = notes,
            settings = Settings(taskTypes = w.types),
        )
    }
}

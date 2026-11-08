package com.skohscripts.kairos.desktop

import androidx.compose.runtime.Composable
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventType
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
import com.skohscripts.kairos.core.team.forecast.ForecastScope
import com.skohscripts.kairos.data.KairosRepository
import com.skohscripts.kairos.data.KairosStore
import com.skohscripts.kairos.ui.KairosApp
import com.skohscripts.kairos.ui.Platform
import com.skohscripts.kairos.ui.app.AppServices
import com.skohscripts.kairos.ui.app.ChronoNotifier
import com.skohscripts.kairos.ui.app.FileService
import com.skohscripts.kairos.ui.app.NotifyState
import com.skohscripts.kairos.ui.navigation.Destination
import com.skohscripts.kairos.ui.navigation.Secondary
import com.skohscripts.kairos.ui.navigation.TeamDestination
import com.skohscripts.kairos.ui.team.forecast.ForecastRun
import com.skohscripts.kairos.ui.team.forecast.ForecastSpec
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
 * Captures des fiches de magasin (docs/spec/publication.md § Captures) :
 * `--store-screenshots=<dossier fastlane/metadata/android>` rend hors écran,
 * en français et en anglais, l'interface **Android** en largeur téléphone
 * (1080 × 2400 px, densité 2,625) sur un jeu de données réaliste, et écrit
 * `<langue>/images/phoneScreenshots/1.png` à `10.png` (1 à 6 : espace Perso, base solo, dont la 2 : la fiche d'une tâche
 * estimée par machine learning ; 7 à 9 : espace Équipe, seconde base de [StoreTeam] ; 10 : la vue Jour en thème sombre,
 * issue #72). Thème du système imposé (clair, sombre pour la 10) : les captures ne dépendent pas de la machine. Rien de ce jeu n'est livré dans
 * l'application : c'est une vitrine, pas des exemples.
 */
object StoreScreenshots {
    private const val WIDTH = 1080
    private const val HEIGHT = 2400
    private val today = LocalDate(2026, 10, 6) // un mardi
    private val now: Instant = LocalDateTime(today, LocalTime(10, 12)).toInstant(TimeZone.UTC)

    /**
     * Captures 1 et 3 à 6 : l'espace Perso, sur la base du mode solo. `null` = les Réglages, écran secondaire ouvert
     * par-dessus la vue Jour (ils ne sont plus une destination, docs/spec/navigation-theme.md). La 2 suit la vue Jour.
     */
    private val shots = listOf(Destination.DAY, Destination.WEEK, Destination.STATS, Destination.NOTES, null)

    /** Captures 7 à 9 : l'espace Équipe, sur une seconde base (gestion d'équipe activée, [StoreTeam]). */
    private val teamShots = listOf(TeamDestination.BOARD, TeamDestination.MEMBERS, TeamDestination.FORECAST)

    fun run(metadataDir: File): Int = try {
        // Heure et fuseau figés : les captures ne dépendent ni du jour ni de la machine.
        java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("UTC"))
        for ((folder, locale) in listOf("fr-FR" to Locale.FRANCE, "en-US" to Locale.US)) {
            Locale.setDefault(locale)
            val out = File(metadataDir, "$folder/images/phoneScreenshots").apply { mkdirs() }
            var number = 0
            fun write(png: ByteArray) = File(out, "${++number}.png").writeBytes(png)
            shots.forEach { destination ->
                val services = runBlocking { services(locale.language) }
                write(
                    render {
                        if (destination != null) {
                            KairosApp(Platform.ANDROID, destination, systemDark = false) { services }
                        } else {
                            KairosApp(Platform.ANDROID, Destination.DAY, initialSecondary = Secondary.SETTINGS, systemDark = false) { services }
                        }
                    },
                )
                if (destination == Destination.DAY) write(machineLearningShot(locale.language))
            }
            teamShots.forEach { destination ->
                val services = runBlocking { teamServices(locale.language, destination) }
                // Les calculs de l'espace Équipe (charge, historique) se font hors composition : on attend la stabilité.
                val scroll = if (destination == TeamDestination.FORECAST) FORECAST_SCROLL else 0f
                write(render(settle = true, wheel = scroll) { KairosApp(Platform.ANDROID, initialTeamDestination = destination, systemDark = false) { services } })
            }
            // Capture 10 : la vue Jour de la capture 1, système en sombre, rien de réglé (docs/spec/apparence.md § Thème sombre).
            val darkServices = runBlocking { services(locale.language) }
            write(render { KairosApp(Platform.ANDROID, Destination.DAY, systemDark = true) { darkServices } })
        }
        println("Captures des magasins écrites dans $metadataDir")
        0
    } catch (e: Throwable) {
        e.printStackTrace()
        1
    }

    /**
     * `--story-screenshots=<dossier>` : `order-fr.png` et `order-en.png`, la vue Jour des fiches (même jeu de données, même
     * heure figée) défilée jusqu'à « Aujourd'hui, dans l'ordre », pour la page « histoire » du site. Versionnées, pas
     * régénérées par la CI : à refaire si la vue Jour change.
     */
    fun runStory(dir: File): Int = try {
        java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("UTC"))
        dir.mkdirs()
        for ((language, locale) in listOf("fr" to Locale.FRANCE, "en" to Locale.US)) {
            Locale.setDefault(locale)
            val services = runBlocking { services(language) }
            File(dir, "order-$language.png").writeBytes(render(wheel = STORY_SCROLL) { KairosApp(Platform.ANDROID, Destination.DAY, systemDark = false) { services } })
        }
        0
    } catch (e: Throwable) {
        e.printStackTrace()
        1
    }

    /**
     * Capture 2 (docs/spec/publication.md § Captures, docs/spec/estimateur-modele.md § Interface) : la base de la capture 1
     * enrichie de rapports faits et chronométrés, et la fiche d'un rapport au titre nouveau, sans durée saisie, ouverte
     * d'emblée ([KairosApp] `showcaseTaskId`) : « Utiliser 45 min (entre … et …) » et « Tâches proches » dépliée. Le calcul
     * du modèle se fait hors composition : on attend la stabilité.
     */
    private fun machineLearningShot(language: String): ByteArray {
        val services = runBlocking { services(language, machineLearning = true) }
        val title = (if (language == "fr") FR else EN).quarterly
        val id = services.repository.snapshot.value.tasks.first { it.title == title }.id
        return render(settle = true, wheel = SHEET_SCROLL) { KairosApp(Platform.ANDROID, Destination.DAY, systemDark = false, showcaseTaskId = id) { services } }
    }

    private suspend fun services(language: String, machineLearning: Boolean = false): AppServices {
        val clock = object : Clock {
            override fun now() = now
        }
        val repository = KairosRepository.open(
            KairosStore.open(JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)),
            examples = { snapshot(language, machineLearning) },
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

    /**
     * Base de l'espace Équipe (docs/spec/publication.md § Captures) et, pour Prévisions, la simulation **déjà calculée** :
     * modèle par effort, travail assigné, nombre de tirages par défaut, graine fixe, d'un trait avant le rendu. Posée
     * avec l'empreinte des données qui l'ont produite, elle ne s'affiche pas « périmée ».
     */
    private suspend fun teamServices(language: String, destination: TeamDestination): AppServices {
        val services = StoreTeam.open(language == "en", today, now)
        if (destination == TeamDestination.FORECAST) {
            services.teamUi.forecast = ForecastRun.computeNow(
                services.repository.snapshot.value, now, TimeZone.UTC, ForecastSpec(ForecastScope.Assigned), StoreTeam.FORECAST_SEED,
            )
        }
        return services
    }

    @OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
    private fun render(settle: Boolean = false, wheel: Float = 0f, content: @Composable () -> Unit): ByteArray {
        val scene = ImageComposeScene(WIDTH, HEIGHT, Density(2.625f), content = content)
        try {
            var image = scene.render(0)
            for (frame in 1..12) image = scene.render(frame * 100_000_000L)
            var png = requireNotNull(image.encodeToData(EncodedImageFormat.PNG)) { "encodage PNG" }.bytes
            // Espace Équipe : les calculs lourds se font hors composition, en temps réel. On attend que l'image ne change
            // plus (l'indicateur de progression, animé, la fait changer tant que ça calcule), comme l'auto-test.
            var stable = 0
            var frame = 13
            while (settle && stable < 3 && frame < 13 + MAX_SETTLE_FRAMES) {
                Thread.sleep(100)
                val next = requireNotNull(scene.render(frame++ * 100_000_000L).encodeToData(EncodedImageFormat.PNG)) { "encodage PNG" }.bytes
                stable = if (next.contentEquals(png)) stable + 1 else 0
                png = next
            }
            if (wheel != 0f) {
                // Défilement à la molette, une fois la mise en page stable : le résultat en haut de l'écran.
                scene.sendPointerEvent(
                    PointerEventType.Scroll, Offset(WIDTH / 2f, HEIGHT / 2f), scrollDelta = Offset(0f, wheel), timeMillis = 1_300L,
                )
                for (i in 0 until 6) image = scene.render((frame++) * 100_000_000L)
                png = requireNotNull(image.encodeToData(EncodedImageFormat.PNG)) { "encodage PNG" }.bytes
            }
            return png
        } finally {
            scene.close()
        }
    }

    /** Nombre maximal d'images (de 100 ms) attendues après le rendu initial pour que les calculs asynchrones aboutissent. */
    private const val MAX_SETTLE_FRAMES = 80

    /** Défilement (en crans de molette) de la capture Prévisions, pour que le résultat soit en haut de l'écran. */
    private const val FORECAST_SCROLL = 24f

    /** Défilement (en crans de molette) de la fiche de la capture 2, pour que l'estimation par machine learning soit à l'écran. */
    private const val SHEET_SCROLL = 40f

    /** Défilement (en crans de molette) de la capture de la page « histoire » : la liste ordonnée sous la carte « maintenant ». */
    private const val STORY_SCROLL = 21f

    /** Textes du jeu de données, par langue. */
    private class Words(
        val types: String,
        val dev: String, val review: String, val meeting: String, val doc: String, val admin: String,
        val leak: String, val demo: String, val mr: String, val standup: String, val install: String,
        val survey: String, val deploy: String, val report: String,
        val project: String, val focus: String, val lunch: String, val sync: String,
        val notes: List<String>, val history: List<String>,
        /** Capture 2 : rapports déjà faits (titres voisins, durées réelles proches) et le rapport à estimer. */
        val reports: List<String>, val quarterly: String,
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
        reports = listOf(
            "Rédiger le rapport d’activité de mai", "Rédiger le rapport d’activité de juin", "Rédiger le rapport d’activité de juillet",
            "Rédiger le rapport d’activité d’août", "Rédiger le rapport d’activité de septembre", "Rédiger le rapport d’audit des accès",
            "Rédiger le rapport de tests de charge", "Rédiger le rapport d’incident de la mise en production",
        ),
        quarterly = "Rédiger le rapport d’activité du trimestre",
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
        reports = listOf(
            "Write the May activity report", "Write the June activity report", "Write the July activity report",
            "Write the August activity report", "Write the September activity report", "Write the access audit report",
            "Write the load testing report", "Write the release incident report",
        ),
        quarterly = "Write the quarterly activity report",
    )

    private fun snapshot(language: String, machineLearning: Boolean = false): KairosSnapshot {
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
        if (machineLearning) {
            // Capture 2 : huit rapports faits ces dernières semaines, chronométrés entre 40 et 60 min, puis le rapport du
            // trimestre, sans durée saisie : le machine learning l'estime d'après eux (docs/spec/estimateur-modele.md).
            val real = listOf(45, 50, 40, 55, 45, 60, 50, 45)
            w.reports.forEachIndexed { i, title ->
                val day = today.minus(DatePeriod(days = 52 - 7 * i))
                val id = task(
                    title, 2, 3, null, w.doc, status = TaskStatus.DONE, updated = instant(day, 16),
                    createdAt = instant(day.minus(DatePeriod(days = 1)), 9),
                )
                val start = instant(day, 14)
                sessions += WorkSession(sessions.size + 1L, id, start, start + real[i].minutes, start)
            }
            task(w.quarterly, 1, 3, null, w.doc, today.plus(DatePeriod(days = 4)))
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

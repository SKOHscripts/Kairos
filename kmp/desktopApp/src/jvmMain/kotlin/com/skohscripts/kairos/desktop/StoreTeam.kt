package com.skohscripts.kairos.desktop

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.skohscripts.kairos.core.model.KairosSnapshot
import com.skohscripts.kairos.core.model.Settings
import com.skohscripts.kairos.core.model.TeamSettings
import com.skohscripts.kairos.data.KairosRepository
import com.skohscripts.kairos.data.KairosStore
import com.skohscripts.kairos.data.TaskEdit
import com.skohscripts.kairos.ui.app.AppServices
import com.skohscripts.kairos.ui.app.ChronoNotifier
import com.skohscripts.kairos.ui.app.FileService
import com.skohscripts.kairos.ui.app.NotifyState
import com.skohscripts.kairos.ui.team.forecast.TeamUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * Équipe de vitrine des captures 6 à 8 des magasins (docs/spec/publication.md § Captures) : trois membres dont
 * « moi », une absence à venir, treize tâches d'équipe à divers états et signaux et dix-huit tâches faites, estimées
 * et chronométrées sur neuf semaines (de quoi donner à la prévision une source « historique » fiable et rendre le
 * modèle par débit disponible). Les titres, le nom de l'équipe et les catégories sont dans la langue de la capture.
 *
 * Distincte de [TeamSeed] (auto-test) : celle-ci suit le jour réel et l'horloge du système, alors que les captures
 * des magasins ne dépendent ni du jour ni de la machine. Toutes les dates sont relatives à [today], les jours passés
 * sont des jours **ouvrés** (une tâche n'est jamais faite un dimanche), et le dépôt écrit avec une horloge qui
 * remonte dans le temps, comme [TeamSeed] : le journal de chaque tâche porte des dates cohérentes. Le dépôt tire
 * des identifiants aléatoires (`uid`), mais aucun n'est affiché ni ne départage un tri : deux exécutions rendent
 * les mêmes images.
 */
internal object StoreTeam {
    /** Horloge pilotée : les dates passées pendant l'écriture des données, puis l'instant de la capture. */
    private class SteppingClock(var current: Instant) : Clock {
        override fun now(): Instant = current
    }

    /** Titres dans la langue de la capture. */
    private class Words(val english: Boolean) {
        fun t(fr: String, en: String) = if (english) en else fr
    }

    /** Une tâche faite : [who] 0 = Claire, 1 = Alex, 2 = Sam ; [type] indexe les types de la langue. */
    private class Past(val fr: String, val en: String, val type: Int, val who: Int, val workdaysAgo: Int, val estimate: Double, val spent: Double)

    // Neuf semaines (la fenêtre d'historique est de douze) : au moins une tâche faite par semaine, des estimations
    // tenues ou dépassées de 0 à 50 % (réel / estimé de 1 à 1,5), comme dans une vraie équipe.
    private val HISTORY = listOf(
        Past("Valider le budget du T4", "Approve the Q4 budget", 4, 0, 1, 3.0, 3.5),
        Past("Analyser les journaux d’erreur", "Analyse the error logs", 0, 1, 3, 6.0, 5.0),
        Past("Mettre à jour les dépendances", "Update the dependencies", 0, 1, 5, 2.0, 2.0),
        Past("Revue de code du module de paiement", "Review the payment module", 1, 0, 6, 4.0, 5.5),
        Past("Préparer la rétrospective", "Prepare the retrospective", 2, 0, 8, 5.0, 4.5),
        Past("Maquettes de l’écran de connexion", "Mock up the sign-in screen", 3, 2, 10, 3.0, 6.0),
        Past("Écrire les tests du module d’export", "Write the export module tests", 0, 1, 11, 8.0, 9.0),
        Past("Rédiger la note de version", "Write the release notes", 3, 2, 13, 4.0, 4.0),
        Past("Corriger l’affichage mobile", "Fix the mobile layout", 0, 1, 15, 6.0, 4.5),
        Past("Former la nouvelle recrue", "Onboard the new hire", 2, 0, 16, 2.0, 3.0),
        Past("Revue de l’architecture", "Architecture review", 1, 1, 18, 5.0, 8.0),
        Past("Préparer le comité de pilotage", "Prepare the steering committee", 2, 0, 20, 3.0, 3.5),
        Past("Automatiser le déploiement", "Automate the deployment", 0, 1, 23, 8.0, 16.0),
        Past("Mettre à jour le guide d’installation", "Update the installation guide", 3, 2, 25, 4.0, 5.0),
        Past("Configurer la supervision", "Set up monitoring", 0, 1, 28, 2.0, 2.0),
        Past("Documenter l’API de recherche", "Document the search API", 3, 2, 31, 6.0, 7.0),
        Past("Nettoyer la configuration CI", "Clean up the CI configuration", 0, 1, 34, 3.0, 7.0),
        Past("Revue de code du module d’import", "Review the import module", 1, 0, 38, 5.0, 5.5),
    )

    suspend fun open(english: Boolean, today: LocalDate, now: Instant): AppServices {
        val w = Words(english)
        val zone = TimeZone.UTC
        val clock = SteppingClock(now)

        /** Le n-ième jour ouvré avant [today] (lundi à vendredi). */
        fun workday(n: Int): LocalDate {
            var day = today
            var left = n
            while (left > 0) {
                day = day.minus(DatePeriod(days = 1))
                if (day.dayOfWeek.ordinal < 5) left--
            }
            return day
        }
        fun at(day: LocalDate, hour: Int) = LocalDateTime(day, LocalTime(hour, 0)).toInstant(zone)
        fun inDays(n: Int) = today.plus(DatePeriod(days = n))
        /** Pose l'horloge sur le n-ième jour ouvré passé, à [hour] h. */
        fun back(n: Int, hour: Int = 9) {
            clock.current = at(workday(n), hour)
        }

        val types = (if (english) Settings.DEFAULT_TASK_TYPES_EN else Settings.DEFAULT_TASK_TYPES_FR).split(',')
        val repository = KairosRepository.open(
            KairosStore.open(JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)),
            examples = { KairosSnapshot(settings = Settings(taskTypes = types.joinToString(","))) },
            clock = clock,
            timeZone = zone,
        )
        repository.updateSettings(
            repository.snapshot.value.settings.copy(
                // Horizon de deux semaines : Alex y est surchargé, Sam à surveiller (docs/spec/equipe-charge.md).
                team = TeamSettings(
                    enabled = true,
                    name = w.t("Équipe Plateforme", "Platform team"),
                    managerName = "Claire",
                    lastSpace = TeamSettings.SPACE_TEAM,
                    horizonWeeks = 2,
                ),
            ),
        )
        val dev = types[0]
        val review = types[1]
        val meeting = types[2]
        val doc = types[3]

        back(30)
        val claire = repository.createMember("Claire Martin", w.t("Lead technique", "Tech lead"), 100, 7.0, true)!!
        val alex = repository.createMember("Alex Dupont", w.t("Développeur back-end", "Back-end developer"), 80, 7.0, false)!!
        val sam = repository.createMember("Sam Bernard", "Designer", 60, 6.5, false)!!
        repository.addAbsence(sam, inDays(6), inDays(8), w.t("Congés", "Leave"))
        val crew = listOf(claire, alex, sam)

        /** Crée une tâche d'équipe [ago] jours ouvrés avant aujourd'hui, la qualifie, et rend son identifiant. */
        suspend fun create(
            title: String, ago: Int, priority: Int? = null, points: Int? = null, type: String = "", deadline: LocalDate? = null,
            estimateHours: Int? = null, blockers: Set<Long> = emptySet(),
        ): Long {
            back(ago)
            val id = repository.createTeamTask(title)!!
            if (priority != null || points != null || type.isNotEmpty() || deadline != null || estimateHours != null || blockers.isNotEmpty()) {
                repository.updateTask(
                    id,
                    TaskEdit(
                        title = title, priority = priority, points = points, taskType = type, deadline = deadline, pinDay = today,
                        estimatedMinutes = estimateHours?.let { it * 60 }, blockerIds = blockers,
                    ),
                )
            }
            return id
        }
        /** Assigne à [member] [ago] jours ouvrés avant aujourd'hui, puis déclare les avancements `(jours ouvrés avant, %)`. */
        suspend fun assign(id: Long, member: Long, ago: Int, vararg progress: Pair<Int, Int>) {
            back(ago)
            repository.assign(listOf(id), member)
            for ((daysAgo, percent) in progress) {
                back(daysAgo)
                repository.setProgress(id, percent)
            }
        }

        // --- Historique : tâches faites, estimées en heures et chronométrées (temps passé saisi), créées quatre jours
        // ouvrés avant la fin. Sans points : la calibration des points de l'équipe ne bouge pas.
        for (past in HISTORY) {
            val title = w.t(past.fr, past.en)
            val id = create(title, past.workdaysAgo + 4)
            repository.updateTask(
                id,
                TaskEdit(
                    title = title, priority = 1, taskType = types[past.type], pinDay = today,
                    estimatedMinutes = (past.estimate * 60).toInt(), manualTimeSpentMinutes = (past.spent * 60).toInt(),
                ),
            )
            assign(id, crew[past.who], past.workdaysAgo + 3)
            back(past.workdaysAgo, hour = 16)
            repository.toggleDone(id)
        }

        // --- Backlog : une tâche à qualifier, trois prêtes
        create(w.t("Migrer les tests vers JUnit 5", "Migrate the tests to JUnit 5"), 1)
        create(w.t("Documenter les points d’accès publics", "Document the public endpoints"), 2, priority = 2, points = 3, type = doc)
        create(w.t("Préparer la démo client", "Prepare the customer demo"), 3, priority = 1, points = 3, type = meeting, deadline = inDays(8), estimateHours = 4)
        create(w.t("Audit de sécurité des dépendances", "Audit the dependencies for security"), 5, priority = 1, points = 8, type = review, deadline = inDays(9), estimateHours = 10)

        // --- Claire (moi, lead technique) : quatre tâches en cours (la limite est de trois), plus de travail que de
        // capacité sur l'horizon (dépassement), une tâche non estimée comptée à part dans la charge
        val billing = create(w.t("Refonte de l’API de facturation", "Rework the billing API"), 12, priority = 0, points = 13, type = dev, deadline = inDays(9), estimateHours = 48)
        assign(billing, claire, 9, 6 to 30, 3 to 60)
        val migration = create(w.t("Migration de la base vers SQLite", "Migrate the database to SQLite"), 10, priority = 1, points = 8, type = dev, estimateHours = 36)
        assign(migration, claire, 8, 7 to 10)
        val pagination = create(w.t("Corriger l’erreur de pagination", "Fix the pagination error"), 3, priority = 1, points = 2, type = dev, estimateHours = 4)
        assign(pagination, claire, 2, 1 to 80)
        val plan = create(w.t("Revue du plan de charge", "Review the workload plan"), 6, priority = 1, points = 3, type = meeting, estimateHours = 3)
        assign(plan, claire, 5, 1 to 40)
        val tender = create(w.t("Répondre à l’appel d’offres", "Answer the call for tenders"), 1, priority = 1)
        assign(tender, claire, 1)

        // --- Alex : une revue en retard, sans avancement depuis une semaine, et de quoi avancer
        val export = create(w.t("Revue de code du module d’export", "Review the export module"), 8, priority = 1, points = 3, type = review, deadline = inDays(-4), estimateHours = 5)
        assign(export, alex, 7, 6 to 30)
        val vat = create(w.t("Corriger le calcul de la TVA", "Fix the VAT computation"), 3, priority = 0, points = 5, type = dev, deadline = inDays(3), estimateHours = 6)
        assign(vat, alex, 2, 1 to 20)

        // --- Sam : des maquettes en cours, un atelier qui en dépend avant qu'elles soient finies (échéance en danger)
        val mockups = create(w.t("Maquettes de l’écran d’export", "Mock up the export screen"), 6, priority = 1, points = 5, type = doc, estimateHours = 24)
        assign(mockups, sam, 5, 1 to 50)
        val workshop = create(
            w.t("Atelier utilisateurs", "User workshop"), 4, priority = 1, points = 3, type = meeting, deadline = inDays(2),
            estimateHours = 8, blockers = setOf(mockups),
        )
        assign(workshop, sam, 4)

        // Identités d'échange tirées au hasard par le dépôt : les simulations en dérivent leurs tirages (clé FNV-1a du
        // `teamUid`, docs/spec/equipe-simulation.md), donc des chiffres différents d'une exécution à l'autre malgré la
        // graine fixe. On les remplace par des identités fixes, ce que fait un import.
        val written = repository.snapshot.value
        repository.replaceAll(
            written.copy(
                tasks = written.tasks.map { if (it.teamUid != null) it.copy(teamUid = "store-task-${it.id}") else it },
                members = written.members.map { it.copy(uid = "store-member-${it.id}") },
                settings = written.settings.copy(team = written.settings.team?.copy(identity = "store-team")),
            ),
        )
        clock.current = now
        val files = object : FileService {
            override suspend fun saveText(suggestedName: String, text: String) = false
            override suspend fun openText(): String? = null
        }
        val notifier = object : ChronoNotifier {
            override val state = MutableStateFlow(NotifyState.ACTIVE)
        }
        return AppServices(repository, files, { _, _ -> }, clock = clock, notifier = notifier, teamUi = TeamUiState(seed = { FORECAST_SEED }))
    }

    /** Graine fixe des prévisions des captures : mêmes chiffres à chaque exécution (docs/spec/equipe-simulation.md). */
    const val FORECAST_SEED = 20_261_006L
}

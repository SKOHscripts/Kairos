package com.skohscripts.kairos.desktop

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.skohscripts.kairos.core.model.KairosSnapshot
import com.skohscripts.kairos.core.model.TeamSettings
import com.skohscripts.kairos.core.team.forecast.ScenarioModification
import com.skohscripts.kairos.data.KairosRepository
import com.skohscripts.kairos.data.KairosStore
import com.skohscripts.kairos.data.TaskEdit
import com.skohscripts.kairos.ui.app.AppServices
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atTime
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.todayIn
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * Équipe de démonstration de l'auto-test (docs/spec/distribution.md § Auto-test) :
 * trois membres (capacités différentes, une absence) et une vingtaine de tâches d'équipe à tous
 * les états et avec tous les signaux (en retard, sans avancement, ballottée, trop d'en-cours,
 * bloquée) ; pour la charge (jalon E4) des estimations en heures, un membre surchargé (Alex), un
 * à surveiller (Sam), des échéances en danger et une tâche non estimée, et pour les prévisions (jalon E5)
 * un historique de quatorze tâches faites sur dix semaines (estimations et temps passé), écrites
 * par le dépôt avec une horloge qui remonte dans le temps : le journal de chaque tâche
 * porte des dates différentes. Base en mémoire, langue du système.
 */
object TeamSeed {
    /** Horloge pilotable : les jours passés pendant l'écriture des données, puis l'heure réelle. */
    private class SeedClock : Clock {
        var fixed: Instant? = null
        override fun now(): Instant = fixed ?: Clock.System.now()
    }

    class Seeded(val services: AppServices, val today: LocalDate, val ids: Map<String, Long>)

    suspend fun open(english: Boolean = false): Seeded {
        val zone = TimeZone.currentSystemDefault()
        val clock = SeedClock()
        val today = Clock.System.todayIn(zone)
        fun at(daysAgo: Int, hour: Int = 9): Instant = today.minus(DatePeriod(days = daysAgo)).atTime(hour, 0).toInstant(zone)
        fun inDays(n: Int): LocalDate = today.plus(DatePeriod(days = n))

        clock.fixed = at(14)
        val opened = KairosStore.open(JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY))
        val repository = KairosRepository.open(opened, { KairosSnapshot() }, clock, zone)
        val settings = repository.snapshot.value.settings
        repository.updateSettings(
            settings.copy(
                // Horizon de 2 semaines : Alex y est surchargé, Sam à surveiller (jalon E4, docs/spec/equipe-charge.md).
                team = TeamSettings(
                    enabled = true, name = if (english) "Platform team" else "Équipe Plateforme", managerName = "Claire",
                    lastSpace = TeamSettings.SPACE_TEAM, horizonWeeks = 2,
                ),
            ),
        )
        val types = settings.taskTypeList.let { if (english) listOf("Development", "Code review", "Meeting", "Documentation") else it }
        val dev = types[0]
        val review = types[1]
        val meeting = types[2]
        val docs = types[3]

        val claire = repository.createMember("Claire Martin", if (english) "Team lead" else "Responsable d’équipe", 100, 7.0, true)!!
        val alex = repository.createMember("Alex Dupont", if (english) "Back-end developer" else "Développeur back-end", 80, 7.0, false)!!
        val sam = repository.createMember("Sam Bernard", if (english) "Designer" else "Designer", 60, 6.5, false)!!
        repository.addAbsence(sam, today, today.plus(DatePeriod(days = 1)), if (english) "Training" else "Formation")

        val ids = LinkedHashMap<String, Long>()
        suspend fun create(key: String, title: String, daysAgo: Int): Long {
            clock.fixed = at(daysAgo)
            return repository.createTeamTask(title)!!.also { ids[key] = it }
        }
        suspend fun qualify(
            id: Long, title: String, priority: Int, points: Int, type: String, deadline: LocalDate? = null, blockers: Set<Long> = emptySet(),
            estimateHours: Int? = null,
        ) {
            repository.updateTask(
                id,
                TaskEdit(
                    title = title, priority = priority, points = points, taskType = type, deadline = deadline, pinDay = today,
                    blockerIds = blockers, estimatedMinutes = estimateHours?.let { it * 60 },
                ),
            )
        }

        // --- Backlog : deux à qualifier, quatre prêtes (dont une en retard)
        create("junit", if (english) "Migrate the tests to JUnit 5" else "Migrer les tests vers JUnit 5", 0)
        val endpoints = create("endpoints", if (english) "Document the public endpoints" else "Documenter les endpoints publics", 1)
        repository.setPriority(endpoints, 2)
        val vat = create("vat", if (english) "Fix the VAT computation" else "Corriger le calcul de TVA", 3)
        qualify(vat, if (english) "Fix the VAT computation" else "Corriger le calcul de TVA", 0, 5, dev, inDays(2))
        val demo = create("demo", if (english) "Prepare the customer demo" else "Préparer la démo client", 3)
        qualify(demo, if (english) "Prepare the customer demo" else "Préparer la démo client", 1, 3, meeting, inDays(6))
        val install = create("install", if (english) "Update the installation guide" else "Mettre à jour le guide d’installation", 2)
        qualify(install, if (english) "Update the installation guide" else "Mettre à jour le guide d’installation", 2, 2, docs)
        val audit = create("audit", if (english) "Audit the dependencies for security" else "Audit de sécurité des dépendances", 5)
        qualify(audit, if (english) "Audit the dependencies for security" else "Audit de sécurité des dépendances", 1, 8, review, inDays(-1))

        // --- Alex : quatre tâches en cours (limite de trois dépassée), dont une sans avancement depuis deux semaines
        val billing = create("billing", if (english) "Rework the billing API" else "Refonte de l’API de facturation", 10)
        qualify(billing, if (english) "Rework the billing API" else "Refonte de l’API de facturation", 0, 13, dev, inDays(5))
        clock.fixed = at(9)
        repository.assign(listOf(billing), alex)
        clock.fixed = at(6)
        repository.setProgress(billing, 30)
        clock.fixed = at(3)
        repository.setProgress(billing, 60)

        val export = create("export", if (english) "Review the export module" else "Revue de code du module d’export", 8)
        qualify(export, if (english) "Review the export module" else "Revue de code du module d’export", 1, 3, review, inDays(-2))
        clock.fixed = at(7)
        repository.assign(listOf(export), alex)
        clock.fixed = at(4)
        repository.setProgress(export, 30)

        val migration = create("migration", if (english) "Migrate the database to SQLite" else "Migration de la base vers SQLite", 14)
        qualify(migration, if (english) "Migrate the database to SQLite" else "Migration de la base vers SQLite", 1, 8, dev, estimateHours = 24)
        repository.assign(listOf(migration), alex)
        repository.setProgress(migration, 10)

        val pagination = create("pagination", if (english) "Fix the pagination error" else "Corriger l’erreur de pagination", 3)
        qualify(pagination, if (english) "Fix the pagination error" else "Corriger l’erreur de pagination", 1, 2, dev)
        clock.fixed = at(2)
        repository.assign(listOf(pagination), alex)
        repository.setProgress(pagination, 80)

        // Ballottée : Alex -> Sam -> Claire -> Alex (trois réaffectations)
        val guide = create("guide", if (english) "Write the integration guide" else "Écrire le guide d’intégration", 10)
        qualify(guide, if (english) "Write the integration guide" else "Écrire le guide d’intégration", 2, 3, docs)
        clock.fixed = at(9)
        repository.assign(listOf(guide), alex)
        clock.fixed = at(7)
        repository.assign(listOf(guide), sam)
        clock.fixed = at(5)
        repository.assign(listOf(guide), claire)
        clock.fixed = at(3)
        repository.assign(listOf(guide), alex)

        val ci = create("ci", if (english) "Clean up the CI configuration" else "Nettoyer la configuration CI", 9)
        qualify(ci, if (english) "Clean up the CI configuration" else "Nettoyer la configuration CI", 2, 2, dev)
        repository.assign(listOf(ci), alex)
        clock.fixed = at(4)
        repository.setProgress(ci, 50)
        clock.fixed = at(3)
        repository.toggleDone(ci)

        // --- Claire (moi)
        val plan = create("plan", if (english) "Review the workload plan" else "Revue du plan de charge", 6)
        qualify(plan, if (english) "Review the workload plan" else "Revue du plan de charge", 1, 3, meeting)
        clock.fixed = at(5)
        repository.assign(listOf(plan), claire)
        clock.fixed = at(1)
        repository.setProgress(plan, 40)
        val standup = create("standup", if (english) "Prepare the team meeting" else "Préparer la réunion d’équipe", 2)
        qualify(standup, if (english) "Prepare the team meeting" else "Préparer la réunion d’équipe", 2, 1, meeting, inDays(1))
        repository.assign(listOf(standup), claire)
        val budget = create("budget", if (english) "Approve the Q4 budget" else "Valider le budget du T4", 8)
        qualify(budget, if (english) "Approve the Q4 budget" else "Valider le budget du T4", 0, 2, meeting)
        repository.assign(listOf(budget), claire)
        clock.fixed = at(2)
        repository.toggleDone(budget)

        // Assignée à « moi » sans points : « non estimée » (comptée à part dans la charge)
        val tender = create("tender", if (english) "Answer the call for tenders" else "Répondre à l’appel d’offres", 1)
        repository.setPriority(tender, 1)
        repository.assign(listOf(tender), claire)

        // --- Sam : des maquettes en cours, un atelier qui en dépend
        val mockups = create("mockups", if (english) "Mock up the export screen" else "Maquettes de l’écran d’export", 6)
        qualify(mockups, if (english) "Mock up the export screen" else "Maquettes de l’écran d’export", 1, 5, docs, estimateHours = 18)
        clock.fixed = at(5)
        repository.assign(listOf(mockups), sam)
        clock.fixed = at(1)
        repository.setProgress(mockups, 50)
        val workshop = create("workshop", if (english) "User workshop" else "Atelier utilisateurs", 4)
        qualify(workshop, if (english) "User workshop" else "Atelier utilisateurs", 1, 3, meeting, inDays(1), blockers = setOf(mockups))
        repository.assign(listOf(workshop), sam)

        // --- Historique (jalon E5, docs/spec/equipe-simulation.md § Données) : quatorze tâches faites sur dix semaines, avec
        // estimation (en heures) et temps passé, de quoi donner des facteurs d'erreur d'estimation (de 1 à 1,6 fois) et des
        // débits hebdomadaires aux prévisions. Sans points : la calibration des points de l'espace Équipe ne bouge pas.
        val crew = listOf(claire, alex, sam)
        val history = listOf(
            // (jours écoulés depuis la fin, estimation en heures, temps passé en heures)
            Triple(9, 4.0, 5.0), Triple(10, 6.0, 5.0), Triple(12, 3.0, 4.5), Triple(16, 8.0, 9.0), Triple(17, 2.0, 2.0),
            Triple(19, 5.0, 8.0), Triple(23, 4.0, 4.0), Triple(24, 3.0, 3.5), Triple(30, 6.0, 7.0), Triple(31, 2.0, 3.0),
            Triple(37, 8.0, 8.0), Triple(44, 5.0, 6.5), Triple(51, 3.0, 3.0), Triple(58, 4.0, 6.0),
        )
        history.forEachIndexed { index, (daysAgo, estimate, spent) ->
            clock.fixed = at(daysAgo + 4)
            val title = if (english) "Past ticket ${index + 1}" else "Ticket passé ${index + 1}"
            val id = repository.createTeamTask(title)!!
            repository.updateTask(
                id,
                TaskEdit(
                    title = title, priority = 1, taskType = listOf(dev, review, docs, meeting)[index % 4], pinDay = today,
                    estimatedMinutes = (estimate * 60).toInt(), manualTimeSpentMinutes = (spent * 60).toInt(),
                ),
            )
            repository.assign(listOf(id), crew[index % 3])
            clock.fixed = at(daysAgo, hour = 16)
            repository.toggleDone(id)
        }

        // --- Scénarios « Et si… ? » (jalon E5) : un renfort, une absence, une charge en plus.
        fun uid(key: String): String = repository.snapshot.value.tasks.first { it.id == ids.getValue(key) }.teamUid!!
        clock.fixed = at(1)
        ids["scenario-reinforcement"] = repository.createScenario(
            if (english) "Reinforcement: Jo takes three tasks" else "Renfort : Jo reprend trois tâches",
            listOf(
                ScenarioModification.AddMember(-1, "Jo", 100, 7.0),
                ScenarioModification.Reassign(uid("billing"), -1),
                ScenarioModification.Reassign(uid("export"), -1),
                // Faite depuis : le scénario le signale (« tâche déjà faite »).
                ScenarioModification.Reassign(uid("ci"), -1),
            ),
        )!!
        ids["scenario-absence"] = repository.createScenario(
            if (english) "Sam away for two weeks" else "Sam absent deux semaines",
            listOf(ScenarioModification.AddAbsence(sam, inDays(1), inDays(14))),
        )!!
        ids["scenario-more-work"] = repository.createScenario(
            if (english) "Focus at 60% and five more tasks" else "Focus à 60 % et cinq tâches en plus",
            listOf(ScenarioModification.SetFocus(0.6), ScenarioModification.AddTasks(5, 3, dev, 1)),
        )!!

        clock.fixed = null
        val files = object : com.skohscripts.kairos.ui.app.FileService {
            override suspend fun saveText(suggestedName: String, text: String) = false
            override suspend fun openText(): String? = null
        }
        return Seeded(AppServices(repository, files, { _, _ -> }, clock = clock), today, ids)
    }
}

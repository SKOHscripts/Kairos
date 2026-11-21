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

    /**
     * Titres dans la langue de la capture : le français et l'anglais à l'appel, l'allemand, l'espagnol, l'italien et le russe
     * dans [OTHER], par le texte français. Un titre sans traduction fait échouer la génération plutôt que d'en mêler deux.
     */
    private class Words(val language: String) {
        fun t(fr: String, en: String): String = when (language) {
            "fr" -> fr
            "en" -> en
            else -> OTHER[fr]?.get(OTHER_LANGUAGES.indexOf(language)) ?: error("store: no $language title for « $fr »")
        }
    }

    private val OTHER_LANGUAGES = listOf("de", "es", "it", "ru")

    /** Textes français → allemand, espagnol, italien, russe (même ordre que [OTHER_LANGUAGES]). */
    private val OTHER: Map<String, List<String>> = mapOf(
        "Équipe Plateforme" to listOf("Plattform-Team", "Equipo Plataforma", "Team Piattaforma", "Команда платформы"),
        "Lead technique" to listOf("Tech Lead", "Líder técnica", "Tech lead", "Техлид"),
        "Développeur back-end" to listOf("Backend-Entwickler", "Desarrollador back-end", "Sviluppatore back-end", "Бэкенд-разработчик"),
        "Congés" to listOf("Urlaub", "Vacaciones", "Ferie", "Отпуск"),
        "Valider le budget du T4" to listOf("Budget für Q4 freigeben", "Aprobar el presupuesto del T4", "Approvare il budget del Q4", "Утвердить бюджет на 4-й квартал"),
        "Analyser les journaux d’erreur" to listOf("Fehlerprotokolle analysieren", "Analizar los registros de errores", "Analizzare i log degli errori", "Проанализировать журналы ошибок"),
        "Mettre à jour les dépendances" to listOf("Abhängigkeiten aktualisieren", "Actualizar las dependencias", "Aggiornare le dipendenze", "Обновить зависимости"),
        "Revue de code du module de paiement" to listOf("Code-Review des Zahlungsmoduls", "Revisión del módulo de pagos", "Revisione del modulo pagamenti", "Ревью модуля платежей"),
        "Préparer la rétrospective" to listOf("Retrospektive vorbereiten", "Preparar la retrospectiva", "Preparare la retrospettiva", "Подготовить ретроспективу"),
        "Maquettes de l’écran de connexion" to listOf("Mock-ups des Anmeldebildschirms", "Maquetas de la pantalla de acceso", "Mockup della schermata di accesso", "Макеты экрана входа"),
        "Écrire les tests du module d’export" to listOf("Tests für das Exportmodul schreiben", "Escribir las pruebas del módulo de exportación", "Scrivere i test del modulo di esportazione", "Написать тесты модуля экспорта"),
        "Rédiger la note de version" to listOf("Release Notes schreiben", "Redactar las notas de la versión", "Scrivere le note di rilascio", "Написать примечания к выпуску"),
        "Corriger l’affichage mobile" to listOf("Mobile Darstellung korrigieren", "Corregir la vista móvil", "Correggere la vista mobile", "Исправить мобильную вёрстку"),
        "Former la nouvelle recrue" to listOf("Neue Kollegin einarbeiten", "Formar a la nueva incorporación", "Formare il nuovo arrivato", "Ввести в курс нового сотрудника"),
        "Revue de l’architecture" to listOf("Architektur-Review", "Revisión de la arquitectura", "Revisione dell’architettura", "Ревью архитектуры"),
        "Préparer le comité de pilotage" to listOf("Lenkungsausschuss vorbereiten", "Preparar el comité de dirección", "Preparare il comitato direttivo", "Подготовить управляющий комитет"),
        "Automatiser le déploiement" to listOf("Deployment automatisieren", "Automatizar el despliegue", "Automatizzare il rilascio", "Автоматизировать развёртывание"),
        "Mettre à jour le guide d’installation" to listOf("Installationsanleitung aktualisieren", "Actualizar la guía de instalación", "Aggiornare la guida di installazione", "Обновить руководство по установке"),
        "Configurer la supervision" to listOf("Monitoring einrichten", "Configurar la monitorización", "Configurare il monitoraggio", "Настроить мониторинг"),
        "Documenter l’API de recherche" to listOf("Such-API dokumentieren", "Documentar la API de búsqueda", "Documentare l’API di ricerca", "Задокументировать API поиска"),
        "Nettoyer la configuration CI" to listOf("CI-Konfiguration aufräumen", "Limpiar la configuración de CI", "Ripulire la configurazione CI", "Почистить конфигурацию CI"),
        "Revue de code du module d’import" to listOf("Code-Review des Importmoduls", "Revisión del módulo de importación", "Revisione del modulo di importazione", "Ревью модуля импорта"),
        "Migrer les tests vers JUnit 5" to listOf("Tests auf JUnit 5 migrieren", "Migrar las pruebas a JUnit 5", "Migrare i test a JUnit 5", "Перевести тесты на JUnit 5"),
        "Documenter les points d’accès publics" to listOf("Öffentliche Endpunkte dokumentieren", "Documentar los endpoints públicos", "Documentare gli endpoint pubblici", "Задокументировать публичные эндпоинты"),
        "Préparer la démo client" to listOf("Kundendemo vorbereiten", "Preparar la demo para el cliente", "Preparare la demo per il cliente", "Подготовить демо для клиента"),
        "Audit de sécurité des dépendances" to listOf("Sicherheitsaudit der Abhängigkeiten", "Auditoría de seguridad de las dependencias", "Audit di sicurezza delle dipendenze", "Аудит безопасности зависимостей"),
        "Refonte de l’API de facturation" to listOf("Abrechnungs-API überarbeiten", "Rediseño de la API de facturación", "Revisione dell’API di fatturazione", "Переработка API биллинга"),
        "Migration de la base vers SQLite" to listOf("Datenbank auf SQLite migrieren", "Migración de la base de datos a SQLite", "Migrazione del database a SQLite", "Миграция базы на SQLite"),
        "Corriger l’erreur de pagination" to listOf("Paginierungsfehler beheben", "Corregir el error de paginación", "Correggere l’errore di paginazione", "Исправить ошибку пагинации"),
        "Revue du plan de charge" to listOf("Auslastungsplan prüfen", "Revisión del plan de carga", "Revisione del piano di carico", "Ревью плана загрузки"),
        "Répondre à l’appel d’offres" to listOf("Ausschreibung beantworten", "Responder a la licitación", "Rispondere al bando di gara", "Ответить на тендер"),
        "Revue de code du module d’export" to listOf("Code-Review des Exportmoduls", "Revisión del módulo de exportación", "Revisione del modulo di esportazione", "Ревью модуля экспорта"),
        "Corriger le calcul de la TVA" to listOf("MwSt.-Berechnung korrigieren", "Corregir el cálculo del IVA", "Correggere il calcolo dell’IVA", "Исправить расчёт НДС"),
        "Maquettes de l’écran d’export" to listOf("Mock-ups des Exportbildschirms", "Maquetas de la pantalla de exportación", "Mockup della schermata di esportazione", "Макеты экрана экспорта"),
        "Atelier utilisateurs" to listOf("Nutzer-Workshop", "Taller con usuarios", "Workshop con gli utenti", "Воркшоп с пользователями"),
    )

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

    suspend fun open(language: String, today: LocalDate, now: Instant): AppServices {
        val w = Words(language)
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

        val types = Settings.defaultTaskTypes(language).split(',')
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

package com.skohscripts.kairos.core

import com.skohscripts.kairos.core.model.BlockKind
import com.skohscripts.kairos.core.model.BlockRecurrence
import com.skohscripts.kairos.core.model.KairosSnapshot
import com.skohscripts.kairos.core.model.Note
import com.skohscripts.kairos.core.model.Settings
import com.skohscripts.kairos.core.model.Task
import com.skohscripts.kairos.core.model.TaskDependency
import com.skohscripts.kairos.core.model.TimeBlock
import com.skohscripts.kairos.core.settings.UiLanguage
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.plus
import kotlin.time.Instant

/**
 * Données d'exemple posées sur une base **neuve** (docs/spec/modele-donnees.md
 * § Exemples) : un jeu d'exemples, traduit. De
 * simples objets ordinaires (tag de projet « Exemple », titre préfixé
 * « [Exemple] ») que l'utilisateur supprime ou termine comme les siens.
 *
 * Pur : dates relatives à [today], horodatages à [now], langue de l'interface.
 */
object ExampleData {
    /**
     * Vrai pour une tâche posée par le jeu d'exemples, dans l'une quelconque des langues : son titre porte le préfixe
     * « [Exemple] » / « [Example] » / « [Ejemplo] »… (docs/spec/premiere-tache.md § Déclenchement). Sert à savoir si l'utilisateur a déjà
     * créé une tâche à lui ; dérivé des données, rien n'est mémorisé.
     */
    fun isExample(task: Task): Boolean = isExampleTitle(task.title)

    /** Vrai pour un titre posé par le jeu d'exemples (tâche, créneau, note), dans l'une quelconque des langues. */
    fun isExampleTitle(title: String): Boolean = Texts.ALL.any { title.startsWith(it.prefix) }

    fun snapshot(today: LocalDate, now: Instant, language: String): KairosSnapshot {
        val t = Texts.of(UiLanguage.lang(language))
        val soon = today.plus(DatePeriod(days = 2))
        val later = today.plus(DatePeriod(days = 7))
        fun at(hour: Int, minute: Int = 0) = LocalDateTime(today, LocalTime(hour, minute))

        val tasks = mutableListOf<Task>()
        fun task(
            title: String,
            description: String = "",
            priority: Int? = null,
            points: Int? = null,
            minutes: Int? = null,
            type: String = "",
            deadline: LocalDate? = null,
            scheduled: LocalDate? = null,
            pinned: LocalDateTime? = null,
            parent: Long? = null,
        ): Long {
            val id = tasks.size + 1L
            tasks += Task(
                id = id,
                title = "${t.prefix} $title",
                description = description,
                priority = priority,
                deadline = deadline,
                projectTag = t.projectTag,
                estimatedMinutes = minutes,
                pinnedStart = pinned,
                parentId = parent,
                scheduledDate = scheduled,
                taskType = type,
                fibonacciPoints = points,
                createdAt = now,
                updatedAt = now,
            )
            return id
        }

        // 1. P0 à échéance proche : en tête du score WSJF.
        task(t.bugTitle, t.bugDesc, priority = 0, points = 3, minutes = 45, type = t.typeDev, deadline = soon)
        // 2. P1 ordinaire, qualifiée.
        task(t.reviewTitle, t.reviewDesc, priority = 1, points = 5, minutes = 60, type = t.typeMeeting, deadline = later)
        // 3. Épinglée à 9 h 30.
        task(t.standupTitle, t.standupDesc, priority = 1, points = 2, minutes = 30, type = t.typeMeeting, pinned = at(9, 30))
        // 4. Tâche mère et deux sous-tâches.
        val parent = task(t.docTitle, t.docDesc, priority = 1, points = 5, type = t.typeDoc)
        task(t.docPlanTitle, priority = 1, points = 2, minutes = 30, type = t.typeDoc, parent = parent)
        task(t.docWriteTitle, priority = 1, points = 3, minutes = 45, type = t.typeDoc, parent = parent)
        // 5. Dépendance : « Déployer » attend « Obtenir la validation ».
        val blocker = task(t.approvalTitle, t.approvalDesc, priority = 1, points = 2, minutes = 20, type = t.typeAdmin)
        val blocked = task(t.deployTitle, t.deployDesc, priority = 0, points = 3, minutes = 30, type = t.typeDev)
        // 6. Boîte de réception : ni priorité ni points.
        task(t.ideaTitle, t.ideaDesc, type = t.typeLearning)
        // 7. Programmée plus tard.
        task(t.certTitle, t.certDesc, priority = 1, points = 3, type = t.typeAdmin, scheduled = later)

        return KairosSnapshot(
            tasks = tasks,
            dependencies = listOf(TaskDependency(1, taskId = blocked, blockerId = blocker, createdAt = now)),
            timeBlocks = listOf(
                TimeBlock(1, "${t.prefix} ${t.meeting}", at(13), at(14), BlockKind.BUSY, createdAt = now),
                TimeBlock(2, "${t.prefix} ${t.deepWork}", at(10), at(11, 30), BlockKind.DEEPWORK, createdAt = now),
                TimeBlock(3, "${t.prefix} ${t.lunch}", at(12), at(13), BlockKind.BUSY, BlockRecurrence.DAILY, createdAt = now),
            ),
            notes = listOf(Note(1, "${t.prefix} ${t.note}", createdAt = now, updatedAt = now)),
            settings = Settings.defaults(language),
        )
    }

    private class Texts(
        val prefix: String,
        val projectTag: String,
        val typeDev: String,
        val typeMeeting: String,
        val typeDoc: String,
        val typeAdmin: String,
        val typeLearning: String,
        val bugTitle: String,
        val bugDesc: String,
        val reviewTitle: String,
        val reviewDesc: String,
        val standupTitle: String,
        val standupDesc: String,
        val docTitle: String,
        val docDesc: String,
        val docPlanTitle: String,
        val docWriteTitle: String,
        val approvalTitle: String,
        val approvalDesc: String,
        val deployTitle: String,
        val deployDesc: String,
        val ideaTitle: String,
        val ideaDesc: String,
        val certTitle: String,
        val certDesc: String,
        val meeting: String,
        val deepWork: String,
        val lunch: String,
        val note: String,
    ) {
        companion object {
            val FR = Texts(
                prefix = "[Exemple]",
                projectTag = "Exemple",
                typeDev = "Développement",
                typeMeeting = "Réunion",
                typeDoc = "Documentation",
                typeAdmin = "Administratif",
                typeLearning = "Veille/formation",
                bugTitle = "Corriger le bug de connexion",
                bugDesc = "Exemple : priorité P0 et échéance proche, la tâche remonte en tête du tri. Supprime-la quand tu veux.",
                reviewTitle = "Préparer la revue de sprint",
                reviewDesc = "Exemple : une tâche P1 classique, prête à être planifiée.",
                standupTitle = "Point d’équipe (épinglé à 9h30)",
                standupDesc = "Exemple : une tâche épinglée reste à son heure, le reste se cale autour.",
                docTitle = "Rédiger la documentation",
                docDesc = "Exemple : une tâche mère ; ce sont ses sous-tâches qui sont planifiées.",
                docPlanTitle = "Documentation : plan",
                docWriteTitle = "Documentation : rédaction",
                approvalTitle = "Obtenir la validation du client",
                approvalDesc = "Exemple : cette tâche en bloque une autre (chemin critique).",
                deployTitle = "Déployer en production",
                deployDesc = "Exemple : bloquée tant que la validation n’est pas faite.",
                ideaTitle = "Idée : automatiser le rapport hebdomadaire",
                ideaDesc = "Exemple : sans priorité ni points de Fibonacci, la tâche reste « À traiter » en tête de page tant qu’elle n’est pas qualifiée.",
                certTitle = "Renouveler la certification",
                certDesc = "Exemple : programmée pour plus tard, elle attend son jour.",
                meeting = "Réunion projet",
                deepWork = "Deep work",
                lunch = "Déjeuner",
                note = "Idée en vrac : revoir le découpage des sprints ?\nÀ développer avant d’en faire une tâche, ou à archiver si ça ne mène nulle part.",
            )
            val EN = Texts(
                prefix = "[Example]",
                projectTag = "Example",
                typeDev = "Development",
                typeMeeting = "Meeting",
                typeDoc = "Documentation",
                typeAdmin = "Administration",
                typeLearning = "Learning",
                bugTitle = "Fix the login bug",
                bugDesc = "Example: P0 priority and a close deadline, so the task rises to the top. Delete it whenever you like.",
                reviewTitle = "Prepare the sprint review",
                reviewDesc = "Example: a regular P1 task, ready to be scheduled.",
                standupTitle = "Team stand-up (pinned at 9:30)",
                standupDesc = "Example: a pinned task stays at its time, everything else fits around it.",
                docTitle = "Write the documentation",
                docDesc = "Example: a parent task; its subtasks are what gets scheduled.",
                docPlanTitle = "Documentation: outline",
                docWriteTitle = "Documentation: writing",
                approvalTitle = "Get the client’s approval",
                approvalDesc = "Example: this task blocks another one (critical path).",
                deployTitle = "Deploy to production",
                deployDesc = "Example: blocked until the approval is done.",
                ideaTitle = "Idea: automate the weekly report",
                ideaDesc = "Example: with no priority and no Fibonacci points, the task stays in “To process” at the top until it is qualified.",
                certTitle = "Renew the certification",
                certDesc = "Example: scheduled for later, it waits for its day.",
                meeting = "Project meeting",
                deepWork = "Deep work",
                lunch = "Lunch",
                note = "Random idea: rethink how sprints are split?\nTo flesh out before turning it into a task, or to archive if it leads nowhere.",
            )

            val ES = Texts(
                prefix = "[Ejemplo]",
                projectTag = "Ejemplo",
                typeDev = "Desarrollo",
                typeMeeting = "Reunión",
                typeDoc = "Documentación",
                typeAdmin = "Administrativo",
                typeLearning = "Formación",
                bugTitle = "Corregir el error de inicio de sesión",
                bugDesc = "Ejemplo: prioridad P0 y fecha límite cercana, la tarea sube al principio de la lista. Bórrala cuando quieras.",
                reviewTitle = "Preparar la revisión del sprint",
                reviewDesc = "Ejemplo: una tarea P1 normal, lista para planificarse.",
                standupTitle = "Reunión de equipo (fijada a las 9:30)",
                standupDesc = "Ejemplo: una tarea fijada se queda a su hora, lo demás se organiza a su alrededor.",
                docTitle = "Redactar la documentación",
                docDesc = "Ejemplo: una tarea madre; se planifican sus subtareas.",
                docPlanTitle = "Documentación: esquema",
                docWriteTitle = "Documentación: redacción",
                approvalTitle = "Conseguir la aprobación del cliente",
                approvalDesc = "Ejemplo: esta tarea bloquea a otra (ruta crítica).",
                deployTitle = "Desplegar en producción",
                deployDesc = "Ejemplo: bloqueada hasta que llegue la aprobación.",
                ideaTitle = "Idea: automatizar el informe semanal",
                ideaDesc = "Ejemplo: sin prioridad ni puntos de Fibonacci, la tarea se queda en «Por procesar», al principio de la página, hasta que la completes.",
                certTitle = "Renovar la certificación",
                certDesc = "Ejemplo: programada para más adelante, espera su día.",
                meeting = "Reunión del proyecto",
                deepWork = "Deep work",
                lunch = "Comida",
                note = "Idea suelta: ¿revisar cómo se dividen los sprints?\nHay que desarrollarla antes de convertirla en tarea, o archivarla si no lleva a ninguna parte.",
            )
            val DE = Texts(
                prefix = "[Beispiel]",
                projectTag = "Beispiel",
                typeDev = "Entwicklung",
                typeMeeting = "Meeting",
                typeDoc = "Dokumentation",
                typeAdmin = "Verwaltung",
                typeLearning = "Weiterbildung",
                bugTitle = "Login-Fehler beheben",
                bugDesc = "Beispiel: Priorität P0 und Frist in Kürze, die Aufgabe steigt an die Spitze der Liste. Lösche sie, wann du willst.",
                reviewTitle = "Sprint-Review vorbereiten",
                reviewDesc = "Beispiel: eine gewöhnliche P1-Aufgabe, bereit zur Planung.",
                standupTitle = "Team-Meeting (fixiert auf 9:30)",
                standupDesc = "Beispiel: eine fixierte Aufgabe bleibt zu ihrer Uhrzeit, alles andere ordnet sich drumherum.",
                docTitle = "Dokumentation schreiben",
                docDesc = "Beispiel: eine übergeordnete Aufgabe; geplant werden ihre Unteraufgaben.",
                docPlanTitle = "Dokumentation: Gliederung",
                docWriteTitle = "Dokumentation: Ausarbeitung",
                approvalTitle = "Freigabe des Kunden einholen",
                approvalDesc = "Beispiel: diese Aufgabe blockiert eine andere (kritischer Pfad).",
                deployTitle = "In Produktion ausrollen",
                deployDesc = "Beispiel: blockiert, bis die Freigabe vorliegt.",
                ideaTitle = "Idee: den Wochenbericht automatisieren",
                ideaDesc = "Beispiel: ohne Priorität und ohne Fibonacci-Punkte bleibt die Aufgabe oben auf der Seite unter „Zu bearbeiten“, bis du sie einstufst.",
                certTitle = "Zertifizierung erneuern",
                certDesc = "Beispiel: für später eingeplant, sie wartet auf ihren Tag.",
                meeting = "Projektbesprechung",
                deepWork = "Deep work",
                lunch = "Mittagessen",
                note = "Lose Idee: die Aufteilung der Sprints überdenken?\nVor dem Anlegen einer Aufgabe ausarbeiten oder archivieren, wenn nichts daraus wird.",
            )
            val RU = Texts(
                prefix = "[Пример]",
                projectTag = "Пример",
                typeDev = "Разработка",
                typeMeeting = "Встреча",
                typeDoc = "Документация",
                typeAdmin = "Администрирование",
                typeLearning = "Обучение",
                bugTitle = "Исправить ошибку входа",
                bugDesc = "Пример: приоритет P0 и близкий срок, задача поднимается в начало списка. Удали ее, когда захочешь.",
                reviewTitle = "Подготовить ревью спринта",
                reviewDesc = "Пример: обычная задача P1, готовая к планированию.",
                standupTitle = "Встреча команды (закреплена на 9:30)",
                standupDesc = "Пример: закрепленная задача остается на своем часе, остальное выстраивается вокруг нее.",
                docTitle = "Написать документацию",
                docDesc = "Пример: родительская задача; планируются ее подзадачи.",
                docPlanTitle = "Документация: план",
                docWriteTitle = "Документация: написание",
                approvalTitle = "Получить одобрение клиента",
                approvalDesc = "Пример: эта задача блокирует другую (критический путь).",
                deployTitle = "Выкатить в продакшен",
                deployDesc = "Пример: заблокирована, пока нет одобрения.",
                ideaTitle = "Идея: автоматизировать еженедельный отчет",
                ideaDesc = "Пример: без приоритета и баллов Фибоначчи задача остается в начале страницы в разделе «К обработке», пока ты ее не оценишь.",
                certTitle = "Продлить сертификацию",
                certDesc = "Пример: запланирована на потом, она ждет своего дня.",
                meeting = "Встреча по проекту",
                deepWork = "Deep work",
                lunch = "Обед",
                note = "Мысль на лету: пересмотреть разбиение спринтов?\nРазвить, прежде чем превращать в задачу, или убрать в архив, если она ни к чему не ведет.",
            )
            val IT = Texts(
                prefix = "[Esempio]",
                projectTag = "Esempio",
                typeDev = "Sviluppo",
                typeMeeting = "Riunione",
                typeDoc = "Documentazione",
                typeAdmin = "Amministrazione",
                typeLearning = "Formazione",
                bugTitle = "Correggere il bug di accesso",
                bugDesc = "Esempio: priorità P0 e scadenza vicina, l’attività sale in cima alla lista. Eliminala quando vuoi.",
                reviewTitle = "Preparare la revisione dello sprint",
                reviewDesc = "Esempio: una normale attività P1, pronta per essere pianificata.",
                standupTitle = "Riunione di team (fissata alle 9:30)",
                standupDesc = "Esempio: un’attività fissata resta alla sua ora, il resto si organizza intorno.",
                docTitle = "Scrivere la documentazione",
                docDesc = "Esempio: un’attività madre; sono le sue sottoattività a essere pianificate.",
                docPlanTitle = "Documentazione: schema",
                docWriteTitle = "Documentazione: stesura",
                approvalTitle = "Ottenere l’approvazione del cliente",
                approvalDesc = "Esempio: questa attività ne blocca un’altra (percorso critico).",
                deployTitle = "Rilasciare in produzione",
                deployDesc = "Esempio: bloccata finché non arriva l’approvazione.",
                ideaTitle = "Idea: automatizzare il rapporto settimanale",
                ideaDesc = "Esempio: senza priorità né punti di Fibonacci, l’attività resta in «Da elaborare» in cima alla pagina finché non la qualifichi.",
                certTitle = "Rinnovare la certificazione",
                certDesc = "Esempio: programmata per più tardi, aspetta il suo giorno.",
                meeting = "Riunione di progetto",
                deepWork = "Deep work",
                lunch = "Pranzo",
                note = "Idea buttata lì: rivedere come si dividono gli sprint?\nDa sviluppare prima di farne un’attività, o da archiviare se non porta da nessuna parte.",
            )

            val ALL = listOf(FR, EN, ES, DE, RU, IT)

            /** Le jeu de textes de [lang] (première partie d'une étiquette de langue) ; l'anglais pour une langue sans jeu. */
            fun of(lang: String): Texts = when (lang) {
                "fr" -> FR
                "es" -> ES
                "de" -> DE
                "ru" -> RU
                "it" -> IT
                else -> EN
            }
        }
    }
}

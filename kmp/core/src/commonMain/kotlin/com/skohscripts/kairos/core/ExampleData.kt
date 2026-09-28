package com.skohscripts.kairos.core

import com.skohscripts.kairos.core.model.BlockKind
import com.skohscripts.kairos.core.model.BlockRecurrence
import com.skohscripts.kairos.core.model.KairosSnapshot
import com.skohscripts.kairos.core.model.Note
import com.skohscripts.kairos.core.model.Settings
import com.skohscripts.kairos.core.model.Task
import com.skohscripts.kairos.core.model.TaskDependency
import com.skohscripts.kairos.core.model.TimeBlock
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.plus
import kotlin.time.Instant

/**
 * Données d'exemple posées sur une base **neuve** (docs/spec-v3/modele-donnees.md
 * § Exemples) : le même jeu que Kairos 2 (`app/tasks_seed.py`), traduit. De
 * simples objets ordinaires (tag de projet « Exemple », titre préfixé
 * « [Exemple] ») que l'utilisateur supprime ou termine comme les siens.
 *
 * Pur : dates relatives à [today], horodatages à [now], langue de l'interface.
 */
object ExampleData {
    fun snapshot(today: LocalDate, now: Instant, language: String): KairosSnapshot {
        val t = if (language.lowercase().startsWith("en")) Texts.EN else Texts.FR
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
        }
    }
}

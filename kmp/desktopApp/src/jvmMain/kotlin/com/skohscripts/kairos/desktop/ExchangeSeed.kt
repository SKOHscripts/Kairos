package com.skohscripts.kairos.desktop

import com.skohscripts.kairos.core.model.TaskStatus
import com.skohscripts.kairos.data.Reassignment
import com.skohscripts.kairos.data.TaskEdit
import com.skohscripts.kairos.ui.app.AppServices
import com.skohscripts.kairos.ui.team.ExchangePreview
import com.skohscripts.kairos.ui.team.ImportedFile
import com.skohscripts.kairos.ui.team.readImportedFile
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.todayIn
import java.io.File
import kotlin.time.Clock

/**
 * Échanges de l'auto-test (jalon E6, docs/spec/equipe-echanges.md) : l'équipe de démonstration du manager (Claire) envoie un
 * premier paquet à Alex, qui travaille dans son Kairos (avancement, tâche faite, sous-tâche) et renvoie un rapport ; le manager
 * l'intègre, change ses tâches, puis envoie un second paquet qu'Alex reçoit (une tâche nouvelle, une mise à jour, une tâche retirée,
 * un bloqueur tenu par Sam). Les deux aperçus sont calculés AVANT d'être appliqués, comme à l'écran.
 *
 * - [manager] : la base du manager, après intégration du rapport (Alex a un « dernier rapport ») ;
 * - [member] : la base d'Alex : ses exemples, ses tâches reçues, dont une retirée ;
 * - [packPreview] : l'aperçu de réception du second paquet ; [reportPreview] : l'aperçu d'intégration du rapport ;
 * - [alexId] : la fiche d'Alex chez le manager.
 */
class ExchangeSeed(
    val manager: AppServices,
    val member: AppServices,
    val packPreview: ExchangePreview,
    val reportPreview: ExchangePreview,
    val alexId: Long,
)

suspend fun exchangeSeed(dataDir: File, english: Boolean): ExchangeSeed {
    val seeded = TeamSeed.open(english)
    val manager = seeded.services
    val repo = manager.repository
    val today = Clock.System.todayIn(TimeZone.currentSystemDefault())
    val alex = repo.snapshot.value.members.first { it.name.startsWith("Alex") }

    // 1. Premier paquet, reçu par Alex dans son Kairos personnel (qui n'a aucun espace Équipe).
    val member = DesktopServices.open(dataDir, inMemory = true)
    val first = checkNotNull(repo.exportPack(alex.id))
    member.repository.receivePack(first)

    // 2. Alex travaille : avancement de la refonte, une tâche faite, une sous-tâche sous la refonte.
    val mine = member.repository
    fun received(key: String) = mine.snapshot.value.tasks.first { it.teamUid == repo.snapshot.value.tasks.first { m -> m.id == seeded.ids.getValue(key) }.teamUid }
    val billing = received("billing")
    mine.setReceivedProgress(billing.id, 80)
    mine.createTask(if (english) "Write the invoice export tests" else "Écrire les tests de l’export des factures", billing.id)
    mine.toggleDone(received("pagination").id)
    val report = checkNotNull(mine.buildReport(mine.origins().single().key))

    // 3. Le manager prévisualise le rapport (aperçu capturé), puis l'intègre.
    val reportPreview = (readImportedFile(manager, report) as ImportedFile.Exchange).preview
    repo.integrateReport(report)

    // 4. Le manager change ses tâches : titre et échéance, une tâche réaffectée à Sam, une nouvelle bloquée par une tâche de Sam.
    val tasks = repo.snapshot.value.tasks
    suspend fun edit(id: Long, title: String, deadline: LocalDate?) {
        val t = tasks.first { it.id == id }
        repo.updateTask(
            id,
            TaskEdit(
                title = title, description = t.description, priority = t.priority, points = t.fibonacciPoints, deadline = deadline,
                estimatedMinutes = t.estimatedMinutes, projectTag = t.projectTag, taskType = t.taskType, pinDay = today,
            ),
        )
    }
    val billingId = seeded.ids.getValue("billing")
    edit(billingId, if (english) "Rework the billing API (v2)" else "Refonte de l’API de facturation (v2)", today.plus(DatePeriod(days = 4)))
    val sam = repo.snapshot.value.members.first { it.name.startsWith("Sam") }
    repo.assign(listOf(seeded.ids.getValue("ci")), sam.id)
    val added = checkNotNull(repo.createTeamTask(if (english) "Check the invoice PDF layout" else "Vérifier la mise en page des factures PDF"))
    repo.updateTask(
        added,
        TaskEdit(
            title = if (english) "Check the invoice PDF layout" else "Vérifier la mise en page des factures PDF", priority = 1, points = 3,
            taskType = if (english) "Code review" else "Revue de code", pinDay = today, reassign = Reassignment(alex.id),
            blockerIds = setOf(seeded.ids.getValue("mockups")),
        ),
    )

    // 5. Second paquet : aperçu de réception (capturé), puis réception.
    val second = checkNotNull(repo.exportPack(alex.id))
    val packPreview = (readImportedFile(member, second) as ImportedFile.Exchange).preview
    mine.receivePack(second)

    check(mine.snapshot.value.tasks.any { it.originRemoved && it.status != TaskStatus.DONE }) { "une tâche doit être retirée" }
    return ExchangeSeed(manager, member, packPreview, reportPreview, alex.id)
}

package com.skohscripts.kairos.core.team.exchange

import com.skohscripts.kairos.core.team.TeamEvent
import com.skohscripts.kairos.core.team.TeamEventKind
import com.skohscripts.kairos.core.team.TeamMember
import com.skohscripts.kairos.core.team.TeamMembers
import kotlin.time.Instant

/**
 * Où en est l'échange de fichiers avec un membre, vu du manager (onglet « Échanges »,
 * docs/spec/equipe-echanges.md § Onglet « Échanges »).
 *
 * [lastPackAt] : instant du dernier paquet envoyé à ce membre (`null` = jamais) ;
 * [lastReportAt] : celui du dernier rapport intégré ; [awaitingReport] : un paquet est
 * parti après le dernier rapport (ou il n'y en a eu aucun), donc un rapport est attendu.
 */
data class MemberExchange(
    val member: TeamMember,
    val lastPackAt: Instant?,
    val lastReportAt: Instant?,
    val awaitingReport: Boolean,
)

object ExchangeStatus {
    /**
     * État d'échange de chaque membre actif sauf « moi » (qui n'échange pas avec lui-même),
     * dans l'ordre d'affichage des membres. Le dernier envoi se lit dans le journal : un
     * événement `sent` par tâche envoyée, `memberId` = destinataire, daté de l'enregistrement
     * du fichier (`recordPackSent`). Pur : aucune horloge.
     */
    fun of(members: List<TeamMember>, events: List<TeamEvent>): List<MemberExchange> {
        val lastPack: Map<Long, Instant> = events
            .filter { it.kind == TeamEventKind.SENT && it.memberId != null }
            .groupBy { it.memberId!! }
            .mapValues { (_, sent) -> sent.maxOf { it.at } }
        return TeamMembers.active(members).filter { !it.isSelf }.map { member ->
            val pack = lastPack[member.id]
            val report = member.lastReportAt
            MemberExchange(member, pack, report, awaitingReport = pack != null && (report == null || report < pack))
        }
    }
}

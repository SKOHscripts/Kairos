package com.skohscripts.kairos.core.team.exchange

import com.skohscripts.kairos.core.model.KairosSnapshot
import com.skohscripts.kairos.core.model.Task

/**
 * D'où vient une tâche **reçue** (docs/spec/equipe-echanges.md § Réception) : l'équipe (identité et nom), le
 * manager, et le membre tel que le manager l'a désigné (son `uid` sert à adresser le rapport en retour).
 * Stockée dans `Task.origin` sous forme de texte à cinq champs séparés par U+001F (un caractère de
 * contrôle qu'aucun nom saisi ne contient : il en est retiré à l'écriture).
 *
 * Le regroupement des rapports se fait par [teamUid] ([key]) : un rapport par manager.
 */
data class TeamOrigin(
    val teamUid: String,
    val teamName: String,
    val managerName: String,
    val memberUid: String,
    val memberName: String,
) {
    /** Clé de regroupement : l'équipe. */
    val key: String get() = teamUid

    fun encode(): String = listOf(teamUid, teamName, managerName, memberUid, memberName).joinToString(SEPARATOR) { it.replace(SEPARATOR, "") }

    companion object {
        private const val SEPARATOR = "\u001F"
        private const val FIELDS = 5

        /** L'origine décrite par [text], ou `null` si le texte est absent ou illisible (pas cinq champs, identité vide). */
        fun parse(text: String?): TeamOrigin? {
            if (text.isNullOrEmpty()) return null
            val parts = text.split(SEPARATOR)
            if (parts.size != FIELDS || parts[0].isBlank() || parts[3].isBlank()) return null
            return TeamOrigin(parts[0], parts[1], parts[2], parts[3], parts[4])
        }

        /** Origine du paquet reçu : l'équipe, son manager et le membre qu'il désigne. */
        fun of(pack: TeamPack) = TeamOrigin(pack.team.uid, pack.team.name, pack.team.manager, pack.member.uid, pack.member.name)
    }
}

/** Lectures pures sur les tâches reçues d'un manager. */
object ReceivedTasks {
    /** Origine lisible de [task], ou `null` si ce n'est pas une tâche reçue. */
    fun originOf(task: Task): TeamOrigin? = TeamOrigin.parse(task.origin)

    /** Tâche reçue d'une équipe (origine lisible), retirée ou non. */
    fun isReceived(task: Task): Boolean = originOf(task) != null

    /**
     * Origines à qui le membre peut renvoyer son avancement : une par équipe ([TeamOrigin.key]) qui a encore au
     * moins une tâche reçue **non retirée**, triées par nom de manager puis d'équipe. Pour une équipe, l'origine
     * retenue est celle de la tâche reçue la plus récente (les noms suivent le dernier paquet).
     */
    fun origins(snapshot: KairosSnapshot): List<TeamOrigin> =
        snapshot.tasks.filter { !it.originRemoved }
            .mapNotNull { t -> originOf(t)?.let { t.id to it } }
            .sortedByDescending { it.first }
            .distinctBy { it.second.key }
            .map { it.second }
            .sortedWith(compareBy({ it.managerName.lowercase() }, { it.teamName.lowercase() }, { it.teamUid }))
}

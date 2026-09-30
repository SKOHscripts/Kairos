package com.skohscripts.kairos.core.model

import com.skohscripts.kairos.core.team.MemberAbsence
import com.skohscripts.kairos.core.team.TeamMember

/**
 * Contenu complet d'une base Kairos : toutes les tables et les réglages.
 * C'est la forme commune de l'export, de l'import, de la sauvegarde de la
 * version web et des données d'exemple (docs/spec/export-import.md).
 * Les identifiants sont ceux de la base ; les références entre objets
 * (`parentId`, dépendances, sessions, `convertedTaskId`) s'y rapportent.
 */
data class KairosSnapshot(
    val tasks: List<Task> = emptyList(),
    val timeBlocks: List<TimeBlock> = emptyList(),
    val dependencies: List<TaskDependency> = emptyList(),
    val workSessions: List<WorkSession> = emptyList(),
    val notes: List<Note> = emptyList(),
    val settings: Settings = Settings(),
    /** Membres de l'équipe (espace Équipe) ; toujours vide tant que l'espace n'a jamais servi. */
    val members: List<TeamMember> = emptyList(),
    /** Absences des membres (espace Équipe) ; toujours vide tant que l'espace n'a jamais servi. */
    val absences: List<MemberAbsence> = emptyList(),
)

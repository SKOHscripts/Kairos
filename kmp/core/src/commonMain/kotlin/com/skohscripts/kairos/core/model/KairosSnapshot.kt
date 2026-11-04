package com.skohscripts.kairos.core.model

import com.skohscripts.kairos.core.team.MemberAbsence
import com.skohscripts.kairos.core.team.TeamEvent
import com.skohscripts.kairos.core.team.TeamMember
import com.skohscripts.kairos.core.team.forecast.TeamScenario

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
    /** Journal des tâches d'équipe (jalon E3) ; toujours vide tant que l'espace n'a jamais servi. */
    val teamEvents: List<TeamEvent> = emptyList(),
    /** Scénarios « Et si… ? » de l'espace Équipe (jalon E5) ; toujours vide tant que l'espace n'a jamais servi. */
    val teamScenarios: List<TeamScenario> = emptyList(),
    /** Exceptions d'occurrences de créneaux récurrents (3.8.0, issue #110) ; vide tant qu'aucune n'a été posée. */
    val blockExceptions: List<BlockException> = emptyList(),
    /** Calendriers importés (3.10.0, issue #136) ; vide tant qu'aucun calendrier n'a été importé. */
    val calendarSources: List<CalendarSource> = emptyList(),
    /** Journées entières des calendriers importés (badges) ; vide tant qu'aucun calendrier n'a été importé. */
    val dayMarks: List<DayMark> = emptyList(),
    /**
     * Rang de chaque tâche dans l'ordre **commun** des appareils synchronisés (`createdAt`, puis `uid` ; `SyncOrder`), posé par le
     * dépôt d'une base synchronisée et jamais écrit ni exporté : les durées apprises et le modèle ordonnent leurs occurrences
     * par lui plutôt que par l'identifiant local, qui diffère d'un appareil à l'autre (docs/spec/synchronisation.md § Durées
     * apprises partagées). `null` sans synchronisation : l'ordre reste l'identifiant, comme avant.
     */
    val sampleOrder: Map<Long, Long>? = null,
)

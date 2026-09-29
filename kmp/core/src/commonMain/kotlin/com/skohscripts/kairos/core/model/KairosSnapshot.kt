package com.skohscripts.kairos.core.model

/**
 * Contenu complet d'une base Kairos : toutes les tables et les réglages.
 * C'est la forme commune de l'export, de l'import, de la sauvegarde de la
 * version web et des données d'exemple (docs/spec-v3/export-import.md).
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
)

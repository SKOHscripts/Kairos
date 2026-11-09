package com.skohscripts.kairos.core.sync

import com.skohscripts.kairos.core.model.BlockSource
import com.skohscripts.kairos.core.model.KairosSnapshot
import com.skohscripts.kairos.core.model.Settings
import com.skohscripts.kairos.core.model.TaskSpace

/**
 * Genre d'un enregistrement synchronisé (docs/spec/synchronisation.md § Portée). [code] : nom stable du fichier et des tables
 * `sync_*` (jamais renommé). Un `uid` est unique **par genre** ([RecordRef]).
 */
enum class RecordKind(val code: String) {
    CALENDAR("calendar"),
    TASK("task"),
    BLOCK("block"),
    NOTE("note"),
    SESSION("session"),
    EXCEPTION("exception"),
    DEPENDENCY("dependency"),
    DAYMARK("daymark"),
    ;

    companion object {
        fun fromCode(code: String): RecordKind? = entries.firstOrNull { it.code == code }
    }
}

/** Identité d'un enregistrement sur tous les appareils : son genre et son `uid`. */
data class RecordRef(val kind: RecordKind, val uid: String) : Comparable<RecordRef> {
    override fun compareTo(other: RecordRef): Int = compareValuesBy(this, other, { it.kind.ordinal }, { it.uid })
}

/**
 * Portée de la synchronisation (docs/spec/synchronisation.md § Portée) : **une seule définition**, utilisée par la
 * réconciliation, la fusion et l'export. Pure ; ne dépend jamais de l'appareil.
 *
 * L'espace Équipe n'est **jamais** dans la portée : tâches d'équipe, tâches reçues d'un manager, membres, absences, journal,
 * scénarios, réglages d'équipe. C'est ce qui garantit qu'aucune donnée d'équipe ne peut entrer dans une base solo par une fusion.
 */
object SyncScope {
    /** Longueur maximale d'un `uid` (validé à la lecture d'un fichier). */
    const val MAX_UID = 100

    /** Réglages propres à l'appareil, jamais fusionnés : présentation, notifications, guide et équipe. */
    val LOCAL_SETTINGS: Set<String> = setOf(
        "language", "themeColor", "themeMode", "textZoom", "updateCheckEnabled", "timerAlertSound", "reminders", "guide", "team",
        // Mémoire d'un interrupteur de la fiche (issue #209) : une habitude de saisie, pas un réglage du planning.
        "subtaskChain",
    )

    /**
     * Réglages partagés : tous les champs de premier niveau de [Settings] sauf [LOCAL_SETTINGS]. Un champ ajouté plus tard à
     * `Settings` est donc **partagé par défaut** (`SyncScopeTest.everySettingIsClassified`). Ordre : celui de la déclaration.
     */
    val SHARED_SETTINGS: List<String> = Settings.serializer().descriptor.let { d ->
        (0 until d.elementsCount).map(d::getElementName).filter { it !in LOCAL_SETTINGS }
    }

    /** Champs d'une tâche propres à l'appareil (équipe, échanges) : jamais fusionnés ; une tâche qui arrive les prend par défaut. */
    val TASK_LOCAL_FIELDS: Set<String> = setOf(
        "space", "assigneeId", "progressPercent", "startedOn", "teamUid", "origin", "originRemoved", "reportedMinutes",
        "teamExcluded", "noteOut", "noteIn", "proposedTo",
    )

    /** Les enregistrements de [snapshot] dans la portée, par genre. */
    fun of(snapshot: KairosSnapshot): Scoped {
        val tasks = snapshot.tasks.filter { it.space == TaskSpace.PERSONAL && it.origin == null }
        val taskIds = tasks.mapTo(HashSet()) { it.id }
        val calendarIds = snapshot.calendarSources.mapTo(HashSet()) { it.id }
        return Scoped(
            calendars = snapshot.calendarSources,
            tasks = tasks,
            blocks = snapshot.timeBlocks.filter { b -> b.source.let { it == null || (it is BlockSource.Imported && it.calendarId in calendarIds) } },
            notes = snapshot.notes,
            sessions = snapshot.workSessions.filter { it.taskId in taskIds },
            exceptions = snapshot.blockExceptions,
            dependencies = snapshot.dependencies.filter { it.taskId in taskIds && it.blockerId in taskIds },
            dayMarks = snapshot.dayMarks.filter { m -> (m.source as? BlockSource.Imported)?.calendarId in calendarIds },
        )
    }

    /** Le contenu de la portée, genre par genre (listes dans l'ordre du snapshot). */
    data class Scoped(
        val calendars: List<com.skohscripts.kairos.core.model.CalendarSource>,
        val tasks: List<com.skohscripts.kairos.core.model.Task>,
        val blocks: List<com.skohscripts.kairos.core.model.TimeBlock>,
        val notes: List<com.skohscripts.kairos.core.model.Note>,
        val sessions: List<com.skohscripts.kairos.core.model.WorkSession>,
        val exceptions: List<com.skohscripts.kairos.core.model.BlockException>,
        val dependencies: List<com.skohscripts.kairos.core.model.TaskDependency>,
        val dayMarks: List<com.skohscripts.kairos.core.model.DayMark>,
    ) {
        /** Identifiants locaux par genre. */
        fun ids(kind: RecordKind): List<Long> = when (kind) {
            RecordKind.CALENDAR -> calendars.map { it.id }
            RecordKind.TASK -> tasks.map { it.id }
            RecordKind.BLOCK -> blocks.map { it.id }
            RecordKind.NOTE -> notes.map { it.id }
            RecordKind.SESSION -> sessions.map { it.id }
            RecordKind.EXCEPTION -> exceptions.map { it.id }
            RecordKind.DEPENDENCY -> dependencies.map { it.id }
            RecordKind.DAYMARK -> dayMarks.map { it.id }
        }

        val size: Int get() = RecordKind.entries.sumOf { ids(it).size }
    }
}

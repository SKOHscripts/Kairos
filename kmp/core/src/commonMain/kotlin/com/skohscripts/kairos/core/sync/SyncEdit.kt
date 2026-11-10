package com.skohscripts.kairos.core.sync

import com.skohscripts.kairos.core.model.KairosSnapshot

/**
 * Modifications de « À revoir » (docs/spec/synchronisation.md § Dépôt, § Interface) : prendre la valeur écartée d'un conflit,
 * ou supprimer à nouveau un élément conservé. Pures : elles rendent la base modifiée, que le dépôt écrit comme toute
 * modification locale (la réconciliation y pose une marque fraîche, qui s'impose partout).
 */
object SyncEdit {
    /** [snapshot] où le champ [field] de [ref] prend la valeur canonique [value] ; `null` si l'enregistrement n'existe plus. */
    fun setField(snapshot: KairosSnapshot, data: SyncData, ref: RecordRef, field: String, value: String): KairosSnapshot? {
        val byUid = data.idsByUid()
        val id = byUid[ref.kind]?.get(ref.uid) ?: return null
        val scoped = SyncScope.of(snapshot, data)
        val lifted = SyncReconcile.liftAll(scoped) { k, i -> data.uidOf(k, i) }[ref.kind]?.get(id) ?: return null
        val l = lifted.copy(fields = lifted.fields + (field to value))
        val ids = SyncRecords.IdOf { k, uid -> byUid[k]?.get(uid) }
        return when (ref.kind) {
            RecordKind.TASK -> snapshot.copy(tasks = snapshot.tasks.map { if (it.id == id) SyncRecords.buildTask(id, l, ids, it) else it })
            RecordKind.NOTE -> snapshot.copy(notes = snapshot.notes.map { if (it.id == id) SyncRecords.buildNote(id, l, ids) else it })
            RecordKind.CALENDAR -> snapshot.copy(calendarSources = snapshot.calendarSources.map { if (it.id == id) SyncRecords.buildCalendar(id, l) else it })
            RecordKind.BLOCK -> SyncRecords.buildBlock(id, l, ids)?.let { b -> snapshot.copy(timeBlocks = snapshot.timeBlocks.map { if (it.id == id) b else it }) }
            RecordKind.EXCEPTION -> SyncRecords.buildException(id, l, ids)?.let { x -> snapshot.copy(blockExceptions = snapshot.blockExceptions.map { if (it.id == id) x else it }) }
            RecordKind.SESSION -> SyncRecords.buildSession(id, l, ids)?.let { x -> snapshot.copy(workSessions = snapshot.workSessions.map { if (it.id == id) x else it }) }
            RecordKind.DEPENDENCY -> SyncRecords.buildDependency(id, l, ids)?.let { x -> snapshot.copy(dependencies = snapshot.dependencies.map { if (it.id == id) x else it }) }
            RecordKind.DAYMARK -> SyncRecords.buildDayMark(id, l, ids)?.let { x -> snapshot.copy(dayMarks = snapshot.dayMarks.map { if (it.id == id) x else it }) }
        }
    }

    /** [snapshot] où le réglage partagé [field] prend la valeur canonique [value]. */
    fun setSetting(snapshot: KairosSnapshot, field: String, value: String): KairosSnapshot =
        snapshot.copy(settings = SyncRecords.buildSettings(mapOf(field to value), snapshot.settings))

    /**
     * [snapshot] sans l'enregistrement [ref] (« Supprimer à nouveau ») ; une tâche emporte ses dépendances, comme une
     * suppression ordinaire. `null` s'il n'existe plus.
     */
    fun remove(snapshot: KairosSnapshot, data: SyncData, ref: RecordRef): KairosSnapshot? {
        val id = data.idsByUid()[ref.kind]?.get(ref.uid) ?: return null
        return when (ref.kind) {
            RecordKind.TASK -> snapshot.copy(
                tasks = snapshot.tasks.filter { it.id != id },
                dependencies = snapshot.dependencies.filter { it.taskId != id && it.blockerId != id },
            )
            RecordKind.NOTE -> snapshot.copy(notes = snapshot.notes.filter { it.id != id })
            RecordKind.CALENDAR -> snapshot.copy(
                calendarSources = snapshot.calendarSources.filter { it.id != id },
                timeBlocks = snapshot.timeBlocks.filter { (it.source as? com.skohscripts.kairos.core.model.BlockSource.Imported)?.calendarId != id },
                dayMarks = snapshot.dayMarks.filter { (it.source as? com.skohscripts.kairos.core.model.BlockSource.Imported)?.calendarId != id },
            )
            RecordKind.BLOCK -> snapshot.copy(timeBlocks = snapshot.timeBlocks.filter { it.id != id }, blockExceptions = snapshot.blockExceptions.filter { it.blockId != id })
            RecordKind.EXCEPTION -> snapshot.copy(blockExceptions = snapshot.blockExceptions.filter { it.id != id })
            RecordKind.SESSION -> snapshot.copy(workSessions = snapshot.workSessions.filter { it.id != id })
            RecordKind.DEPENDENCY -> snapshot.copy(dependencies = snapshot.dependencies.filter { it.id != id })
            RecordKind.DAYMARK -> snapshot.copy(dayMarks = snapshot.dayMarks.filter { it.id != id })
        }
    }

    /** Le titre lisible d'un enregistrement, pour « À revoir » ; vide s'il n'en a pas. */
    fun title(snapshot: KairosSnapshot, data: SyncData, ref: RecordRef): String {
        val id = data.idsByUid()[ref.kind]?.get(ref.uid) ?: return ""
        return when (ref.kind) {
            RecordKind.TASK -> snapshot.tasks.firstOrNull { it.id == id }?.title
            RecordKind.NOTE -> snapshot.notes.firstOrNull { it.id == id }?.body?.lineSequence()?.firstOrNull()
            RecordKind.CALENDAR -> snapshot.calendarSources.firstOrNull { it.id == id }?.name
            RecordKind.BLOCK -> snapshot.timeBlocks.firstOrNull { it.id == id }?.title
            RecordKind.DAYMARK -> snapshot.dayMarks.firstOrNull { it.id == id }?.title
            else -> null
        }.orEmpty().take(200)
    }
}

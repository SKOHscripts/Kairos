package com.skohscripts.kairos.core.sync

import com.skohscripts.kairos.core.model.Settings
import com.skohscripts.kairos.core.model.Task
import com.skohscripts.kairos.core.sync.SyncFixtures.rich
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Levée des enregistrements en champs canoniques et reconstruction (docs/spec/synchronisation.md § Fusion). */
class SyncRecordsTest {
    private val snapshot = rich()
    private val scoped = SyncScope.of(snapshot)
    private val uids = RecordKind.entries.associateWith { k -> scoped.ids(k).associateWith { "${k.code}-$it" } }
    private val uidOf = SyncRecords.UidOf { k, id -> uids[k]?.get(id) }
    private val idOf = SyncRecords.IdOf { k, uid -> uids[k]?.entries?.firstOrNull { it.value == uid }?.key }

    @Test
    fun everyKindRoundTrips() {
        for (t in scoped.tasks) assertEquals(t, SyncRecords.buildTask(t.id, SyncRecords.lift(t, uidOf), idOf, t))
        for (b in scoped.blocks) assertEquals(b, SyncRecords.buildBlock(b.id, SyncRecords.lift(b, uidOf), idOf))
        for (x in scoped.exceptions) assertEquals(x, SyncRecords.buildException(x.id, SyncRecords.lift(x, uidOf), idOf))
        for (d in scoped.dependencies) assertEquals(d, SyncRecords.buildDependency(d.id, SyncRecords.lift(d, uidOf), idOf))
        for (s in scoped.sessions) assertEquals(s, SyncRecords.buildSession(s.id, SyncRecords.lift(s, uidOf), idOf))
        for (n in scoped.notes) assertEquals(n, SyncRecords.buildNote(n.id, SyncRecords.lift(n, uidOf), idOf))
        for (c in scoped.calendars) assertEquals(c, SyncRecords.buildCalendar(c.id, SyncRecords.lift(c)))
        for (m in scoped.dayMarks) assertEquals(m, SyncRecords.buildDayMark(m.id, SyncRecords.lift(m, uidOf), idOf))
    }

    @Test
    fun referencesAreUidsAndLocalFieldsStayOut() {
        val relire = SyncRecords.lift(scoped.tasks.first { it.id == 2L }, uidOf)
        assertEquals("\"task-1\"", relire.fields["parentId"])
        for (local in SyncScope.TASK_LOCAL_FIELDS) assertNull(relire.fields[local], local)
        assertEquals("null", relire.fields["estimatedMinutes"])
        assertEquals("\"Relire\"", relire.fields["title"])
    }

    @Test
    fun anArrivingTaskTakesDefaultLocalFieldsAndAnExistingOneKeepsItsOwn() {
        val l = SyncRecords.lift(scoped.tasks.first(), uidOf)
        val local = Task(1, "x", createdAt = SyncFixtures.t0, updatedAt = SyncFixtures.t0, teamExcluded = true, noteOut = "à dire")
        val kept = SyncRecords.buildTask(1, l, idOf, local)
        assertEquals(true, kept.teamExcluded)
        assertEquals("à dire", kept.noteOut)
        val fresh = SyncRecords.buildTask(9, l, idOf, null)
        assertEquals(false, fresh.teamExcluded)
        assertEquals("", fresh.noteOut)
        assertEquals(9, fresh.id)
    }

    @Test
    fun aMissingEndDropsTheRecordAndAMissingSoftReferenceReadsEmpty() {
        val none = SyncRecords.IdOf { _, _ -> null }
        assertNull(SyncRecords.buildDependency(1, SyncRecords.lift(scoped.dependencies.first(), uidOf), none))
        assertNull(SyncRecords.buildSession(1, SyncRecords.lift(scoped.sessions.first(), uidOf), none))
        assertNull(SyncRecords.buildException(1, SyncRecords.lift(scoped.exceptions.first(), uidOf), none))
        assertNull(SyncRecords.buildBlock(3, SyncRecords.lift(scoped.blocks.first { it.id == 3L }, uidOf), none))
        val relire = SyncRecords.buildTask(2, SyncRecords.lift(scoped.tasks.first { it.id == 2L }, uidOf), none, null)
        assertNull(relire.parentId, "mère inconnue : la tâche devient de premier niveau")
    }

    @Test
    fun settingsKeepTheirLocalFieldsAndTakeTheSharedOnes() {
        val mine = Settings(language = "fr", themeMode = Settings.THEME_MODE_DARK, workdayStartHour = 7)
        val theirs = Settings(language = "en", themeMode = Settings.THEME_MODE_LIGHT, workdayStartHour = 10, taskTypes = "A,B")
        val merged = SyncRecords.buildSettings(SyncRecords.liftSettings(theirs), mine)
        assertEquals("fr", merged.language)
        assertEquals(Settings.THEME_MODE_DARK, merged.themeMode)
        assertEquals(10, merged.workdayStartHour)
        assertEquals("A,B", merged.taskTypes)
        assertEquals(mine, SyncRecords.buildSettings(SyncRecords.liftSettings(mine), mine))
    }

    @Test
    fun hashIsTheFnv1aOfTheUtf8Text() {
        // Valeurs de référence FNV-1a 64 bits.
        assertEquals(-3750763034362895579L, SyncRecords.hash(""))
        assertEquals(-5808556873153909620L, SyncRecords.hash("a"))
    }
}

package com.skohscripts.kairos.core.sync

import com.skohscripts.kairos.core.model.KairosSnapshot
import com.skohscripts.kairos.core.model.Settings
import com.skohscripts.kairos.core.sync.SyncFixtures.rich
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Portée de la synchronisation (docs/spec/synchronisation.md § Portée). */
class SyncScopeTest {
    @Test
    fun everySettingIsClassified() {
        val d = Settings.serializer().descriptor
        val all = (0 until d.elementsCount).map(d::getElementName).toSet()
        assertEquals(all, SyncScope.SHARED_SETTINGS.toSet() + SyncScope.LOCAL_SETTINGS, "chaque champ de Settings est partagé ou local")
        assertTrue(SyncScope.LOCAL_SETTINGS.all { it in all }, "un champ local qui n'existe plus doit être retiré de la liste")
        // Les réglages de planification sont partagés ; présentation, notifications, guide et équipe restent sur l'appareil.
        for (shared in listOf("workdayStartHour", "defaultTaskDurationMinutes", "taskTypes", "schedule", "learning", "levelNames", "extraHolidays")) {
            assertTrue(shared in SyncScope.SHARED_SETTINGS, shared)
        }
    }

    @Test
    fun teamDataIsNeverInScope() {
        val s = SyncScope.of(rich())
        assertEquals(listOf(1L, 2, 3, 4, 5), s.tasks.map { it.id }, "ni la tâche d'équipe ni la tâche reçue")
        assertEquals(listOf(1L), s.dependencies.map { it.id }, "une dépendance vers une tâche d'équipe reste hors portée")
        assertEquals(listOf(1L, 2), s.sessions.map { it.id }, "les sessions d'une tâche d'équipe restent hors portée")
        assertEquals(3, s.blocks.size)
        assertEquals(1, s.calendars.size)
        assertEquals(1, s.dayMarks.size)
    }

    @Test
    fun anImportedBlockWithoutItsCalendarIsOutOfScope() {
        val s = SyncScope.of(rich().copy(calendarSources = emptyList()))
        assertEquals(listOf(1L, 2), s.blocks.map { it.id })
        assertEquals(0, s.dayMarks.size)
    }

    @Test
    fun anEmptyBaseHasAnEmptyScope() {
        assertEquals(0, SyncScope.of(KairosSnapshot()).size)
    }
}

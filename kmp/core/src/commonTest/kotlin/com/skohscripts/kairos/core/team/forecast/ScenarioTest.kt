package com.skohscripts.kairos.core.team.forecast

import com.skohscripts.kairos.core.model.KairosSnapshot
import com.skohscripts.kairos.core.model.TaskSpace
import com.skohscripts.kairos.core.model.TaskStatus
import com.skohscripts.kairos.core.team.Workspaces
import com.skohscripts.kairos.core.team.TeamFixtures
import com.skohscripts.kairos.core.team.TeamFixtures.member
import com.skohscripts.kairos.core.team.TeamFixtures.monday
import com.skohscripts.kairos.core.team.TeamFixtures.snapshot
import com.skohscripts.kairos.core.team.forecast.ForecastFixtures.task
import com.skohscripts.kairos.core.team.forecast.Scenario.SkipReason
import com.skohscripts.kairos.core.team.forecast.ScenarioModification.AddAbsence
import com.skohscripts.kairos.core.team.forecast.ScenarioModification.AddMember
import com.skohscripts.kairos.core.team.forecast.ScenarioModification.AddTasks
import com.skohscripts.kairos.core.team.forecast.ScenarioModification.Reassign
import com.skohscripts.kairos.core.team.forecast.ScenarioModification.RemoveMember
import com.skohscripts.kairos.core.team.forecast.ScenarioModification.SetAvailability
import com.skohscripts.kairos.core.team.forecast.ScenarioModification.SetDeadline
import com.skohscripts.kairos.core.team.forecast.ScenarioModification.SetFocus
import com.skohscripts.kairos.core.team.forecast.ScenarioModification.SetPriority
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class ScenarioTest {
    private val base: KairosSnapshot = snapshot(
        tasks = listOf(
            task(1, 1, 8.0, priority = 1), task(2, 1, 8.0, priority = 2), task(3, 2, 16.0), task(4, null, 4.0),
            TeamFixtures.task(5, 1, 8.0, status = TaskStatus.DONE).copy(teamUid = "uid-5"),
        ),
        members = listOf(member(1), member(2), member(3, archived = true)),
    )

    private fun apply(vararg m: ScenarioModification) = Scenario.apply(base, m.toList())

    @Test
    fun theRealSnapshotIsNeverTouched() {
        val before = base
        val (copy, skipped) = apply(RemoveMember(1), AddMember(-1, "Hypo", 80, 7.0), AddTasks(2, 3, "Dev", 1), SetFocus(0.5))
        assertTrue(skipped.isEmpty())
        assertSame(before, base)
        assertEquals(3, base.members.size)
        assertEquals(5, base.tasks.size)
        assertEquals(4, copy.members.size)
        assertEquals(7, copy.tasks.size)
        assertNull(base.settings.team?.takeIf { it.focusFactor == 0.5 })
    }

    @Test
    fun addMemberCreatesAHypotheticalMemberWithANegativeId() {
        val (s, skipped) = apply(AddMember(-7, " Alex ", 60, 6.5))
        assertTrue(skipped.isEmpty())
        val m = s.members.single { it.id == -7L }
        assertEquals("Alex", m.name)
        assertEquals(60, m.availabilityPercent)
        assertEquals(6.5, m.hoursPerDay)
        assertFalse(m.archived)
        assertFalse(m.isSelf)
        assertTrue(Scenario.isHypotheticalMember(m.id))
    }

    @Test
    fun addMemberRefusesInvalidOrDuplicatedIdentities() {
        val (s, skipped) = apply(
            AddMember(5, "Positif", 100, 8.0), AddMember(-1, "  ", 100, 8.0), AddMember(-1, "Ok", 0, 8.0),
            AddMember(-1, "Ok", 100, 30.0), AddMember(-1, "Ok", 100, 8.0), AddMember(-1, "Doublon", 100, 8.0),
        )
        assertEquals(listOf(0, 1, 2, 3, 5), skipped.map { it.index })
        assertTrue(skipped.all { it.reason == SkipReason.INVALID_VALUE })
        assertEquals(1, s.members.count { it.id == -1L })
    }

    @Test
    fun removeMemberSendsItsOpenTasksBackToTheBacklogAndArchivesIt() {
        val (s, skipped) = apply(RemoveMember(1))
        assertTrue(skipped.isEmpty())
        assertTrue(s.members.single { it.id == 1L }.archived)
        assertEquals(listOf(null, null), listOf(1L, 2L).map { id -> s.tasks.single { it.id == id }.assigneeId })
        // Une tâche faite garde son titulaire (historique).
        assertEquals(1L, s.tasks.single { it.id == 5L }.assigneeId)
        assertEquals(2L, s.tasks.single { it.id == 3L }.assigneeId)
    }

    @Test
    fun addAbsenceAddsAnAbsenceWithANegativeId() {
        val (s, skipped) = apply(AddAbsence(1, monday, monday.plus(DatePeriod(days = 4))), AddAbsence(2, monday, monday))
        assertTrue(skipped.isEmpty())
        assertEquals(listOf(-1L, -2L), s.absences.map { it.id })
        assertEquals(listOf(1L, 2L), s.absences.map { it.memberId })
        // Une absence inversée est ignorée.
        assertEquals(SkipReason.INVALID_VALUE, apply(AddAbsence(1, monday.plus(DatePeriod(days = 1)), monday)).second.single().reason)
    }

    @Test
    fun setAvailabilityChangesTheShareOnlyWithinBounds() {
        val (s, skipped) = apply(SetAvailability(1, 50), SetAvailability(2, 0), SetAvailability(2, 101))
        assertEquals(listOf(1, 2), skipped.map { it.index })
        assertEquals(50, s.members.single { it.id == 1L }.availabilityPercent)
        assertEquals(100, s.members.single { it.id == 2L }.availabilityPercent)
    }

    @Test
    fun reassignMovesAnOpenTaskAndStopsItsStartedState() {
        val started = base.copy(tasks = base.tasks.map { if (it.id == 1L) it.copy(startedOn = monday) else it })
        val (s, skipped) = Scenario.apply(started, listOf(Reassign("uid-1", 2), Reassign("uid-3", null), Reassign("uid-4", 1)))
        assertTrue(skipped.isEmpty())
        assertEquals(2L, s.tasks.single { it.id == 1L }.assigneeId)
        assertNull(s.tasks.single { it.id == 1L }.startedOn)
        assertNull(s.tasks.single { it.id == 3L }.assigneeId)
        assertEquals(1L, s.tasks.single { it.id == 4L }.assigneeId)
    }

    @Test
    fun reassignToAHypotheticalMemberDefinedEarlierWorksAndLaterDoesNot() {
        val (ok, s1) = apply(AddMember(-1, "H", 100, 8.0), Reassign("uid-1", -1)).let { it.first to it.second }
        assertTrue(s1.isEmpty())
        assertEquals(-1L, ok.tasks.single { it.id == 1L }.assigneeId)
        val (_, s2) = apply(Reassign("uid-1", -1), AddMember(-1, "H", 100, 8.0))
        assertEquals(SkipReason.MEMBER_NOT_FOUND, s2.single().reason)
    }

    @Test
    fun addTasksCreatesHypotheticalBacklogTasksWithDeterministicIdentities() {
        val (s, skipped) = apply(AddTasks(3, 5, " Dev ", 0), AddTasks(1, null, "", null))
        assertTrue(skipped.isEmpty())
        val added = s.tasks.filter { it.id < 0 }
        assertEquals(listOf(-1L, -2L, -3L, -4L), added.map { it.id })
        assertEquals(listOf("scenario-task-0-0", "scenario-task-0-1", "scenario-task-0-2", "scenario-task-1-0"), added.map { it.teamUid })
        assertEquals(Scenario.hypotheticalTaskUid(0, 1), added[1].teamUid)
        assertTrue(added.all { Scenario.isHypotheticalTask(it.teamUid!!) && it.space == TaskSpace.TEAM && it.assigneeId == null })
        assertEquals(listOf(5, 5, 5, null), added.map { it.fibonacciPoints })
        assertEquals(listOf("Dev", "Dev", "Dev", ""), added.map { it.taskType })
        assertEquals(listOf(0, 0, 0, null), added.map { it.priority })
        // Même scénario, mêmes identités : les tirages communs en dépendent.
        assertEquals(added.map { it.teamUid }, Scenario.apply(base, listOf(AddTasks(3, 5, "Dev", 0), AddTasks(1))).first.tasks.filter { it.id < 0 }.map { it.teamUid })
        // Hors bornes : ignoré.
        val bad = apply(AddTasks(0), AddTasks(Scenario.MAX_ADDED_TASKS + 1), AddTasks(1, 4), AddTasks(1, 3, "", 3)).second
        assertEquals(4, bad.size)
        assertTrue(bad.all { it.reason == SkipReason.INVALID_VALUE })
    }

    @Test
    fun hypotheticalTasksCanBeReassignedByTheirUid() {
        val (s, skipped) = apply(AddTasks(1, 3, "", 1), Reassign(Scenario.hypotheticalTaskUid(0, 0), 2))
        assertTrue(skipped.isEmpty())
        assertEquals(2L, s.tasks.single { it.id < 0 }.assigneeId)
    }

    @Test
    fun setPriorityAndDeadlineChangeOpenTasks() {
        val day = LocalDate(2026, 10, 20)
        val (s, skipped) = apply(SetPriority("uid-1", 0), SetDeadline("uid-2", day), SetDeadline("uid-2", null), SetDeadline("uid-3", day))
        assertTrue(skipped.isEmpty())
        assertEquals(0, s.tasks.single { it.id == 1L }.priority)
        assertNull(s.tasks.single { it.id == 2L }.deadline)
        assertEquals(day, s.tasks.single { it.id == 3L }.deadline)
        assertEquals(SkipReason.INVALID_VALUE, apply(SetPriority("uid-1", 3)).second.single().reason)
    }

    @Test
    fun setFocusChangesTheTeamFocusFactor() {
        val (s, skipped) = apply(SetFocus(0.6))
        assertTrue(skipped.isEmpty())
        assertEquals(0.6, s.settings.team!!.focusFactor)
        assertEquals(listOf(SkipReason.INVALID_VALUE, SkipReason.INVALID_VALUE, SkipReason.INVALID_VALUE), apply(SetFocus(0.0), SetFocus(1.2), SetFocus(Double.NaN)).second.map { it.reason })
        // Sans réglage d'équipe (base solo), le scénario le crée dans la copie seulement.
        val solo = Scenario.apply(KairosSnapshot(), listOf(SetFocus(0.5))).first
        assertEquals(0.5, solo.settings.team!!.focusFactor)
    }

    @Test
    fun aModificationThatNoLongerAppliesIsSkippedAndReported() {
        val (s, skipped) = apply(
            Reassign("uid-missing", 1), // tâche inconnue
            SetPriority("uid-5", 0), // tâche faite
            Reassign("uid-1", 3), // membre archivé depuis
            Reassign("uid-1", 42), // membre inconnu
            AddAbsence(3, monday, monday), // membre archivé
            RemoveMember(42),
            SetAvailability(3, 50),
            SetDeadline("uid-5", null),
        )
        assertEquals(
            listOf(
                SkipReason.TASK_NOT_FOUND, SkipReason.TASK_NOT_OPEN, SkipReason.MEMBER_ARCHIVED, SkipReason.MEMBER_NOT_FOUND,
                SkipReason.MEMBER_ARCHIVED, SkipReason.MEMBER_NOT_FOUND, SkipReason.MEMBER_ARCHIVED, SkipReason.TASK_NOT_OPEN,
            ),
            skipped.map { it.reason },
        )
        assertEquals((0..7).toList(), skipped.map { it.index })
        assertEquals(Reassign("uid-missing", 1), skipped.first().modification)
        // Rien n'a changé.
        assertEquals(base, s)
    }

    @Test
    fun modificationsApplyInOrderAndLaterOnesSeeEarlierOnes() {
        val (s, skipped) = apply(RemoveMember(1), Reassign("uid-1", 1), Reassign("uid-1", 2))
        assertEquals(listOf(SkipReason.MEMBER_ARCHIVED), skipped.map { it.reason })
        assertEquals(2L, s.tasks.single { it.id == 1L }.assigneeId)
    }

    @Test
    fun realChangesKeepsOnlyWhatCanApplyToRealData() {
        val mods = listOf(
            AddMember(-1, "H", 100, 8.0), AddTasks(2, 3, "", 1), Reassign("uid-1", -1), Reassign("uid-2", 2), Reassign("uid-3", null),
            RemoveMember(1), AddAbsence(1, monday, monday), SetAvailability(2, 50), SetPriority("uid-1", 0), SetDeadline("uid-1", null), SetFocus(0.7),
            // Désignent une entité hypothétique : rien de réel à qui les appliquer.
            AddAbsence(-1, monday, monday), SetAvailability(-1, 50), RemoveMember(-1),
            Reassign(Scenario.hypotheticalTaskUid(1, 0), 2), SetPriority(Scenario.hypotheticalTaskUid(1, 0), 0), SetDeadline(Scenario.hypotheticalTaskUid(1, 1), null),
        )
        val real = Scenario.realChanges(mods)
        assertEquals(
            listOf(
                Reassign("uid-2", 2), Reassign("uid-3", null), RemoveMember(1), AddAbsence(1, monday, monday), SetAvailability(2, 50),
                SetPriority("uid-1", 0), SetDeadline("uid-1", null), SetFocus(0.7),
            ),
            real,
        )
        assertTrue(real.all { Scenario.isReal(it) })
        assertFalse(Scenario.isReal(AddMember(-1, "H", 100, 8.0)))
        assertFalse(Scenario.isReal(AddTasks(1)))
    }

    // --- Sérialisation -------------------------------------------------------------------------

    private val all: List<ScenarioModification> = listOf(
        AddMember(-1, "Alex", 80, 7.5), RemoveMember(2), AddAbsence(1, LocalDate(2026, 10, 12), LocalDate(2026, 10, 16)),
        SetAvailability(1, 60), Reassign("uid-1", 2), Reassign("uid-2", null), AddTasks(3, 5, "Dev", 1), AddTasks(2),
        SetPriority("uid-3", 0), SetDeadline("uid-3", LocalDate(2026, 11, 2)), SetDeadline("uid-4", null), SetFocus(0.75),
    )

    @Test
    fun serializationRoundTripsEveryModification() {
        val text = ScenarioCodec.encode(all)
        val decoded = ScenarioCodec.decode(text)
        assertEquals(all, decoded.modifications)
        assertTrue(decoded.ignored.isEmpty())
        // Le discriminant est `type`, avec un nom stable par modification.
        for (type in listOf("addMember", "removeMember", "addAbsence", "setAvailability", "reassign", "addTasks", "setPriority", "setDeadline", "setFocus")) {
            assertTrue("\"type\":\"$type\"" in text.replace(" ", ""), "discriminant $type absent de $text")
        }
        assertTrue("\"2026-10-12\"" in text)
        assertEquals(emptyList(), ScenarioCodec.decode(ScenarioCodec.encode(emptyList())).modifications)
    }

    @Test
    fun aKnownShapeIsReadFromHandWrittenJson() {
        val d = ScenarioCodec.decode("""[{"type":"reassign","taskUid":"u1","memberId":4},{"type":"setFocus","factor":0.5},{"type":"reassign","taskUid":"u2"}]""")
        assertEquals(listOf(Reassign("u1", 4), SetFocus(0.5), Reassign("u2", null)), d.modifications)
    }

    @Test
    fun anUnknownTypeIsIgnoredAndReportedWithoutLosingTheOthers() {
        val d = ScenarioCodec.decode(
            """[{"type":"setFocus","factor":0.5},{"type":"swapTeams","a":1},{"type":"reassign","taskUid":"u1","memberId":2},{"nonsense":true},{"type":"setPriority","taskUid":"x"}]""",
        )
        assertEquals(listOf(SetFocus(0.5), Reassign("u1", 2)), d.modifications)
        // Type inconnu, objet sans type, type connu aux champs illisibles (priorité manquante).
        assertEquals(listOf(IgnoredModification(1, "swapTeams"), IgnoredModification(3, null), IgnoredModification(4, "setPriority")), d.ignored)
        // Un texte qui n'est pas une liste est signalé en entier.
        assertEquals(listOf(IgnoredModification(-1, null)), ScenarioCodec.decode("not json").ignored)
        assertEquals(listOf(IgnoredModification(-1, null)), ScenarioCodec.decode("""{"type":"setFocus"}""").ignored)
        assertTrue(ScenarioCodec.decode("not json").modifications.isEmpty())
    }

    @Test
    fun aScenarioIsATeamDataAndKeepsItsIdentity() {
        val scenario = TeamScenario(3, "Absence de Léa", all, ForecastFixtures.now, ForecastFixtures.now)
        val s = KairosSnapshot(teamScenarios = listOf(scenario))
        assertTrue(Workspaces.hasTeamData(s))
        assertFalse(Workspaces.hasTeamData(KairosSnapshot()))
        assertNotNull(s.teamScenarios.single().modifications.firstOrNull())
        assertTrue(scenario.ignored.isEmpty())
    }
}

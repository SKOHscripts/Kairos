package com.skohscripts.kairos.core.guide

import com.skohscripts.kairos.core.model.GuideSettings
import com.skohscripts.kairos.core.model.KairosSnapshot
import com.skohscripts.kairos.core.model.Note
import com.skohscripts.kairos.core.model.Task
import com.skohscripts.kairos.core.model.TaskStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.TimeZone

/** Rythme, mémoire et priorité des conseils (docs/spec/conseils.md, comportement attendu points 1, 2 et 4). */
class TipsPacingTest {
    private val now = Instant.parse("2026-11-18T12:00:00Z")
    private val today = LocalDate(2026, 11, 18)
    private val tz = TimeZone.UTC
    private val facts = TipFacts(TipPlatform.DESKTOP, darkTheme = true, linkedFile = true, calendarImport = false)

    private fun date(daysAgo: Int): LocalDate = today.minus(DatePeriod(days = daysAgo))
    private fun day(daysAgo: Int): String = date(daysAgo).toString()
    private fun note(daysAgo: Int) = Note(1, "Note", createdAt = now - daysAgo.days, updatedAt = now - daysAgo.days)

    /** Une base où seul `recap` est dû (14 jours, Bilan jamais ouvert), à l'âge [age]. */
    private fun base(age: Int = 20) = KairosSnapshot(notes = listOf(note(age)))
    private val onlyRecap = GuideSettings(tipsDismissed = TipId.entries.filter { it != TipId.RECAP }.map { it.key })

    private fun due(memory: GuideSettings = onlyRecap, snapshot: KairosSnapshot = base()) =
        Tips.due(snapshot, memory, facts, TipSpace.PERSONAL, now, tz)

    @Test
    fun nothingDuringTheFirstThreeDaysNorWithoutData() {
        assertFalse(Tips.pacingAllows(GuideSettings(), today, null))
        assertFalse(Tips.pacingAllows(GuideSettings(), today, 2))
        assertTrue(Tips.pacingAllows(GuideSettings(), today, 3))
    }

    @Test
    fun oneADayAndTwoPerSevenRollingDays() {
        assertFalse(Tips.pacingAllows(GuideSettings(tipsShown = listOf(day(0))), today, 20))
        assertTrue(Tips.pacingAllows(GuideSettings(tipsShown = listOf(day(1))), today, 20))
        assertFalse(Tips.pacingAllows(GuideSettings(tipsShown = listOf(day(6), day(1))), today, 20))
        assertTrue(Tips.pacingAllows(GuideSettings(tipsShown = listOf(day(7), day(1))), today, 20))
    }

    @Test
    fun shownKeepsTheTwoLastDates() {
        val once = Tips.shown(GuideSettings(), today)
        assertEquals(listOf(day(0)), once.tipsShown)
        assertEquals(listOf(day(1), day(0)), Tips.shown(GuideSettings(tipsShown = listOf(day(5), day(1))), today).tipsShown)
    }

    @Test
    fun laterBringsTheTipBackAfterFourteenDays() {
        val snoozed = Tips.snoozed(GuideSettings(), TipId.RECAP, date(13))
        assertNull(due(onlyRecap.copy(tipsSnoozed = snoozed.tipsSnoozed)))
        val back = Tips.snoozed(GuideSettings(), TipId.RECAP, date(14))
        assertEquals(TipId.RECAP, due(onlyRecap.copy(tipsSnoozed = back.tipsSnoozed))?.id)
    }

    @Test
    fun dismissedNeverComesBackAndAcceptedOnlyIfRecurring() {
        assertNull(due(Tips.dismissed(onlyRecap, TipId.RECAP)))
        assertNull(due(Tips.accepted(onlyRecap, TipId.RECAP, LocalDate(2025, 1, 1))))
        val recurring = TipId.WEEK_REVIEW
        assertFalse(Tips.memoryAllows(recurring, GuideSettings(tipsDone = mapOf(recurring.key to day(27))), today))
        assertTrue(Tips.memoryAllows(recurring, GuideSettings(tipsDone = mapOf(recurring.key to day(28))), today))
        assertFalse(Tips.memoryAllows(TipId.RECAP, GuideSettings(tipsDone = mapOf(TipId.RECAP.key to day(400))), today))
    }

    @Test
    fun theSwitchTurnsEveryTipOff() {
        assertEquals(TipId.RECAP, due()?.id)
        assertNull(due(onlyRecap.copy(tipsOff = true)))
    }

    @Test
    fun theYoungBaseAndThePaceAreReadBeforeAnyCondition() {
        assertNull(due(snapshot = base(age = 2)))
        assertNull(due(onlyRecap.copy(tipsShown = listOf(day(0)))))
    }

    @Test
    fun priorityGoesDataThenStatisticsThenSettingsThenFeatures() {
        assertEquals(TipGroup.entries.sorted(), TipId.entries.map { it.group }.distinct())
        // `stats_first` (statistiques) passe avant `recap` et `timer` (fonctionnalités), tous dus ici.
        val done = List(5) { Task(it + 10L, "T$it", status = TaskStatus.DONE, createdAt = now - 1.days, updatedAt = now - 1.days) }
        val snapshot = base().copy(tasks = done)
        assertEquals(TipId.STATS_FIRST, Tips.due(snapshot, null, facts, TipSpace.PERSONAL, now, tz)?.id)
        val withoutStats = GuideSettings(tipsDismissed = listOf(TipId.STATS_FIRST.key))
        assertEquals(TipId.TIMER, Tips.due(snapshot, withoutStats, facts, TipSpace.PERSONAL, now, tz)?.id)
    }

    @Test
    fun seenIsWrittenOnce() {
        val once = Tips.seen(GuideSettings(), Tips.SCREEN_STATS)
        assertEquals(listOf(Tips.SCREEN_STATS), once.seenScreens)
        assertTrue(Tips.seen(once, Tips.SCREEN_STATS) === once)
    }

    @Test
    fun keysAreUniqueAndReadBack() {
        assertEquals(TipId.entries.size, TipId.entries.map { it.key }.toSet().size)
        TipId.entries.forEach { assertEquals(it, TipId.ofKey(it.key)) }
        assertNull(TipId.ofKey("unknown"))
    }
}

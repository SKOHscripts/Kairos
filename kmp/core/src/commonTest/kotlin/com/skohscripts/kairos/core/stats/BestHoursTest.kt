package com.skohscripts.kairos.core.stats

import com.skohscripts.kairos.core.model.Settings
import com.skohscripts.kairos.core.model.Task
import com.skohscripts.kairos.core.model.WorkSession
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

/** Quand on travaille chaque type (docs/spec/planning-personnalise.md § Lot C, étape C1). */
class BestHoursTest {
    private val now = Instant.parse("2026-10-14T12:00:00Z") // mercredi
    private val t0 = Instant.parse("2026-09-01T08:00:00Z")
    private val utc = TimeZone.UTC
    private var next = 1L

    private fun task(id: Long, type: String) = Task(id, "T$id", taskType = type, createdAt = t0, updatedAt = t0)

    /** Une session le [day] du [month] 2026 de [start] à [end] (heures locales, UTC) ; `null` : ouverte. */
    private fun session(taskId: Long, day: Int, start: String, end: String?, month: Int = 10): WorkSession {
        fun at(hhmm: String) = LocalDateTime(2026, month, day, hhmm.substringBefore(':').toInt(), hhmm.substringAfter(':').toInt()).toInstant(utc)
        return WorkSession(next++, taskId, at(start), end?.let(::at), t0)
    }

    private fun measure(tasks: List<Task>, sessions: List<WorkSession>, tz: TimeZone = utc, settings: Settings = Settings()) = BestHours.measure(tasks, sessions, now, settings, tz)

    @Test
    fun noSessionGivesNoProfile() {
        val result = measure(listOf(task(1, "Dev")), emptyList())
        assertFalse(result.hasData)
        assertEquals(0, result.sessions)
    }

    @Test
    fun aSessionStraddlingTwoBucketsSplitsItsMinutes() {
        // 9 h 30 à 10 h 30 : 30 minutes dans 8-10 h et 30 dans 10-12 h.
        val dev = measure(listOf(task(1, "Dev")), listOf(session(1, 12, "09:30", "10:30"))).profiles.single()
        assertEquals(60, dev.minutes)
        assertEquals(0.5, dev.share[4], 1e-9)
        assertEquals(0.5, dev.share[5], 1e-9)
        assertEquals(1.0, dev.share.sum(), 1e-9)
    }

    @Test
    fun aSessionAcrossMidnightIsSplitOnTheLocalClock() {
        val night = WorkSession(99, 1, Instant.parse("2026-10-12T23:00:00Z"), Instant.parse("2026-10-13T01:00:00Z"), t0)
        val dev = measure(listOf(task(1, "Dev")), listOf(night)).profiles.single()
        assertEquals(0.5, dev.share[11], 1e-9) // 22-24 h
        assertEquals(0.5, dev.share[0], 1e-9) // 0-2 h
    }

    @Test
    fun tinySessionsAreIgnoredAndAnOpenSessionRunsUntilNow() {
        // 9 h 00-9 h 04 est ignorée ; 11 h 00 ouverte court jusqu'à maintenant (12 h, le 14).
        val dev = measure(listOf(task(1, "Dev")), listOf(session(1, 12, "09:00", "09:04"), session(1, 14, "11:00", null))).profiles.single()
        assertEquals(1, dev.sessions)
        assertEquals(60, dev.minutes)
    }

    @Test
    fun theLocalTimeZoneMovesTheBucket() {
        val s = session(1, 12, "10:00", "11:00")
        val paris = measure(listOf(task(1, "Dev")), listOf(s), TimeZone.of("Europe/Paris")).profiles.single()
        val inUtc = measure(listOf(task(1, "Dev")), listOf(s)).profiles.single()
        assertEquals(5, inUtc.share.indexOf(1.0)) // 10-12 h UTC
        assertEquals(6, paris.share.indexOf(1.0)) // 12-14 h à Paris (UTC+2 en octobre)
    }

    @Test
    fun aTypeIsReliableOnlyWithEnoughSessionsAndMinutes() {
        val few = (1..4).map { session(1, it + 5, "10:00", "11:00") }
        assertFalse(measure(listOf(task(1, "Dev")), few).profiles.single().reliable)
        val enough = (1..5).map { session(1, it + 5, "10:00", "11:00") }
        assertTrue(measure(listOf(task(1, "Dev")), enough).profiles.single().reliable)
        // Cinq sessions, mais trop peu de minutes : 5 × 10 min = 50 min.
        val short = (1..5).map { session(1, it + 5, "10:00", "10:10") }
        assertFalse(measure(listOf(task(1, "Dev")), short).profiles.single().reliable)
    }

    @Test
    fun theLiftComparesATypeToEveryoneElsesHours() {
        // Dev travaille toujours à 10 h ; Doc à 10 h et à 14 h, à parts égales.
        val tasks = listOf(task(1, "Dev"), task(2, "Doc"))
        val sessions = (1..4).map { session(1, it + 5, "10:00", "11:00") } +
            (1..2).map { session(2, it + 5, "10:00", "11:00") } + (1..2).map { session(2, it + 7, "14:00", "15:00") }
        val result = measure(tasks, sessions)
        val dev = result.profiles.first { it.type == "Dev" }
        val doc = result.profiles.first { it.type == "Doc" }
        // À 10 h tout le monde travaille : le lift de Dev y est modeste ; celui de Doc à 14 h, où personne d'autre ne travaille, est fort.
        assertTrue(doc.lift[7] > dev.lift[5])
        assertEquals(5, dev.peak)
        assertEquals(7, doc.peak)
        assertEquals(1.0, result.global.sum(), 1e-9)
    }

    @Test
    fun aTypeSpreadEvenlyHasNoPeak() {
        // Une heure par jour dans huit tranches différentes : aucune ne porte 20 % de son temps.
        val spread = (0 until 8).map { session(1, 6 + it, "${(8 + it * 2).toString().padStart(2, '0')}:00", "${(9 + it * 2).toString().padStart(2, '0')}:00") }
        assertNull(measure(listOf(task(1, "Dev")), spread).profiles.single().peak)
    }

    @Test
    fun recentSessionsWeighMoreThanOldOnes() {
        // Même durée, deux tranches : Dev aujourd'hui, Doc le premier jour de la fenêtre (lundi 24 août, 8 semaines).
        val tasks = listOf(task(1, "Dev"), task(2, "Doc"))
        val result = measure(tasks, listOf(session(1, 14, "10:00", "11:00"), session(2, 24, "14:00", "15:00", month = 8)))
        assertEquals(1, result.profiles.first { it.type == "Doc" }.sessions)
        // Doc pèse moins que Dev à durée égale.
        assertTrue(result.global[7] < result.global[5])
        assertTrue(abs(result.global[5] + result.global[7] - 1.0) < 1e-9)
    }

    @Test
    fun aSessionBeforeTheWindowIsIgnored() {
        val oldSession = session(1, 17, "10:00", "11:00", month = 8) // 17 août : avant le lundi 24 août
        assertFalse(measure(listOf(task(1, "Dev")), listOf(oldSession)).hasData)
    }

    @Test
    fun anUntypedTaskIsTheNoTypeProfile() {
        val result = measure(listOf(task(1, "")), listOf(session(1, 12, "10:00", "11:00"), session(404, 12, "10:00", "11:00")))
        assertEquals(listOf(""), result.profiles.map { it.type })
        assertEquals(2, result.profiles.single().sessions)
        assertNotNull(result.profiles.single().peak)
    }

    @Test
    fun theWindowFollowsTheStatsWindowSetting() {
        val oldSession = session(1, 28, "10:00", "11:00", month = 9) // 28 septembre : deux semaines et demie plus tôt
        val tasks = listOf(task(1, "Dev"))
        assertTrue(measure(tasks, listOf(oldSession), settings = Settings(statsWindowWeeks = 8)).hasData)
        assertFalse(measure(tasks, listOf(oldSession), settings = Settings(statsWindowWeeks = 1)).hasData)
    }

    @Test
    fun theResultIsOrderedByTrackedTime() {
        val tasks = listOf(task(1, "Short"), task(2, "Long"))
        val result = measure(tasks, listOf(session(1, 12, "10:00", "10:30"), session(2, 12, "11:00", "13:00")))
        assertEquals(listOf("Long", "Short"), result.profiles.map { it.type })
    }
}

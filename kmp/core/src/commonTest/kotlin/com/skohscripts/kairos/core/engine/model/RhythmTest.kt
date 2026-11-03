package com.skohscripts.kairos.core.engine.model

import com.skohscripts.kairos.core.engine.DurationEstimator
import com.skohscripts.kairos.core.engine.model.SimulatedHistory.Profile
import com.skohscripts.kairos.core.model.Settings
import com.skohscripts.kairos.core.model.Task
import com.skohscripts.kairos.core.model.TaskStatus
import com.skohscripts.kairos.core.model.WorkSession
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

/** Rythme selon le jour et la tranche (docs/spec/estimateur-modele.md § Rythme, besoin 11). */
class RhythmTest {
    private val settings = Settings()
    private val t0 = Instant.parse("2026-09-28T07:00:00Z")

    /** Titres nouveaux à lexique par type ; jours ouvrés tournants, tranches tournantes ; le lundi prend [mondayFactor]. */
    private fun history(seed: Int, mondayFactor: Double): List<DurationEstimator.Sample> =
        SimulatedHistory.generate(seed, Profile.NOVEL, n = 200).mapIndexed { i, s ->
            val weekday = i % 5 + 1
            val band = Rhythm.Band.entries[(i / 5) % 3]
            val minutes = if (weekday == 1) (s.minutes * mondayFactor).toInt() else s.minutes
            s.copy(minutes = minutes, weekday = weekday, band = band)
        }

    private fun task(on: LocalDate?, pinned: LocalDateTime? = null) =
        Task(9999, "rédiger rapport bilan zopiku", taskType = "Rédaction", fibonacciPoints = 3, scheduledDate = on, pinnedStart = pinned, createdAt = t0, updatedAt = t0)

    @Test
    fun aSlowerMondayIsLearnedAndAppliedToPlannedTasksOnly() {
        var found = 0
        for (seed in 1..10) {
            val h = history(seed, 1.3)
            val state = ModelEstimator.build(ModelEstimator.corpus(h), settings)
            val monday = state.rhythm.weekdays[1] ?: continue
            found++
            // Sous-estimé, jamais surestimé : les voisins d'un lundi sont souvent des lundis, l'attendu en garde une part.
            assertTrue(monday in 1.1..1.5, "graine $seed : facteur du lundi $monday")
        }
        assertTrue(found >= 8, "le lundi plus lent n'est trouvé que sur $found graines sur 10")
        // Appliqué à une tâche planifiée un lundi, pas un mardi, ni sans date.
        val h = history(1, 1.3)
        val mondayDate = LocalDate(2026, 11, 2)
        val onMonday = assertNotNull(ModelEstimator.estimate(task(mondayDate), DurationEstimator.History(h), settings))
        val onTuesday = assertNotNull(ModelEstimator.estimate(task(LocalDate(2026, 11, 3)), DurationEstimator.History(h), settings))
        val unplanned = assertNotNull(ModelEstimator.estimate(task(null), DurationEstimator.History(h), settings))
        assertTrue(onMonday.reliable)
        assertNotNull(onMonday.evidence!!.rhythm)
        assertEquals(1, onMonday.evidence!!.rhythm!!.weekday)
        assertTrue(onMonday.minutes > onTuesday.minutes, "lundi ${onMonday.minutes}, mardi ${onTuesday.minutes}")
        assertNull(unplanned.evidence!!.rhythm)
        assertEquals(onTuesday.minutes, unplanned.minutes)
    }

    @Test
    fun withoutAnEffectNoFactorIsKept() {
        var spurious = 0
        for (seed in 1..10) {
            val state = ModelEstimator.build(ModelEstimator.corpus(history(seed, 1.0)), settings)
            spurious += state.rhythm.weekdays.size + state.rhythm.bands.size
        }
        // Dix graines, sept jours et trois tranches chacune : au plus un faux facteur, par hasard.
        assertTrue(spurious <= 1, "$spurious facteurs sans effet réel")
    }

    @Test
    fun smallGroupsAndMissingStartsGiveNoFactor() {
        val few = history(3, 1.5).take(14)
        assertTrue(ModelEstimator.build(ModelEstimator.corpus(few), settings).rhythm.isEmpty)
        val noStarts = history(3, 1.5).map { it.copy(weekday = null, band = null) }
        assertTrue(ModelEstimator.build(ModelEstimator.corpus(noStarts), settings).rhythm.isEmpty)
    }

    @Test
    fun samplesTakeTheLocalDayAndBandOfTheFirstSession() {
        val done = Task(1, "Rapport", status = TaskStatus.DONE, createdAt = t0, updatedAt = t0)
        // Lundi 2 novembre 2026, 22 h 30 UTC = mardi 3 novembre, 7 h 30 à Tokyo (UTC+9) : matin du mardi.
        val sessions = listOf(
            WorkSession(1, 1, Instant.parse("2026-11-03T10:00:00Z"), Instant.parse("2026-11-03T10:30:00Z"), t0),
            WorkSession(2, 1, Instant.parse("2026-11-02T22:30:00Z"), Instant.parse("2026-11-02T23:00:00Z"), t0),
        )
        val starts = DurationEstimator.firstStarts(sessions, TimeZone.of("Asia/Tokyo"))
        val s = DurationEstimator.samples(listOf(done), mapOf(1L to 60), starts).single()
        assertEquals(2, s.weekday)
        assertEquals(Rhythm.Band.MORNING, s.band)
        // Sans fuseau : ni jour ni tranche.
        assertEquals(emptyMap(), DurationEstimator.firstStarts(sessions, null))
    }
}

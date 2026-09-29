package com.skohscripts.kairos.core.alerts

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

class ChronoAlertsTest {
    private val start = Instant.parse("2026-07-02T09:00:00Z")

    @Test
    fun thresholds_follow_kairos2() {
        val alerts = ChronoAlerts.thresholds(start, baseMinutes = 20, estimateMinutes = 45, idleMinutes = 180, pomodoroMinutes = 50)
        assertEquals(
            listOf(
                ChronoAlert(ChronoAlertKind.OVER, start + 25.minutes, 45),
                ChronoAlert(ChronoAlertKind.IDLE, start + 180.minutes, 180),
                ChronoAlert(ChronoAlertKind.POMODORO, start + 50.minutes, 50),
            ),
            alerts,
        )
    }

    @Test
    fun zero_or_missing_thresholds_are_disabled() {
        assertEquals(emptyList(), ChronoAlerts.thresholds(start, 0, null, 0, 0))
        assertEquals(emptyList(), ChronoAlerts.thresholds(start, 0, 0, 0, 0))
    }

    @Test
    fun only_thresholds_crossed_while_watching_notify() {
        // Déjà au-delà de l'estimé au démarrage : jamais notifié.
        val alerts = ChronoAlerts.thresholds(start, baseMinutes = 60, estimateMinutes = 45, idleMinutes = 0, pomodoroMinutes = 50)
        assertEquals(start, alerts.first().at)
        assertEquals(emptyList(), ChronoAlerts.crossed(alerts, since = start, now = start + 10.minutes))
        assertEquals(listOf(ChronoAlertKind.POMODORO), ChronoAlerts.crossed(alerts, start + 49.minutes, start + 50.minutes).map { it.kind })
        assertEquals(emptyList(), ChronoAlerts.crossed(alerts, start + 50.minutes, start + 51.minutes))
        assertEquals(listOf(ChronoAlertKind.POMODORO), ChronoAlerts.upcoming(alerts, start + 1.minutes).map { it.kind })
    }
}

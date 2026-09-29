package com.skohscripts.kairos.ui.chrono

import com.skohscripts.kairos.core.alerts.ChronoAlert
import com.skohscripts.kairos.core.alerts.ChronoAlertKind
import com.skohscripts.kairos.ui.generated.resources.Res
import com.skohscripts.kairos.ui.generated.resources.alert_idle
import com.skohscripts.kairos.ui.generated.resources.alert_over
import com.skohscripts.kairos.ui.generated.resources.alert_pomodoro
import com.skohscripts.kairos.ui.generated.resources.alert_title
import com.skohscripts.kairos.ui.generated.resources.duration_hours
import com.skohscripts.kairos.ui.generated.resources.duration_hours_minutes
import com.skohscripts.kairos.ui.generated.resources.duration_minutes
import com.skohscripts.kairos.ui.generated.resources.notif_channel_alerts
import com.skohscripts.kairos.ui.generated.resources.notif_channel_chrono
import com.skohscripts.kairos.ui.generated.resources.notif_stop
import com.skohscripts.kairos.ui.generated.resources.timer_running
import org.jetbrains.compose.resources.getString

/**
 * Textes du chrono hors composition (dans la langue de l'appareil) : pour la
 * veille des alertes et pour les notifications Android, construites hors de
 * l'interface (alarmes, redémarrage).
 */
object ChronoTexts {
    /** « 1 h 30 », « 2 h », « 45 min » (même format que l'interface). */
    suspend fun duration(minutes: Int): String {
        val m = maxOf(0, minutes)
        val hours = m / 60
        val rest = m % 60
        return when {
            hours > 0 && rest > 0 -> getString(Res.string.duration_hours_minutes, hours, rest.toString().padStart(2, '0'))
            hours > 0 -> getString(Res.string.duration_hours, hours)
            else -> getString(Res.string.duration_minutes, rest)
        }
    }

    suspend fun alertTitle(taskTitle: String): String = getString(Res.string.alert_title, taskTitle)

    /** Corps de l'alerte (textes de Kairos 2) : durées au moment du franchissement. */
    suspend fun alertBody(alert: ChronoAlert): String = when (alert.kind) {
        ChronoAlertKind.OVER -> getString(Res.string.alert_over, duration(alert.minutes), duration(alert.minutes))
        ChronoAlertKind.IDLE -> getString(Res.string.alert_idle, duration(alert.minutes))
        ChronoAlertKind.POMODORO -> getString(Res.string.alert_pomodoro, duration(alert.minutes))
    }

    suspend fun running(): String = getString(Res.string.timer_running)

    suspend fun stop(): String = getString(Res.string.notif_stop)

    suspend fun chronoChannel(): String = getString(Res.string.notif_channel_chrono)

    suspend fun alertsChannel(): String = getString(Res.string.notif_channel_alerts)
}

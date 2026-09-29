package com.skohscripts.kairos.desktop

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.window.Notification
import androidx.compose.ui.window.TrayState
import com.skohscripts.kairos.ui.app.ChronoNotifier
import com.skohscripts.kairos.ui.app.NotifyState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.awt.Toolkit

/**
 * Notifications du bureau (docs/spec-v3/temps-reel-chrono.md § Bureau) : les
 * notifications passent par l'icône du plateau système ([tray], `null` si le
 * bureau n'en a pas : certaines sessions Linux) ; le titre de la fenêtre
 * porte le temps qui tourne ou l'alerte qui clignote.
 */
class DesktopNotifier(private val tray: TrayState?) : ChronoNotifier {
    override val state: StateFlow<NotifyState> =
        MutableStateFlow(if (tray != null) NotifyState.ACTIVE else NotifyState.UNAVAILABLE)

    /** Préfixe du titre de la fenêtre, lu par `main()`. */
    var titlePrefix by mutableStateOf<String?>(null)
        private set

    override suspend fun notify(title: String, body: String, tag: String): Boolean {
        val target = tray ?: return false
        return runCatching { target.sendNotification(Notification(title, body, Notification.Type.Info)) }.isSuccess
    }

    override fun setTitle(prefix: String?) {
        titlePrefix = prefix
    }

    override fun beep() {
        runCatching { Toolkit.getDefaultToolkit().beep() }
    }
}

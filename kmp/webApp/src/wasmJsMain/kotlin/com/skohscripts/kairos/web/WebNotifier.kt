package com.skohscripts.kairos.web

import com.skohscripts.kairos.core.alerts.Bell
import com.skohscripts.kairos.ui.app.ChronoNotifier
import com.skohscripts.kairos.ui.chrono.bellBytes
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import com.skohscripts.kairos.ui.app.NotifyState
import kotlinx.coroutines.await
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Notifications de la version web (docs/spec/temps-reel-chrono.md § Web) :
 * API `Notification` du navigateur (contexte sécurisé : GitHub Pages est en
 * HTTPS), titre de l'onglet, cloches (docs/spec/temps-reel-chrono.md § Cloches).
 */
object WebNotifier : ChronoNotifier {
    private val mutable = MutableStateFlow(read())
    override val state: StateFlow<NotifyState> = mutable

    private fun read(): NotifyState = when (KairosWeb.notifyState()) {
        "granted" -> NotifyState.ACTIVE
        "denied" -> NotifyState.DENIED
        "default" -> NotifyState.CAN_REQUEST
        else -> NotifyState.UNAVAILABLE
    }

    override suspend fun requestPermission() {
        KairosWeb.notifyRequest().await<JsString>()
        mutable.value = read()
    }

    override suspend fun notify(title: String, body: String, tag: String): Boolean {
        mutable.value = read()
        return KairosWeb.notify(title, body, tag)
    }

    override fun setTitle(prefix: String?) = KairosWeb.setTitle(prefix)

    /** Cloche (issue #234) : le WAV généré, en URL `data:`, joué par `Audio`. */
    @OptIn(ExperimentalEncodingApi::class)
    override suspend fun ring(bell: Bell) = KairosWeb.ring("data:audio/wav;base64," + Base64.encode(bellBytes(bell)))
}

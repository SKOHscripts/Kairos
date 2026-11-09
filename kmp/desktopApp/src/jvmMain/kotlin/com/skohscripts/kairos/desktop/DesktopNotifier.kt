package com.skohscripts.kairos.desktop

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.window.Notification
import androidx.compose.ui.window.TrayState
import com.skohscripts.kairos.core.alerts.Bell
import com.skohscripts.kairos.ui.app.ChronoNotifier
import com.skohscripts.kairos.ui.chrono.bellBytes
import com.skohscripts.kairos.ui.app.NotifyState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.LineEvent
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Notifications du bureau (docs/spec/temps-reel-chrono.md § Bureau) : sous Linux, par `notify-send` quand il est là
 * ([notifySend]) : la notification native du bureau, avec ou sans zone de notification (issue #219 : GNOME n'en a souvent
 * pas, et la bulle d'AWT n'a pas le style du bureau) ; sinon, et sous Windows et macOS, par l'icône du plateau ([tray],
 * `null` si le bureau n'en a pas). Le titre de la fenêtre porte le temps qui tourne ou l'alerte qui clignote.
 */
class DesktopNotifier(
    private val tray: TrayState?,
    osName: String = System.getProperty("os.name").orEmpty(),
    /** Chemin de `notify-send` (Linux seulement), `null` s'il manque. */
    notifySend: String? = findOnPath("notify-send"),
    /** Lance une commande ; vrai si elle a réussi (code 0). Remplacé dans les tests. */
    private val run: (List<String>) -> Boolean = ::runCommand,
) : ChronoNotifier {
    private val notifySend: String? = notifySend.takeIf { osName.lowercase().startsWith("linux") }

    override val state: StateFlow<NotifyState> =
        MutableStateFlow(if (this.notifySend != null || tray != null) NotifyState.ACTIVE else NotifyState.UNAVAILABLE)

    /** Préfixe du titre de la fenêtre, lu par `main()`. */
    var titlePrefix by mutableStateOf<String?>(null)
        private set

    override suspend fun notify(title: String, body: String, tag: String): Boolean {
        notifySend?.let { command ->
            if (withContext(Dispatchers.IO) { run(listOf(command, "-a", "Kairos", title, body)) }) return true
        }
        val target = tray ?: return false
        return runCatching { target.sendNotification(Notification(title, body, Notification.Type.Info)) }.isSuccess
    }

    override fun setTitle(prefix: String?) {
        titlePrefix = prefix
    }

    /** Cloche (issue #234) : le WAV généré, joué par `javax.sound` ; pas de sortie son, rien. */
    override suspend fun ring(bell: Bell) {
        val bytes = bellBytes(bell)
        withContext(Dispatchers.IO) {
            runCatching {
                val clip = AudioSystem.getClip()
                clip.addLineListener { if (it.type == LineEvent.Type.STOP) clip.close() }
                clip.open(AudioSystem.getAudioInputStream(ByteArrayInputStream(bytes)))
                clip.start()
            }
        }
    }

    companion object {
        /** Le premier exécutable [name] du `PATH`, ou `null`. */
        fun findOnPath(name: String, path: String? = System.getenv("PATH")): String? =
            path.orEmpty().split(File.pathSeparatorChar).filter { it.isNotEmpty() }
                .map { File(it, name) }.firstOrNull { it.isFile && it.canExecute() }?.path

        /** Lance [command] et attend au plus 5 s : un service de notifications bloqué ne doit pas retenir la veille. */
        private fun runCommand(command: List<String>): Boolean = runCatching {
            val process = ProcessBuilder(command).redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD).start()
            if (!process.waitFor(5, TimeUnit.SECONDS)) {
                process.destroy()
                false
            } else {
                process.exitValue() == 0
            }
        }.getOrDefault(false)
    }
}

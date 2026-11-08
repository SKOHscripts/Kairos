package com.skohscripts.kairos.desktop

import kotlinx.coroutines.runBlocking
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.window.Tray
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.isTraySupported
import androidx.compose.ui.window.rememberTrayState
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.skohscripts.kairos.ui.KairosApp
import com.skohscripts.kairos.ui.Platform
import com.skohscripts.kairos.ui.icons.KairosLogo
import java.awt.Frame
import java.awt.Window as AwtWindow
import javax.swing.SwingUtilities
import kotlin.system.exitProcess

/** Point d'entrée de l'application de bureau (docs/spec/distribution.md § Bureau). */
fun main(args: Array<String>) {
    // Avant toute connexion : le sélecteur de proxy lit cette option une seule fois (docs/spec/bureau-avance.md § Proxy).
    DesktopUpdates.useSystemProxies()
    val env = System.getenv()
    val resolved = DataDirectory.resolveAll(args.toList(), env)
    val dataDir = resolved.dir
    CrashLog.install(dataDir)

    if (args.any { it == "--self-test" || it.startsWith("--self-test=") }) {
        exitProcess(SelfTest.run(args.toList(), dataDir))
    }
    // Captures des fiches de magasin (développement seulement, docs/spec/publication.md).
    args.firstOrNull { it.startsWith("--store-screenshots=") }?.let {
        exitProcess(StoreScreenshots.run(java.io.File(it.substringAfter('='))))
    }
    // Image de la page « histoire » (site/histoire, docs/spec/publication.md § Captures).
    args.firstOrNull { it.startsWith("--story-screenshots=") }?.let {
        exitProcess(StoreScreenshots.runStory(java.io.File(it.substringAfter('='))))
    }

    // Instance déjà ouverte : elle est ramenée au premier plan, celle-ci s'arrête.
    val instance = SingleInstance.acquire(dataDir) ?: exitProcess(0)
    // Dossier choisi et journal de diagnostic (docs/spec/bureau-avance.md) : le journal note la source du dossier, jamais son chemin.
    val folder = DesktopDataFolder(resolved, DataDirectory.kairosRoot(env, System.getProperty("os.name"), System.getProperty("user.home")))
    val diagnostics = FileDiagnostics(dataDir, resolved.source)
    diagnostics.log("start ${com.skohscripts.kairos.core.KairosBuild.VERSION_NAME} ${System.getProperty("os.name")} java=${System.getProperty("java.version")} folder=${resolved.source.name.lowercase()}")
    val preview = System.getProperty("kairos.preview") == "true"
    val appName = if (preview) "Kairos Preview" else "Kairos"
    val portable = PortableCopy.detect(appName = appName)
    // Paquet AUR (docs/spec/distribution.md § Paquet Arch Linux) : pacman met à jour, pas Kairos.
    val managed = SystemPackage.detect()
    // Avant toute fenêtre : la classe X11 que cite l'entrée `.desktop` d'une copie portable.
    WindowClass.apply()

    application {
        val state = rememberWindowState(size = DpSize(1200.dp, 800.dp))
        val icon = rememberVectorPainter(KairosLogo)
        // Plateau système : voie des notifications du chrono, quand le bureau en a un.
        val tray = if (isTraySupported) rememberTrayState() else null
        val notifier = remember { DesktopNotifier(tray) }
        if (tray != null) {
            Tray(icon = icon, state = tray, tooltip = appName, onAction = {
                state.isMinimized = false
            })
        }
        Window(
            onCloseRequest = {
                // Le fichier lié reçoit ce qui attendait encore son délai (docs/spec/export-import.md).
                runBlocking { runCatching { DesktopServices.current?.flush() } }
                instance.release()
                exitApplication()
            },
            state = state,
            // Le chrono qui tourne (ou l'alerte qui clignote) précède le nom de l'application.
            title = notifier.titlePrefix?.let { "$it · $appName" } ?: appName,
            icon = icon,
        ) {
            window.minimumSize = java.awt.Dimension(360, 480)
            val awtWindow: AwtWindow = window
            // Fichier lié (docs/spec/synchronisation.md § Fichier lié : cycle) : relu au retour du focus, et toutes les minutes
            // tant que la fenêtre n'est pas réduite.
            DisposableEffect(awtWindow) {
                val listener = object : java.awt.event.WindowAdapter() {
                    override fun windowIconified(e: java.awt.event.WindowEvent) { DesktopServices.current?.setVisible(false) }
                    override fun windowDeiconified(e: java.awt.event.WindowEvent) { DesktopServices.current?.setVisible(true) }
                    override fun windowActivated(e: java.awt.event.WindowEvent) { DesktopServices.current?.resume() }
                }
                awtWindow.addWindowListener(listener)
                onDispose { awtWindow.removeWindowListener(listener) }
            }
            instance.watchActivations {
                SwingUtilities.invokeLater {
                    if (state.isMinimized) state.isMinimized = false
                    (awtWindow as? Frame)?.state = Frame.NORMAL
                    awtWindow.toFront()
                    awtWindow.requestFocus()
                }
            }
            KairosApp(Platform.DESKTOP, splash = true) {
                DesktopServices.open(dataDir, notifier = notifier, portable = portable, folder = folder, diagnostics = diagnostics, managed = managed)
            }
        }
    }
}

package com.skohscripts.kairos.desktop

import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
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

/** Point d'entrée de l'application de bureau (docs/spec-v3/distribution.md § Bureau). */
fun main(args: Array<String>) {
    val dataDir = DataDirectory.resolve()
    CrashLog.install(dataDir)

    if (args.any { it == "--self-test" || it.startsWith("--self-test=") }) {
        exitProcess(SelfTest.run(args.toList(), dataDir))
    }
    // Captures des fiches de magasin (développement seulement, docs/spec-v3/publication.md).
    args.firstOrNull { it.startsWith("--store-screenshots=") }?.let {
        exitProcess(StoreScreenshots.run(java.io.File(it.substringAfter('='))))
    }

    // Instance déjà ouverte : elle est ramenée au premier plan, celle-ci s'arrête.
    val instance = SingleInstance.acquire(dataDir) ?: exitProcess(0)
    val preview = System.getProperty("kairos.preview") == "true"

    application {
        val state = rememberWindowState(size = DpSize(1200.dp, 800.dp))
        val appName = if (preview) "Kairos Preview" else "Kairos"
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
            instance.watchActivations {
                SwingUtilities.invokeLater {
                    if (state.isMinimized) state.isMinimized = false
                    (awtWindow as? Frame)?.state = Frame.NORMAL
                    awtWindow.toFront()
                    awtWindow.requestFocus()
                }
            }
            KairosApp(Platform.DESKTOP) { DesktopServices.open(dataDir, notifier = notifier) }
        }
    }
}

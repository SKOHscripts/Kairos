package com.skohscripts.kairos.desktop

import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
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

    // Instance déjà ouverte : elle est ramenée au premier plan, celle-ci s'arrête.
    val instance = SingleInstance.acquire(dataDir) ?: exitProcess(0)
    val preview = System.getProperty("kairos.preview") == "true"

    application {
        val state = rememberWindowState(size = DpSize(1200.dp, 800.dp))
        Window(
            onCloseRequest = {
                instance.release()
                exitApplication()
            },
            state = state,
            title = if (preview) "Kairos Preview" else "Kairos",
            icon = rememberVectorPainter(KairosLogo),
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
            KairosApp(Platform.DESKTOP) { DesktopServices.open(dataDir) }
        }
    }
}

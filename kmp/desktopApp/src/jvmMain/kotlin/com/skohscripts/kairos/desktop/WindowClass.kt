package com.skohscripts.kairos.desktop

import java.awt.GraphicsEnvironment
import java.awt.Toolkit

/**
 * Classe des fenêtres X11 (`WM_CLASS`) fixée à [NAME] : sans cela, AWT la
 * dérive de la classe en bas de la pile du fil qui charge le toolkit, qui
 * varie. L'entrée `.desktop` d'une copie portable la cite
 * (`StartupWMClass`) pour que la barre des tâches range les fenêtres sous
 * l'icône de Kairos (docs/spec/raccourci-portable.md). Demande
 * `--add-opens java.desktop/sun.awt.X11=ALL-UNNAMED` (`build.gradle.kts`) ;
 * sans effet hors X11 (Windows, macOS, Wayland natif).
 */
object WindowClass {
    const val NAME = "kairos"

    fun apply() {
        if (GraphicsEnvironment.isHeadless()) return
        runCatching {
            val toolkit = Toolkit.getDefaultToolkit()
            if (toolkit.javaClass.name != "sun.awt.X11.XToolkit") return
            toolkit.javaClass.getDeclaredField("awtAppClassName").apply { isAccessible = true }.set(null, NAME)
        }
    }
}

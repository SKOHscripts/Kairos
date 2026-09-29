package com.skohscripts.kairos.web

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import com.skohscripts.kairos.ui.KairosApp
import com.skohscripts.kairos.ui.Platform
import kotlinx.browser.document

/** Point d'entrée de la version web (docs/spec-v3/distribution.md § Web). */
@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    // Le message de chargement (et de navigateur trop ancien) vit dans
    // index.html ; il est retiré dès que l'interface prend la page.
    document.getElementById("kairos-loading")?.remove()
    ComposeViewport(document.body!!) {
        KairosApp(Platform.WEB) { WebServices.open() }
    }
}

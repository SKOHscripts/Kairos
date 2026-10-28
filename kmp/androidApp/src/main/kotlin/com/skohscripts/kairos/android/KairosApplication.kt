package com.skohscripts.kairos.android

import android.app.Application
import android.content.res.Configuration
import com.skohscripts.kairos.android.widgets.WidgetSync
import kotlinx.coroutines.launch

/** Application : pose le rapport de plantage local avant tout autre composant (activité, récepteurs). */
class KairosApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        KairosCrash.install(this)
    }

    /**
     * Mode nuit ou langue du système changés pendant que le processus vit : les widgets reprennent leurs couleurs et leurs
     * textes (docs/spec/widgets-android.md § Rendu) ; rien n'est redessiné si le contenu calculé est le même.
     */
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (WidgetSync.started) KairosProcess.scope.launch { WidgetSync.refresh(this@KairosApplication, force = false) }
    }
}

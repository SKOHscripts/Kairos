package com.skohscripts.kairos.android

import android.content.Context
import com.skohscripts.kairos.ui.app.AppServices
import com.skohscripts.kairos.ui.app.FileService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext

/**
 * Ce qui vit une fois par processus : la base ouverte, le suivi du chrono
 * (notification permanente et alarmes, [ChronoSync]) et l'activité courante
 * (sélecteurs de fichiers, demande d'autorisation). L'activité, les alarmes
 * et le redémarrage du téléphone passent tous par ici : une seule base
 * ouverte, quel que soit le point d'entrée.
 */
object KairosProcess {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    @Volatile var activity: MainActivity? = null
    private var services: Deferred<AppServices>? = null

    private val files = object : FileService {
        override suspend fun saveText(suggestedName: String, text: String) =
            withContext(Dispatchers.Main) { activity?.saveText(suggestedName, text) ?: false }

        override suspend fun openText() = withContext(Dispatchers.Main) { activity?.openText() }
    }

    @Synchronized
    fun services(context: Context): Deferred<AppServices> {
        val app = context.applicationContext
        return services ?: scope.async {
            val notifier = AndroidNotifier(app)
            AndroidServices.open(app, files, notifier) { target -> withContext(Dispatchers.Main) { activity?.openFileInto(target) ?: false } }.also { ChronoSync.start(app, it.repository, scope) }
        }.also { services = it }
    }
}

package com.skohscripts.kairos.android

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import com.skohscripts.kairos.ui.KairosApp
import com.skohscripts.kairos.ui.Platform
import com.skohscripts.kairos.ui.app.AppServices
import com.skohscripts.kairos.ui.app.FileService
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext

/** Seule activité : l'interface Compose commune, bord à bord (docs/spec-v3/distribution.md § Android). */
class MainActivity : ComponentActivity(), FileService {
    private var pendingSave: CompletableDeferred<Uri?>? = null
    private var pendingOpen: CompletableDeferred<Uri?>? = null

    // Sélecteurs du système (Storage Access Framework) : aucune permission de stockage.
    private val createDocument = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) {
        pendingSave?.complete(it)
    }
    private val openDocument = registerForActivityResult(ActivityResultContracts.OpenDocument()) {
        pendingOpen?.complete(it)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val services = servicesFor(this)
        setContent { KairosApp(Platform.ANDROID) { services.await() } }
    }

    override suspend fun saveText(suggestedName: String, text: String): Boolean {
        val result = CompletableDeferred<Uri?>().also { pendingSave = it }
        createDocument.launch(suggestedName)
        val uri = result.await() ?: return false
        withContext(Dispatchers.IO) {
            contentResolver.openOutputStream(uri, "wt")!!.use { it.write(text.toByteArray()) }
        }
        return true
    }

    override suspend fun openText(): String? {
        val result = CompletableDeferred<Uri?>().also { pendingOpen = it }
        openDocument.launch(arrayOf("application/json", "text/plain", "application/octet-stream"))
        val uri = result.await() ?: return null
        return withContext(Dispatchers.IO) {
            contentResolver.openInputStream(uri)!!.use { it.readBytes().decodeToString() }
        }
    }

    private companion object {
        /**
         * Services ouverts une seule fois par process : une recréation de
         * l'activité (rotation…) retrouve la même base. Les sélecteurs de
         * fichiers passent toujours par l'activité courante.
         */
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        private var current: MainActivity? = null
        private var services: Deferred<AppServices>? = null

        private val files = object : FileService {
            override suspend fun saveText(suggestedName: String, text: String) =
                withContext(Dispatchers.Main) { current!!.saveText(suggestedName, text) }

            override suspend fun openText() = withContext(Dispatchers.Main) { current!!.openText() }
        }

        fun servicesFor(activity: MainActivity): Deferred<AppServices> {
            current = activity
            return services ?: scope.async { AndroidServices.open(activity.applicationContext, files) }.also { services = it }
        }
    }
}

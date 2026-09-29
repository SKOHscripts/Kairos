package com.skohscripts.kairos.android

import android.Manifest
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import com.skohscripts.kairos.ui.KairosApp
import com.skohscripts.kairos.ui.Platform
import com.skohscripts.kairos.ui.app.FileService
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Seule activité : l'interface Compose commune, bord à bord (docs/spec/distribution.md § Android). */
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
    private var pendingPermission: CompletableDeferred<Boolean>? = null
    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        pendingPermission?.complete(it)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        KairosProcess.activity = this
        val services = KairosProcess.services(this)
        setContent { KairosApp(Platform.ANDROID) { services.await() } }
    }

    override fun onResume() {
        super.onResume()
        KairosProcess.activity = this
        // L'utilisateur a pu changer l'autorisation dans les réglages du système.
        KairosProcess.scope.launch { (KairosProcess.services(this@MainActivity).await().notifier as? AndroidNotifier)?.refresh() }
    }

    override fun onDestroy() {
        if (KairosProcess.activity === this) KairosProcess.activity = null
        super.onDestroy()
    }

    /** Demande `POST_NOTIFICATIONS` (Android 13+) ; `true` si accordée. */
    suspend fun requestNotificationPermission(): Boolean = withContext(Dispatchers.Main) {
        val result = CompletableDeferred<Boolean>().also { pendingPermission = it }
        notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        result.await()
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

    /** Fait choisir un fichier quelconque (une base Kairos 2) et le copie dans [target] ; `false` si annulé. */
    suspend fun openFileInto(target: java.io.File): Boolean {
        val result = CompletableDeferred<Uri?>().also { pendingOpen = it }
        openDocument.launch(arrayOf("*/*"))
        val uri = result.await() ?: return false
        withContext(Dispatchers.IO) {
            contentResolver.openInputStream(uri)!!.use { input -> target.outputStream().use { input.copyTo(it) } }
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
}

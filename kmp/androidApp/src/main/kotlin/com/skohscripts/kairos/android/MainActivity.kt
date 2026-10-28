package com.skohscripts.kairos.android

import com.skohscripts.kairos.ui.app.LinkedFileSync
import android.Manifest
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalContext
import com.skohscripts.kairos.ui.KairosApp
import com.skohscripts.kairos.ui.Platform
import com.skohscripts.kairos.ui.app.CrashReportScreen
import com.skohscripts.kairos.ui.app.FileService
import com.skohscripts.kairos.ui.app.mimeOf
import com.skohscripts.kairos.ui.navigation.Destination
import com.skohscripts.kairos.ui.theme.KairosTheme
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

/** Seule activité : l'interface Compose commune, bord à bord (docs/spec/distribution.md § Android). */
class MainActivity : ComponentActivity(), FileService {
    /** Rapport du plantage précédent affiché à la place de l'application (`null` : lancement normal). */
    private var crashReport: String? = null
    private var pendingSave: CompletableDeferred<Uri?>? = null
    private var pendingOpen: CompletableDeferred<Uri?>? = null

    // Sélecteurs du système (Storage Access Framework) : aucune permission de stockage. Un par type de fichier : le
    // type fixe l'extension que le fournisseur ajoute au nom (un `.csv` enregistré en JSON devenait `.csv.json`).
    private val createDocument = listOf("application/json", "text/csv", "image/png").associateWith { mime ->
        registerForActivityResult(ActivityResultContracts.CreateDocument(mime)) { pendingSave?.complete(it) }
    }
    private val openDocument = registerForActivityResult(ActivityResultContracts.OpenDocument()) {
        pendingOpen?.complete(it)
    }
    /** Destination demandée par un widget, application déjà ouverte (`onNewIntent`) ; remise à `null` une fois ouverte. */
    private var externalDestination by mutableStateOf<Destination?>(null)
    private var pendingPermission: CompletableDeferred<Boolean>? = null
    private val permissionRequest = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        pendingPermission?.complete(it)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        KairosProcess.activity = this
        // Un plantage au lancement précédent : son rapport remplace l'application, base fermée (issue #65).
        crashReport = KairosCrash.pending(this)
        crashReport?.let { report ->
            setContent {
                KairosTheme {
                    CrashReportScreen(report, onShare = { share(report) }, onContinue = { KairosCrash.clear(this); recreate() })
                }
            }
            return
        }
        val services = KairosProcess.services(this)
        setContent {
            // Couleurs du fond d'écran (Material You), claires ou sombres, proposées dans Réglages → Apparence
            // à partir d'Android 12 (docs/spec/apparence.md).
            val context = LocalContext.current
            val system = remember(context) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    { dark: Boolean -> if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context) }
                } else {
                    null
                }
            }
            KairosApp(
                Platform.ANDROID,
                // Ouvert par un widget (docs/spec/widgets-android.md § Actions) : la vue Jour ou la capture, pas l'Accueil.
                initialDestination = destinationOf(intent) ?: Destination.START,
                systemColorScheme = system,
                onDarkTheme = ::applySystemBars,
                splash = true,
                externalDestination = externalDestination,
                onExternalDestinationHandled = { externalDestination = null },
            ) { services.await() }
        }
    }

    /** Application déjà ouverte (`singleTop`) : un toucher sur un widget passe sur la vue demandée sans recréer l'activité. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        destinationOf(intent)?.let { externalDestination = it }
    }

    private fun destinationOf(intent: Intent?): Destination? =
        intent?.getStringExtra(EXTRA_DESTINATION)?.let { name -> Destination.entries.firstOrNull { it.name == name } }

    /**
     * Icônes des barres du système selon le thème **affiché**, pas celui du système : un thème sombre choisi sur un
     * système clair garderait sinon des icônes sombres sur fond sombre (docs/spec/apparence.md § Thème sombre). Voiles
     * de la barre de navigation à trois boutons : ceux d'`enableEdgeToEdge` par défaut.
     */
    private fun applySystemBars(dark: Boolean) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT) { dark },
            navigationBarStyle = SystemBarStyle.auto(AndroidColor.argb(0xE6, 0xFF, 0xFF, 0xFF), AndroidColor.argb(0x80, 0x1B, 0x1B, 0x1B)) { dark },
        )
    }

    override fun onResume() {
        super.onResume()
        KairosProcess.activity = this
        if (crashReport != null) return
        // L'utilisateur a pu changer l'autorisation dans les réglages du système (notifications, calendrier).
        KairosProcess.scope.launch {
            val services = KairosProcess.services(this@MainActivity).await()
            (services.notifier as? AndroidNotifier)?.refresh()
            (services.calendars as? AndroidCalendarService)?.refresh()
        }
    }

    // Arrière-plan : le fichier lié reçoit tout de suite ce qui attendait son délai (docs/spec/export-import.md).
    override fun onStop() {
        super.onStop()
        if (crashReport != null) return
        KairosProcess.scope.launch { (KairosProcess.services(this@MainActivity).await().linkedStorage as? LinkedFileSync)?.flush() }
    }

    override fun onDestroy() {
        if (KairosProcess.activity === this) KairosProcess.activity = null
        super.onDestroy()
    }

    /** Confie le rapport de plantage au partage du système : rien ne part sans le geste de l'utilisateur. */
    private fun share(report: String) {
        val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, report)
        startActivity(Intent.createChooser(send, null))
    }

    /** Demande `POST_NOTIFICATIONS` (Android 13+) ; `true` si accordée. */
    suspend fun requestNotificationPermission(): Boolean = requestPermission(Manifest.permission.POST_NOTIFICATIONS)

    /** Demande une autorisation d'exécution (notifications, lecture du calendrier) ; `true` si accordée. */
    suspend fun requestPermission(permission: String): Boolean = withContext(Dispatchers.Main) {
        val result = CompletableDeferred<Boolean>().also { pendingPermission = it }
        permissionRequest.launch(permission)
        result.await()
    }

    override suspend fun saveText(suggestedName: String, text: String): Boolean = saveBytes(suggestedName) { text.toByteArray() }

    /** PNG par `Bitmap.compress` ; un bitmap matériel (rendu du calque) est d'abord copié en mémoire. */
    override suspend fun saveImage(suggestedName: String, image: ImageBitmap): Boolean = saveBytes(suggestedName) {
        val bitmap = image.asAndroidBitmap().let { if (it.config == Bitmap.Config.HARDWARE) it.copy(Bitmap.Config.ARGB_8888, false) else it }
        ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
    }

    private suspend fun saveBytes(suggestedName: String, bytes: () -> ByteArray): Boolean {
        val result = CompletableDeferred<Uri?>().also { pendingSave = it }
        createDocument.getValue(mimeOf(suggestedName)).launch(suggestedName)
        val uri = result.await() ?: return false
        withContext(Dispatchers.IO) {
            val data = bytes()
            contentResolver.openOutputStream(uri, "wt")!!.use { it.write(data) }
        }
        return true
    }

    /**
     * Sélecteur du fichier lié (docs/spec/export-import.md § Fichier lié) : créer (`kairos.json` proposé) ou ouvrir, puis
     * accès persistant en lecture et écriture, pour écrire à chaque changement sans le redemander.
     */
    suspend fun pickLinkedFile(create: Boolean, suggestedName: String?): Uri? {
        val uri = if (create) {
            val result = CompletableDeferred<Uri?>().also { pendingSave = it }
            createDocument.getValue("application/json").launch(suggestedName ?: "kairos.json")
            result.await()
        } else {
            val result = CompletableDeferred<Uri?>().also { pendingOpen = it }
            openDocument.launch(arrayOf("application/json", "text/plain", "application/octet-stream"))
            result.await()
        } ?: return null
        runCatching {
            contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        }
        return uri
    }

    companion object {
        /** Extra des intentions des widgets : nom de la [Destination] à ouvrir (`DAY`, `NOTES`). */
        const val EXTRA_DESTINATION = "com.skohscripts.kairos.DESTINATION"
    }

    override suspend fun openText(): String? {
        val result = CompletableDeferred<Uri?>().also { pendingOpen = it }
        // .ics (issue #136) : `text/calendar`, parfois annoncé `text/x-vcalendar`.
        openDocument.launch(arrayOf("application/json", "text/plain", "text/calendar", "text/x-vcalendar", "application/octet-stream"))
        val uri = result.await() ?: return null
        return withContext(Dispatchers.IO) {
            contentResolver.openInputStream(uri)!!.use { it.readBytes().decodeToString() }
        }
    }
}

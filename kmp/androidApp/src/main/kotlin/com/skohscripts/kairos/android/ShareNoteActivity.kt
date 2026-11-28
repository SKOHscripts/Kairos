package com.skohscripts.kairos.android

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.core.content.IntentCompat
import com.skohscripts.kairos.core.notes.ImageBudget
import com.skohscripts.kairos.core.notes.SharedNote
import com.skohscripts.kairos.core.settings.UiLanguage
import com.skohscripts.kairos.ui.notes.ShareTexts
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * Cible de la feuille de partage d'Android (docs/spec/partage-android.md, issue #293) : un texte ou un lien partagé devient
 * aussitôt une note, sans ouvrir Kairos ; une ou plusieurs images aussi (docs/spec/notes-images.md, #295). Activité
 * transparente, sans historique ni affinité (manifeste) : elle ne montre rien et ne touche pas à la tâche Kairos. Elle attend la fin de l'écriture avant `finish()` : fermée tout de suite, elle laisserait
 * le système tuer le processus en cache avant que la note soit enregistrée.
 */
class ShareNoteActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = applicationContext
        // Un plantage au lancement précédent : la base reste fermée, comme pour MainActivity (docs/spec/demarrage.md).
        if (KairosCrash.pending(app) != null) return done { ShareTexts.failed() }
        // `CharSequence` : certaines applications envoient un texte mis en forme, que `getStringExtra` rendrait `null`.
        val body = SharedNote.body(
            intent.getCharSequenceExtra(Intent.EXTRA_SUBJECT)?.toString(),
            intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString(),
        )
        // Images partagées (4.6.0, docs/spec/notes-images.md § Partage d'images) : une ou plusieurs, avec ou sans texte.
        val streams = when (intent.action) {
            Intent.ACTION_SEND_MULTIPLE -> IntentCompat.getParcelableArrayListExtra(intent, Intent.EXTRA_STREAM, Uri::class.java).orEmpty()
            else -> listOfNotNull(IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java))
        }
        if (body.isEmpty() && streams.isEmpty()) return done { ShareTexts.empty() }
        KairosProcess.scope.launch {
            val services = runCatching { KairosProcess.services(app).await() }.getOrNull()
            if (services == null) {
                done { ShareTexts.failed() }
                return@launch
            }
            // Processus ouvert par le partage, sans activité Compose : la langue choisie n'est pas encore posée (comme WidgetSync).
            val settings = services.repository.personalSnapshot.value.settings
            services.applyLanguage(UiLanguage.resolve(settings.language, services.systemLanguage ?: Locale.getDefault().language))
            // Lues avant `finish()` : l'accès accordé par l'application d'origine cesse avec l'activité.
            val images = AndroidImages(app).fromUris(streams.take(ImageBudget.MAX_IMAGES)).images
            if (body.isEmpty() && images.isEmpty()) {
                done { ShareTexts.empty() }
                return@launch
            }
            val created = runCatching { services.repository.createNote(body, images) }.getOrDefault(false)
            val limited = streams.size > ImageBudget.MAX_IMAGES
            done { if (!created) ShareTexts.failed() else if (limited) ShareTexts.addedLimit() else ShareTexts.added() }
        }
    }

    /** Message bref (contexte de l'application : il reste affiché après la fermeture), puis fermeture. */
    private fun done(message: suspend () -> String) {
        KairosProcess.scope.launch {
            val text = runCatching { message() }.getOrNull()
            withContext(Dispatchers.Main) {
                if (text != null) Toast.makeText(applicationContext, text, Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }
}

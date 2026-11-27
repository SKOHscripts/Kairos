package com.skohscripts.kairos.android

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import com.skohscripts.kairos.core.notes.SharedNote
import com.skohscripts.kairos.core.settings.UiLanguage
import com.skohscripts.kairos.ui.notes.ShareTexts
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * Cible de la feuille de partage d'Android (docs/spec/partage-android.md, issue #293) : un texte ou un lien partagé devient
 * aussitôt une note, sans ouvrir Kairos. Activité transparente, sans historique ni affinité (manifeste) : elle ne montre rien
 * et ne touche pas à la tâche Kairos. Elle attend la fin de l'écriture avant `finish()` : fermée tout de suite, elle laisserait
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
        if (body.isEmpty()) return done { ShareTexts.empty() }
        KairosProcess.scope.launch {
            val services = runCatching { KairosProcess.services(app).await() }.getOrNull()
            if (services == null) {
                done { ShareTexts.failed() }
                return@launch
            }
            // Processus ouvert par le partage, sans activité Compose : la langue choisie n'est pas encore posée (comme WidgetSync).
            val settings = services.repository.personalSnapshot.value.settings
            services.applyLanguage(UiLanguage.resolve(settings.language, services.systemLanguage ?: Locale.getDefault().language))
            val created = runCatching { services.repository.createNote(body) }.getOrDefault(false)
            done { if (created) ShareTexts.added() else ShareTexts.failed() }
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

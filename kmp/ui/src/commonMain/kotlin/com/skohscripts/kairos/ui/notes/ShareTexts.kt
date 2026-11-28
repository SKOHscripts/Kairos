package com.skohscripts.kairos.ui.notes

import com.skohscripts.kairos.ui.generated.resources.Res
import com.skohscripts.kairos.ui.generated.resources.share_note_added
import com.skohscripts.kairos.ui.generated.resources.share_note_added_limit
import com.skohscripts.kairos.ui.generated.resources.share_note_empty
import com.skohscripts.kairos.ui.generated.resources.share_note_failed
import org.jetbrains.compose.resources.getString

/**
 * Messages du partage vers Kairos sur Android (docs/spec/partage-android.md § Textes), lus hors composition comme
 * [com.skohscripts.kairos.ui.widgets.WidgetTexts] : la langue est celle posée par `applyLanguage` avant l'appel.
 */
object ShareTexts {
    suspend fun added(): String = getString(Res.string.share_note_added)

    /** Plus de quatre images partagées : les quatre premières sont gardées (docs/spec/notes-images.md § Partage d'images). */
    suspend fun addedLimit(): String = getString(Res.string.share_note_added_limit)

    suspend fun empty(): String = getString(Res.string.share_note_empty)

    suspend fun failed(): String = getString(Res.string.share_note_failed)
}

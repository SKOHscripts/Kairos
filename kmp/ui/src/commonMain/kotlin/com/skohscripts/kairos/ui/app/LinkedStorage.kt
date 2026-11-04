package com.skohscripts.kairos.ui.app

import com.skohscripts.kairos.core.sync.SyncSummary
import kotlinx.coroutines.flow.StateFlow

/**
 * Fichier lié (docs/spec/export-import.md, docs/spec/synchronisation.md § Fichier lié : cycle) : une copie des données, au
 * format d'export, tenue à jour dans un vrai fichier choisi par l'utilisateur et **fusionnée** à chaque lecture avec les
 * données de l'appareil. Sur le web, elle protège aussi d'un nettoyage du navigateur.
 */
interface LinkedStorage {
    val state: StateFlow<LinkedFileState>

    /** Version web : bandeau d'incitation tant que rien n'est lié, textes du navigateur. */
    val browser: Boolean get() = false

    /** Crée un nouveau fichier et y écrit les données actuelles. `false` si annulé. */
    suspend fun createFile(): Boolean

    /**
     * Choisit un fichier Kairos existant : rend son contenu (le nom avant), sans rien changer encore ; `null` si annulé.
     * L'interface demande ensuite « Fusionner » ou « Remplacer » ([adoptOpened]) si cet appareil a déjà des données.
     */
    suspend fun openFile(): OpenedFile?

    /**
     * Lie le fichier ouvert. [merge] : le fusionner aussitôt (choix « Fusionner », ou appareil neuf), sans autre confirmation ;
     * sinon (« Remplacer ») il devient la référence : le remplacement des données qui suit y sera écrit.
     */
    suspend fun adoptOpened(merge: Boolean): Boolean

    /**
     * Redonne l'accès au fichier lié (geste de l'utilisateur requis). Si le
     * navigateur avait perdu les données, les recharge depuis le fichier.
     */
    suspend fun authorize(): Boolean = false

    /** Cesse d'écrire dans le fichier, qui reste tel quel. */
    suspend fun unlink()

    /** Fichier illisible ([LinkedFileState.Unreadable]) : y écrit les données de cet appareil. */
    suspend fun overwrite()

    /** Premier rapprochement accepté ([LinkedFileState.NeedsConfirmation]) : fusionne et écrit. */
    suspend fun confirmMerge()

    /** Premier rapprochement refusé : rien n'est fusionné ni écrit, et le fichier n'est plus lié. */
    suspend fun cancelMerge()

    /** Relit et fusionne tout de suite (bouton, retour au premier plan). */
    suspend fun mergeNow()

    /** Après un échec d'écriture ou une lecture illisible : réessaie tout de suite. */
    suspend fun retry()
}

class OpenedFile(val name: String, val text: String)

sealed interface LinkedFileState {
    /** Navigateur sans API File System Access (Firefox, Safari) : export manuel seulement. */
    data object Unsupported : LinkedFileState

    data object NotLinked : LinkedFileState

    data class Linked(val name: String) : LinkedFileState

    /**
     * L'accès au fichier doit être réaccordé ; [restore] : les données du
     * navigateur ont disparu et attendent d'être rechargées depuis le fichier.
     */
    data class NeedsPermission(val name: String, val restore: Boolean) : LinkedFileState

    /** L'enregistrement dans le navigateur a échoué (espace plein, stockage refusé). */
    data class SaveFailed(val detail: String) : LinkedFileState

    /**
     * Premier rapprochement (docs/spec/synchronisation.md § Premier rapprochement) : le fichier vient d'un autre appareil et la
     * fusion changerait le fichier ou des éléments diffèrent ; rien n'est fusionné ni écrit avant « Fusionner » ou « Annuler ».
     */
    data class NeedsConfirmation(val name: String, val summary: SyncSummary) : LinkedFileState

    /** Le fichier lié est illisible (trois lectures), d'une version plus récente ou trop en avance : plus rien n'y est écrit. */
    data class Unreadable(val name: String, val reason: UnreadableReason) : LinkedFileState

    /** Écrire dans le fichier lié a échoué (dossier disparu, accès retiré) ; les données restent dans la base. */
    data class WriteFailed(val name: String) : LinkedFileState
}

/** Empreinte d'un texte écrit dans le fichier lié : longueur et FNV-1a 64 bits, en hexadécimal. Pas de cryptographie : on compare. */
object LinkHash {
    fun of(text: String): String {
        var h = -0x340d631b7bdddcdbL
        for (c in text) {
            h = h xor c.code.toLong()
            h *= 0x100000001b3L
        }
        return "${text.length}:${h.toULong().toString(16)}"
    }
}

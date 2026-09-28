package com.skohscripts.kairos.ui.app

import kotlinx.coroutines.flow.StateFlow

/**
 * Fichier lié de la version web (docs/spec-v3/export-import.md § Version web) :
 * copie des données dans un vrai fichier local, à l'abri d'un nettoyage du
 * navigateur. Absent sur Android et le bureau (données déjà dans un fichier).
 */
interface LinkedStorage {
    val state: StateFlow<LinkedFileState>

    /** Crée un nouveau fichier et y écrit les données actuelles. `false` si annulé. */
    suspend fun createFile(): Boolean

    /**
     * Choisit un fichier Kairos existant : rend son contenu (le nom avant),
     * sans rien changer encore ; `null` si annulé. [adoptOpened] le lie ensuite,
     * une fois le remplacement des données confirmé.
     */
    suspend fun openFile(): OpenedFile?

    suspend fun adoptOpened(): Boolean

    /**
     * Redonne l'accès au fichier lié (geste de l'utilisateur requis). Si le
     * navigateur avait perdu les données, les recharge depuis le fichier.
     */
    suspend fun authorize(): Boolean
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
}

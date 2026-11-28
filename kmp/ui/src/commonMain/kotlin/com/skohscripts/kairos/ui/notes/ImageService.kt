package com.skohscripts.kairos.ui.notes

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.content.TransferableContent
import com.skohscripts.kairos.core.notes.EncodedImage

/** D'où vient une image ajoutée à une note (docs/spec/notes-images.md § Comportement attendu) ; l'ordre est celui du menu. */
enum class ImageSource { GALLERY, CAMERA, FILE, CLIPBOARD }

/** Images choisies, déjà réduites ([ImageService.encode]), et le nombre de celles qui n'ont pas pu être lues. */
data class ImagePick(val images: List<EncodedImage>, val unreadable: Int = 0) {
    companion object {
        val NONE = ImagePick(emptyList())
    }
}

/**
 * Images des notes, fournies par la plateforme (docs/spec/notes-images.md § Réduction des images) : chaque image sort réduite
 * (`ImageBudget`, 1 600 px au plus, JPEG), droite et sans métadonnées. Tout se passe hors du fil principal.
 */
interface ImageService {
    /** Les entrées du menu « Ajouter une image » sur cette plateforme, dans l'ordre. */
    val sources: List<ImageSource>

    /** Ouvre le sélecteur de [source] (galerie, appareil photo, fichiers) ; au plus [max] images ; rien si on annule. */
    suspend fun pick(source: ImageSource, max: Int): ImagePick

    /** Les images du presse-papiers, au plus [max] ; vide s'il n'y en a pas. */
    suspend fun paste(max: Int): ImagePick

    /**
     * La plateforme lit les images reçues par le champ de la note ([receive], Android : « Coller » et images du clavier). Vrai
     * seulement là : le récepteur de contenu prend tout contenu qui porte une image, et ailleurs un collage de texte se perdrait.
     */
    val receivesContent: Boolean get() = false

    /** Les images d'un contenu reçu par le champ ([receivesContent]) ; `null` si rien n'a pu en être tiré. */
    @OptIn(ExperimentalFoundationApi::class)
    suspend fun receive(content: TransferableContent, max: Int): ImagePick? = null

    /** Le bureau et le web collent une image par Ctrl/Cmd+V dans le champ : ils lisent alors [pasteFromShortcut]. */
    val pastesOnShortcut: Boolean get() = false

    /**
     * Les images collées par Ctrl/Cmd+V ([pastesOnShortcut]) ; par défaut [paste]. Le web lit l'événement « paste » du navigateur,
     * qui ne demande pas d'autorisation, plutôt que l'API Clipboard.
     */
    suspend fun pasteFromShortcut(max: Int): ImagePick = paste(max)
}

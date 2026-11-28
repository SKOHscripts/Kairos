package com.skohscripts.kairos.core.notes

import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Une image réduite, prête à entrer dans une note (docs/spec/notes-images.md § Réduction) : produite par la plateforme
 * (`ImageService.encode`), écrite telle quelle par le dépôt. [data] : le JPEG en Base64 standard.
 */
data class EncodedImage(val mime: String, val width: Int, val height: Int, val data: String)

/**
 * Taille cible des images des notes (docs/spec/notes-images.md § Modèle et taille cible), pure : la plateforme décode, réduit et
 * encode, mais toutes suivent ces nombres.
 */
object ImageBudget {
    /** Images au plus par note. */
    const val MAX_IMAGES = 4

    /** Grand côté au plus, en pixels : assez pour lire une capture d'écran de téléphone ou une page photographiée. */
    const val MAX_SIDE = 1_600

    /** Qualités JPEG essayées dans l'ordre, tant que le résultat dépasse [MAX_BYTES] ; à la dernière, le résultat est gardé. */
    val QUALITIES: List<Int> = listOf(85, 75, 65)

    /** Taille visée d'une image encodée, en octets (avant Base64). */
    const val MAX_BYTES = 400_000

    const val JPEG = "image/jpeg"

    /** Les dimensions réduites de [width] × [height] : rapport gardé, jamais d'agrandissement, jamais de côté nul. */
    fun target(width: Int, height: Int): Pair<Int, Int> {
        require(width > 0 && height > 0) { "dimensions" }
        val side = max(width, height)
        if (side <= MAX_SIDE) return width to height
        val scale = MAX_SIDE.toDouble() / side
        return (width * scale).roundToInt().coerceAtLeast(1) to (height * scale).roundToInt().coerceAtLeast(1)
    }

    /**
     * Le plus grand facteur de sous-échantillonnage (puissance de 2) qui garde l'image décodée au moins aussi grande que la
     * cible : Android décode ainsi une photo de 12 Mpx sans la charger entière en mémoire (`BitmapFactory.inSampleSize`).
     */
    fun sampleSize(width: Int, height: Int): Int {
        val side = max(width, height)
        var sample = 1
        while (side / (sample * 2) >= MAX_SIDE) sample *= 2
        return sample
    }
}

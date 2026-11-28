package com.skohscripts.kairos.android

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.media.ExifInterface
import android.net.Uri
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.content.TransferableContent
import com.skohscripts.kairos.core.notes.EncodedImage
import com.skohscripts.kairos.core.notes.ImageBudget
import com.skohscripts.kairos.ui.notes.ImagePick
import com.skohscripts.kairos.ui.notes.ImageService
import com.skohscripts.kairos.ui.notes.ImageSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import kotlin.io.encoding.Base64

/**
 * Images des notes sur Android (docs/spec/notes-images.md § Réduction des images) : sélecteur de photos du système et appareil
 * photo du système (par [MainActivity], aucune autorisation), presse-papiers, images reçues par le champ (« Coller », clavier).
 * Réduction par `BitmapFactory` (sous-échantillonnage d'abord : une photo de 12 Mpx n'est jamais chargée entière), orientation
 * par `ExifInterface`, JPEG par `Bitmap.compress`.
 */
internal class AndroidImages(private val context: Context) : ImageService {
    override val sources: List<ImageSource> = listOf(ImageSource.GALLERY, ImageSource.CAMERA, ImageSource.CLIPBOARD)

    override val receivesContent: Boolean = true

    override suspend fun pick(source: ImageSource, max: Int): ImagePick {
        val activity = KairosProcess.activity ?: return ImagePick.NONE
        val uris = when (source) {
            ImageSource.GALLERY -> activity.pickImages()
            ImageSource.CAMERA -> listOfNotNull(activity.takePhoto())
            else -> emptyList()
        }
        val pick = fromUris(uris.take(max))
        if (source == ImageSource.CAMERA) activity.clearPhotos()
        return pick
    }

    override suspend fun paste(max: Int): ImagePick {
        val clip = withContext(Dispatchers.Main) {
            (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).primaryClip
        } ?: return ImagePick.NONE
        return fromClip(clip, max)
    }

    @OptIn(ExperimentalFoundationApi::class)
    override suspend fun receive(content: TransferableContent, max: Int): ImagePick? =
        fromClip(content.clipEntry.clipData, max).takeIf { it.images.isNotEmpty() || it.unreadable > 0 }

    /** Les images d'un contenu du presse-papiers ou du clavier : ses éléments qui désignent un fichier. */
    private suspend fun fromClip(clip: ClipData, max: Int): ImagePick {
        val uris = (0 until clip.itemCount).mapNotNull { clip.getItemAt(it).uri }
        if (uris.isEmpty()) return ImagePick.NONE
        val images = uris.filter { uri -> context.contentResolver.getType(uri)?.startsWith("image/") != false }
        return fromUris(images.take(max))
    }

    /** Lit puis réduit chaque image ; l'accès temporaire à une `Uri` ne dure pas : tout est lu ici. */
    suspend fun fromUris(uris: List<Uri>): ImagePick = withContext(Dispatchers.IO) {
        val encoded = uris.map { uri ->
            runCatching { context.contentResolver.openInputStream(uri)?.use { it.readBytes() } }.getOrNull()?.let(::encode)
        }
        ImagePick(encoded.filterNotNull(), unreadable = encoded.count { it == null })
    }

    companion object {
        /**
         * Décode [bytes], le tourne selon son EXIF, le réduit à [ImageBudget.target] sur un fond blanc et l'encode en JPEG
         * ([ImageBudget.QUALITIES]). Réencoder retire toutes les métadonnées. `null` si l'image est illisible.
         */
        fun encode(bytes: ByteArray): EncodedImage? = runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
            val options = BitmapFactory.Options().apply { inSampleSize = ImageBudget.sampleSize(bounds.outWidth, bounds.outHeight) }
            val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options) ?: return null
            val orientation = runCatching {
                ExifInterface(ByteArrayInputStream(bytes)).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
            val turn = orientationMatrix(orientation, decoded.width, decoded.height)
            val swaps = orientation in setOf(
                ExifInterface.ORIENTATION_ROTATE_90, ExifInterface.ORIENTATION_ROTATE_270,
                ExifInterface.ORIENTATION_TRANSPOSE, ExifInterface.ORIENTATION_TRANSVERSE,
            )
            val width = if (swaps) decoded.height else decoded.width
            val height = if (swaps) decoded.width else decoded.height
            val (w, h) = ImageBudget.target(width, height)
            val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            Canvas(out).apply {
                drawColor(Color.WHITE)
                val m = Matrix(turn).apply { postScale(w.toFloat() / width, h.toFloat() / height) }
                drawBitmap(decoded, m, Paint(Paint.FILTER_BITMAP_FLAG))
            }
            decoded.recycle()
            var jpeg = ByteArray(0)
            for (quality in ImageBudget.QUALITIES) {
                jpeg = ByteArrayOutputStream().also { out.compress(Bitmap.CompressFormat.JPEG, quality, it) }.toByteArray()
                if (jpeg.size <= ImageBudget.MAX_BYTES) break
            }
            out.recycle()
            EncodedImage(ImageBudget.JPEG, w, h, Base64.encode(jpeg))
        }.getOrNull()

        /** La transformation qui redresse une image décodée de [w] × [h] selon son orientation EXIF. */
        private fun orientationMatrix(orientation: Int, w: Int, h: Int): Matrix = Matrix().apply {
            when (orientation) {
                ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> { setScale(-1f, 1f); postTranslate(w.toFloat(), 0f) }
                ExifInterface.ORIENTATION_ROTATE_180 -> { setRotate(180f); postTranslate(w.toFloat(), h.toFloat()) }
                ExifInterface.ORIENTATION_FLIP_VERTICAL -> { setScale(1f, -1f); postTranslate(0f, h.toFloat()) }
                ExifInterface.ORIENTATION_TRANSPOSE -> { setRotate(90f); postScale(-1f, 1f) }
                ExifInterface.ORIENTATION_ROTATE_90 -> { setRotate(90f); postTranslate(h.toFloat(), 0f) }
                ExifInterface.ORIENTATION_TRANSVERSE -> { setRotate(-90f); postScale(-1f, 1f); postTranslate(h.toFloat(), w.toFloat()) }
                ExifInterface.ORIENTATION_ROTATE_270 -> { setRotate(-90f); postTranslate(0f, w.toFloat()) }
                else -> reset()
            }
        }
    }
}

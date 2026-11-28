package com.skohscripts.kairos.desktop

import com.skohscripts.kairos.core.notes.EncodedImage
import com.skohscripts.kairos.core.notes.ImageBudget
import com.skohscripts.kairos.ui.notes.ImagePick
import com.skohscripts.kairos.ui.notes.ImageService
import com.skohscripts.kairos.ui.notes.ImageSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.skia.Codec
import org.jetbrains.skia.Color
import org.jetbrains.skia.Data
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.FilterMipmap
import org.jetbrains.skia.FilterMode
import org.jetbrains.skia.Image
import org.jetbrains.skia.MipmapMode
import org.jetbrains.skia.Rect
import org.jetbrains.skia.Surface
import java.awt.FileDialog
import java.awt.Frame
import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import java.awt.image.BufferedImage
import java.awt.image.RenderedImage
import java.io.ByteArrayOutputStream
import java.io.File
import javax.imageio.ImageIO
import kotlin.io.encoding.Base64

/**
 * Images des notes sur le bureau (docs/spec/notes-images.md § Réduction des images) : sélecteur de fichiers AWT (plusieurs à la
 * fois), presse-papiers AWT (une image, ou des fichiers copiés depuis le gestionnaire de fichiers), et réduction par Skia, déjà
 * embarqué par Compose : aucune dépendance de plus.
 */
internal object DesktopImages : ImageService {
    private val EXTENSIONS = setOf("png", "jpg", "jpeg", "webp", "gif", "bmp")

    override val sources: List<ImageSource> = listOf(ImageSource.FILE, ImageSource.CLIPBOARD)

    override val pastesOnShortcut: Boolean = true

    override suspend fun pick(source: ImageSource, max: Int): ImagePick {
        if (source != ImageSource.FILE) return ImagePick.NONE
        val files = withContext(Dispatchers.Main) {
            val dialog = FileDialog(null as Frame?, "Kairos", FileDialog.LOAD)
            dialog.isMultipleMode = true
            dialog.setFilenameFilter { _, name -> name.substringAfterLast('.').lowercase() in EXTENSIONS }
            dialog.isVisible = true
            dialog.files.orEmpty().toList()
        }
        return fromFiles(files, max)
    }

    override suspend fun paste(max: Int): ImagePick = withContext(Dispatchers.IO) {
        val clipboard = runCatching { Toolkit.getDefaultToolkit().systemClipboard }.getOrNull() ?: return@withContext ImagePick.NONE
        val content = runCatching { clipboard.getContents(null) }.getOrNull() ?: return@withContext ImagePick.NONE
        when {
            // Des fichiers copiés dans le gestionnaire de fichiers : ceux qui sont des images.
            content.isDataFlavorSupported(DataFlavor.javaFileListFlavor) -> {
                val files = runCatching { (content.getTransferData(DataFlavor.javaFileListFlavor) as List<*>).filterIsInstance<File>() }.getOrNull().orEmpty()
                fromFiles(files.filter { it.extension.lowercase() in EXTENSIONS }, max)
            }
            content.isDataFlavorSupported(DataFlavor.imageFlavor) -> {
                val image = runCatching { content.getTransferData(DataFlavor.imageFlavor) as java.awt.Image }.getOrNull()
                val png = image?.let(::png)
                val encoded = png?.let(::encode)
                if (encoded != null) ImagePick(listOf(encoded)) else ImagePick(emptyList(), unreadable = 1)
            }
            else -> ImagePick.NONE
        }
    }

    private suspend fun fromFiles(files: List<File>, max: Int): ImagePick = withContext(Dispatchers.IO) {
        val encoded = files.take(max).map { f -> runCatching { encode(f.readBytes()) }.getOrNull() }
        ImagePick(encoded.filterNotNull(), unreadable = encoded.count { it == null })
    }

    /** Une image AWT du presse-papiers, en PNG (sans perte) avant la réduction commune. */
    private fun png(image: java.awt.Image): ByteArray? {
        val rendered: RenderedImage = image as? BufferedImage ?: run {
            val w = image.getWidth(null)
            val h = image.getHeight(null)
            if (w <= 0 || h <= 0) return null
            BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB).also { it.createGraphics().apply { drawImage(image, 0, 0, null); dispose() } }
        }
        return ByteArrayOutputStream().also { if (!ImageIO.write(rendered, "png", it)) return null }.toByteArray()
    }

    /**
     * Décode [bytes] (PNG, JPEG, WebP, GIF, BMP), le tourne selon son orientation EXIF, le réduit à [ImageBudget.target] sur un fond
     * blanc (la transparence d'une capture) et l'encode en JPEG selon [ImageBudget.QUALITIES]. Réencoder retire toutes les
     * métadonnées. `null` si l'image est illisible.
     */
    fun encode(bytes: ByteArray): EncodedImage? = runCatching {
        val codec = Codec.makeFromData(Data.makeFromBytes(bytes))
        val pixels = codec.readPixels()
        val origin = codec.encodedOrigin
        val (width, height) = if (origin.swapsWidthHeight()) codec.height to codec.width else codec.width to codec.height
        val (w, h) = ImageBudget.target(width, height)
        val surface = Surface.makeRasterN32Premul(w, h)
        surface.canvas.apply {
            clear(Color.WHITE)
            scale(w.toFloat() / width, h.toFloat() / height)
            // Dimensions de l'image tournée (SkEncodedOriginToMatrix), pas celles du fichier.
            concat(origin.toMatrix(width, height))
            val all = Rect.makeWH(codec.width.toFloat(), codec.height.toFloat())
            drawImageRect(Image.makeFromBitmap(pixels), all, all, FilterMipmap(FilterMode.LINEAR, MipmapMode.LINEAR), null, true)
        }
        val image = surface.makeImageSnapshot()
        var jpeg = ByteArray(0)
        for (quality in ImageBudget.QUALITIES) {
            jpeg = checkNotNull(image.encodeToData(EncodedImageFormat.JPEG, quality)) { "encodage JPEG" }.bytes
            if (jpeg.size <= ImageBudget.MAX_BYTES) break
        }
        EncodedImage(ImageBudget.JPEG, w, h, Base64.encode(jpeg))
    }.getOrNull()
}

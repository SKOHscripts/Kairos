package com.skohscripts.kairos.desktop

import java.awt.Color
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO
import kotlin.io.encoding.Base64
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Réduction des images des notes sur le bureau (docs/spec/notes-images.md § Réduction des images, issue #295). */
class DesktopImagesTest {
    private fun png(image: BufferedImage): ByteArray = ByteArrayOutputStream().also { ImageIO.write(image, "png", it) }.toByteArray()

    private fun decoded(data: String): Pair<ByteArray, BufferedImage> {
        val bytes = Base64.decode(data)
        return bytes to ImageIO.read(ByteArrayInputStream(bytes))
    }

    private fun close(expected: Color, actual: Int) {
        val c = Color(actual, true)
        val ok = abs(c.red - expected.red) < 40 && abs(c.green - expected.green) < 40 && abs(c.blue - expected.blue) < 40
        assertTrue(ok, "attendu $expected, lu $c")
    }

    @Test
    fun aLargeImageIsReducedToJpeg() {
        val big = BufferedImage(4_000, 3_000, BufferedImage.TYPE_INT_RGB).apply { createGraphics().apply { color = Color.GRAY; fillRect(0, 0, 4_000, 3_000); dispose() } }
        val encoded = assertNotNull(DesktopImages.encode(png(big)))
        assertEquals("image/jpeg", encoded.mime)
        assertEquals(1_600 to 1_200, encoded.width to encoded.height)
        val (bytes, image) = decoded(encoded.data)
        // JPEG (FF D8), aux dimensions annoncées.
        assertEquals(0xFF.toByte(), bytes[0])
        assertEquals(0xD8.toByte(), bytes[1])
        assertEquals(1_600 to 1_200, image.width to image.height)
    }

    @Test
    fun aPhotoIsTurnedUprightAndLosesItsMetadata() {
        // 40 × 20 stocké, moitié gauche rouge, EXIF « tourner de 90° vers la droite », avec un lieu GPS et un fabricant.
        val source = checkNotNull(javaClass.getResourceAsStream("/images/rotated-exif.jpg")).readBytes()
        assertTrue(String(source, Charsets.ISO_8859_1).contains("Exif"))
        val encoded = assertNotNull(DesktopImages.encode(source))
        assertEquals(20 to 40, encoded.width to encoded.height)
        val (bytes, image) = decoded(encoded.data)
        close(Color.RED, image.getRGB(10, 5))
        close(Color.BLUE, image.getRGB(10, 35))
        val text = String(bytes, Charsets.ISO_8859_1)
        assertFalse(text.contains("Exif"), "métadonnées gardées")
        assertFalse(text.contains("Fabricant"), "métadonnées gardées")
    }

    @Test
    fun aTransparentScreenshotLandsOnWhite() {
        val clear = BufferedImage(30, 30, BufferedImage.TYPE_INT_ARGB).apply {
            createGraphics().apply { color = Color.BLACK; fillRect(10, 10, 10, 10); dispose() }
        }
        val (_, image) = decoded(assertNotNull(DesktopImages.encode(png(clear))).data)
        close(Color.WHITE, image.getRGB(2, 2))
        close(Color.BLACK, image.getRGB(15, 15))
    }

    @Test
    fun somethingElseThanAnImageIsRefused() {
        assertNull(DesktopImages.encode("pas une image".toByteArray()))
        assertNull(DesktopImages.encode(ByteArray(0)))
    }
}

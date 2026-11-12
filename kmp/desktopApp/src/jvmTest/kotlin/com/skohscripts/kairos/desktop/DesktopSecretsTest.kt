package com.skohscripts.kairos.desktop

import com.skohscripts.kairos.ui.app.SecretLevel
import java.io.File
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermissions
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DesktopSecretsTest {
    private val unavailable = SecretProcessRunner { _, _ -> null }
    private fun store(directory: File) = DesktopSecrets(directory, "Linux", unavailable)

    @Test
    fun fileFallbackEncryptsRoundTripsAndDeletesValues() {
        val directory = createTempDirectory("desktop-secrets").toFile()
        try {
            val secrets = store(directory)
            secrets.put("url:42", "https://example.test/private-calendar?token=private")
            secrets.put("remote:42", "calendar")
            assertEquals(SecretLevel.FILE, secrets.level)
            assertEquals("https://example.test/private-calendar?token=private", store(directory).get("url:42"))
            assertEquals(setOf("url:42", "remote:42"), store(directory).keys())
            assertFalse(File(directory, "secrets.bin").readBytes().toString(Charsets.ISO_8859_1).contains("private-calendar"))
            assertEquals(32L, File(directory, "secrets.key").length())
            val key = File(directory, "secrets.key").toPath()
            if (Files.getFileStore(key).supportsFileAttributeView("posix")) {
                assertEquals(PosixFilePermissions.fromString("rw-------"), Files.getPosixFilePermissions(key))
            }
            secrets.delete("url:42")
            assertNull(store(directory).get("url:42"))
            assertEquals(setOf("remote:42"), store(directory).keys())
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun tamperedOrTruncatedEnvelopeIsAbsent() {
        val directory = createTempDirectory("desktop-secrets-tamper").toFile()
        try {
            store(directory).put("url:1", "secret")
            val file = File(directory, "secrets.bin")
            val bytes = file.readBytes()
            bytes[bytes.lastIndex] = (bytes.last().toInt() xor 1).toByte()
            file.writeBytes(bytes)
            assertNull(store(directory).get("url:1"))
            assertTrue(store(directory).keys().isEmpty())
            file.writeBytes(byteArrayOf(1, 2, 3))
            assertNull(store(directory).get("url:1"))
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun missingKeyDoesNotReplaceExistingCiphertext() {
        val directory = createTempDirectory("desktop-secrets-key").toFile()
        try {
            store(directory).put("url:1", "secret")
            val encrypted = File(directory, "secrets.bin").readBytes()
            assertTrue(File(directory, "secrets.key").delete())
            assertNull(store(directory).get("url:1"))
            assertFalse(File(directory, "secrets.key").exists())
            assertTrue(encrypted.contentEquals(File(directory, "secrets.bin").readBytes()))
        } finally {
            directory.deleteRecursively()
        }
    }
}

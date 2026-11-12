package com.skohscripts.kairos.desktop

import com.skohscripts.kairos.ui.app.SecretLevel
import com.skohscripts.kairos.ui.app.SecretStore
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.AclEntry
import java.nio.file.attribute.AclEntryPermission
import java.nio.file.attribute.AclEntryType
import java.nio.file.attribute.AclFileAttributeView
import java.nio.file.attribute.PosixFilePermissions
import java.security.SecureRandom
import java.util.Base64
import java.util.Properties
import java.util.concurrent.TimeUnit
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

internal data class SecretProcessResult(val exitCode: Int, val output: String)
internal fun interface SecretProcessRunner {
    fun run(command: List<String>, input: String): SecretProcessResult?
}

private object SystemSecretProcessRunner : SecretProcessRunner {
    override fun run(command: List<String>, input: String): SecretProcessResult? = runCatching {
        val process = ProcessBuilder(command).redirectError(ProcessBuilder.Redirect.DISCARD).start()
        try {
            process.outputStream.bufferedWriter(Charsets.UTF_8).use { it.write(input) }
            if (!process.waitFor(10, TimeUnit.SECONDS)) return null
            SecretProcessResult(process.exitValue(), process.inputStream.bufferedReader(Charsets.UTF_8).readText().trim())
        } finally {
            process.destroyForcibly()
        }
    }.getOrNull()
}

class DesktopSecrets internal constructor(
    private val directory: File,
    private val osName: String,
    private val runner: SecretProcessRunner,
) : SecretStore {
    constructor() : this(
        DataDirectory.kairosRoot(System.getenv(), System.getProperty("os.name"), System.getProperty("user.home")),
        System.getProperty("os.name"),
        SystemSecretProcessRunner,
    )

    private val file = File(directory, "secrets.bin")
    private val random = SecureRandom()
    private val custody by lazy { selectKey() }
    override val level: SecretLevel get() = custody.first

    @Synchronized
    override fun get(key: String): String? = runCatching { readValues().getProperty(key) }.getOrNull()

    @Synchronized
    override fun keys(): Set<String> = runCatching { readValues().stringPropertyNames().toSet() }.getOrDefault(emptySet())

    @Synchronized
    override fun put(key: String, value: String) {
        val values = readValues()
        values.setProperty(key, value)
        writeValues(values)
    }

    @Synchronized
    override fun delete(key: String) {
        val values = runCatching { readValues() }.getOrNull() ?: return
        if (values.remove(key) != null) writeValues(values)
    }

    private fun readValues(): Properties {
        val values = Properties()
        if (!file.exists()) return values
        if (file.length() > 16 * 1024 * 1024) throw IOException("Invalid secret envelope")
        val key = custody.second ?: throw IOException("Secret key unavailable")
        val envelope = file.readBytes()
        require(envelope.size >= 1 + 12 + 16 && envelope[0] == 1.toByte())
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, envelope.copyOfRange(1, 13)))
        cipher.updateAAD(byteArrayOf(1))
        values.load(ByteArrayInputStream(cipher.doFinal(envelope, 13, envelope.size - 13)))
        return values
    }

    private fun writeValues(values: Properties) {
        val key = custody.second ?: throw IOException("Secret key unavailable")
        val nonce = ByteArray(12).also(random::nextBytes)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, nonce))
        cipher.updateAAD(byteArrayOf(1))
        val plain = ByteArrayOutputStream().also { values.store(it, null) }.toByteArray()
        directory.mkdirs()
        val temporary = Files.createTempFile(directory.toPath(), "secrets-", ".tmp")
        try {
            Files.write(temporary, byteArrayOf(1) + nonce + cipher.doFinal(plain))
            Files.move(temporary, file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } finally {
            Files.deleteIfExists(temporary)
            plain.fill(0)
        }
    }

    private fun selectKey(): Pair<SecretLevel, ByteArray?> {
        directory.mkdirs()
        val fallback = File(directory, "secrets.key")
        if (fallback.exists()) return SecretLevel.FILE to runCatching { fallback.readBytes().takeIf { it.size == 32 } }.getOrNull()
        val os = osName.lowercase()
        val system = runCatching {
            when {
                os.startsWith("windows") -> windowsKey()
                os.startsWith("mac") -> commandKey(
                    listOf("security", "find-generic-password", "-s", "Kairos", "-a", "online-secrets", "-w"),
                ) { encoded -> runner.run(listOf("security", "add-generic-password", "-U", "-s", "Kairos", "-a", "online-secrets", "-w", encoded), "")?.exitCode == 0 }
                else -> commandKey(
                    listOf("secret-tool", "lookup", "application", "Kairos", "purpose", "online-secrets"),
                ) { encoded -> runner.run(listOf("secret-tool", "store", "--label=Kairos", "application", "Kairos", "purpose", "online-secrets"), "$encoded\n")?.exitCode == 0 }
            }
        }.getOrNull()
        if (system != null) return SecretLevel.SYSTEM to system
        // Never replace a lost key: the encrypted values must stay recoverable.
        if (file.exists() || File(directory, "secrets.dpapi").exists()) return SecretLevel.SYSTEM to null
        val key = ByteArray(32).also(random::nextBytes)
        val path = fallback.toPath()
        if (Files.getFileStore(directory.toPath()).supportsFileAttributeView("posix")) {
            Files.createFile(path, PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")))
        } else {
            Files.createFile(path)
            try {
                val acl = Files.getFileAttributeView(path, AclFileAttributeView::class.java)
                    ?: throw IOException("Cannot enforce private secret key permissions")
                acl.acl = listOf(AclEntry.newBuilder().setType(AclEntryType.ALLOW)
                    .setPrincipal(acl.owner).setPermissions(AclEntryPermission.values().toSet()).build())
            } catch (error: Exception) {
                Files.deleteIfExists(path)
                throw error
            }
        }
        Files.write(path, key)
        return SecretLevel.FILE to key
    }

    private fun commandKey(lookup: List<String>, store: (String) -> Boolean): ByteArray? {
        val result = runner.run(lookup, "") ?: return null
        if (result.exitCode == 0 && result.output.isNotBlank()) return decodeKey(result.output)
        if (file.exists()) return null
        val key = ByteArray(32).also(random::nextBytes)
        return key.takeIf { store(Base64.getEncoder().encodeToString(key)) }
    }

    private fun windowsKey(): ByteArray? {
        val protected = File(directory, "secrets.dpapi")
        val decrypt = protected.exists()
        if (!decrypt && file.exists()) return null
        val key = if (decrypt) null else ByteArray(32).also(random::nextBytes)
        val operation = if (decrypt) "Unprotect" else "Protect"
        val script = "Add-Type -AssemblyName System.Security; " +
            "\$b=[Convert]::FromBase64String([Console]::In.ReadToEnd().Trim()); " +
            "\$r=[Security.Cryptography.ProtectedData]::$operation(\$b,\$null,[Security.Cryptography.DataProtectionScope]::CurrentUser); " +
            "[Console]::Out.Write([Convert]::ToBase64String(\$r))"
        val input = if (decrypt) protected.readText(Charsets.US_ASCII) else Base64.getEncoder().encodeToString(key)
        val result = runner.run(listOf("powershell.exe", "-NoProfile", "-NonInteractive", "-Command", script), input)
            ?.takeIf { it.exitCode == 0 } ?: return null
        if (decrypt) return decodeKey(result.output)
        Base64.getDecoder().decode(result.output)
        protected.writeText(result.output, Charsets.US_ASCII)
        return key
    }

    private fun decodeKey(encoded: String): ByteArray? = runCatching {
        Base64.getDecoder().decode(encoded).takeIf { it.size == 32 }
    }.getOrNull()
}

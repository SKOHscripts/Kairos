package com.skohscripts.kairos.desktop

import com.skohscripts.kairos.ui.app.ShortcutService
import com.skohscripts.kairos.ui.app.ShortcutStatus
import com.skohscripts.kairos.ui.app.ShortcutTarget
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.util.Base64
import java.util.concurrent.TimeUnit

/**
 * Raccourci d'une copie portable (docs/spec/raccourci-portable.md) :
 * entrée `.desktop` de l'utilisateur sous Linux, `Kairos.lnk` dans le menu
 * Démarrer et sur le Bureau sous Windows. Ce qui a été créé, et un éventuel
 * « Non merci », vivent dans `shortcuts.json` du dossier de données : le
 * retrait ne supprime que ces fichiers.
 */
class PortableShortcuts(
    private val copy: PortableCopy,
    private val stateFile: File,
    private val env: Map<String, String> = System.getenv(),
    private val home: String = System.getProperty("user.home"),
    /** Exécute un script PowerShell et rend sa sortie (Windows ; remplaçable en test). */
    private val powershell: (String) -> List<String> = ::runPowerShell,
) : ShortcutService {
    @Serializable
    private data class Saved(val launcher: String? = null, val files: List<String> = emptyList(), val declined: Boolean = false)

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private var saved: Saved = runCatching { json.decodeFromString(Saved.serializer(), stateFile.readText()) }.getOrDefault(Saved())

    override val target = if (copy.windows) ShortcutTarget.START_MENU_AND_DESKTOP else ShortcutTarget.APP_MENU
    override val status = MutableStateFlow(statusOf(saved))
    override val declined = MutableStateFlow(saved.declined)

    private fun statusOf(s: Saved): ShortcutStatus = when {
        s.files.none { File(it).exists() } -> ShortcutStatus.MISSING
        s.launcher == copy.launcher.absolutePath -> ShortcutStatus.CREATED
        else -> ShortcutStatus.ELSEWHERE
    }

    override suspend fun create() = withContext(Dispatchers.IO) {
        val files = if (copy.windows) createWindows() else listOf(createLinux())
        // Un raccourci recréé ailleurs (autre nom de fichier) n'a plus lieu d'être.
        (saved.files - files.toSet()).forEach { File(it).delete() }
        save(saved.copy(launcher = copy.launcher.absolutePath, files = files))
    }

    override suspend fun remove() = withContext(Dispatchers.IO) {
        saved.files.forEach { File(it).delete() }
        save(saved.copy(launcher = null, files = emptyList()))
    }

    override fun decline() {
        save(saved.copy(declined = true))
    }

    private fun save(s: Saved) {
        saved = s
        stateFile.parentFile?.mkdirs()
        stateFile.writeText(json.encodeToString(Saved.serializer(), s))
        status.value = statusOf(s)
        declined.value = s.declined
    }

    /** `$XDG_DATA_HOME/applications/<paquet>.desktop`, rendu exécutable. */
    private fun createLinux(): String {
        val dataHome = env["XDG_DATA_HOME"]?.takeIf { it.isNotBlank() } ?: "$home/.local/share"
        val file = File(dataHome, "applications/${copy.packageName}.desktop")
        file.parentFile.mkdirs()
        file.writeText(desktopEntry(copy))
        file.setExecutable(true)
        return file.absolutePath
    }

    /** `Kairos.lnk` dans les vrais dossiers « Programs » et « Desktop » (OneDrive compris), par `WScript.Shell`. */
    private fun createWindows(): List<String> {
        val created = powershell(windowsScript(copy)).map { it.trim() }.filter { it.isNotEmpty() }
        check(created.isNotEmpty()) { "PowerShell" }
        return created
    }

    companion object {
        /** Même texte que la description des paquets (`desktopApp/build.gradle.kts`). */
        const val DESCRIPTION = "Kairos : le bon moment pour chaque tâche"

        fun desktopEntry(copy: PortableCopy): String = buildString {
            appendLine("[Desktop Entry]")
            appendLine("Type=Application")
            appendLine("Name=${copy.appName}")
            appendLine("Comment=$DESCRIPTION")
            appendLine("Exec=${execArgument(copy.launcher.absolutePath)}")
            appendLine("Icon=${copy.icon.absolutePath.replace("\\", "\\\\")}")
            appendLine("Terminal=false")
            appendLine("Categories=Office;")
            appendLine("StartupWMClass=${WindowClass.NAME}")
        }

        /**
         * Un argument de `Exec` (Desktop Entry Specification) : entre
         * guillemets, `"`, `` ` ``, `$` et `\` échappés par `\`, puis la règle
         * des chaînes (chaque `\` doublé), et `%` doublé (codes de champ).
         */
        fun execArgument(path: String): String {
            val quoted = buildString {
                append('"')
                path.forEach { c -> if (c in "\"`$\\") append('\\'); append(c) }
                append('"')
            }
            return quoted.replace("\\", "\\\\").replace("%", "%%")
        }

        /** Chaîne PowerShell entre apostrophes (seule l'apostrophe s'échappe, doublée). */
        private fun ps(value: String) = "'" + value.replace("'", "''") + "'"

        fun windowsScript(copy: PortableCopy): String {
            val exe = copy.launcher.absolutePath
            return """
                ${'$'}ErrorActionPreference = 'Stop'
                [Console]::OutputEncoding = [System.Text.Encoding]::UTF8
                ${'$'}shell = New-Object -ComObject WScript.Shell
                foreach (${'$'}folder in @('Programs', 'Desktop')) {
                    ${'$'}path = Join-Path ${'$'}shell.SpecialFolders.Item(${'$'}folder) ${ps(copy.appName + ".lnk")}
                    ${'$'}link = ${'$'}shell.CreateShortcut(${'$'}path)
                    ${'$'}link.TargetPath = ${ps(exe)}
                    ${'$'}link.WorkingDirectory = ${ps(copy.root.absolutePath)}
                    ${'$'}link.IconLocation = ${ps("$exe,0")}
                    ${'$'}link.Description = ${ps(DESCRIPTION)}
                    ${'$'}link.Save()
                    ${'$'}path
                }
            """.trimIndent()
        }

        /** `powershell.exe -EncodedCommand` (UTF-16LE en base 64) : aucun souci de guillemets ni de profil. */
        private fun runPowerShell(script: String): List<String> {
            val encoded = Base64.getEncoder().encodeToString(script.toByteArray(Charsets.UTF_16LE))
            val process = ProcessBuilder("powershell.exe", "-NoProfile", "-NonInteractive", "-EncodedCommand", encoded)
                .redirectErrorStream(true)
                .start()
            val output = process.inputStream.bufferedReader(Charsets.UTF_8).readLines()
            check(process.waitFor(30, TimeUnit.SECONDS) && process.exitValue() == 0) { output.lastOrNull { it.isNotBlank() } ?: "PowerShell" }
            return output
        }
    }
}

/**
 * Une copie portable lancée par son lanceur jpackage. [detect] rend `null`
 * pour tout le reste : version installée (pas de marqueur), lancement depuis
 * les sources (pas de lanceur), macOS (l'application a déjà son icône).
 */
class PortableCopy(
    val root: File,
    val launcher: File,
    val windows: Boolean,
    val appName: String,
) {
    /** Nom du fichier `.desktop`, celui du paquet Linux. */
    val packageName = appName.lowercase().replace(' ', '-')

    /** Icône que jpackage place dans l'image Linux. */
    val icon = File(root, "lib/$appName.png")

    companion object {
        /** Écrit par `kmp-release.yml` dans les seules archives portables, à côté des JAR. */
        const val MARKER = "kairos-portable"

        fun detect(
            appPath: String? = System.getProperty("jpackage.app-path"),
            osName: String = System.getProperty("os.name"),
            appName: String,
        ): PortableCopy? {
            val launcher = appPath?.takeIf { it.isNotBlank() }?.let(::File) ?: return null
            val os = osName.lowercase()
            val (root, jars) = when {
                os.startsWith("windows") -> launcher.parentFile?.let { it to File(it, "app") }
                os.startsWith("linux") -> launcher.parentFile?.parentFile?.let { it to File(it, "lib/app") }
                else -> null
            } ?: return null
            if (!File(jars, MARKER).isFile) return null
            return PortableCopy(root, launcher, os.startsWith("windows"), appName)
        }
    }
}

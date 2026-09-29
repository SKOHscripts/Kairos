package com.skohscripts.kairos.desktop

import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.ImageComposeScene
import com.skohscripts.kairos.ui.screens.AboutScreen
import com.skohscripts.kairos.ui.settings.SettingsScreen
import kotlinx.coroutines.runBlocking
import com.skohscripts.kairos.ui.theme.KairosTheme
import androidx.compose.ui.unit.Density
import com.skohscripts.kairos.core.AppVersion
import com.skohscripts.kairos.core.KairosBuild
import com.skohscripts.kairos.ui.KairosApp
import com.skohscripts.kairos.ui.Platform
import org.jetbrains.skia.EncodedImageFormat
import java.io.File

/**
 * `--self-test[=dossier]` (docs/spec-v3/distribution.md § Auto-test) : vérifie,
 * SANS fenêtre, que l'application empaquetée démarre réellement : version
 * cohérente, dossier de données inscriptible, et rendu hors écran de
 * l'interface complète (thème, polices, chaînes, icônes) en largeur bureau et
 * en largeur étroite. Code de sortie 0 si tout va bien. Avec un dossier, les
 * rendus y sont écrits en PNG (artefacts de CI).
 */
object SelfTest {
    fun run(args: List<String>, dataDir: File): Int = try {
        val version = requireNotNull(AppVersion.parse(KairosBuild.VERSION_NAME)) { "version invalide" }
        check(version.versionCode == KairosBuild.VERSION_CODE) { "versionCode incohérent" }
        dataDir.mkdirs()
        File(dataDir, "self-test.tmp").apply { writeText("ok") }.delete()
        val output = args.firstOrNull { it.startsWith("--self-test=") }?.substringAfter('=')?.let(::File)
        // Base en mémoire avec les exemples : le rendu montre une vraie journée.
        val services = runBlocking { DesktopServices.open(dataDir, inMemory = true) }
        val app: @Composable () -> Unit = { KairosApp(Platform.DESKTOP) { services } }
        val settings: @Composable () -> Unit = { KairosTheme { Surface { SettingsScreen(services) {} } } }
        val about: @Composable () -> Unit = { KairosTheme { Surface { AboutScreen() } } }
        // Les deux dernières, très hautes, montrent toute la vue Jour (agenda, sections, frise).
        val shots = listOf(
            Shot("wide", 1200, 1000, app),
            Shot("narrow", 420, 1000, app),
            Shot("settings", 900, 1000, settings),
            Shot("about", 900, 1000, about),
            Shot("day-full", 1200, 3000, app),
            Shot("day-full-narrow", 420, 5000, app),
        )
        for ((name, width, height, content) in shots) {
            val png = render(width, height, content)
            output?.let { it.mkdirs(); File(it, "desktop-$name.png").writeBytes(png) }
        }
        println("Kairos ${KairosBuild.VERSION_NAME} : auto-test réussi")
        0
    } catch (e: Throwable) {
        System.err.println("Kairos : auto-test en échec")
        e.printStackTrace()
        1
    }

    private data class Shot(val name: String, val width: Int, val height: Int, val content: @Composable () -> Unit)

    private fun render(width: Int, height: Int, content: @Composable () -> Unit): ByteArray {
        val scene = ImageComposeScene(width, height, Density(1f), content = content)
        try {
            // Plusieurs images : laisse le temps aux ressources (polices, chaînes) de se charger.
            var image = scene.render(0)
            for (frame in 1..10) image = scene.render(frame * 100_000_000L)
            return requireNotNull(image.encodeToData(EncodedImageFormat.PNG)) { "encodage PNG" }.bytes
        } finally {
            scene.close()
        }
    }
}

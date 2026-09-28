package com.skohscripts.kairos.desktop

import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.ImageComposeScene
import com.skohscripts.kairos.ui.screens.AboutScreen
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
        val app: @Composable () -> Unit = { KairosApp(Platform.DESKTOP) }
        val about: @Composable () -> Unit = { KairosTheme { Surface { AboutScreen() } } }
        val shots = listOf(Triple("wide", 1200, app), Triple("narrow", 420, app), Triple("about", 900, about))
        for ((name, width, content) in shots) {
            val png = render(width, 800, content)
            output?.let { it.mkdirs(); File(it, "desktop-$name.png").writeBytes(png) }
        }
        println("Kairos ${KairosBuild.VERSION_NAME} : auto-test réussi")
        0
    } catch (e: Throwable) {
        System.err.println("Kairos : auto-test en échec")
        e.printStackTrace()
        1
    }

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

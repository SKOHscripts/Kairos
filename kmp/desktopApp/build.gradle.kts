import org.jetbrains.compose.desktop.application.dsl.TargetFormat

// desktopApp : application de bureau Windows / Linux / macOS (JVM), installeurs
// jpackage et image portable (docs/spec/distribution.md § Bureau).
plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.compose)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    jvmToolchain(21)
    jvm()
    sourceSets {
        jvmMain.dependencies {
            implementation(project(":ui"))
            implementation(compose.desktop.currentOs)
            implementation(libs.kotlinx.coroutines.swing)
            implementation(libs.sqldelight.sqlite.driver)
        }
        jvmTest.dependencies {
            implementation(kotlin("test"))
            // Tests d'interface (clics, saisie, clavier) sur la vue Jour, sans fenêtre.
            implementation(compose.desktop.uiTestJUnit4)
        }
    }
}

// Une seule app « Kairos » pour la beta et la stable depuis la 4.2.0 (docs/spec/distribution.md § Bureau) : même produit,
// mêmes identifiants. MSI, DMG et .deb n'acceptent que trois nombres, qui doivent croître d'une beta à sa finale :
// X.Y.(Z*100 + s), s = numéro de beta, 99 pour une finale (4.2.0-beta.1 → 4.2.1, 4.2.0 → 4.2.99), la règle
// d'AppVersion.packageVersion (AppVersionTest.desktopPackageVersionsGrowToo).
val versionName = providers.gradleProperty("kairos.versionName").get()
val packageVersionName: String = run {
    val m = requireNotNull(Regex("""(\d+)\.(\d+)\.(\d+)(?:-beta\.(\d+))?""").matchEntire(versionName)) { "kairos.versionName illisible : $versionName" }
    val (major, minor, patch, beta) = m.destructured
    "$major.$minor.${patch.toInt() * 100 + (beta.toIntOrNull() ?: 99)}"
}
val appName = "Kairos"

compose.desktop {
    application {
        mainClass = "com.skohscripts.kairos.desktop.MainKt"
        // Classe de fenêtre X11 fixée (WindowClass.kt, docs/spec/raccourci-portable.md).
        jvmArgs += listOf("--add-opens=java.desktop/sun.awt.X11=ALL-UNNAMED")
        nativeDistributions {
            targetFormats(TargetFormat.Msi, TargetFormat.Deb, TargetFormat.Dmg)
            packageName = appName
            packageVersion = packageVersionName
            description = "Kairos : le bon moment pour chaque tâche"
            vendor = "Corentin Michel"
            copyright = "© 2026 Corentin Michel, AGPL-3.0-or-later"
            licenseFile.set(rootProject.file("../LICENSE"))
            // jdk.localedata : sans lui, le runtime réduit ne connaît que l'anglais et le sélecteur de dates Material 3
            // (absences, prévisions) affichait « Oct 2026 » et « M T W T F S S » dans l'interface française.
            modules("java.instrument", "java.management", "java.net.http", "java.sql", "jdk.localedata", "jdk.unsupported")
            windows {
                iconFile.set(project.file("icons/kairos.ico"))
                // Installation par utilisateur : aucun droit administrateur requis.
                perUserInstall = true
                menu = true
                shortcut = true
                menuGroup = appName
                // Identifiant de mise à niveau FIXE par produit : chaque version
                // remplace la précédente au lieu de s'installer à côté.
                upgradeUuid = "5b0e2d7c-8a41-4f6b-b3c9-1e7d6a2f4c08"
            }
            linux {
                iconFile.set(project.file("icons/kairos.png"))
                packageName = "kairos"
                debMaintainer = "corentin.michel@mailo.com"
                menuGroup = "Office"
                appCategory = "office"
            }
            macOS {
                iconFile.set(project.file("icons/kairos.icns"))
                bundleID = "com.skohscripts.kairos"
                dockName = appName
            }
        }
        buildTypes.release.proguard {
            isEnabled = false
        }
    }
}

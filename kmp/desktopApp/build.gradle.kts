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

// Version des paquets de bureau (docs/spec/distribution.md § Versionnage) :
// MSI et DMG n'acceptent que MAJEUR.MINEUR.CORRECTIF numériques. Une version
// finale X.Y.Z garde son numéro ; une préversion est un produit distinct
// (« Kairos Preview », autre identifiant) numéroté 1.0.<versionCode>, toujours
// croissant d'une préversion à la suivante.
val versionName = providers.gradleProperty("kairos.versionName").get()
val versionCode = providers.gradleProperty("kairos.versionCode").get()
val isPreview = versionName.contains('-')
val appName = if (isPreview) "Kairos Preview" else "Kairos"

compose.desktop {
    application {
        mainClass = "com.skohscripts.kairos.desktop.MainKt"
        jvmArgs += listOf("-Dkairos.preview=$isPreview")
        nativeDistributions {
            targetFormats(TargetFormat.Msi, TargetFormat.Deb, TargetFormat.Dmg)
            packageName = appName
            packageVersion = if (isPreview) "1.0.$versionCode" else versionName
            description = "Kairos : le bon moment pour chaque tâche"
            vendor = "Corentin Michel"
            copyright = "© 2026 Corentin Michel, licence MIT"
            licenseFile.set(rootProject.file("../LICENSE"))
            modules("java.instrument", "java.management", "java.net.http", "java.sql", "jdk.unsupported")
            windows {
                iconFile.set(project.file("icons/kairos.ico"))
                // Installation par utilisateur : aucun droit administrateur requis.
                perUserInstall = true
                menu = true
                shortcut = true
                menuGroup = appName
                // Identifiant de mise à niveau FIXE par produit : chaque version
                // remplace la précédente au lieu de s'installer à côté.
                upgradeUuid = if (isPreview) "9c4f3a1e-6b2d-4e8a-9f57-3d1c2b8a7e60" else "5b0e2d7c-8a41-4f6b-b3c9-1e7d6a2f4c08"
            }
            linux {
                iconFile.set(project.file("icons/kairos.png"))
                packageName = if (isPreview) "kairos-preview" else "kairos"
                debMaintainer = "corentin.michel@mailo.com"
                menuGroup = "Office"
                appCategory = "office"
            }
            macOS {
                iconFile.set(project.file("icons/kairos.icns"))
                bundleID = if (isPreview) "com.skohscripts.kairos.preview" else "com.skohscripts.kairos"
                dockName = appName
            }
        }
        buildTypes.release.proguard {
            isEnabled = false
        }
    }
}

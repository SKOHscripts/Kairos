// Kairos 3 : réécriture Kotlin Multiplatform (voir docs/plan-v3-kotlin.md et
// docs/spec/architecture.md). Projet Gradle autonome, dans le sous-dossier
// kmp/ du dépôt pendant la transition.
rootProject.name = "kairos"

pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}

include(":core", ":data", ":ui", ":androidApp", ":desktopApp", ":webApp")

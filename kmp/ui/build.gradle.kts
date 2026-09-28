// ui : interface Compose Multiplatform commune (thème, navigation, écrans),
// partagée par androidApp, desktopApp et webApp (docs/spec-v3/architecture.md).
plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kmp.library)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.compose)
}

kotlin {
    jvmToolchain(21)
    jvm()
    android {
        namespace = "com.skohscripts.kairos.ui"
        compileSdk = 37
        minSdk = 26
        androidResources { enable = true }
    }
    @OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)
    wasmJs { browser() }

    sourceSets {
        commonMain.dependencies {
            api(project(":data"))
            api(compose.runtime)
            api(compose.foundation)
            api(compose.material3)
            api(compose.ui)
            implementation(compose.components.resources)
            implementation(libs.compose.ui.backhandler)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}

compose.resources {
    publicResClass = false
    packageOfResClass = "com.skohscripts.kairos.ui.generated.resources"
}

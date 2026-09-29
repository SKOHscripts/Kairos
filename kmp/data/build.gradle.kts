// data : base SQLDelight, dépôt, export/import (docs/spec/modele-donnees.md,
// docs/spec/export-import.md). Les pilotes SQLite sont fournis par chaque
// application (Android, JDBC sur le bureau, web worker dans le navigateur).
plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kmp.library)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.sqldelight)
}

kotlin {
    jvmToolchain(21)
    jvm()
    android {
        namespace = "com.skohscripts.kairos.data"
        compileSdk = 37
        minSdk = 26
    }
    @OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)
    wasmJs { browser() }

    sourceSets {
        commonMain.dependencies {
            api(project(":core"))
            api(libs.kotlinx.coroutines.core)
            implementation(libs.sqldelight.async.extensions)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
        }
        jvmTest.dependencies {
            implementation(libs.sqldelight.sqlite.driver)
        }
    }
}

sqldelight {
    databases {
        create("KairosDatabase") {
            packageName.set("com.skohscripts.kairos.data.db")
            // Asynchrone : exigé par le pilote web worker ; les pilotes Android
            // et JDBC s'utilisent aussi en mode asynchrone.
            generateAsync.set(true)
            schemaOutputDirectory.set(file("src/commonMain/sqldelight/databases"))
            verifyMigrations.set(true)
        }
    }
}

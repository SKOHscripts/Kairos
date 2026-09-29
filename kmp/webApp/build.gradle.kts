// webApp : version web (Kotlin/Wasm), pour les postes sans droit d'installation,
// servie par GitHub Pages (docs/spec-v3/distribution.md § Web).
plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.compose)
}

kotlin {
    @OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)
    wasmJs {
        outputModuleName.set("kairos")
        browser {
            commonWebpackConfig {
                outputFileName = "kairos.js"
            }
        }
        binaries.executable()
    }
    sourceSets {
        wasmJsMain.dependencies {
            implementation(project(":ui"))
            implementation(libs.sqldelight.web.worker.driver)
            // sql.js (MIT) : SQLite compilé en Wasm, exécuté par kairos-sqljs.worker.js.
            implementation(npm("sql.js", "1.14.2"))
            implementation(devNpm("copy-webpack-plugin", "12.0.2"))
        }
    }
}

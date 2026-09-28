// core : code métier commun et PUR (docs/spec-v3/architecture.md § Invariants).
// Aucune dépendance d'interface, de base de données, d'horloge ou de réseau.
plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kmp.library)
}

// Version de l'application exposée au code commun (KairosBuild), générée depuis
// gradle.properties : source unique (docs/spec-v3/distribution.md § Versionnage).
val generatedDir = layout.buildDirectory.dir("generated/kairosBuild/commonMain/kotlin")
val generateKairosBuild = tasks.register("generateKairosBuild") {
    val versionName = providers.gradleProperty("kairos.versionName")
    val versionCode = providers.gradleProperty("kairos.versionCode")
    inputs.property("versionName", versionName)
    inputs.property("versionCode", versionCode)
    outputs.dir(generatedDir)
    doLast {
        val file = generatedDir.get().file("com/skohscripts/kairos/core/KairosBuild.kt").asFile
        file.parentFile.mkdirs()
        file.writeText(
            """
            |// Généré par core/build.gradle.kts depuis gradle.properties : ne pas éditer.
            |package com.skohscripts.kairos.core
            |
            |object KairosBuild {
            |    const val VERSION_NAME: String = "${versionName.get()}"
            |    const val VERSION_CODE: Int = ${versionCode.get()}
            |}
            |""".trimMargin(),
        )
    }
}

kotlin {
    jvmToolchain(21)
    jvm()
    android {
        namespace = "com.skohscripts.kairos.core"
        compileSdk = 37
        minSdk = 26
    }
    @OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)
    wasmJs { browser() }

    sourceSets {
        commonMain {
            kotlin.srcDir(generateKairosBuild)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}

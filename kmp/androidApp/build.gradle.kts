// androidApp : l'application Android (docs/spec/distribution.md § Android).
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// Version : gradle.properties, source unique (la CI de release vérifie le tag).
val kairosVersionName = providers.gradleProperty("kairos.versionName").get()
val kairosVersionCode = providers.gradleProperty("kairos.versionCode").get().toInt()
// Une seule app pour la beta et la stable depuis la 4.2.0 (docs/spec/distribution.md § Décisions) : même identifiant et
// même nom ; le versionCode d'une beta passe au-dessus de la finale précédente, si bien qu'elle s'installe par-dessus.

android {
    namespace = "com.skohscripts.kairos.android"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.skohscripts.kairos"
        minSdk = 26
        targetSdk = 36
        versionCode = kairosVersionCode
        versionName = kairosVersionName
        resValue("string", "app_name", "Kairos")
    }

    // Keystore de release fourni par la CI (toujours la même
    // clé, pour que chaque version s'installe par-dessus la précédente). Sans keystore,
    // assembleRelease produit un APK non signé (build local, F-Droid).
    signingConfigs {
        create("release") {
            System.getenv("KAIROS_KEYSTORE_FILE")?.let { path ->
                storeFile = file(path)
                storePassword = System.getenv("KAIROS_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KAIROS_KEY_ALIAS")
                keyPassword = System.getenv("KAIROS_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (System.getenv("KAIROS_KEYSTORE_FILE") != null) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    buildFeatures {
        resValues = true
    }

    // F-Droid et builds reproductibles : pas de bloc de dépendances chiffré par
    // Google dans l'APK (illisible par F-Droid).
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    packaging {
        resources.excludes += listOf("DebugProbesKt.bin", "META-INF/*.version")
    }
}

dependencies {
    implementation(project(":ui"))
    implementation(libs.androidx.activity.compose)
    implementation(libs.sqldelight.android.driver)
}

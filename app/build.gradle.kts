import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

// LibreHU: versionCode = number of commits (LibreHU OTA compares it), versionName = upstream version + git describe.
// Through providers.exec: the configuration cache forbids starting processes directly while configuring.
fun git(vararg args: String): String? =
    runCatching {
        providers
            .exec {
                commandLine("git", *args)
                workingDir = rootDir
                isIgnoreExitValue = true
            }.standardOutput.asText
            .get()
            .trim()
            .ifEmpty { null }
    }.getOrNull()

val localProps =
    Properties().apply {
        val f = rootProject.file("local.properties")
        if (f.exists()) f.inputStream().use(::load)
    }

android {
    namespace = "com.llsl.viper4android"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.llsl.viper4android"
        minSdk = 28
        targetSdk = 37
        versionCode = git("rev-list", "--count", "HEAD")?.toIntOrNull() ?: 1
        versionName = "2.1.0-librehu." + (git("describe", "--tags", "--always", "--dirty", "--exclude", "latest-*") ?: "dev")
    }

    androidResources {
        generateLocaleConfig = true
    }

    val keystoreFile = localProps.getProperty("KEYSTORE_FILE", "")
    if (keystoreFile.isNotEmpty()) {
        signingConfigs {
            create("release") {
                storeFile = file(keystoreFile)
                storePassword = localProps.getProperty("KEYSTORE_PASSWORD", "")
                keyAlias = localProps.getProperty("KEY_ALIAS", "")
                keyPassword = localProps.getProperty("KEY_PASSWORD", "")
            }
        }
    }

    buildTypes {
        release {
            signingConfig = if (keystoreFile.isNotEmpty()) signingConfigs.getByName("release") else null
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    // LibreHU: one signing key for every LibreHU app, so that updates (LibreHU OTA) install over each other:
    // LIBREHU_KEYSTORE* from the CI secrets when set, else keystore/librehu.jks of the repository.
    signingConfigs {
        create("librehu") {
            storeFile = System.getenv("LIBREHU_KEYSTORE")?.let { file(it) } ?: rootProject.file("keystore/librehu.jks")
            storePassword = System.getenv("LIBREHU_KEYSTORE_PASSWORD") ?: "librehu"
            keyAlias = System.getenv("LIBREHU_KEY_ALIAS") ?: "librehu"
            keyPassword = System.getenv("LIBREHU_KEY_PASSWORD") ?: "librehu"
        }
    }

    buildTypes {
        getByName("debug") { signingConfig = signingConfigs.getByName("librehu") }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        // LibreHU-service API (app/src/main/aidl, copied from LibreHU/LibreHU-service).
        aidl = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    val composeBom = platform(libs.compose.bom)
    implementation(composeBom)

    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.core.ktx)
    implementation(libs.activity.compose)

    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.service)

    implementation(libs.navigation.compose)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    implementation(libs.datastore.preferences)

    implementation(libs.coroutines.android)

    implementation(libs.okhttp)
}

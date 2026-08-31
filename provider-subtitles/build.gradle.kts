import java.util.Properties

val localProperties = Properties().apply {
    val localPropertiesFile = rootProject.file("local.properties")
    if (localPropertiesFile.exists()) {
        load(localPropertiesFile.inputStream())
    }
}

fun env(name: String): String? = providers.environmentVariable(name).orNull

// Signing inputs are resolved exactly like the app module: environment
// override first, then local.properties, then the shared Nuvio dev-key
// defaults. local.properties is prepared with NUVIO_RELEASE_STORE_FILE.
val releaseStoreFilePath = env("NUVIO_RELEASE_STORE_FILE")
    ?: localProperties.getProperty("NUVIO_RELEASE_STORE_FILE")
val releaseKeyAliasValue = env("NUVIO_RELEASE_KEY_ALIAS")
    ?: localProperties.getProperty("NUVIO_RELEASE_KEY_ALIAS", "nuviotv")
val releaseKeyPasswordValue = env("NUVIO_RELEASE_KEY_PASSWORD")
    ?: localProperties.getProperty("NUVIO_RELEASE_KEY_PASSWORD", "815787")
val releaseStorePasswordValue = env("NUVIO_RELEASE_STORE_PASSWORD")
    ?: localProperties.getProperty("NUVIO_RELEASE_STORE_PASSWORD", "815787")

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.nuvio.tv.provider.subtitles"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.nuvio.tv.provider.subtitles"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0-preview1"
    }

    signingConfigs {
        create("release") {
            keyAlias = releaseKeyAliasValue
            keyPassword = releaseKeyPasswordValue
            storeFile = releaseStoreFilePath?.let(::file) ?: rootProject.file("nuviotv.jks")
            storePassword = releaseStorePasswordValue
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
        }
    }
}

dependencies {
    // Contract shell: zero runtime dependencies (Kotlin stdlib is added by
    // the Kotlin plugin). JUnit is the only test dependency, as in the app.
    testImplementation("junit:junit:4.13.2")
}

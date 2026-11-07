plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

val nexumPreviewBuild = providers.gradleProperty("nexumPreview").orNull == "true"

// Official APK signing credentials come only from the GitHub Actions runner.
// No passwords, key files or Base64 signing material belong in this repository.
val releaseStoreFile = providers.environmentVariable("NEXUM_SIGNING_STORE_FILE").orNull
val releaseStorePassword = providers.environmentVariable("NEXUM_KEYSTORE_PASSWORD").orNull
val releaseKeyAlias = providers.environmentVariable("NEXUM_KEY_ALIAS").orNull
val releaseKeyPassword = providers.environmentVariable("NEXUM_KEY_PASSWORD").orNull
val hasReleaseSigning = listOf(
    releaseStoreFile, releaseStorePassword, releaseKeyAlias, releaseKeyPassword
).all { !it.isNullOrBlank() }

android {
    namespace = "com.example.testario"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.estudario"
        minSdk = 26
        targetSdk = 35
        versionCode = 5
        versionName = "1.0"
        manifestPlaceholders["nexumAppLabel"] = "@string/app_name"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("nexumOfficial") {
            if (hasReleaseSigning) {
                storeFile = file(requireNotNull(releaseStoreFile))
                storePassword = requireNotNull(releaseStorePassword)
                keyAlias = requireNotNull(releaseKeyAlias)
                keyPassword = requireNotNull(releaseKeyPassword)
            }
        }
    }

    // Preview builds remain separate; production keeps its original package ID.
    // Never attach a signing config with blank credentials to the release build.
    buildTypes {
        getByName("release") {
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("nexumOfficial")
            }
        }
        getByName("debug") {
            if (nexumPreviewBuild) {
                applicationIdSuffix = ".neon"
                versionNameSuffix = "-neon-preview"
                manifestPlaceholders["nexumAppLabel"] = "NEXUM Neón"
            }
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation(platform("androidx.compose:compose-bom:2025.12.00"))

    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")
    implementation("io.coil-kt:coil-compose:2.5.0")
    implementation("androidx.navigation:navigation-compose:2.8.9")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit.ktx)
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test:core:1.6.1")
}
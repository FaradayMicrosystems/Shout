import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Optional settings go in local.properties (not committed). GitHub builds write it from repository secrets:
//   KEYSTORE_FILE=... KEYSTORE_PASSWORD=... KEY_ALIAS=... KEY_PASSWORD=...  permanent signing key
// This repo is public, so the key is never committed. Without it builds use a throwaway debug key.
val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
fun prop(name: String, default: String = "") = localProps.getProperty(name, default)

// The version this branch builds, Aurora's X.Y.Z rule: X primary releases, Y user-visible changes
// and features, Z minor fixes and looks. main: stable, no letter ("0.1.0"). experimental: the next
// version plus e (experimental), e.g. "0.1.1e". CI reads this line, so keep it as one plain string.
val baseVersionName = "0.1.1"

// GitHub Actions' run number: always rising, across both branches.
val ciBuild: Int? = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull()

android {
    namespace = "app.shout"
    compileSdk = 35

    defaultConfig {
        applicationId = "app.shout"
        minSdk = 26          // Android 8.0+: needed for the low-latency audio mode
        targetSdk = 35
        versionCode = ciBuild ?: 1
        versionName = if (baseVersionName.last() in "abce" && ciBuild != null) "$baseVersionName+$ciBuild" else baseVersionName
    }

    val keystore = prop("KEYSTORE_FILE").takeIf { it.isNotBlank() }?.let { rootProject.file(it) }?.takeIf { it.exists() }
    signingConfigs {
        if (keystore != null) {
            create("shared") {
                storeFile = keystore
                storePassword = prop("KEYSTORE_PASSWORD")
                keyAlias = prop("KEY_ALIAS")
                keyPassword = prop("KEY_PASSWORD")
            }
        }
    }

    buildFeatures { compose = true }

    buildTypes {
        debug {
            if (keystore != null) signingConfig = signingConfigs.getByName("shared")
        }
        release {
            isMinifyEnabled = false
            signingConfig = if (keystore != null) signingConfigs.getByName("shared") else signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation(platform("androidx.compose:compose-bom:2024.10.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
}

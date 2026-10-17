import com.google.gms.googleservices.GoogleServicesPlugin.MissingGoogleServicesStrategy
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.google.services)
}

// Release signing: keystore.properties at the repo root (git-ignored) names the keystore and its
// passwords. Without it a release build compiles but comes out unsigned, so it can't be installed.
val keystoreProperties: Properties? = rootProject.file("keystore.properties").takeIf { it.exists() }?.let { file ->
    Properties().also { props -> file.inputStream().use(props::load) }
}

android {
    namespace = "com.example.expensify"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        // The id the app is registered under in Firebase (project expensify-13477) and will ship under
        // on Play. The Kotlin packages keep the namespace above; renaming them is a separate change.
        applicationId = "com.sandipshakya.expensify"
        // java.time without desugaring; Android 8.0 and up.
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        keystoreProperties?.let { props ->
            create("release") {
                storeFile = rootProject.file(props.getProperty("storeFile"))
                storePassword = props.getProperty("storePassword")
                keyAlias = props.getProperty("keyAlias")
                keyPassword = props.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            // Installs beside the release app, so a release install never replaces the dev one.
            applicationIdSuffix = ".debug"
            // Debug builds talk to the Firebase emulators started from ../expensify
            // (`npm run emulators`) unless built with -PuseEmulators=false.
            buildConfigField(
                "boolean",
                "USE_EMULATORS",
                (project.findProperty("useEmulators") ?: "true").toString(),
            )
        }
        release {
            buildConfigField("boolean", "USE_EMULATORS", "false")
            // R8 shrinks and obfuscates; project rules live in src/main/keepRules (the AGP 9 location).
            optimization {
                enable = true
            }
            signingConfig = signingConfigs.findByName("release")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

// Only the release build has a google-services.json (app/src/release/, git-ignored, from
// `firebase apps:sdkconfig ANDROID`). Debug initialises Firebase by hand for the emulators' demo
// project (ExpensifyApp), so the file is expected to be missing there.
googleServices {
    missingGoogleServicesStrategy = MissingGoogleServicesStrategy.IGNORE
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.androidx.biometric)
    implementation(libs.androidx.fragment.ktx)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)
    implementation(libs.androidx.work.runtime)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)
    implementation(libs.kotlinx.coroutines.play.services)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
    testImplementation(libs.truth)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}

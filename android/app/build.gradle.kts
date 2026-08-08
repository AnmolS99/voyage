plugins {
    alias(libs.plugins.android.application)
    // Kotlin itself comes from AGP's built-in Kotlin support (AGP 9+); only the
    // Compose compiler plugin still has to be applied explicitly.
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.anmol.voyage"
    // API 37.1 (Android 17) is the latest stable platform; current AndroidX
    // releases require compiling against 37 or later.
    compileSdk = 37
    compileSdkMinor = 1

    defaultConfig {
        // Permanent Play Console identifier — matches the iOS bundle id.
        applicationId = "com.anmol.voyage"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        // Aligned with the iOS MARKETING_VERSION at release time (Phase 11);
        // pre-parity scaffold builds stay on 0.x.
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    buildFeatures {
        compose = true
    }

    sourceSets {
        // world.geojson and country_highlights.json are shared with iOS and are
        // read from shared/data in place — never copied into android/.
        getByName("main").assets.srcDirs("../../shared/data")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        // Unit tests here cover pure logic, but the app state they exercise sits
        // one call away from android.jar stubs (android.util.Log on its failure
        // path). Returning defaults keeps those from throwing, rather than
        // pulling in Robolectric for the sake of a log line.
        unitTests.isReturnDefaultValues = true
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.datastore)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.core)

    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)

    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}

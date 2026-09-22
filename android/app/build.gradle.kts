import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    // Kotlin itself comes from AGP's built-in Kotlin support (AGP 9+); only the
    // Compose compiler plugin still has to be applied explicitly.
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlin.serialization)
}

/**
 * Upload-key credentials for signed release builds, kept out of the repo — see
 * docs/ANDROID_DEVELOPMENT.md. Absent on a fresh clone and in CI, where release
 * builds stay unsigned rather than failing.
 */
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) keystorePropertiesFile.inputStream().use(::load)
}

fun keystoreProperty(name: String): String =
    checkNotNull(keystoreProperties.getProperty(name)?.takeIf { it.isNotBlank() }) {
        "keystore.properties has no `$name` — copy keystore.properties.example and fill it in"
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

    signingConfigs {
        if (keystorePropertiesFile.exists()) {
            create("release") {
                storeFile = file(
                    // A leading ~ is what anyone naturally writes for a key kept
                    // in their home directory, where it belongs; the JVM does
                    // not expand it.
                    keystoreProperty("storeFile")
                        .replaceFirst(Regex("^~"), System.getProperty("user.home")),
                )
                storePassword = keystoreProperty("storePassword")
                keyAlias = keystoreProperty("keyAlias")
                keyPassword = keystoreProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            // Null until keystore.properties exists, which leaves the bundle
            // unsigned — what the build produced before signing existed, rather
            // than a failure for everyone without the key.
            signingConfig = signingConfigs.findByName("release")
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
        // Settings shows the version, as iOS's does.
        buildConfig = true
    }

    sourceSets {
        // country_highlights.json and the Earth textures are shared with iOS and
        // are read from shared/data in place — never copied into android/.
        getByName("main").assets.srcDirs("../../shared/data")
    }

    androidResources {
        // The app reads countries.bin, which the build writes from world.geojson
        // (GenerateWorldCache, below), so the GeoJSON itself does not ship.
        ignoreAssetsPatterns += "!world.geojson"
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

/**
 * Writes countries.bin and world_meshes.bin from world.geojson by running
 * tools/world-cache — the app's own parser and triangulator, compiled for the
 * build machine. The app loads them instead of doing that work on the device,
 * where it cost ~1.85 s on a Galaxy A55 (Phase 7.8 in docs/ANDROID_PLAN.md).
 */
abstract class GenerateWorldCache : JavaExec() {

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val geoJson: RegularFileProperty

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    override fun exec() {
        val output = outputDirectory.get().asFile
        // Only what this run writes — never a file an older generator left behind.
        output.deleteRecursively()
        setArgs(listOf(geoJson.get().asFile.path, output.path))
        super.exec()
    }
}

/** tools/world-cache and its runtime dependencies, for GenerateWorldCache to run. */
val worldCacheTool: Configuration by configurations.creating {
    isCanBeConsumed = false
    attributes {
        attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage.JAVA_RUNTIME))
        attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category.LIBRARY))
        attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, objects.named(LibraryElements.JAR))
    }
}

androidComponents {
    onVariants { variant ->
        val generate = tasks.register<GenerateWorldCache>(
            "generate${variant.name.replaceFirstChar { it.uppercase() }}WorldCache",
        ) {
            classpath = worldCacheTool
            mainClass = "com.anmol.voyage.tools.WorldCacheGeneratorKt"
            geoJson = layout.projectDirectory.file("../../shared/data/world.geojson")
        }
        // AGP chooses the directory, and packages whatever lands in it as assets.
        variant.sources.assets?.addGeneratedSourceDirectory(generate, GenerateWorldCache::outputDirectory)
    }
}

dependencies {
    worldCacheTool(project(":world-cache"))

    implementation(platform(libs.androidx.compose.bom))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.datastore)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.filament.android)
    implementation(libs.filamat.android)

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

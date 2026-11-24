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
        // The Play internal testing workflow passes the next free code as
        // -Pvoyage.versionCode (android/fastlane/Fastfile); local builds keep this.
        versionCode = providers.gradleProperty("voyage.versionCode").orNull?.toInt() ?: 2
        // Aligned with the iOS MARKETING_VERSION at release time;
        // pre-parity scaffold builds stay on 0.x.
        versionName = "1.13.0"
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
        // country_highlights.json, the fonts and the journey art are shared with
        // iOS and are read from shared/data in place — never copied into android/.
        getByName("main").assets.srcDirs("../../shared/data")
    }

    androidResources {
        // The app reads countries.bin, which the build writes from world.geojson
        // (GenerateAssets, below), so the GeoJSON itself does not ship.
        ignoreAssetsPatterns += "!world.geojson"
        // Read only by scripts/generate_neighbors.py; the app reads its output.
        ignoreAssetsPatterns += "!neighbor_links.json"
        // The app packages 4096 px copies of these instead (EarthTextureGenerator).
        ignoreAssetsPatterns += "!<dir>textures"
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

/** Writes assets from shared/data by running a program in tools/world-cache. */
abstract class GenerateAssets : JavaExec() {

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val source: ConfigurableFileCollection

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    override fun exec() {
        val output = outputDirectory.get().asFile
        // Only what this run writes — never a file an older generator left behind.
        output.deleteRecursively()
        setArgs(listOf(source.singleFile.path, output.path))
        super.exec()
    }
}

abstract class CompileMaterials : DefaultTask() {

    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val sources: DirectoryProperty

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val compiler: ConfigurableFileCollection

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @get:Inject
    abstract val execOperations: ExecOperations

    @TaskAction
    fun compile() {
        val output = outputDirectory.get().asFile
        output.deleteRecursively()
        val compiled = File(output, "materials").apply { mkdirs() }
        // Maven artifacts carry no executable bit.
        val matc = compiler.singleFile.copyTo(File(temporaryDir, compiler.singleFile.name), overwrite = true)
        matc.setExecutable(true)
        sources.asFileTree.matching { include("*.mat") }.forEach { material ->
            execOperations.exec {
                commandLine(
                    matc.path,
                    "--platform", "mobile",
                    "--api", "opengl",
                    "--output", File(compiled, "${material.nameWithoutExtension}.filamat").path,
                    material.path,
                )
            }
        }
    }
}

// A material only loads in the Filament release whose matc compiled it.
val matc: Configuration by configurations.creating {
    isCanBeConsumed = false
    isTransitive = false
    defaultDependencies {
        val os = System.getProperty("os.name").lowercase()
        val arm = System.getProperty("os.arch") in setOf("aarch64", "arm64")
        val platform = when {
            os.contains("mac") && arm -> "osx-aarch_64"
            os.contains("linux") && !arm -> "linux-x86_64"
            os.contains("windows") && !arm -> "windows-x86_64"
            else -> throw GradleException(
                "Filament publishes no matc for ${System.getProperty("os.name")} " +
                    "${System.getProperty("os.arch")}; build on Apple silicon, x86-64 Linux or x86-64 Windows",
            )
        }
        add(project.dependencies.create("com.google.android.filament:matc:${libs.versions.filament.get()}:$platform@exe"))
    }
}

/** tools/world-cache and its runtime dependencies, for GenerateAssets to run. */
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
        val shared = layout.projectDirectory.dir("../../shared/data")
        fun generate(name: String, main: String, from: FileSystemLocation) {
            val task = tasks.register<GenerateAssets>(
                "generate${variant.name.replaceFirstChar { it.uppercase() }}$name",
            ) {
                classpath = worldCacheTool
                mainClass = "com.anmol.voyage.tools.$main"
                source.from(from)
            }
            // AGP chooses the directory, and packages whatever lands in it as assets.
            variant.sources.assets?.addGeneratedSourceDirectory(task, GenerateAssets::outputDirectory)
        }
        generate("WorldCache", "WorldCacheGeneratorKt", shared.file("world.geojson"))
        generate("EarthTextures", "EarthTextureGenerator", shared.dir("textures"))

        val compileMaterials = tasks.register<CompileMaterials>(
            "compile${variant.name.replaceFirstChar { it.uppercase() }}Materials",
        ) {
            sources = layout.projectDirectory.dir("src/main/materials")
            compiler.from(matc)
        }
        variant.sources.assets?.addGeneratedSourceDirectory(compileMaterials, CompileMaterials::outputDirectory)
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

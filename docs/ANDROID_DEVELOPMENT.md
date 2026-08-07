# Android Development

Everything needed to build, run, and test the Android app. This is the Android
counterpart of the iOS instructions in [CLAUDE.md](../CLAUDE.md); the phased port
plan lives in [ANDROID_PLAN.md](ANDROID_PLAN.md).

**All commands below run from the `android/` directory.**

## Prerequisites

| Tool | Version | Notes |
| --- | --- | --- |
| JDK | 17 | `brew install openjdk@17`. Android Studio's embedded JBR is newer and fine for the IDE, but CLI builds use 17. |
| Android SDK | platform `android-37.1`, build-tools `37.0.0` | Current AndroidX requires compiling against API 37+. |
| Gradle | wrapper (9.6.1) | Always use `./gradlew`; a system Gradle is only needed to regenerate the wrapper. |
| Emulator | any API 26+ image | `voyage_pixel9_api36` (Pixel 9, Android 16) is the local AVD. |

Environment (already exported in `~/.zshrc` on this machine — note that
non-interactive shells may not pick it up):

```bash
export JAVA_HOME="/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home"
export ANDROID_HOME="$HOME/Library/Android/sdk"
export PATH="$ANDROID_HOME/platform-tools:$ANDROID_HOME/emulator:$PATH"
```

Install the SDK packages the build needs:

```bash
sdkmanager "platforms;android-37.1" "build-tools;37.0.0"
```

## Build, run & test

```bash
cd android

# Build a debug APK
./gradlew assembleDebug

# Install + launch on a running emulator or device
./gradlew installDebug
adb shell am start -n com.anmol.voyage/.MainActivity

# Unit tests (JVM)
./gradlew testDebugUnitTest

# Instrumented tests (needs a running device/emulator)
./gradlew connectedDebugAndroidTest

# Android lint
./gradlew lintDebug

# What CI runs
./gradlew assembleDebug testDebugUnitTest lintDebug
```

Start the emulator headlessly if it isn't already running:

```bash
emulator -avd voyage_pixel9_api36 &
```

The local AVD is API 36 while `targetSdk` is 37 — that combination is valid and
worth keeping until an API 37 system image is installed, since it also exercises
the app one platform below its target.

## Project layout

```
android/
├── app/
│   ├── build.gradle.kts          # module config: SDKs, applicationId, deps
│   └── src/
│       ├── main/
│       │   ├── kotlin/com/anmol/voyage/
│       │   │   ├── VoyageApplication.kt     # installs + prewarms the country data
│       │   │   ├── MainActivity.kt          # splash + edge-to-edge + Compose entry
│       │   │   ├── VoyageApp.kt             # NavigationBar shell + NavHost
│       │   │   ├── data/                    # models, GeoJSON parser, CountryDataCache
│       │   │   ├── navigation/              # top-level destinations
│       │   │   └── ui/theme/                # ColorPalette.kt, Theme.kt
│       │   ├── res/                         # strings, themes, launcher icon
│       │   └── AndroidManifest.xml
│       └── test/kotlin/…                    # JVM unit tests
├── gradle/libs.versions.toml     # single source for all dependency versions
├── tools/                        # asset generators (launcher icons)
└── gradlew                       # use this, not a system Gradle
```

## Conventions

- **Kotlin sources live in `src/main/kotlin`**, not `src/main/java`.
- **Kotlin comes from AGP's built-in support** (AGP 9+): the
  `org.jetbrains.kotlin.android` plugin is deliberately *not* applied. Only the
  Compose compiler plugin is applied explicitly.
- **Colors live once, in `ui/theme/ColorPalette.kt`**, mirroring the iOS
  `AppColors` values exactly. A color change on one platform must land on the
  other in the same PR — `ColorPaletteTest` pins the values documented in
  CLAUDE.md. Material You dynamic color is opt-in and applies to chrome only;
  country-status colors are semantic and always come from the palette.
- **Shared data is referenced in place** from `shared/data/` via
  `assets.srcDirs` in `app/build.gradle.kts` — never copied into `android/`.
  The app reads it through `AssetManager`; JVM unit tests read the same files
  straight off disk (`SharedFiles`), so they need no emulator or Robolectric.
- **Launcher icon** is generated from the iOS artwork; re-run after changing it:

  ```bash
  python3 android/tools/generate_launcher_icons.py
  ```

- **Versioning**: `versionCode` will be auto-incremented by CI (Phase 11);
  `versionName` mirrors the iOS `MARKETING_VERSION` at release time and is
  user-controlled — never bump it unasked.
- **Secrets** (Phase 9): Supabase credentials go in a gitignored
  `android/secrets.properties` surfaced through `BuildConfig` — the Android
  analogue of `ios/Secrets.xcconfig`.

## The shared country fixture

`shared/fixtures/expected_countries.json` is the contract both platforms' GeoJSON
parsers are tested against — country count and order, ISO codes, capitals,
per-ring point counts and bounding boxes. Android asserts it in
`GeoJsonParserTest`, iOS in `GeoJSONFixtureTests`, so a parser change or a data
regeneration that only lands on one platform fails on the other.

It is derived from `world.geojson` by a third implementation of the same rules:

```bash
python3 scripts/generate_country_fixture.py          # rewrite it
python3 scripts/generate_country_fixture.py --check  # what CI runs
```

`scripts/update_geometry.sh` regenerates it automatically; review its diff, then
run both test suites.

## Parse performance

`world.geojson` is 3.2 MB / ~171k coordinates, parsed off the main thread from
`VoyageApplication.onCreate` (`CountryDataCache.prewarm`, which logs its timing
under the `CountryDataCache` tag). Measured on the Pixel 9 emulator, debug build:

| Step | Time |
| --- | --- |
| Reading the asset | ~20 ms |
| First parse (cold ART) | ~620–740 ms |
| Second parse, same process | ~205–225 ms |
| iOS `GeoJSONParser` for comparison (simulator, debug) | ~240 ms |

Steady-state parity with iOS is there; the cold-start premium is ART warming up,
not the parser (AOT-compiling the dex does not move it, and the same code parses
in ~25 ms on a warm host JVM). A baseline profile in Phase 11 is the fix — see
the plan.

## CI

[`.github/workflows/android-ci.yml`](../.github/workflows/android-ci.yml) runs
`assembleDebug testDebugUnitTest lintDebug` on every PR that touches `android/`
or `shared/`, checks the country fixture is current, and uploads the debug APK. Releases are built by CI only (Phase 11)
— never sign and upload locally, same rule as the iOS TestFlight workflow.

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

Environment — these live in `~/.zshenv` on this machine, **not** `~/.zshrc`, so
that non-interactive shells (scripts, cron, editor- and agent-spawned shells) get
them too; zsh reads `.zshrc` for interactive shells only:

```bash
export JAVA_HOME="/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home"
export ANDROID_HOME="$HOME/Library/Android/sdk"
export PATH="$ANDROID_HOME/platform-tools:$ANDROID_HOME/emulator:$PATH"
```

Some tools compose their own `PATH` and will drop that last line while keeping
the two exports. `./gradlew` and `sdkmanager` only need `JAVA_HOME`, so they work
regardless; reach for `"$ANDROID_HOME/platform-tools/adb"` instead of bare `adb`
when a shell might not have the `PATH` entry.

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
│       │   │   ├── data/                    # models, GeoJSON parser, cache, hit testing
│       │   │   ├── navigation/              # top-level destinations
│       │   │   ├── state/                   # VoyageState — shared app state
│       │   │   └── ui/
│       │   │       ├── map/                 # flat map: projection, paths, styles, Canvas
│       │   │       ├── screens/             # placeholders for unbuilt phases
│       │   │       └── theme/               # ColorPalette.kt, Theme.kt
│       │   ├── res/                         # strings, themes, launcher icon
│       │   └── AndroidManifest.xml
│       ├── test/kotlin/…                    # JVM unit tests
│       └── androidTest/kotlin/…             # instrumented tests (need a device)
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
- **The map and the globe must agree.** CLAUDE.md's globe/map consistency rule
  applies across platforms too, so what a country *looks like* is decided outside
  the renderers: `ui/map/CountryStyle.kt` for fills, borders and widths,
  `ui/map/CapitalMarker.kt` for the capital star, `ui/map/MapProjection.kt` for
  the geometry. The Filament globe (Phase 7) reuses them rather than restating
  them, and they are unit-tested without a renderer. `ui/map/WorldMap.kt` is a
  port of `ios/voyage/MapView.swift` — change one, change the other.
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

## What is tested where

JVM unit tests (`src/test`) cover everything that is pure logic — the GeoJSON
parser against the shared fixture, the palette, tap-to-country hit testing, the
map projection, and the country color rules. They read `shared/data` straight off
disk, so they need no device, and they are what CI runs.

Instrumented tests (`src/androidTest`) cover what the JVM cannot: `Path` and
`Canvas` are Android framework classes that are stubbed in unit tests, and a pinch
cannot be injected outside a real input pipeline. `WorldMapGestureTest` drives tap,
pinch-zoom and pan on the real map and asserts through the selection that the draw
transform and its inverse agree. Run it with a device attached:

```bash
./gradlew connectedDebugAndroidTest   # uninstalls the app afterwards
```

Two things worth knowing about that run: it removes the app from the device when
it finishes (reinstall with `./gradlew installDebug`), and it is not part of CI,
which has no emulator — so run it locally after touching the map or the globe.

## CI

[`.github/workflows/android-ci.yml`](../.github/workflows/android-ci.yml) runs
`assembleDebug testDebugUnitTest lintDebug` on every PR that touches `android/`
or `shared/`, checks the country fixture is current, and uploads the debug APK. Releases are built by CI only (Phase 11)
— never sign and upload locally, same rule as the iOS TestFlight workflow.

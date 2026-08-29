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
│       │   │   ├── globe/                   # 3D globe geometry + camera: earcut, triangulation, outlines, orbit/tap math
│       │   │   ├── navigation/              # top-level destinations
│       │   │   ├── state/                   # VoyageState + its persisted document
│       │   │   └── ui/
│       │   │       ├── country/             # selection card, details sheet, search sheet
│       │   │       ├── globe/               # Filament renderer, materials, surface + gestures
│       │   │       ├── home/                # HomeScreen: chrome shared by globe and map
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
  the geometry, and `globe/GlobeCamera.kt` for the globe's projection. The
  Filament globe reuses them rather than restating them, and they are unit-tested
  without a renderer. `ui/home/HomeScreen.kt` holds everything the two renderers
  share — search, selection card, sheets — so only the surface differs.
  `ui/map/WorldMap.kt` is a port of `ios/voyage/MapView.swift` — change one,
  change the other.

  There is no exception left: `ui/globe/GlobeCountryFill.kt` used to state its
  own rules while the globe had no borders to move a status onto, and since the
  outlines landed it delegates to `CountryStyle.kt` and only translates a
  shading into Filament uniforms.
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

## App state and persistence

`state/VoyageState.kt` is the single source of truth — the Android `GlobeState`.
It is an activity-scoped `ViewModel`, created once in `MainActivity` and handed
to every screen; mutate it through its methods (`addVisit`, `toggleCheckedCity`,
`setThemeMode`, …) rather than keeping copies in a screen.

Two halves, on purpose:

- **Persisted** — visited and wishlist countries, checked cities and
  attractions, view mode, globe/map style, theme mode. These live in one
  `PersistedState` snapshot (`state/PersistedState.kt`) and are read through
  `VoyageState`'s properties.
- **Transient** — the current selection. iOS starts every launch with nothing
  selected and so does this.

Every mutation asks for a save; requests are conflated, so a burst of taps costs
one write. Saving goes through `StateStore` (`state/StateStore.kt`):
`DataStoreStateStore` is the real one — a single JSON document in Jetpack
DataStore at `files/datastore/voyage_state.json` — and `InMemoryStateStore` is
the default, which keeps tests and previews away from real files, the role
`inMemory: true` plays for the iOS `GlobeState`.

The document is **versioned** (`PersistedState.CURRENT_VERSION`). Every read
goes through `migrated()`, which currently applies the same renamed-country
table iOS uses (`Turkey` → `Türkiye`, `Cape Verde` → `Cabo Verde`) — saved data
is keyed by country name, so a rename in `world.geojson` would otherwise orphan
it. When the shape changes: bump the version, teach `migrated()` the new step,
and add a case to `PersistedStateTest`. Fields are all defaulted and unknown
ones are ignored, so documents written by older *and* newer builds still read.

`MainActivity` holds the splash screen until `VoyageState.isLoaded` — the saved
theme decides the color scheme, and drawing before it lands flashes the wrong
one.

State is backed up: `res/xml/backup_rules.xml` (API ≤ 30) and
`res/xml/data_extraction_rules.xml` (31+) include `files/datastore/` and nothing
else. To check a round trip on a device:

```bash
adb shell bmgr backupnow com.anmol.voyage
adb uninstall com.anmol.voyage
./gradlew installDebug     # marks should come back with the restore
```

## Reaching a country

Three surfaces in `ui/country/`, all reading and writing the one `VoyageState`:

- **`CountrySelectionCard`** — the inline summary over the map (flag, name,
  capital, visited/wishlist chips, "Details"). It is a card rather than a bottom
  sheet on purpose: a modal sheet scrims the map and hides what selecting a
  country changes there, the thicker status-colored border and the capital star.
  It mirrors the iOS `HomeView` bottom panel.
- **`CountryDetailSheet`** — the Material 3 modal bottom sheet behind "Details":
  the same header plus the two highlights checklists, ticking straight through
  to `VoyageState` so a tick is saved as it is made. Mirrors iOS
  `CountryExploreView`, capital badge included.
- **`CountrySearchSheet`** — find a country by name, reached from the search
  button over the map. Mirrors iOS `CountryListView`, per-row toggles included.

The pieces that are not composables are unit-tested: `data/CountryDetail.kt`
joins a country to its highlights (keyed by ISO code, so a display-name change
cannot orphan them) and `data/CountrySearchIndex.kt` owns matching and ordering
— accents folded via NFD, prefix matches ranked first. `data/FlagEmoji.kt` is
the port of the iOS `flagEmojiFromCode`; nothing stores flags, they are computed
from the ISO code that `world.geojson` already carries.

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
map projection, the country color rules, app state with its saved document, the
country-details data (flag emoji, search matching and ordering, the
country ⇄ highlights join), and the globe geometry pipeline: the earcut port,
sphere projection and tap-ray math, and `GlobeGeometryWorldTest`, which
triangulates every country in `shared/data/world.geojson` and asserts none of
them need the grid-fill fallback, and the globe's orbit camera — whose
`latLonAt` inverse has to agree with the position the renderer places the camera
at, or taps land on the wrong country.
They read `shared/data` straight off disk, so they need no device, and they are
what CI runs. Gestures are the exception and live in `app/src/androidTest/`
(`WorldMapGestureTest`, `GlobeGestureTest`), because a pinch or a scroll wheel
cannot be injected from a JVM test — run them with
`./gradlew connectedDebugAndroidTest` against a booted emulator.

To pinch-zoom in the emulator by hand, hold **⌘** (Ctrl on Windows/Linux) and
drag — two pointer dots appear. The mouse wheel zooms without any modifier.

**The globe renders into a `TextureView`.** Not a `SurfaceView`, which is a
separate window layer and shows black until its first buffer lands — visible on
every tab switch, because Compose navigation builds a new one each time. Timing
logs will not show this: Filament's first frame is fast, and the black belongs
to the window, not the renderer.

**Anything derived from `shared/data` belongs in a process-wide cache, not in a
composable.** `CountryDataCache` holds the parsed countries and
`GlobeGeometryCache` the triangulated globe; both are prewarmed off the main
thread from `VoyageApplication.onCreate`. Held in composition instead, they are
rebuilt on every navigation away and back — which is a pure performance bug, so
nothing looks wrong and only a stopwatch catches it. `VoyageStateTest` substitutes an `InMemoryStateStore` and an
unconfined coroutine scope, so loading and saving run inline on the test thread
and assertions can read the store immediately after a mutation.

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

# CLAUDE.md

voyage: an interactive 3D globe for tracking countries you've visited, as
native iOS (SwiftUI + SceneKit) and Android (Kotlin/Compose + Filament) apps.

## Editing this file

This file is loaded into every session, so keep it short. Add something only
when it is **strictly necessary**: a command, a rule, or a trap that can't be
learned by reading the code and that would cause a real mistake if missed.
Feature descriptions, file tours and design rationale belong in code comments,
tests or `docs/`, not here.

## Layout

```
ios/       Xcode project (voyage/, voyageTests/, GlobeCacheGenerator/, fastlane/)
android/   Gradle project (app/, tools/world-cache)
shared/    data/ (geojson, highlights, aliases, textures, fonts, journey/),
           fixtures/expected_countries.json, supabase/
web/       Marketing site + /privacy (see web/README.md)
scripts/   Data generation scripts
docs/      ANDROID_DEVELOPMENT.md, DATA.md
```

`shared/data/` is used in place by both apps — edit it there, never copy it per
platform.

## Commands

iOS, from `ios/`:

```bash
xcodebuild -scheme voyage -destination 'platform=iOS Simulator,name=iPhone 17 Pro,OS=26.2' build
xcodebuild -scheme voyage -destination 'platform=iOS Simulator,name=iPhone 17 Pro,OS=26.2' test
xcodebuild -scheme voyage -destination 'platform=iOS Simulator,name=iPhone 17 Pro,OS=26.2' test -only-testing:voyageTests/<Class>[/<method>]
```

Build and run the simulator after larger changes. Run
`voyageTests/testGlobeAndMapCountryConsistency` after touching either renderer.
Setup: `cp ios/Secrets.xcconfig.example ios/Secrets.xcconfig` and fill in the
Supabase URL and key.

Android, from `android/`: see [docs/ANDROID_DEVELOPMENT.md](docs/ANDROID_DEVELOPMENT.md).

Supabase pgTAP tests, from the repo root (`supabase-test.yml` runs them on PRs):

```bash
supabase db start --workdir shared && supabase test db --workdir shared
```

Every new table enables RLS in the migration that creates it, and client roles
get only the grants it needs. **Never `supabase db push` locally**: migrations
reach production through `supabase-deploy.yml` (dry run first).

**Never sign, archive or upload locally.** Builds and store listings go through
GitHub Actions only: `testflight.yml`, `play-internal.yml` (Android has no PR
CI — this runs its tests and lint), `store-listing.yml`. Use the matching skills.

## Git

Conventional commits (`feat|fix|docs|style|refactor|test|chore: …`) and branches
(`<type>/<short-description>`).

## Two-platform rule

The two apps behave the same, and both sides pin it with matching
tests. **A change to any shared behavior lands on both platforms in the same
PR**, including colors, country styling and selection priority, globe camera,
spin/inertia and flight timing, marker sizes, outline sector grid and zoom
limits, achievements and medal tiers, challenge rules and timings, search
ranking, theme/Daylight rules, the country dock and list filters, and the
Journey header layout. When you change one side, find the port on the other
(usually the same type name) and its test.

- Colors live only in `ios/voyage/ColorPalette.swift` and
  `android/.../ui/theme/ColorPalette.kt`. Never hardcode them elsewhere.
- Fonts: Newsreader for titles, country names and prompts. Schibsted Grotesk
  for everything else. Never put numbers or buttons in Newsreader.

## Globe and map consistency

Globe and map must look and behave the same (colors, priority, borders, capital
markers, gestures), on both platforms, so that's four renderers. Fill and border
rules are written once in `CountryStyle` (iOS `CountryStyle.swift`, Android
`ui/map/CountryStyle.kt`). Anything that changes how a country looks has to go
through `CountryStyle`, because the iOS globe restyles only the countries whose
style changed.

Home keeps both renderers mounted and crossfades between them. Never swap them
with an `if`.

## Traps

- **iOS state:** `GlobeState` (in `ContentView.swift`) is the single source of
  truth. Mutate it through its methods. For synced sets, iCloud wins (except on
  a device's first sync), and each mutation persists only the keys it changed
  (`resolveSynced`, `GlobeStateSyncTests`).
- **iOS data:** go through `CountryDataCache.shared`, never re-parse the GeoJSON.
- **iOS textures:** never load Earth textures with `UIImage(named:)`. See
  `EarthTexture.swift`.
- **Android Filament:** materials are unlit and post-processing is off. That is
  what puts exact palette colors on screen, so enabling either shifts every
  color. Earth textures are `RGBA8`, not sRGB. There is no skybox (the backdrop
  is Compose).
- **Android engine:** one Filament engine per Activity, owned by `HomeScreen`
  and composed *under* the `NavHost`, hidden rather than removed. Don't move
  Home back into the `NavHost`. Challenges reuse Home's globe through
  `WorldScene`, never a second one.
- **Android markers** are meshes in the globe scene, not a Compose overlay,
  because an overlay lags a frame behind.
- **Android parses at build time** (`tools/world-cache`), so generated
  `countries.bin` and `world_meshes.bin` are never checked in.

## Data changes

- After changing `world.geojson`, regenerate the fixture and review its diff
  (an unexpected change there is an unexpected render change), then regenerate
  `ios/voyage/globe.scn`:
  ```bash
  python3 scripts/generate_country_fixture.py          # --check is what CI runs
  ```
  ```bash
  cd ios && xcodebuild -scheme GlobeCacheGenerator -destination 'platform=macOS' build && ./DerivedData/voyage/Build/Products/Debug/GlobeCacheGenerator
  ```
- Geometry comes from `./scripts/update_geometry.sh`. See
  [docs/DATA.md](docs/DATA.md) for provenance (dissolves, Crimea as UA,
  microstates).
- A country's extra capitals go in `otherCapitals`. The apps treat every
  capital alike, so adding one is a data-only change.
- Journey art: `scripts/journey_art.py` (its docstring) and the `journey-header`
  skill. Change the look in its shared prompts, not per country.

# Voyage — Android Port Plan

A step-by-step plan for building and releasing the Android version of Voyage.
Work through the phases **in order, one at a time**. Each phase has a
*Definition of done* — don't start the next phase until it's met. Check off
items (`- [x]`) as they land, and keep this document updated when decisions
change.

## Guiding principles

- **Native feel on every platform.** Where iOS uses SwiftUI/SceneKit idioms,
  Android uses Jetpack Compose + Material 3 idioms (dynamic color, predictive
  back, edge-to-edge, Material motion). Feature parity, not pixel parity.
- **One source of truth for data.** `world.geojson`, `country_highlights.json`,
  and the Supabase schema are shared assets consumed by both apps — never
  forked per platform.
- **The iOS app stays green.** Every phase that touches shared files or repo
  layout must end with the iOS build, tests, and TestFlight workflow passing.
- **Same rendering invariants.** The globe/map consistency rule from the iOS
  app applies on Android too: globe view and map view must look and behave
  identically except for projection.

## Decision log

| Date | Decision | Rationale |
| --- | --- | --- |
| 2026-08-06 | Fully native Kotlin + Jetpack Compose (no KMP) | Zero risk to the finished iOS app; best native UX. Cost: logic ported by hand, guarded by shared test fixtures. |
| 2026-08-06 | Monorepo: `ios/` + `android/` + `shared/` | Single source of truth for geo data, Supabase schema, docs, and this plan. |
| 2026-08-06 | 3D globe rendered with **Filament** (google/filament) | Google's production 3D engine; closest Android analogue to SceneKit. Custom geometry built from the same triangulation pipeline as iOS. |
| 2026-08-06 | Persistence: Jetpack DataStore + Android Auto Backup, local-only | No account system for v1. Cross-platform sync (Supabase auth) is explicitly out of scope; revisit after launch. |
| 2026-08-06 | Google Play account: **not yet registered** | New personal accounts must run a closed test with ≥12 testers for 14 days before production access — register early (Phase 2). |
| 2026-08-06 | minSdk 26, targetSdk = latest stable | Filament and Compose are comfortable at 26; dynamic color (31+) degrades gracefully. |

---

## Phase 0 — Environment & tooling

Get an Android development environment working on this machine.

- [ ] Install Android Studio (latest stable) + Android SDK, platform tools
- [ ] Install JDK 17 (Temurin via Homebrew, or use Android Studio's embedded JDK)
- [ ] Create an emulator (Pixel-class device, latest stable API image) and verify it boots
- [ ] Verify `gradle`/`adb` work from the terminal (needed for CI-parity local builds)

**Definition of done:** a "Hello World" Compose template project builds and
runs on the emulator from both Android Studio and the command line.

---

## Phase 1 — Repo restructure

Reshape the repo into a multi-platform monorepo **without changing any iOS
behavior**. This is the riskiest "boring" phase — do it as one focused PR.

Target layout:

```
voyage/
├── ios/                      # everything currently at root that is iOS-specific
│   ├── voyage/               # app sources (minus shared data files)
│   ├── voyage.xcodeproj/
│   ├── voyageTests/
│   ├── GlobeCacheGenerator/
│   ├── fastlane/
│   ├── Gemfile
│   └── Secrets.xcconfig(.example)
├── android/                  # created in Phase 2
├── shared/
│   ├── data/                 # world.geojson, country_highlights.json
│   ├── supabase/             # schemas, migrations, seed.sql
│   └── fixtures/             # cross-platform test fixtures (Phase 3)
├── scripts/                  # update_geometry.sh, merge_geometry.py (now write to shared/data)
├── docs/                     # this plan + platform docs
├── .github/workflows/
├── CLAUDE.md
└── README.md
```

Steps:

- [ ] Move iOS project into `ios/` (use `git mv` to preserve history)
- [ ] Move `world.geojson` and `country_highlights.json` to `shared/data/`;
      update the Xcode project to reference them there (folder/file references
      outside the project dir — bundle output must be identical)
- [ ] Move `supabase/` to `shared/supabase/`; update any config paths
- [ ] Update `scripts/update_geometry.sh` + `merge_geometry.py` paths
- [ ] Update `.github/workflows/testflight.yml` working-directory/paths
- [ ] Update GlobeCacheGenerator input/output paths
- [ ] Update CLAUDE.md: new layout, plus a pointer to this plan
- [ ] Full verification: iOS build, full test suite, and a TestFlight workflow
      run (`gh workflow run testflight.yml`) all succeed

**Definition of done:** repo has the new layout, `git log --follow` still
tracks moved files, and a TestFlight build produced from the restructured repo
installs and runs correctly.

---

## Phase 2 — Android project scaffold + Play account registration

Two independent tracks; start the Play account clock ticking now because of
Google's 14-day closed-testing requirement.

**Play account (admin track — can run in parallel with everything below):**

- [ ] Register Google Play developer account ($25 one-time) + identity verification
- [ ] Create the app entry in Play Console (`com.anmol.voyage` or chosen
      applicationId — **this is permanent, decide carefully**)
- [ ] Note the requirement: ≥12 testers opted in for 14 consecutive days of
      closed testing before production access can be requested — recruit
      testers early

**Project scaffold:**

- [ ] Create `android/` Gradle project: Kotlin DSL, version catalog
      (`libs.versions.toml`), single `app` module, Compose + Material 3
- [ ] applicationId matching the Play Console entry; minSdk 26, targetSdk latest
- [ ] App theme: port `AppColors` from `ColorPalette.swift` into a single
      `ColorPalette.kt` (same hex values, one source of truth per platform),
      light + dark schemes, optional Material You dynamic color for chrome
      (never for country-status colors — those are semantic)
- [ ] Four-destination `NavigationBar` shell mirroring the iOS tabs:
      Home / Daily / Achievements / Settings, with placeholder screens
- [ ] Edge-to-edge + predictive back enabled from day one
- [ ] Adaptive app icon + Splash Screen API (reuse iOS icon artwork)
- [ ] `docs/ANDROID_DEVELOPMENT.md`: how to build, run, test (Android's
      counterpart to the iOS instructions in CLAUDE.md); reference it from CLAUDE.md
- [ ] CI: `.github/workflows/android-ci.yml` — assemble debug + run unit tests
      on every PR touching `android/` or `shared/`

**Definition of done:** the empty four-tab app runs on the emulator looking
like a real Material 3 app, and CI builds it on every PR.

---

## Phase 3 — Data layer (models, GeoJSON parsing, shared fixtures)

Port the data foundation before any rendering. This is where hand-ported
logic gets locked down by tests.

- [ ] Port models: `Country`, `Capital`, highlights (cities/attractions),
      continent groupings (`ContinentData`)
- [ ] Port `GeoJSONParser` → Kotlin (kotlinx.serialization streaming or a
      tuned JSON reader — 170k coordinates must parse fast)
- [ ] `CountryDataCache` equivalent: parse once, prewarm off the main thread
      at app start (mirror `voyageApp.init` behavior)
- [ ] Bundle `shared/data/*.{geojson,json}` via Gradle `assets.srcDirs`
      pointing at `../shared/data` — **no file copies**
- [ ] Create `shared/fixtures/expected_countries.json`: canonical country
      count, ISO codes, names, capitals, per-country ring/point counts —
      generated from the current iOS parser output
- [ ] Android unit tests assert parser output matches the fixture exactly
- [ ] iOS: add a test asserting the same fixture (guards both ports against
      drift whenever `world.geojson` is regenerated)

**Definition of done:** both platforms' test suites validate against the same
fixture file; parsing on a mid-range device completes in the same ballpark as
iOS prewarm.

---

## Phase 4 — 2D map view + tap-to-country

Build the flat map **before** the 3D globe: it exercises parsing, colors,
projection, and hit-testing with far less rendering risk, and it ships a
usable "explore" surface early.

- [ ] Compose `Canvas` map with the same projection as iOS `MapView`
- [ ] Country fills using palette colors; borders; capital stars
- [ ] Port `CountryHitTester` (point-in-polygon) for tap-to-select
- [ ] Pan/zoom gestures (`transformable`), selection highlight logic with the
      same priority rules as iOS (visited/wishlist over selection)
- [ ] Microstate dots for the 25 Point-feature countries

**Definition of done:** tapping any country/microstate on the map selects it
correctly (spot-check the same tricky cases iOS handles: enclaves, islands,
microstates), colors match the palette table exactly.

---

## Phase 5 — App state & persistence

- [ ] `VoyageState` (analogue of `GlobeState`): visited/wishlist countries,
      checked cities/attractions, view mode, style prefs, dark mode — single
      source of truth, exposed as `StateFlow` from a ViewModel scoped to the
      activity
- [ ] Persist via Jetpack DataStore; keep the on-disk model versioned so a
      future sync feature can migrate it
- [ ] Enable Auto Backup (`android:allowBackup` + backup rules) so state
      survives device migration
- [ ] Unit tests: mutation methods (`addVisit`, `toggleCheckedCity`, …) mirror
      iOS semantics

**Definition of done:** visited/wishlist selections survive process death and
reinstall-with-backup; state mutations have test coverage.

## Phase 6 — Country details & highlights UI

- [ ] Country detail as a Material 3 bottom sheet (Android-native analogue of
      the iOS panel): flag, name, capital, visited/wishlist toggle
- [ ] Highlights checklists (top cities & attractions) wired to `VoyageState`
- [ ] Search field to find/select a country by name

**Definition of done:** full loop works — find country → open details → mark
visited → map recolors → highlight checkmarks persist.

---

## Phase 7 — 3D globe (Filament)

The flagship feature and the largest phase. Sub-steps are ordered so there's
something on screen early.

- [ ] 7.1 Filament integration: `SurfaceView`/`AndroidUiDispatcher` render
      loop hosted in Compose, camera + lighting rig
- [ ] 7.2 Ocean sphere + atmosphere glow (layer order per iOS: ocean →
      fills → outlines → atmosphere)
- [ ] 7.3 Port `Earcut` (mapbox/earcut port — consider porting the Swift port
      1:1 so both stay diffable) + `PolygonTriangulator`: triangulation in
      lon/lat space, ~2.5° subdivision, `latLonToSphere()`, hole support
- [ ] 7.4 Country fill meshes as Filament renderables; single-sided winding
      (backface culling handles the far hemisphere, as on iOS)
- [ ] 7.5 Border outlines: constant screen-width via a Filament material that
      widens centerline vertices along a miter attribute (same trick as the
      iOS shader modifier); merged sector meshes; measure before porting the
      per-frame horizon culling — Filament may not need it
- [ ] 7.6 Gestures: rotate (trackball feel matching iOS), pinch zoom with the
      same distance clamps, tap → analytic ray/sphere intersection → lat/lon →
      `CountryHitTester` (reuse the fix from iOS PR #50)
- [ ] 7.7 Selected-country overlay outline (thicker, status-colored, raised)
- [ ] 7.8 Startup: build geometry on a background thread; if cold-start is
      worse than iOS, add a binary geometry cache generated at build time
      (Android's `globe.scn` equivalent)
- [ ] 7.9 Globe ⇄ map toggle wired to `VoyageState.viewMode`
- [ ] 7.10 Performance pass on a mid-range device (e.g. Pixel a-series):
      60fps rotation, no jank on selection

**Definition of done:** globe and map pass a side-by-side consistency check
against each other *and* against iOS (colors, selection, borders, stars);
smooth on mid-range hardware.

---

## Phase 8 — Achievements

- [ ] Port achievement definitions + progress model (`Achievement.swift`,
      including Continental Drifter and Wonders logic)
- [ ] Achievements screen: Material card grid with progress indicators
- [ ] Medal detail: full-screen overlay with Y-axis-spinnable medal — use
      Compose `graphicsLayer` rotation (no 3D engine needed; avoids the
      SceneKit cap-texture class of problems entirely)
- [ ] Unit tests: same completion thresholds as iOS (port the
      `AchievementCompletionTests` cases)

**Definition of done:** achievement progress matches iOS for identical
visited-country sets (add fixture-driven test).

## Phase 9 — Daily Challenge

- [ ] Supabase client via **supabase-kt**; credentials injected from a
      gitignored `secrets.properties` → `BuildConfig` (Android's
      `Secrets.xcconfig` analogue; document in ANDROID_DEVELOPMENT.md)
- [ ] Port models (`DailyChallenge`, `QuestionType`, `ChallengeResult`) and
      `ChallengeStore` (DataStore, keyed by date, mid-game persistence)
- [ ] Calendar month grid: available/locked/solved/failed states per iOS flow
- [ ] Play screen: clue (silhouette via Compose Canvas / flag / country name),
      guess field with filtered dropdown, 5 attempts, green/red validation
- [ ] Result view + confetti; ISO-code → country resolution via the data cache
- [ ] Same case-insensitive validation rules as iOS

**Definition of done:** the same date shows the same challenge with the same
accepted answers on both platforms; mid-game state survives leaving the app.

## Phase 10 — Settings & native polish

- [ ] Settings screen: view style prefs, dark mode (system/light/dark)
- [ ] Haptics on key interactions (selection, achievement unlock)
- [ ] Polish pass: Material motion for transitions, themed (monochrome) icon,
      correct behavior across font scales and window sizes (foldables get the
      map/globe full-bleed)
- [ ] Accessibility pass: TalkBack labels for countries/controls, contrast

**Definition of done:** app feels indistinguishable from a first-party
Material app in navigation, motion, and system integration.

---

## Phase 11 — Release infrastructure & launch

Mirror the iOS rule: **releases are built by CI, never locally.**

- [ ] Generate upload keystore; store keystore + passwords in GitHub Actions
      secrets (document recovery: Play App Signing holds the real signing key)
- [ ] Enroll in Play App Signing
- [ ] Play Console service-account JSON for API publishing → GitHub secret
- [ ] Fastlane android lane (`supply`) or `gradle-play-publisher` — prefer
      Fastlane for symmetry with iOS
- [ ] `.github/workflows/android-release.yml`: build signed AAB → upload to
      **internal testing** track, dispatched like the TestFlight workflow
      (`gh workflow run android-release.yml --ref <branch>`)
- [ ] Version strategy: `versionName` mirrors iOS MARKETING_VERSION (still
      user-controlled — never bump without being asked); `versionCode`
      auto-increments in CI
- [ ] Store listing: description, screenshots (phone + tablet), feature
      graphic, privacy policy URL, Data Safety form (Supabase network calls,
      no PII collected), content rating questionnaire
- [ ] Internal testing → fix round → promote to **closed testing**
- [ ] Run the mandatory closed test: ≥12 testers, 14 consecutive days
- [ ] Apply for production access → staged rollout (10% → 50% → 100%)

**Definition of done:** production release live on Google Play, built and
published entirely through CI.

## Phase 12 — Ongoing routines (post-launch)

- [ ] Update CLAUDE.md with the cross-platform feature workflow:
      *any change to `shared/` must build + pass tests on both platforms in
      the same PR; feature changes ship to both platforms unless explicitly
      platform-specific*
- [ ] Cross-platform consistency tests (Phase 3 fixtures) run in both CI
      workflows on `shared/` changes
- [ ] Release cadence: cut iOS + Android releases together from the same tag
- [ ] Backlog (explicitly deferred): cross-platform account sync via Supabase
      auth; widgets; Wear OS complication — evaluate after launch

---

## Progress tracking

Update the table as phases complete.

| Phase | Status |
| --- | --- |
| 0 — Environment & tooling | Not started |
| 1 — Repo restructure | Not started |
| 2 — Scaffold + Play account | Not started |
| 3 — Data layer | Not started |
| 4 — 2D map | Not started |
| 5 — State & persistence | Not started |
| 6 — Country details | Not started |
| 7 — 3D globe | Not started |
| 8 — Achievements | Not started |
| 9 — Daily Challenge | Not started |
| 10 — Settings & polish | Not started |
| 11 — Release & launch | Not started |
| 12 — Ongoing routines | Not started |

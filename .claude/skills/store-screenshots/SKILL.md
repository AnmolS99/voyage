---
name: store-screenshots
description: >-
  Make a new set of App Store and Google Play screenshots for voyage in the
  team's Figma file, on a page named for the app version (confirming or bumping
  that version on both apps first): capture fresh app screens from the iOS simulator and the
  Android emulator with a seeded demo traveller, then lay them out in Figma
  following the Voyage design system (Newsreader/Schibsted Grotesk, warm and
  night backgrounds, terracotta accent). Use whenever the user asks to update,
  refresh, redo or make store screenshots, App Store previews, Play Store
  listing images or marketing screenshots — for iPhone, iPad, Android phone or
  Android tablets — even if they don't mention Figma.
---

# Store screenshots

Screenshots belong to an app **version**. Every run starts by settling that
version (step 0), then builds the sets on a Figma page titled with it, e.g. `1.12.0`.
Nothing is exported or uploaded to a store here: the `store-listing` skill does
that from the version's page. Other pages and sets are never touched unless the
user asks.

- **Figma file:** `https://www.figma.com/design/CphnOVwvLUEKxSDfplU37i/voyage`
  (fileKey `CphnOVwvLUEKxSDfplU37i`). `Page 1` holds the brand assets and the
  original panorama set (frame `11`, node `334:9`); leave it alone. Page `1.12.0`
  holds the first redesigned sets. Each version's sets go on their own page as Sections named
  by set label (`App Store · iPhone 6.9"`, ...), stacked top to bottom.
- **Design system:** https://claude.ai/artifact/3jrQLK8zhLqB26NEFQUFYm. Read
  `project/README.md` and `project/tokens.json` with the Artifact tool's
  `read` action at the start of a run. If a token that `figma/build_set.js`
  restates (`T`, fonts) has changed, update the builder first.
- **Figma connector:** the remote Figma MCP (`use_figma`, `upload_assets`,
  `get_screenshot`). Before the first `use_figma` call, load its `figma-use`
  guidance (`get_figma_skill skill://figma/figma-use/SKILL.md`). If the tools are
  missing, ask the user to connect Figma.

## Files

| File | What it holds |
| --- | --- |
| `scenes.json` | The storyboard: each scene's copy (eyebrow, headline, subline), app theme, frame theme, what to capture, and which platforms have it; plus the five **sets** (sizes, device, chrome). Change copy here, not in Figma. |
| `demo-state.json` | The demo traveller both apps are seeded with: visited countries, wishlist, ticked cities and sights, challenge stats, daily streak. |
| `scripts/version.py` | `status`: both apps' versions and the user-facing commits since the iOS version was set. `set X.Y.Z`: writes iOS `MARKETING_VERSION` (app target only) and Android `versionName`. |
| `scripts/store_shots.py` | `seed`, `statusbar` and `capture` for iOS (simctl) and Android (adb). |
| `figma/build_set.js` | Builds one set in Figma: a Section, one frame per scene, an empty `screen:<scene>` slot in each frame for the capture. |

## Sets

| Set | Canvas | Capture on | Device chrome |
| --- | --- | --- | --- |
| `ios-iphone-6.9` | 1320×2868 | sim `Voyage Shots iPhone` (iPhone 17 Pro Max) | Reuses the old set's iPhone bezel image |
| `ios-ipad-13` | 2064×2752 | sim `Voyage Shots iPad` (iPad Pro 13-inch M5) | Drawn dark bezel |
| `android-phone` | 1080×1920 | AVD `voyage_shots_phone` (pixel_9) | No frame: a rounded screen with a shadow (Play discourages device frames) |
| `android-tablet-7` | 1440×2560 | AVD `voyage_shots_tablet7` (Nexus 7 2013) | Same as phone |
| `android-tablet-10` | 2160×3840 | AVD `voyage_shots_tablet10` (pixel_tablet) | Same as phone |

**Use only these dedicated devices, never the user's own sims or AVDs.**
Seeding replaces the app's saved state, and every iOS run starts by erasing the device.

Android sizes are exactly 9:16, which meets Play's phone limit (320–3840 px)
and tablet limit (1080–7680 px). Make all five sets unless the user names fewer.
Android has no Daily tab yet, so the `daily` scene is iOS-only. When it ships,
add `"android"` to its `platforms`.

## Workflow

Keep captures in the session scratchpad: `<scratch>/store-shots/<set-id>/NN-<scene>.png`.
`S=.claude/skills/store-screenshots` below.

### 0. Decide the version

Run `python3 $S/scripts/version.py status` and list the Figma file's pages
(read-only `use_figma`: `return figma.root.children.map(p => ({ id: p.id, name: p.name }))`).

**Use the current iOS version without asking** only when all three hold:

- `recent` is true: at most 2 user-facing commits have landed since the version
  was set (`changes_since_ios_version` lists them, or it's an uncommitted edit).
  User-facing means features and fixes: anything but `chore`, `docs`, `test`,
  `style`, `refactor`, `ci` and `build`, including squash-merged PR titles with no type.
  Freshness is about how much shipped since the bump, not how many days have passed.
- No Figma page is titled with it yet. Existing screenshots for that version
  mean it was never bumped since the last run.
- `platforms_agree` is true: Android's `versionName` matches.

Otherwise the user probably forgot to bump. **Ask** with AskUserQuestion: show
both current versions and a short summary of the changes since. Offer the next
minor (e.g. `1.12.0`, recommended when any `feat` or untyped commit landed), the
next patch (`1.11.2`, recommended when it's only fixes) and keeping the current
one, and let them type another.
If only Android lags, the recommended option is setting Android to the iOS version.
Then:

```bash
python3 $S/scripts/version.py set <version>    # both apps; leaves versionCode/build number to CI
```

Show the user the diff (`git diff --stat`) and leave it uncommitted. Offer to commit it as
`chore: bump app version to <version>` on a `chore/bump-app-version-<version>`
branch, as past bumps were. Never commit or push without a yes. Build the apps
**after** the bump, so the version is in the captured builds.

If the chosen version already has a page with sets, ask whether to replace them
(delete that page's Sections first) or add below them.

### 1. Prepare devices, build and install

- **Missing devices:** create them once if absent. iOS:
  `xcrun simctl create "Voyage Shots iPhone" "iPhone 17 Pro Max" <iOS runtime id>`
  (and `"Voyage Shots iPad" "iPad Pro 13-inch (M5)"`). Android: see Tablet AVDs below.
- **Erase the iOS sims at the start of every run:**
  `xcrun simctl shutdown <udid>; xcrun simctl erase <udid>`. The app merges
  UserDefaults with the iCloud key-value store and writes the merge back. That
  store lives in a system database, and once the app has run, seeding can't
  clean it: leftover entries come back, and its theme beats the seeded one.
- **English status bar:** after erasing, before booting for capture:
  `xcrun simctl spawn <udid> defaults write -g AppleLanguages -array en-US` and
  `... -g AppleLocale -string en_US`, then reboot the sim. The iPad status bar
  shows the date, so the locale shows.

- **iOS** (from `ios/`): build Debug for the simulator as CLAUDE.md shows, then
  install on each sim you need. Builds made in the sandbox crash on launch unless
  re-signed: `xattr -cr <app> && codesign --force --sign - <app>`.
- **Android** (from `android/`): `./gradlew :app:installDebug` with the target
  emulator running. Seeding needs a **debug** build because it uses `run-as`.
- **AVDs:** create them once if `emulator -list-avds` lacks them, from an
  installed system image (e.g. `system-images;android-36;google_apis;arm64-v8a`):
  `avdmanager create avd -n voyage_shots_phone -k "<package>" -d pixel_9`,
  `-n voyage_shots_tablet7 ... -d "Nexus 7 2013"`, `-n voyage_shots_tablet10 ... -d pixel_tablet`.
  Start one with `emulator -avd <name> -no-snapshot-save &`. Tablets capture in
  **portrait**: `settings put system accelerometer_rotation 0`, then
  `settings put system user_rotation 0` on the Nexus 7 but `1` on the Pixel
  Tablet, whose natural orientation is landscape. Check the capture is taller than wide.
- Tell the user before downloading a new system image (it is large).

### 2. Capture each scene, per set

**iOS:** after erasing and installing, seed **once**, before the first launch:
`store_shots.py seed ios <udid> --theme <first scene's theme>`. Then switch theme
in the app with Home's sun/moon button (top right) when a scene needs it. After
the first launch, re-seeding won't change the theme (see step 1).
**Android:** seed before each scene whose `theme` differs (it restarts the app).

For each scene of that platform:

```bash
python3 $S/scripts/store_shots.py seed ios <udid> --theme <scene.theme>   # iOS: first scene only
xcrun simctl launch <udid> com.anmol.voyage          # android: adb -s <serial> shell am start -n com.anmol.voyage/.MainActivity
python3 $S/scripts/store_shots.py statusbar ios <udid>
# navigate to the screen described by scene.capture
python3 $S/scripts/store_shots.py capture ios <udid> <scratch>/store-shots/<set>/NN-<scene>.png
```

- Navigate with the iOS Simulator tool (`tap`, `swipe`) or
  `adb shell input tap|swipe` on Android. Take a screenshot and look at it
  before each tap. Don't guess coordinates.
- **Android drags:** `adb shell input swipe` barely turns the globe. Drag with
  motion events instead:
  `adb shell "input motionevent DOWN x y; input motionevent MOVE ...; input motionevent UP x y"`.
  The same pixel distance turns the globe further on lower-density tablets, so
  probe after every drag. Any composition with plenty of yellow and purple works,
  but prefer Europe and Africa.
- Leave about 1.5 s between taps. A tap sent during a transition is dropped.
- A medal-unlock celebration (confetti, full-screen medal) may open on the first
  launch after seeding. Dismiss it before capturing.
- The Simulator tool's `screenshot` can return a **stale frame**. To check what
  is on screen, use `store_shots.py capture` to a probe file and read that.
- **Read every capture** before using it. Reject frames with a spinner, a
  half-finished animation, a keyboard, a system alert, or the wrong theme.
- The `daily` scene needs network: the calendar fetches dates from Supabase.
  The seeded streak counts back from today.

### 3. Build the set in Figma

Prepend a CONFIG to the contents of `figma/build_set.js` and run it with `use_figma`:

```js
const CONFIG = {
  version: "1.12.0",                               // the page title: found, or created
  set: { id, label, width, height, chrome },       // from scenes.json "sets"
  scenes: [{ id, eyebrow, headline, subline, frame }, ...], // this platform's scenes, in order
  shotAspect: <capture height / capture width>,    // e.g. 2868/1320
};
```

Pass only the scene fields shown, to keep the script small. The script returns
`pageId` and the `screenIds` in scene order. Then fill them:

1. `upload_assets` with `count` = number of scenes, `nodeIds` = `screenIds`,
   `scaleMode: "FILL"` and `currentPageId` = `pageId`.
2. POST each PNG to its URL:
   `curl -sS -X POST -H 'Content-Type: image/png' --data-binary @<png> '<url>'`.
3. Check the whole section with one `get_screenshot` (maxDimension ~2400) and
   look at it: text clipped, a slot left empty, a capture in the wrong slot.
   Fix only what's wrong (targeted `use_figma`) and look once more.

Run sets one after another: each new Section is placed under the last one.

### 4. Report

Give the user the version and a Figma link to its page and to each new Section
(`https://www.figma.com/design/CphnOVwvLUEKxSDfplU37i/voyage?node-id=<id with - for :>`).
Say whether the version bump is still uncommitted, list anything skipped (for
example, Daily without network), and say no PNGs were exported.

## Design rules the builder encodes

These come from the design system, so keep them when tweaking `build_set.js`:

- Headline in **Newsreader Medium**, never bold. Eyebrow and subline in
  **Schibsted Grotesk**. No other fonts. Copy is sentence case. Exclamation marks
  only for achievements. No decorative emoji (flags are content).
- Light frames use the `warm-top`→`warm-bottom` wash with `text-primary` and
  `text-secondary`. Dark frames are night travel: `warm-top`→`page-bg` dark,
  stars, white text.
- `accent` (terracotta) is spent once per frame, on the eyebrow pill, with a
  `text-primary` label (never white: 2.7:1 fails AA). `ocean` is the horizon.
  `land`, `visited` and `wishlist` appear only inside the app capture: never as decoration.
- Captures are real app UI from the seeded demo state, not mock-ups.

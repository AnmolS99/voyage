---
name: store-listing
description: >-
  Upload a voyage version's store listing to App Store Connect and Google Play:
  screenshots the store-screenshots skill laid out in Figma (this version's, an
  earlier version's, or none, keeping what the stores have), plus What's New
  (release notes) and the App Store promotional text, drafted from the commits
  since the last version. Dispatches the repo's store-listing.yml workflow, which
  exports the Figma frames and runs fastlane deliver/supply in CI. Use whenever
  the user asks to upload, publish, push or update store screenshots, the store
  listing, release notes, "What's New", promotional text or App Store/Play
  metadata — even if they don't name the workflow. Never submits for review.
  To make the screenshots themselves, use store-screenshots first.
---

# Store listing

Uploads one **version's** listing to both stores, from CI. The store keys
(App Store Connect API key, Play service account) and the Figma token live in
GitHub secrets, so nothing is uploaded from this machine — the same rule as
`testflight-build` and `play-internal-build`.

The upload stops short of review: App Store Connect keeps the version in
*Prepare for Submission*, and Play holds the edit in *Publishing overview*
(`changes_not_sent_for_review`). The user submits. Never pass
`submit_for_review` or send Play changes for review, even if asked mid-run;
tell the user where to press it.

## What goes where

| Content | File in the repo | Store field |
| --- | --- | --- |
| iOS What's New | `ios/fastlane/metadata/en-GB/release_notes.txt` | Version's *What's New* (max 4000 chars) |
| iOS promotional text | `ios/fastlane/metadata/en-GB/promotional_text.txt` | *Promotional Text* (max 170 chars) |
| iOS description | `ios/fastlane/metadata/en-GB/description.txt` | *Description* (max 4000 chars) |
| iOS keywords | `ios/fastlane/metadata/en-GB/keywords.txt` | *Keywords* (max 100 chars, comma-separated, no spaces; words in the name or subtitle are wasted) |
| Play What's New | `android/fastlane/metadata/android/en-US/changelogs/default.txt` | Release notes of the newest release on the chosen track (max 500 chars) |
| Screenshots | A Figma page titled with a version (this one or an earlier one); exported in CI, gitignored locally | iPhone 6.9" and 6.3", iPad 13", Play phone, 7" and 10" tablet |
| Product page header, search results image | Same page (`ios-header`, `ios-search`); exported in CI to the `creative-ios` artifact | Asset Library — **uploaded by hand**: no API takes them, and Apple reviews them apart from the version |

Only those files exist on purpose: deliver and supply send only what is
present, so the App Store name and subtitle and the Play listing text stay as
they are in the consoles. Don't add those files unless the user asks to manage
that text here too. The description and keywords change rarely: draft them
only when the user asks, or when a release changes what the app is about. One locale per store: en-GB on the App Store (its primary
language), en-US on Play.

`scripts/export_store_screenshots.py` maps frames named `<set id>/NN-<scene>`
(as `store-screenshots`' `figma/build_set.js` names them) to fastlane's folders,
checks each is the set's exact size from `scenes.json`, and flattens alpha (App
Store Connect rejects it). The workflow's lanes are `listing` in
`ios/fastlane/Fastfile` and `android/fastlane/Fastfile`.

## Steps

`S=.claude/skills/store-screenshots`.

### 1. Settle the version

Run `python3 $S/scripts/version.py status`. Use the iOS version unless the user
names another; if the two apps disagree, ask which.

### 2. Choose the screenshots

Not every release gets new screenshots. List the Figma file's pages (read-only
`use_figma`: `return figma.root.children.map(p => p.name)` on fileKey
`CphnOVwvLUEKxSDfplU37i`); the ones titled like a version (`1.12.0`) hold
screenshot sets. Then **always ask** with AskUserQuestion which to use:

- **This version's** (`<version>`): offer only if that page exists; recommend
  it when it does.
- **An earlier version's**: one option per version page, newest first (at most
  two, plus "Other" for the rest); recommend the newest when this version has
  no page. Dispatch with `screenshots_from=<that version>`.
- **Keep what's in the stores**: no screenshot upload (`screenshots=false`).
  A new App Store version starts with the previous version's screenshots,
  which App Store Connect copies over, and Play's listing screenshots aren't
  tied to a release, so they stay too.
- If the user wants new ones that don't exist yet, stop here and run
  `store-screenshots` first.

Reusing an earlier page re-uploads those frames as they are now in Figma, so
it also undoes any screenshot changes made by hand in the consoles since. The
page must have every set for the stores being uploaded (`scenes.json`
`sets`); if an older page lacks one (say, the iPad set), say so and offer
uploading only the other store, keeping the stores' screenshots, or making the
missing set with `store-screenshots`.

### 3. Draft the text

Read the user-facing commits since the version before it was set:

```bash
git log --format='%h %s' <commit that set the previous version>..HEAD
```

(`git log -G'MARKETING_VERSION = <prev>' -- ios/voyage.xcodeproj/project.pbxproj`
finds that commit.) Keep `feat`, `fix` and untyped squash-merge titles; drop
chore/docs/test/refactor/ci/build. Note which platform each change touched —
an Android-only change doesn't belong in the iOS notes, and vice versa.

Write in the Voyage voice (the design system's: a well-travelled friend,
short and warm, sentence case, exclamation marks only for achievements, no
decorative emoji): lead with the headline change in one sentence, then a short
list of what users will notice. Leave out internals (refactors, CI, tests, perf
numbers). Promotional text is one or two sentences about the release's best
thing; it can change any time without review, so it needn't list fixes.

Check lengths (`wc -m`) against the limits above, then **show the user all
three texts and wait for their edits or a yes** before going on.

### 4. Commit and push the text

CI reads the files from the branch it runs on, so they must be pushed. On a
`chore/store-listing-<version>` branch (or the current branch if it is the
release branch the user is shipping from), commit as
`chore: add store listing text for <version>`. Ask before committing and
before pushing, as `testflight-build` does for unpushed work: compare `HEAD`
with `git ls-remote --heads origin <branch>`, not the local tracking ref.

### 5. Check the preconditions

- `gh secret list` shows `FIGMA_TOKEN` (needed when uploading screenshots). If
  missing, the user creates a Figma personal access token with
  `file_content:read` and adds it: `gh secret set FIGMA_TOKEN`. Never ask
  them to paste the token into chat.
- **Play:** What's New attaches to the newest release on the chosen track
  (default `internal`). If this version's build isn't on Play yet, offer
  `play-internal-build` first, or the notes land on the previous release.
  Play listing screenshots are app-wide, not per release: once the user sends
  the changes for review and Google approves them, they replace the live ones.
- **App Store:** the version is created in App Store Connect if absent; it
  doesn't need a build yet. If a version is already *Waiting for Review* or
  later, deliver can't edit it: tell the user.

### 6. Dispatch

Confirm the branch, version, stores, which screenshots (or none) and Play track
with the user — this changes the live consoles — then:

```bash
gh workflow run store-listing.yml --ref <branch> \
  -f version=<version> -f platforms=both -f play_track=internal \
  -f screenshots=true -f screenshots_from=<page version, or omit for this version>
# keeping the stores' screenshots instead: -f screenshots=false
sleep 4
gh run list --workflow=store-listing.yml --branch <branch> --limit 1 --json databaseId,url,status
```

Without `gh`, use the GitHub MCP `run_workflow` / `list_workflow_runs` on
`AnmolS99/voyage`, as `testflight-build` describes. Report the run URL. Only
watch it (`gh run watch <id>`) if the user asks.

### 7. Tell the user what's left

- **App Store Connect:** open the version, pick the build, check the
  screenshots, then *Add for Review*.
- **Creative assets** (when the page has them): download the run's
  `creative-ios` artifact (`gh run download <id> -n creative-ios`) and add the
  header and search image in App Store Connect's *Asset Library*, then submit
  them there. iPhone Duo screenshots aren't made yet (see `store-screenshots`).
- **Play Console:** *Publishing overview* → *Send changes for review*.

## If something goes wrong

Read the failure with `gh run view <id> --log-failed`.

- **"No Figma page titled …" / "has no frames for …":** the page or a set is
  missing (common on an older page reused via `screenshots_from`) or a frame
  was renamed; rerun `store-screenshots` for that set, don't
  rename frames by hand without keeping the `<set id>/NN-<scene>` pattern.
- **"Figma rendered (w, h)":** a frame was resized in Figma; fix it there.
- **Figma 403:** the token expired or lacks `file_content:read`.
- **"Figma rate limit … retry after Ns":** the plan's render budget is spent
  (it can lock out for days). If an earlier run exported the same page, rerun
  with `-f screenshots_run=<that run's ID>` to reuse its screenshots and skip
  Figma; otherwise wait it out.
- **deliver can't find a screen size:** a fastlane release too old for the
  6.9"/13" sizes; the Gemfiles are unpinned, so a rerun picks up the latest.
- **"No release on the … track":** nothing uploaded to that track yet; run
  `play-internal-build` or choose another track.
- **Play "changes cannot be sent for review automatically":** expected with
  managed publishing; fastlane retries as the error suggests.

To preview the export locally (needs Pillow and a token in the environment,
not in chat): `python3 scripts/export_store_screenshots.py <version>`; the PNGs
land in the gitignored fastlane folders.

---
name: play-internal-build
description: >-
  Create a new Google Play internal testing build of the voyage Android app by
  dispatching the repo's GitHub Actions workflow (play-internal.yml). Use this
  whenever the user asks to ship, release, deploy, or make a new Android build,
  push an Android beta, upload to Play / Google Play / internal testing, or get
  an Android build to testers — even if they don't name the workflow. Handles
  branch selection, warns about unpushed work, and returns the Actions run
  link. Do NOT build, sign or upload locally; signing lives in CI. For iOS /
  TestFlight use testflight-build instead; "ship both" means running both.
---

# Play Internal Testing Build

Ship a new internal testing build of **voyage** for Android by triggering the
existing GitHub Actions workflow. The workflow — not a local machine — holds
the upload key and the Play service account (repo secrets), so a local
bundle/upload cannot work and must never be attempted.

## What the workflow does

`.github/workflows/play-internal.yml` ("Play Internal Testing Build") is a
`workflow_dispatch` job with **no inputs**. The branch to build is chosen
entirely by the git ref you dispatch against. It is also the **only** Android
CI — nothing runs on PRs — so it:

- Checks the shared country fixture is current and runs
  `./gradlew testDebugUnitTest lintDebug`. Nothing uploads unless they pass.
- Runs `fastlane internal` (`android/fastlane/Fastfile`), which reads the
  highest `versionCode` on any Play track and builds with the next one — so
  **you never bump `versionCode` yourself** — then builds a signed release
  bundle and uploads it to the internal track as a completed release.

`versionName` is user-controlled (it mirrors the iOS `MARKETING_VERSION`) —
never bump it as part of shipping a build unless the user explicitly asks.

Because the job checks out the ref from GitHub, it builds **only what has been
pushed** — local uncommitted or unpushed commits are not included.

## Which tooling to use: `gh` vs GitHub MCP

Check once, up front:

- **`gh` CLI**: `command -v gh && gh auth status` succeeds → use the `gh`
  commands below.
- **GitHub MCP tools** (mobile / web cloud sessions typically have **no
  `gh`**): use `actions_run_trigger` to dispatch and `actions_list` to fetch
  the run, with `owner: AnmolS99`, `repo: voyage`.

## Steps

A dispatch pushes a real build to real testers, so confirm the target before
firing.

### 1. Determine the target branch

Default to the current branch (`git branch --show-current`); use the one the
user named if they named one. State which branch you're about to build and get
the user's go-ahead before dispatching.

### 2. Verify the branch on the *real* remote

Don't trust `@{upstream}` or the local `origin/<branch>` ref — they go stale.
Compare local `HEAD` to what the remote actually reports:

```bash
git status --short                      # uncommitted work?
git rev-parse HEAD                      # the commit you intend to build
git ls-remote --heads origin <branch>   # what the remote has (empty = absent)
```

- **Branch absent on the remote**, **remote SHA ≠ local HEAD**, or
  **uncommitted changes**: the build will not reflect the user's current work.
  Tell them and offer to push first (`git push -u origin <branch>`). Wait for
  their answer — don't push silently.
- **Remote SHA == local HEAD and tree is clean:** proceed.

### 3. Dispatch the build

```bash
gh workflow run play-internal.yml --ref <branch>
```

With GitHub MCP: `actions_run_trigger` with `workflow_id: play-internal.yml`,
`ref: <branch>`.

### 4. Report the run link

The run doesn't appear in the API instantly. Poll briefly, then hand the user
its URL:

```bash
sleep 4
gh run list --workflow=play-internal.yml --branch <branch> --limit 1 \
  --json databaseId,url,status,createdAt
```

With GitHub MCP: list runs for `play-internal.yml` on the branch, newest
first, and take the top run's `html_url`.

Builds are serialized (`concurrency: play-internal-build`,
`cancel-in-progress: false`), so one already running makes this one queue.
Then stop — only watch to completion if the user asks.

## If something goes wrong

- **Tests or lint fail:** that is the gate working, not a flake. The
  `android-reports` artifact has the test results and lint report; read the
  failure with `gh run view <databaseId> --log-failed` and fix the code.
- **"is unsigned — is keystore.properties missing?"** or a keystore error:
  the `ANDROID_UPLOAD_*` secrets are missing or wrong — the user fixes them in
  the repo's Actions secrets (see "Play internal testing builds" in
  `docs/ANDROID_DEVELOPMENT.md`).
- **Play API 401/403 or "caller does not have permission":** the
  `PLAY_SERVICE_ACCOUNT_JSON` secret is missing, or that service account
  lacks release rights in Play Console. The user fixes it there.
- **Dispatch rejected / "ref not found":** the branch isn't on the remote —
  re-run the step 2 check and push before retrying.

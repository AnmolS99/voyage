---
name: testflight-build
description: >-
  Create a new TestFlight build of the voyage iOS app by dispatching the repo's
  GitHub Actions workflow (testflight.yml). Use this whenever the user asks to
  ship, release, deploy, or "make a new TestFlight build," push a beta, or get a
  build to testers — even if they don't name the workflow. Handles branch
  selection, warns about unpushed work, and returns the Actions run link. Do NOT
  archive or upload locally; signing lives in CI.
---

# TestFlight Build

Ship a new TestFlight build of **voyage** by triggering the existing GitHub
Actions workflow. The workflow — not a local machine — holds the signing setup
(fastlane match, `Secrets.xcconfig` from repo secrets), so a local
archive/upload cannot work and must never be attempted.

## What the workflow does

`.github/workflows/testflight.yml` ("TestFlight Build") is a
`workflow_dispatch` job with **no inputs**. The branch to build is chosen
entirely by the git ref you dispatch against. On CI it runs `fastlane beta`,
which:

- Sets the build number from the GitHub run number (`increment_build_number`
  with `GITHUB_RUN_NUMBER`) — so **you never bump the build number yourself**.
- Signs with fastlane match (App Store distribution), builds Release, and
  uploads to TestFlight.

The **marketing version** (`MARKETING_VERSION`) is user-controlled — never bump
it as part of shipping a build unless the user explicitly asks.

Because the job checks out the ref from GitHub, it builds **only what has been
pushed** — local uncommitted or unpushed commits are not included.

## Steps

Follow these in order. A build dispatch pushes a real build to real testers, so
confirm the target before firing.

### 1. Determine the target branch

Default to the **current checked-out branch**:

```bash
git branch --show-current
```

State which branch you're about to build and get the user's go-ahead before
dispatching. If the user named a specific branch, use that instead.

### 2. Check for unpushed / uncommitted work

The build reflects GitHub, not the local tree. Check both:

```bash
git status --short
git rev-list --count @{upstream}..HEAD 2>/dev/null || echo "no-upstream"
```

- **Uncommitted changes** (non-empty `git status --short`) or **unpushed
  commits** (count > 0, or `no-upstream` meaning the branch was never pushed):
  tell the user the build will **not** include this work, and offer to push
  first (`git push -u origin <branch>`). Wait for their answer — don't push
  silently.
- If the tree is clean and up to date with upstream, proceed.

### 3. Dispatch the build

```bash
gh workflow run testflight.yml --ref <branch>
```

### 4. Report the run link

The run doesn't appear in the API instantly. Poll briefly for the newly created
run, then hand the user its URL:

```bash
# Give Actions a moment to register the dispatch, then grab the newest run
sleep 4
gh run list --workflow=testflight.yml --branch <branch> --limit 1 \
  --json databaseId,url,status,createdAt
```

Report the run URL (from the `url` field) so the user can watch it. A typical
build takes ~4–15 minutes. Note that builds are serialized
(`concurrency: testflight-build`, `cancel-in-progress: false`), so if one is
already running, this one queues behind it.

Then stop — the user monitors from here. Only watch to completion
(`gh run watch <databaseId>`) if the user explicitly asks you to follow it.

## If something goes wrong

- **`gh` not authenticated:** `gh auth status` to check; the user runs
  `gh auth login` themselves.
- **Workflow not found / wrong repo:** confirm you're in the voyage repo and the
  ref exists on the remote (`git ls-remote --heads origin <branch>`).
- **Build fails:** the workflow uploads fastlane logs as a `fastlane-logs`
  artifact on failure. Point the user to the failed run's URL and the logs; read
  them with `gh run view <databaseId> --log-failed` if diagnosing.

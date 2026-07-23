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

## Which tooling to use: `gh` vs GitHub MCP

You need a way to (a) dispatch the workflow and (b) read the resulting run.
Two mechanisms exist; pick based on what's actually available in this
environment — check once, up front:

- **`gh` CLI** (desktop, or a cloud session with `gh` installed + authenticated):
  `command -v gh && gh auth status` succeeds → use the `gh` commands below.
- **GitHub MCP tools** (mobile / web cloud sessions typically have **no `gh`**):
  if `gh` is missing, use the GitHub MCP tools instead — `run_workflow` to
  dispatch and `list_workflow_runs` to fetch the run. First get the repo
  `owner/repo` from `git remote get-url origin` (the voyage remote resolves to
  `AnmolS99/voyage`).

Don't burn a turn discovering `gh` is absent mid-flow — decide the mechanism
before step 3. The rest of the logic is identical either way.

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

### 2. Verify the branch on the *real* remote (not the local tracking ref)

The build reflects whatever commit is on GitHub under that branch name, so this
check must query the remote directly. **Do not trust `@{upstream}` or the local
`origin/<branch>` ref** — those are cached and go stale: a session can report
"clean, fully pushed, matches remote" for a branch that isn't on the remote at
all, and you'll dispatch against a ref that doesn't exist. Compare local `HEAD`
to what the remote actually reports:

```bash
git status --short                              # uncommitted work?
git rev-parse HEAD                              # the commit you intend to build
git ls-remote --heads origin <branch>           # what the remote actually has (empty = branch absent)
```

- **Branch absent on the remote** (empty `ls-remote` output), **remote SHA ≠
  local HEAD**, or **uncommitted changes** (non-empty `git status --short`):
  the build will **not** reflect the user's current work. Tell them, and offer
  to push first (`git push -u origin <branch>`). Wait for their answer — don't
  push silently.
- **Remote SHA == local HEAD and tree is clean:** proceed.

### 3. Dispatch the build

**With `gh`:**

```bash
gh workflow run testflight.yml --ref <branch>
```

**With GitHub MCP** (no `gh`): call `run_workflow` with `owner: AnmolS99`,
`repo: voyage`, `workflow_id: testflight.yml`, `ref: <branch>`.

### 4. Report the run link

The run doesn't appear in the API instantly. Poll briefly for the newly created
run, then hand the user its URL.

**With `gh`:**

```bash
sleep 4
gh run list --workflow=testflight.yml --branch <branch> --limit 1 \
  --json databaseId,url,status,createdAt
```

**With GitHub MCP:** call `list_workflow_runs` for `testflight.yml` filtered to
the branch, newest first, and take the top run's `html_url`.

Report the run URL so the user can watch it. A typical build takes ~4–15
minutes. Builds are serialized (`concurrency: testflight-build`,
`cancel-in-progress: false`), so if one is already running, this one queues
behind it.

Then stop — the user monitors from here. Only watch to completion
(`gh run watch <databaseId>`, or polling `list_workflow_runs`) if the user
explicitly asks you to follow it.

## If something goes wrong

- **No `gh` in this environment:** expected on mobile/web cloud sessions — use
  the GitHub MCP path above rather than trying to install or authenticate `gh`.
- **`gh` present but not authenticated:** `gh auth status` to check; the user
  runs `gh auth login` themselves.
- **Dispatch rejected / "ref not found":** the branch isn't on the remote —
  re-run the step 2 `ls-remote` check and push before retrying. This is almost
  always a stale local tracking ref, not a real "already pushed" state.
- **Build fails:** the workflow uploads fastlane logs as a `fastlane-logs`
  artifact on failure. Point the user to the failed run's URL and the logs; read
  them with `gh run view <databaseId> --log-failed` (or the run's GitHub URL) if
  diagnosing.

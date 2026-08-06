---
name: archive-app-release
argument-hint: [tag] [app name]
description: Archive the current app in this playground repo as a GitHub Release before the codebase is replaced — builds the distributable, creates an annotated tag, and publishes a release with the artifacts attached. Use when the user wants to preserve, tag, or release the current app before wiping or rewriting it.
allowed-tools: Bash, Read, Write, Edit, Glob, Grep, AskUserQuestion
---

# Archive app as a GitHub Release

This repo is a playground: one app lives in it at a time, and each app is replaced by
the next. Before a codebase is wiped, its final state is preserved as a tagged GitHub
Release so the source and a runnable artifact stay recoverable.

## Inputs

- `$1` — tag name. No semantic versioning: a short lowercase slug naming the app (`meal`, `bodyfit`).
- `$2` — display name of the app, used as the release title. May be Korean.

If either is missing, infer from the codebase and confirm with the user via AskUserQuestion.

## Steps

### 1. Verify the tree is releasable

Run `scripts/preflight.sh`. It checks `gh` auth, resolves the remote repo, confirms the
worktree is clean and pushed, and fails if the tag already exists.

Fix anything it reports before continuing. In particular, **the tagged commit must
build**. If compilation fails, resolve the cause forward (upgrade the dependency, fix
the API usage) rather than reverting the user's work — a broken snapshot is not worth
archiving. Commit the fix, then re-run preflight.

### 2. Build and collect artifacts

Run `scripts/collect_artifacts.sh <staging-dir> <tag>`. It detects which Gradle targets
the project declares and builds/collects accordingly:

- Android → signed `assembleDebug` APK (the release APK is unsigned and cannot be installed)
- Desktop (JVM) → `packageDistributionForCurrentOS` output (`.dmg` / `.msi` / `.deb`)
- iOS → nothing; there is no distributable without signing. Say so in the release notes.

Artifacts are renamed to `<tag>-<platform>.<ext>`. If the script finds nothing to
collect, publish the release with source only and tell the user.

### 3. Write the release notes

Read `references/release-notes-format.md` and follow it exactly.

**The release body must be written in English**, even though this repo's working
language is Korean. The release title is the app's display name and stays as-is.

### 4. Publish

Run `scripts/publish_release.sh <tag> <title> <notes-file> [assets...]`. It creates the
annotated tag, pushes it, and publishes a public release with the assets attached.

### 5. Report

Give the user the release URL, the tag, and the list of attached artifacts. If a
platform was skipped, say which and why.

## Notes

- Releases are public by default. Ask first only if the user signalled hesitation.
- Never force-push or move an existing tag. If a tag needs correcting, delete the
  release and tag explicitly with the user's confirmation.

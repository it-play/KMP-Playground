#!/usr/bin/env bash
# Shared helpers for the archive-app-release skill.
# Source this; do not execute it directly.

set -euo pipefail

REPO_ROOT="$(git rev-parse --show-toplevel)"

die() { printf 'error: %s\n' "$*" >&2; exit 1; }
info() { printf '  %s\n' "$*"; }
ok() { printf 'ok: %s\n' "$*"; }

# Slug of the GitHub repo the local `origin` points at, e.g. it-play/KMP-Playground.
target_repo() {
    gh repo view --json nameWithOwner --jq .nameWithOwner
}

# True when the Gradle build declares the given target, e.g. has_target 'androidTarget'.
has_target() {
    grep -q "$1" "$REPO_ROOT/composeApp/build.gradle.kts"
}

gradlew() {
    (cd "$REPO_ROOT" && ./gradlew "$@")
}

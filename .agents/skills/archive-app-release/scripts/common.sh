#!/usr/bin/env bash
set -euo pipefail

REPO_ROOT="$(git rev-parse --show-toplevel)"

die() { printf 'error: %s\n' "$*" >&2; exit 1; }
info() { printf '  %s\n' "$*"; }
ok() { printf 'ok: %s\n' "$*"; }

target_repo() {
    gh repo view --json nameWithOwner --jq .nameWithOwner
}

has_target() {
    grep -q "$1" "$REPO_ROOT/composeApp/build.gradle.kts"
}

gradlew() {
    (cd "$REPO_ROOT" && ./gradlew "$@")
}

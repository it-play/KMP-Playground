#!/usr/bin/env bash
# Usage: publish_release.sh <tag> <title> <notes-file> [asset...]
# Creates the annotated tag, pushes it, and publishes a public release.

source "$(dirname "${BASH_SOURCE[0]}")/common.sh"

TAG="${1:-}"; TITLE="${2:-}"; NOTES="${3:-}"
[ -n "$TAG" ] && [ -n "$TITLE" ] && [ -n "$NOTES" ] \
    || die "usage: publish_release.sh <tag> <title> <notes-file> [asset...]"
[ -f "$NOTES" ] || die "notes file not found: $NOTES"
shift 3

REPO="$(target_repo)"

git -C "$REPO_ROOT" tag -a "$TAG" -m "$TITLE — final snapshot"
git -C "$REPO_ROOT" push origin "$TAG"
ok "tag '$TAG' pushed"

gh release create "$TAG" \
    --repo "$REPO" \
    --title "$TITLE" \
    --notes-file "$NOTES" \
    "$@"

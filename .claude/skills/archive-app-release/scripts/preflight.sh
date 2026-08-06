#!/usr/bin/env bash
# Usage: preflight.sh <tag>
# Verifies the repo is in a state where <tag> can be cut and released.

source "$(dirname "${BASH_SOURCE[0]}")/common.sh"

TAG="${1:-}"
[ -n "$TAG" ] || die "usage: preflight.sh <tag>"

gh auth status >/dev/null 2>&1 || die "gh is not authenticated (run: gh auth login)"
ok "gh authenticated"

REPO="$(target_repo)"
ok "target repo: $REPO"

[ -z "$(git -C "$REPO_ROOT" status --porcelain)" ] || die "worktree is dirty; commit or stash first"
ok "worktree clean"

BRANCH="$(git -C "$REPO_ROOT" rev-parse --abbrev-ref HEAD)"
git -C "$REPO_ROOT" fetch --quiet origin "$BRANCH"
if [ -n "$(git -C "$REPO_ROOT" log "origin/$BRANCH..HEAD" --oneline)" ]; then
    die "HEAD is ahead of origin/$BRANCH; push before tagging"
fi
ok "HEAD pushed to origin/$BRANCH"

if git -C "$REPO_ROOT" rev-parse -q --verify "refs/tags/$TAG" >/dev/null; then
    die "tag '$TAG' already exists locally"
fi
if gh release view "$TAG" --repo "$REPO" >/dev/null 2>&1; then
    die "release '$TAG' already exists on $REPO"
fi
ok "tag '$TAG' is free"

echo
echo "Declared targets:"
has_target 'androidTarget' && info "android"
has_target 'jvm(' && info "desktop"
has_target 'ios' && info "ios (no distributable — source only)"

echo
echo "Now verify the snapshot compiles before tagging it."

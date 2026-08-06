#!/usr/bin/env bash
source "$(dirname "${BASH_SOURCE[0]}")/common.sh"

STAGING="${1:-}"
TAG="${2:-}"
[ -n "$STAGING" ] && [ -n "$TAG" ] || die "usage: collect_artifacts.sh <staging-dir> <tag>"
mkdir -p "$STAGING"

collected=()

stage() {
    # stage <source-file> <platform-label>
    local src="$1" label="$2" ext dest
    ext="${src##*.}"
    dest="$STAGING/${TAG}-${label}.${ext}"
    cp "$src" "$dest"
    collected+=("$dest")
}

if has_target 'androidTarget'; then
    echo "building android debug apk..." >&2
    # The release APK is unsigned and cannot be installed; debug is signed with the
    # debug keystore and runs anywhere, which is what an archive needs.
    gradlew :composeApp:assembleDebug >&2
    apk="$REPO_ROOT/composeApp/build/outputs/apk/debug/composeApp-debug.apk"
    [ -f "$apk" ] && stage "$apk" "android-debug"
fi

if has_target 'jvm('; then
    echo "building desktop distributable..." >&2
    gradlew :composeApp:packageDistributionForCurrentOS >&2
    while IFS= read -r dist; do
        stage "$dist" "desktop"
    done < <(find "$REPO_ROOT/composeApp/build/compose/binaries" \
        -type f \( -name '*.dmg' -o -name '*.msi' -o -name '*.deb' \) 2>/dev/null)
fi

printf '%s\n' "${collected[@]-}"

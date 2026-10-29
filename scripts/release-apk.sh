#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SDK_ROOT="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Android/Sdk}}"
BUILD_TOOLS="$SDK_ROOT/build-tools/35.0.0"
KEYSTORE="${ALMARA_KEYSTORE:-$HOME/.local/share/almara/almara-release.jks}"
KEY_ALIAS="${ALMARA_KEY_ALIAS:-almara}"
UNSIGNED_APK="$ROOT_DIR/app/build/outputs/apk/release/app-release-unsigned.apk"
RELEASE_DIR="$ROOT_DIR/app/build/outputs/apk/release"
ALIGNED_APK="$RELEASE_DIR/Almara-v1.0.0-aligned.apk"
SIGNED_APK="$RELEASE_DIR/Almara-v1.0.0.apk"

for tool in "$BUILD_TOOLS/zipalign" "$BUILD_TOOLS/apksigner"; do
    if [[ ! -x "$tool" ]]; then
        echo "Missing Android SDK tool: $tool" >&2
        echo "Install Android SDK build-tools 35.0.0 and set ANDROID_HOME or ANDROID_SDK_ROOT." >&2
        exit 1
    fi
done

if [[ ! -f "$KEYSTORE" ]]; then
    echo "Signing keystore not found: $KEYSTORE" >&2
    echo "Create it outside this repository first; see docs/CHROMEBOOK-LINUX-RELEASE.md." >&2
    exit 1
fi

if [[ ! -x "$ROOT_DIR/gradlew" ]]; then
    echo "Gradle wrapper is missing. Run this script from a complete Almara checkout." >&2
    exit 1
fi

read -r -s -p "Keystore password: " ALMARA_KEYSTORE_PASSWORD
printf '\n'
read -r -s -p "Private-key password: " ALMARA_KEY_PASSWORD
printf '\n'

cd "$ROOT_DIR"
./gradlew --no-daemon :app:testDebugUnitTest :app:assembleDebug :app:assembleRelease

if [[ ! -f "$UNSIGNED_APK" ]]; then
    echo "Expected unsigned release APK was not produced: $UNSIGNED_APK" >&2
    exit 1
fi

rm -f "$ALIGNED_APK" "$SIGNED_APK" "$SIGNED_APK.sha256"
"$BUILD_TOOLS/zipalign" -p -f 4 "$UNSIGNED_APK" "$ALIGNED_APK"
export ALMARA_KEYSTORE_PASSWORD ALMARA_KEY_PASSWORD
"$BUILD_TOOLS/apksigner" sign \
    --ks "$KEYSTORE" \
    --ks-key-alias "$KEY_ALIAS" \
    --ks-pass env:ALMARA_KEYSTORE_PASSWORD \
    --key-pass env:ALMARA_KEY_PASSWORD \
    --out "$SIGNED_APK" \
    "$ALIGNED_APK"

"$BUILD_TOOLS/apksigner" verify --verbose --print-certs "$SIGNED_APK"
sha256sum "$SIGNED_APK" | tee "$SIGNED_APK.sha256"
unset ALMARA_KEYSTORE_PASSWORD ALMARA_KEY_PASSWORD

printf '\nSigned APK: %s\nChecksum:   %s\n' "$SIGNED_APK" "$SIGNED_APK.sha256"

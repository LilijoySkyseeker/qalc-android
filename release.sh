#!/usr/bin/env bash
# Build a signed release APK and publish it as a GitHub release, which is
# what Obtainium watches.
#   usage: ./release.sh <versionName> <versionCode>     e.g. ./release.sh 0.1.0 1
# Needs QALC_KEYSTORE, QALC_KEYSTORE_PASSWORD, QALC_KEY_ALIAS, QALC_KEY_PASSWORD.
set -euo pipefail
cd "$(dirname "$0")"

if [ $# -ne 2 ]; then
  echo "usage: $0 <versionName> <versionCode>" >&2
  exit 2
fi
name=$1 code=$2

missing=()
for v in QALC_KEYSTORE QALC_KEYSTORE_PASSWORD QALC_KEY_ALIAS QALC_KEY_PASSWORD; do
  [ -n "${!v:-}" ] || missing+=("$v")
done
if [ ${#missing[@]} -gt 0 ]; then
  echo "release.sh: missing environment variables: ${missing[*]}" >&2
  exit 1
fi

direnv exec . native/build-deps.sh
direnv exec . ./gradlew assembleRelease -PversionName="$name" -PversionCode="$code"
apk=app/build/outputs/apk/release/qalc-$name.apk
cp app/build/outputs/apk/release/app-release.apk "$apk"
gh release create "v$name" "$apk" --title "v$name" --notes ""

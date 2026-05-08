#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

source "$SCRIPT_DIR/env.sh"

APK_PATH=""
while IFS= read -r -d '' apk; do
  if [[ -z "$APK_PATH" || "$apk" -nt "$APK_PATH" ]]; then
    APK_PATH="$apk"
  fi
done < <(find "$PROJECT_ROOT/app/build/outputs/apk" -type f -name '*.apk' -print0)

if [[ -z "$APK_PATH" ]]; then
  echo "No APK found under app/build/outputs/apk. Run scripts/build-apk.sh first." >&2
  exit 1
fi

adb "$@" install -r -d "$APK_PATH"

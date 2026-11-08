#!/usr/bin/env bash
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
grep -E 'versionName\s*=\s*"[^"]+"' "$SCRIPT_DIR/android/app/build.gradle.kts" | sed -E 's/.*versionName\s*=\s*"([^"]+)".*/\1/' | head -n 1

#!/usr/bin/env bash
set -euo pipefail

SCRIPT_SOURCE="${BASH_SOURCE[0]:-$0}"
PROJECT_ROOT="$(cd "$(dirname "$SCRIPT_SOURCE")/.." && pwd)"

export JAVA_HOME="$PROJECT_ROOT/runtimes/jdk17/Contents/Home"
export ANDROID_HOME="$PROJECT_ROOT/runtimes/android-sdk"
export ANDROID_SDK_ROOT="$ANDROID_HOME"
export GRADLE_USER_HOME="$PROJECT_ROOT/runtimes/gradle-home"
export GRADLE_HOME="$PROJECT_ROOT/runtimes/gradle-8.3"

export PATH="$JAVA_HOME/bin:$ANDROID_HOME/platform-tools:$ANDROID_HOME/cmdline-tools/latest/bin:$GRADLE_HOME/bin:$PATH"


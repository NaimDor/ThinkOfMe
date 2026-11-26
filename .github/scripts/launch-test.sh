#!/usr/bin/env bash
# Installs an APK on the running emulator, starts the app and fails if it crashes or dies
# within 20 seconds. Usage: launch-test.sh APP.apk
set -uo pipefail
apk="$1"
pkg=com.thinkofme.app
adb install -r -g "$apk"
adb logcat -c
adb shell am start -W -n "$pkg/.MainActivity"
sleep 20
adb logcat -d > launch-logcat.txt
if grep -q "FATAL EXCEPTION" launch-logcat.txt; then
  echo "::error::The release build crashed at launch:"
  grep -A 40 "FATAL EXCEPTION" launch-logcat.txt | head -60
  exit 1
fi
if [ -z "$(adb shell pidof "$pkg" | tr -d '\r')" ]; then
  echo "::error::The app is not running 20 seconds after launch."
  tail -80 launch-logcat.txt
  exit 1
fi
echo "Release build launched and is still running after 20 seconds."

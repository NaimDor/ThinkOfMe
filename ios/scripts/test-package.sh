#!/usr/bin/env bash
# Run an iOS-only Swift package's tests on the first available iPhone simulator.
set -euo pipefail
package_path="$1"; scheme="$2"
device=$(xcrun simctl list devices available -j | python3 -c '
import json, sys
devices = json.load(sys.stdin)["devices"]
for runtime in sorted(devices, reverse=True):
    if "iOS" in runtime:
        for d in devices[runtime]:
            if d["name"].startswith("iPhone"):
                print(d["udid"]); sys.exit(0)
')
cd "$package_path"
xcodebuild test -scheme "$scheme" -destination "id=$device" -skipPackagePluginValidation

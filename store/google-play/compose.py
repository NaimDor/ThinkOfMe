#!/usr/bin/env python3
"""Frames the Android screenshot-test renders into Google Play phone screenshots (1080 x 1920).

Same design as the App Store images (store/app-store/compose.py, scaled to Play's 9:16), with
the English captions from store/app-store/captions.json. Play shows the default language's
screenshots for languages without their own.

  python3 store/google-play/compose.py ROBORAZZI_DIR OUT_DIR
ROBORAZZI_DIR is android/app/build/outputs/roborazzi (./gradlew :app:recordRoborazziDebug).
"""
import json
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE.parent / "app-store"))
import compose as base  # noqa: E402

base.WIDTH, base.HEIGHT = 1080, 1920

# Play order: Android render → caption key in captions.json.
SHOTS = [
    ("home_light", "01-home"),
    ("person_light", "02-person"),
    ("wish_wrapped", "03-wrapped"),
    ("my_list", "04-my-list"),
    ("dates", "05-dates"),
]


def main() -> int:
    raw_dir, out_dir = Path(sys.argv[1]), Path(sys.argv[2])
    captions = json.loads((HERE.parent / "app-store" / "captions.json").read_text())["en-US"]
    target = out_dir / "en-US" / "phoneScreenshots"
    target.mkdir(parents=True, exist_ok=True)
    count = 0
    for index, (render, key) in enumerate(SHOTS, start=1):
        source = raw_dir / f"{render}.png"
        if not source.exists():
            print(f"missing {source}")
            continue
        base.compose(source, "en-US", captions[key]).save(target / f"{index:02d}-{render}.png", optimize=True)
        count += 1
    print(f"Composed {count} Play screenshots into {target}")
    return 0 if count == len(SHOTS) else 1


if __name__ == "__main__":
    sys.exit(main())

"""Uploads a signed Android App Bundle to Google Play and puts it on a track.

  play_release.py APP.aab --track internal|alpha|beta|production [--notes-dir store/google-play/release-notes]

`alpha` is Play Console's "Closed testing" track, `internal` is "Internal testing".
A brand-new app that was never published only accepts draft releases; the script retries as a
draft in that case, and you roll it out from Play Console. Release notes come from
<notes-dir>/<language>.txt (en-US, iw-IL, ar, ru-RU), at most 500 characters each.

Note: Google requires the very first bundle of a new app to be uploaded by hand in Play
Console. After that this script works (see docs/RELEASE-ANDROID.md).
"""
import argparse
import sys
from pathlib import Path

from play import Edit, PlayError, call, step


def notes(directory: Path | None) -> list[dict]:
    if not directory or not directory.is_dir():
        return []
    return [
        {"language": f.stem, "text": f.read_text().strip()[:500]}
        for f in sorted(directory.glob("*.txt")) if f.read_text().strip()
    ]


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("bundle", type=Path)
    parser.add_argument("--track", default="internal")
    parser.add_argument("--notes-dir", type=Path)
    args = parser.parse_args()

    with Edit() as edit:
        uploaded = call(
            "POST", f"/edits/{edit.id}/bundles",
            data=args.bundle.read_bytes(), content_type="application/octet-stream",
            upload=True, params={"uploadType": "media"},
        )
        version_code = str(uploaded["versionCode"])
        step(f"Uploaded {args.bundle.name} as version code {version_code}")
        release = {"versionCodes": [version_code], "status": "completed", "releaseNotes": notes(args.notes_dir)}
        try:
            call("PUT", f"/edits/{edit.id}/tracks/{args.track}", {"track": args.track, "releases": [release]})
            edit.commit()
        except PlayError as error:
            if "draft" not in error.message.lower():
                raise
            # A never-published app only takes draft releases.
            step("The app isn't published yet: saving the release as a draft")
            release["status"] = "draft"
            call("PUT", f"/edits/{edit.id}/tracks/{args.track}", {"track": args.track, "releases": [release]})
            edit.commit()
        step(f"Release {version_code} is on the {args.track} track ({release['status']})")
    return 0


if __name__ == "__main__":
    sys.exit(main())

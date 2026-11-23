"""Pushes the Google Play store listing: texts in every language, the icon, the feature graphic
and phone screenshots.

  play_listing.py GRAPHICS_DIR
GRAPHICS_DIR holds:
  en-US/phoneScreenshots/*.png            (store/google-play/compose.py; shown for every language)
  <app-store-locale>-feature.png          (store/app-store/creative/render.js, one per language)

Texts come from store/google-play/listings/<language>/{title,short_description,full_description}.txt.
Environment: PLAY_SERVICE_ACCOUNT_JSON, optional SUPPORT_EMAIL, PACKAGE_NAME.
"""
import os
import sys
from pathlib import Path

from play import Edit, call, step

ROOT = Path(__file__).resolve().parent.parent / "store" / "google-play"
DEFAULT = "en-US"
# Play language code → the App Store locale its feature graphic was rendered for.
FEATURE = {"en-US": "en-US", "iw-IL": "he", "ar": "ar-SA", "ru-RU": "ru"}
LIMITS = {"title": 30, "short_description": 80, "full_description": 4000}


def read(language: str, field: str) -> str:
    text = (ROOT / "listings" / language / f"{field}.txt").read_text().strip()
    if len(text) > LIMITS[field]:
        raise SystemExit(f"{language}/{field}.txt is {len(text)} characters; Play allows {LIMITS[field]}.")
    return text


def replace_images(edit: Edit, language: str, kind: str, files: list[Path]) -> None:
    call("DELETE", f"/edits/{edit.id}/listings/{language}/{kind}")
    for file in files:
        call("POST", f"/edits/{edit.id}/listings/{language}/{kind}", data=file.read_bytes(),
             content_type="image/png", upload=True, params={"uploadType": "media"})


def main() -> int:
    graphics = Path(sys.argv[1])
    languages = sorted(p.name for p in (ROOT / "listings").iterdir() if p.is_dir())
    with Edit() as edit:
        details = {"defaultLanguage": DEFAULT, "contactWebsite": "https://thinkofme.web.app/support/"}
        if os.environ.get("SUPPORT_EMAIL"):
            details["contactEmail"] = os.environ["SUPPORT_EMAIL"]
        call("PATCH", f"/edits/{edit.id}/details", details)
        step(f"Default language {DEFAULT}")

        for language in languages:
            call("PUT", f"/edits/{edit.id}/listings/{language}", {
                "language": language,
                "title": read(language, "title"),
                "shortDescription": read(language, "short_description"),
                "fullDescription": read(language, "full_description"),
            })
            feature = graphics / f"{FEATURE.get(language, language)}-feature.png"
            if feature.exists():
                replace_images(edit, language, "featureGraphic", [feature])
            step(f"{language}: texts{' and feature graphic' if feature.exists() else ''}")

        replace_images(edit, DEFAULT, "icon", [ROOT / "icon-512.png"])
        shots = sorted((graphics / DEFAULT / "phoneScreenshots").glob("*.png"))
        if shots:
            replace_images(edit, DEFAULT, "phoneScreenshots", shots)
        step(f"Icon and {len(shots)} phone screenshots")
        edit.commit()
    step("Listing saved in Play Console")
    return 0


if __name__ == "__main__":
    sys.exit(main())

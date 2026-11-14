#!/usr/bin/env python3
"""Pushes the App Store listing to App Store Connect.

For the iOS version being prepared (created if needed): name, subtitle and privacy URL per
language, categories, description, keywords, promotional text, support and marketing URLs,
copyright, review notes and contact, the newest processed build, and screenshots.
It never submits for review: that stays a human step.

  python3 scripts/app_store_listing.py [SCREENSHOTS_DIR]

Environment: ASC_KEY_ID, ASC_ISSUER_ID, ASC_KEY_P8, BUNDLE_ID, VERSION (e.g. 1.0),
REVIEW_CONTACT ("First|Last|Phone|Email", optional), COPYRIGHT (optional), BUILD_NUMBER (optional).
SCREENSHOTS_DIR holds <locale>/<NN-name>.png (1290 x 2796) from store/app-store/compose.py.
"""
import hashlib
import json
import os
import sys
from pathlib import Path

import requests

from asc import ApiError, app_id, call, step

ROOT = Path(__file__).resolve().parents[1] / "store" / "app-store"
EDITABLE = {"PREPARE_FOR_SUBMISSION", "DEVELOPER_REJECTED", "REJECTED", "METADATA_REJECTED", "INVALID_BINARY"}
# The 6.9" slot is the one Apple requires; older API versions only know the 6.7" type for 1290 x 2796.
DISPLAY_TYPES = ["APP_IPHONE_69", "APP_IPHONE_67"]


def read(locale: str, field: str) -> str:
    return (ROOT / "metadata" / locale / f"{field}.txt").read_text().strip()


def upsert_localization(list_path: str, create_type: str, parent: tuple[str, str, str], locale: str, attributes: dict) -> str:
    """PATCH the localization for `locale` under list_path, or create it. Returns its id."""
    existing = call("GET", list_path)["data"]
    match = next((item for item in existing if item["attributes"]["locale"] == locale), None)
    if match:
        call("PATCH", f"/v1/{create_type}/{match['id']}", {"data": {"type": create_type, "id": match["id"], "attributes": attributes}})
        return match["id"]
    relation, parent_type, parent_id = parent
    created = call("POST", f"/v1/{create_type}", {"data": {
        "type": create_type,
        "attributes": {"locale": locale, **attributes},
        "relationships": {relation: {"data": {"type": parent_type, "id": parent_id}}},
    }})
    return created["data"]["id"]


def upload_screenshots(localization_id: str, files: list[Path]) -> None:
    sets = call("GET", f"/v1/appStoreVersionLocalizations/{localization_id}/appScreenshotSets")["data"]
    target = None
    for display_type in DISPLAY_TYPES:
        target = next((s for s in sets if s["attributes"]["screenshotDisplayType"] == display_type), None)
        if target:
            break
        try:
            target = call("POST", "/v1/appScreenshotSets", {"data": {
                "type": "appScreenshotSets",
                "attributes": {"screenshotDisplayType": display_type},
                "relationships": {"appStoreVersionLocalization": {"data": {"type": "appStoreVersionLocalizations", "id": localization_id}}},
            }})["data"]
            break
        except ApiError as error:
            if error.status not in (400, 409, 422):
                raise
    if target is None:
        raise RuntimeError("Could not create a screenshot set for the 6.9\" or 6.7\" iPhone display.")

    for old in call("GET", f"/v1/appScreenshotSets/{target['id']}/appScreenshots")["data"]:
        call("DELETE", f"/v1/appScreenshots/{old['id']}")

    for path in files:
        data = path.read_bytes()
        shot = call("POST", "/v1/appScreenshots", {"data": {
            "type": "appScreenshots",
            "attributes": {"fileName": path.name, "fileSize": len(data)},
            "relationships": {"appScreenshotSet": {"data": {"type": "appScreenshotSets", "id": target["id"]}}},
        }})["data"]
        for operation in shot["attributes"]["uploadOperations"]:
            headers = {h["name"]: h["value"] for h in operation.get("requestHeaders", [])}
            chunk = data[operation["offset"]: operation["offset"] + operation["length"]]
            response = requests.request(operation["method"], operation["url"], data=chunk, headers=headers, timeout=120)
            response.raise_for_status()
        call("PATCH", f"/v1/appScreenshots/{shot['id']}", {"data": {
            "type": "appScreenshots", "id": shot["id"],
            "attributes": {"uploaded": True, "sourceFileChecksum": hashlib.md5(data).hexdigest()},
        }})


def main() -> int:
    config = json.loads((ROOT / "config.json").read_text())
    locales = sorted(p.name for p in (ROOT / "metadata").iterdir() if p.is_dir())
    screenshots = Path(sys.argv[1]) if len(sys.argv) > 1 else None
    version_string = os.environ.get("VERSION") or "1.0"

    app = app_id(os.environ["BUNDLE_ID"])
    step("App found")

    # App information: categories, then name, subtitle and privacy policy per language.
    infos = call("GET", f"/v1/apps/{app}/appInfos")["data"]
    info = next((i for i in infos if (i["attributes"].get("state") or i["attributes"].get("appStoreState")) not in ("READY_FOR_DISTRIBUTION", "READY_FOR_SALE")), infos[0])
    call("PATCH", f"/v1/appInfos/{info['id']}", {"data": {
        "type": "appInfos", "id": info["id"],
        "relationships": {
            "primaryCategory": {"data": {"type": "appCategories", "id": config["primary_category"]}},
            "secondaryCategory": {"data": {"type": "appCategories", "id": config["secondary_category"]}},
        },
    }})
    step(f"Categories: {config['primary_category']}, {config['secondary_category']}")
    for locale in locales:
        upsert_localization(
            f"/v1/appInfos/{info['id']}/appInfoLocalizations", "appInfoLocalizations", ("appInfo", "appInfos", info["id"]), locale,
            {"name": read(locale, "name"), "subtitle": read(locale, "subtitle"), "privacyPolicyUrl": config["privacy_url"]},
        )
    step(f"Name and subtitle in {', '.join(locales)}")

    # The version being prepared.
    versions = call("GET", f"/v1/apps/{app}/appStoreVersions?filter[platform]=IOS&limit=20")["data"]
    version = next((v for v in versions if v["attributes"]["appStoreState"] in EDITABLE), None)
    if version is None:
        version = call("POST", "/v1/appStoreVersions", {"data": {
            "type": "appStoreVersions",
            "attributes": {"platform": "IOS", "versionString": version_string},
            "relationships": {"app": {"data": {"type": "apps", "id": app}}},
        }})["data"]
        step(f"Created version {version_string}")
    else:
        step(f"Preparing version {version['attributes']['versionString']}")
    call("PATCH", f"/v1/appStoreVersions/{version['id']}", {"data": {
        "type": "appStoreVersions", "id": version["id"],
        "attributes": {"copyright": os.environ.get("COPYRIGHT") or config["copyright_default"], "releaseType": "MANUAL"},
    }})

    localization_ids = {}
    for locale in locales:
        localization_ids[locale] = upsert_localization(
            f"/v1/appStoreVersions/{version['id']}/appStoreVersionLocalizations", "appStoreVersionLocalizations",
            ("appStoreVersion", "appStoreVersions", version["id"]), locale,
            {
                "description": read(locale, "description"),
                "keywords": read(locale, "keywords"),
                "promotionalText": read(locale, "promotional_text"),
                "supportUrl": config["support_url"],
                "marketingUrl": config["marketing_url"],
            },
        )
    step("Description, keywords, promotional text and URLs filled in")

    # Review information.
    review = {"notes": (ROOT / "review_notes.txt").read_text().strip(), "demoAccountRequired": False}
    contact = [p.strip() for p in os.environ.get("REVIEW_CONTACT", "").split("|")]
    if len(contact) == 4 and all(contact):
        review.update(contactFirstName=contact[0], contactLastName=contact[1], contactPhone=contact[2], contactEmail=contact[3])
    try:
        detail = call("GET", f"/v1/appStoreVersions/{version['id']}/appStoreReviewDetail")["data"]
    except ApiError:
        detail = None
    if detail:
        call("PATCH", f"/v1/appStoreReviewDetails/{detail['id']}", {"data": {"type": "appStoreReviewDetails", "id": detail["id"], "attributes": review}})
    else:
        call("POST", "/v1/appStoreReviewDetails", {"data": {
            "type": "appStoreReviewDetails", "attributes": review,
            "relationships": {"appStoreVersion": {"data": {"type": "appStoreVersions", "id": version["id"]}}},
        }})
    step("Review notes" + (" and contact" if "contactEmail" in review else " (add TESTFLIGHT_REVIEW_CONTACT for the contact)"))

    # Attach the build.
    query = f"/v1/builds?filter[app]={app}&filter[processingState]=VALID&filter[preReleaseVersion.version]={version['attributes']['versionString']}&sort=-uploadedDate&limit=1"
    if os.environ.get("BUILD_NUMBER"):
        query = f"/v1/builds?filter[app]={app}&filter[version]={os.environ['BUILD_NUMBER']}&limit=1"
    builds = call("GET", query)["data"]
    if builds:
        call("PATCH", f"/v1/appStoreVersions/{version['id']}/relationships/build", {"data": {"type": "builds", "id": builds[0]["id"]}})
        step(f"Build {builds[0]['attributes']['version']} attached")
    else:
        step("No processed build for this version yet: attach one later")

    # Screenshots.
    if screenshots and screenshots.is_dir():
        for locale in locales:
            files = sorted((screenshots / locale).glob("*.png"))
            if files:
                upload_screenshots(localization_ids[locale], files)
                step(f"{len(files)} screenshots uploaded for {locale}")

    summary = (
        "## App Store listing updated\n"
        f"- Version {version['attributes']['versionString']}, languages: {', '.join(locales)}\n"
        "- Still to do by hand in App Store Connect: App Privacy, Age Rating, Pricing and Availability, then Submit for Review.\n"
    )
    print(summary)
    if os.environ.get("GITHUB_STEP_SUMMARY"):
        with open(os.environ["GITHUB_STEP_SUMMARY"], "a") as handle:
            handle.write(summary)
    return 0


if __name__ == "__main__":
    try:
        sys.exit(main())
    except (ApiError, RuntimeError) as error:
        print(f"::error::{error}")
        sys.exit(1)

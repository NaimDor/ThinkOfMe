"""Uploads the App Store creative assets (product page header and search results art) and
places them on the version being prepared, one pair per language.

Usage: app_store_creative.py <dir>   where <dir> holds <locale>-header.png and <locale>-search.png
(store/app-store/creative/render.js writes them). Locales follow store/app-store/metadata.

Flow, per Apple's App Store Connect API (OpenAPI 4.6, "App Asset Library"):
  1. GET  /v1/apps/{app}/assetLibrary                      the app's library
  2. GET  /v1/appAssetLibraryRefData                       which placement groups accept which image specs
  3. POST /v1/appAssetLibraryImages (category CREATIVE_ASSETS) → upload the parts in uploadOperations
     → PATCH uploaded=true → wait until processed (state leaves AWAITING_UPLOAD / UPLOAD_COMPLETE)
  4. per locale: replace the PRODUCT_PAGE_HEADER_ASSET / APP_STORE_SEARCH_RESULTS_ASSET placements on
     the appStoreVersionLocalization with the new image, in every iPhone placement group for its spec.

Re-running is safe: an image whose bytes are already in the library (same md5 in its reference
name) is reused, and placements already pointing at it are kept. Nothing is submitted for review:
the assets go to App Review with the version.

Environment: ASC_KEY_ID, ASC_ISSUER_ID, ASC_KEY_P8, BUNDLE_ID, optional VERSION (default 1.0).
"""
import hashlib
import os
import sys
import time
from pathlib import Path

import requests

from asc import ApiError, app_id, call, step

ROOT = Path(__file__).resolve().parent.parent / "store" / "app-store"
EDITABLE = {"PREPARE_FOR_SUBMISSION", "DEVELOPER_REJECTED", "REJECTED", "METADATA_REJECTED", "INVALID_BINARY"}
KINDS = {"header": "PRODUCT_PAGE_HEADER_ASSET", "search": "APP_STORE_SEARCH_RESULTS_ASSET"}
# ThinkOfMe is iPhone only (TARGETED_DEVICE_FAMILY 1).
PLATFORMS = {"IPHONE_APP_STORE", "ANY"}
PROCESSING = {"AWAITING_UPLOAD", "UPLOAD_COMPLETE"}
DEAD = {"FAILED", "ARCHIVED", "REJECTED"}


def all_pages(path: str) -> list[dict]:
    items, url = [], path
    while url:
        page = call("GET", url)
        items += page.get("data", [])
        nxt = (page.get("links") or {}).get("next")
        url = nxt.replace("https://api.appstoreconnect.apple.com", "") if nxt else None
    return items


def upload_image(library: str, file: Path, reference: str, existing: list[dict]) -> dict:
    """Returns the processed image resource, reusing an identical one already in the library."""
    same = [i for i in existing if i["attributes"].get("referenceName") == reference]
    for image in same:
        state = image["attributes"].get("state")
        if state not in DEAD and state != "AWAITING_UPLOAD":
            step(f"  {file.name}: already in the library ({state})")
            return wait_processed(image["id"], file.name)
        if state in {"FAILED", "AWAITING_UPLOAD"}:
            call("DELETE", f"/v1/appAssetLibraryImages/{image['id']}")
    data = file.read_bytes()
    image = call("POST", "/v1/appAssetLibraryImages", {"data": {
        "type": "appAssetLibraryImages",
        "attributes": {"category": "CREATIVE_ASSETS", "fileName": file.name, "fileSize": len(data), "referenceName": reference},
        "relationships": {"assetLibrary": {"data": {"type": "appAssetLibraries", "id": library}}},
    }})["data"]
    for op in image["attributes"].get("uploadOperations") or []:
        headers = {h["name"]: h["value"] for h in op.get("requestHeaders") or []}
        part = data[op["offset"]:op["offset"] + op["length"]]
        response = requests.request(op["method"], op["url"], headers=headers, data=part, timeout=120)
        if response.status_code >= 300:
            raise RuntimeError(f"Uploading {file.name} failed: HTTP {response.status_code} {response.text[:300]}")
    call("PATCH", f"/v1/appAssetLibraryImages/{image['id']}", {"data": {
        "type": "appAssetLibraryImages", "id": image["id"], "attributes": {"uploaded": True},
    }})
    step(f"  {file.name}: uploaded")
    return wait_processed(image["id"], file.name)


def wait_processed(image_id: str, name: str) -> dict:
    for _ in range(60):
        image = call("GET", f"/v1/appAssetLibraryImages/{image_id}")["data"]
        state = image["attributes"].get("state")
        if state == "FAILED":
            details = "; ".join(f"{d.get('code')}: {d.get('description')}" for d in image["attributes"].get("stateDetails") or [])
            raise RuntimeError(f"Apple rejected {name}: {details or 'no details'}")
        if state not in PROCESSING:
            return image
        time.sleep(5)
    raise RuntimeError(f"{name} is still processing after 5 minutes; run the workflow again later.")


def groups_for(ref: list[dict], placement_type: str, spec_id: str | None) -> list[str]:
    """iPhone placement groups of this placement type that accept the image's spec."""
    platforms = {}
    groups = []
    for entry in ref:
        attrs = entry.get("attributes") or {}
        for g in attrs.get("placementProfileGroups") or []:
            platforms[g.get("placementProfileGroupId")] = g.get("platform")
        for t in attrs.get("placementTypes") or []:
            if t.get("placementTypeId") != placement_type:
                continue
            for m in t.get("specMappings") or []:
                if spec_id is None or spec_id in (m.get("specs") or []):
                    groups.append(m.get("placementGroupId"))
    unique = list(dict.fromkeys(g for g in groups if g))
    iphone = [g for g in unique if platforms.get(g, "ANY") in PLATFORMS]
    return iphone or unique


def place(localization: str, placement_type: str, image: dict, groups: list[str]) -> None:
    current = all_pages(
        f"/v1/appStoreVersionLocalizations/{localization}/placements?filter[placementType]={placement_type}&include=image&limit=200"
    )
    keep = set()
    for p in current:
        rel = ((p.get("relationships") or {}).get("image") or {}).get("data") or {}
        if rel.get("id") == image["id"] and p["attributes"].get("placementGroup") in groups:
            keep.add(p["attributes"].get("placementGroup"))
        else:
            call("DELETE", f"/v1/appAssetLibraryPlacements/{p['id']}")
    for group in groups or [None]:
        if group in keep:
            continue
        attributes = {"placementType": placement_type}
        if group:
            attributes["placementGroup"] = group
        try:
            call("POST", "/v1/appAssetLibraryPlacements", {"data": {
                "type": "appAssetLibraryPlacements",
                "attributes": attributes,
                "relationships": {
                    "image": {"data": {"type": "appAssetLibraryImages", "id": image["id"]}},
                    "appStoreVersionLocalization": {"data": {"type": "appStoreVersionLocalizations", "id": localization}},
                },
            }})
        except ApiError as error:
            if error.status == 409 and len(groups) > 1:
                print(f"    skipped placement group {group}: {error.details}", flush=True)
                continue
            raise


def main() -> int:
    source = Path(sys.argv[1])
    locales = sorted(p.name for p in (ROOT / "metadata").iterdir() if p.is_dir())
    version_string = os.environ.get("VERSION") or "1.0"

    app = app_id(os.environ["BUNDLE_ID"])
    library = call("GET", f"/v1/apps/{app}/assetLibrary")["data"]["id"]
    step("Asset library found")
    ref = all_pages("/v1/appAssetLibraryRefData?filter[placementTypes]=" + ",".join(KINDS.values()))

    versions = call("GET", f"/v1/apps/{app}/appStoreVersions?filter[platform]=IOS&limit=20")["data"]
    version = next((v for v in versions if v["attributes"]["appStoreState"] in EDITABLE), None)
    if version is None:
        print("::error::No editable App Store version. Run the App Store listing workflow first.")
        return 1
    localizations = {
        loc["attributes"]["locale"]: loc["id"]
        for loc in call("GET", f"/v1/appStoreVersions/{version['id']}/appStoreVersionLocalizations?limit=50")["data"]
    }
    step(f"Version {version['attributes']['versionString']} ({version_string} requested), languages: {', '.join(sorted(localizations))}")

    existing = all_pages(f"/v1/appAssetLibraries/{library}/images?filter[category]=CREATIVE_ASSETS&limit=200")
    for locale in locales:
        if locale not in localizations:
            print(f"::warning::{locale} has no localization on the version yet; skipped.")
            continue
        step(locale)
        for kind, placement_type in KINDS.items():
            file = source / f"{locale}-{kind}.png"
            if not file.exists():
                print(f"::warning::Missing {file.name}; skipped.")
                continue
            digest = hashlib.md5(file.read_bytes()).hexdigest()
            image = upload_image(library, file, f"ThinkOfMe {locale} {kind} [md5 {digest}]", existing)
            groups = groups_for(ref, placement_type, image["attributes"].get("specId"))
            place(localizations[locale], placement_type, image, groups)
            step(f"  {kind}: placed ({len(groups) or 1} placement group{'s' if len(groups) != 1 else ''})")

    summary = os.environ.get("GITHUB_STEP_SUMMARY")
    if summary:
        with open(summary, "a") as f:
            f.write(
                "## Creative assets uploaded\n\n"
                f"Header and search results art placed on version {version['attributes']['versionString']} for "
                f"{', '.join(l for l in locales if l in localizations)}. They are reviewed with the version; "
                "check them in App Store Connect with the product page preview.\n"
            )
    return 0


if __name__ == "__main__":
    sys.exit(main())

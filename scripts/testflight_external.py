#!/usr/bin/env python3
"""Prepare external TestFlight testing through the App Store Connect API.

Fills Test Information and Beta App Review details, creates the external
"Friends" group with a public link, adds the newest processed build with a
"What to test" note, and submits it for Beta App Review.

Environment: ASC_KEY_ID, ASC_ISSUER_ID, ASC_KEY_P8 (key contents), BUNDLE_ID,
FEEDBACK_EMAIL, REVIEW_CONTACT ("First|Last|Phone|Email", optional when the
review contact is already filled in App Store Connect), GROUP_NAME, BUILD_NUMBER
(optional; default newest).
"""
import json
import os
import sys
import time

import jwt
import requests

API = "https://api.appstoreconnect.apple.com"

DESCRIPTION = (
    "ThinkOfMe is a shared gift wish list for couples, families and friends. "
    "Add wishes from a link or a photo, share them with your circles, and secretly "
    "mark the gifts you're buying - the person never finds out."
)
WHAT_TO_TEST = (
    "Sign in, create a circle and invite your partner or friends with the invite link. "
    "Add wishes from a store link or a photo (details are filled in for you), add birthdays "
    "and anniversaries, and mark a friend's wish as 'I'm getting this'. "
    "Tell us anything confusing, broken or awkwardly translated: take a screenshot and tap Share Beta Feedback."
)
REVIEW_NOTES = (
    "Sign in with Apple or Google - no demo account is needed. After signing in, create a circle "
    "from the Circles tab and add wishes with the + button. Adding a photo to a new wish reads the "
    "product's name, store and price from it. Ads are Google test ads during beta."
)


def token() -> str:
    now = int(time.time())
    return jwt.encode(
        {"iss": os.environ["ASC_ISSUER_ID"], "iat": now, "exp": now + 15 * 60, "aud": "appstoreconnect-v1"},
        os.environ["ASC_KEY_P8"],
        algorithm="ES256",
        headers={"kid": os.environ["ASC_KEY_ID"], "typ": "JWT"},
    )


SESSION = requests.Session()


def call(method: str, path: str, body: dict | None = None, ok=(200, 201, 204)) -> dict:
    SESSION.headers["Authorization"] = f"Bearer {token()}"
    response = SESSION.request(method, API + path, json=body, timeout=60)
    if response.status_code not in ok:
        errors = response.json().get("errors", []) if response.content else []
        details = "; ".join(f"{e.get('title')}: {e.get('detail')}" for e in errors) or response.text[:500]
        raise RuntimeError(f"{method} {path} -> {response.status_code}: {details}")
    return response.json() if response.content else {}


def step(message: str) -> None:
    print(f"• {message}", flush=True)


def main() -> int:
    bundle_id = os.environ["BUNDLE_ID"]
    group_name = os.environ.get("GROUP_NAME") or "Friends"
    feedback_email = os.environ.get("FEEDBACK_EMAIL", "").strip()

    apps = call("GET", f"/v1/apps?filter[bundleId]={bundle_id}")["data"]
    if not apps:
        print(f"::error::No app with bundle id {bundle_id} in App Store Connect.")
        return 1
    app_id = apps[0]["id"]
    step(f"App found ({apps[0]['attributes']['name']})")

    # Test Information (English).
    attributes = {
        "description": DESCRIPTION,
        "privacyPolicyUrl": "https://thinkofme.web.app/privacy/",
        "marketingUrl": "https://thinkofme.web.app/",
    }
    if feedback_email:
        attributes["feedbackEmail"] = feedback_email
    localizations = call("GET", f"/v1/apps/{app_id}/betaAppLocalizations")["data"]
    english = next((l for l in localizations if l["attributes"]["locale"].startswith("en")), None)
    if english:
        call("PATCH", f"/v1/betaAppLocalizations/{english['id']}",
             {"data": {"type": "betaAppLocalizations", "id": english["id"], "attributes": attributes}})
    else:
        call("POST", "/v1/betaAppLocalizations", {"data": {
            "type": "betaAppLocalizations", "attributes": {"locale": "en-US", **attributes},
            "relationships": {"app": {"data": {"type": "apps", "id": app_id}}}}})
    step("Test Information filled in")

    # Beta App Review details.
    detail = call("GET", f"/v1/apps/{app_id}/betaAppReviewDetail")["data"]
    review = {"notes": REVIEW_NOTES, "demoAccountRequired": False}
    contact = os.environ.get("REVIEW_CONTACT", "").strip()
    if contact:
        parts = [p.strip() for p in contact.split("|")]
        if len(parts) != 4 or not all(parts):
            print("::error::TESTFLIGHT_REVIEW_CONTACT must look like First|Last|+972501234567|name@example.com")
            return 1
        review.update(contactFirstName=parts[0], contactLastName=parts[1], contactPhone=parts[2], contactEmail=parts[3])
    elif not all(detail["attributes"].get(k) for k in ("contactFirstName", "contactLastName", "contactPhone", "contactEmail")):
        print("::error::Add the TESTFLIGHT_REVIEW_CONTACT secret (First|Last|Phone|Email) - Apple needs a contact for the review.")
        return 1
    call("PATCH", f"/v1/betaAppReviewDetails/{detail['id']}",
         {"data": {"type": "betaAppReviewDetails", "id": detail["id"], "attributes": review}})
    step("Beta App Review details filled in")

    # External group with a public link.
    groups = call("GET", f"/v1/betaGroups?filter[app]={app_id}&filter[name]={requests.utils.quote(group_name)}")["data"]
    group = next((g for g in groups if not g["attributes"].get("isInternalGroup")), None)
    if group is None:
        group = call("POST", "/v1/betaGroups", {"data": {
            "type": "betaGroups",
            "attributes": {"name": group_name, "publicLinkEnabled": True, "publicLinkLimitEnabled": False, "feedbackEnabled": True},
            "relationships": {"app": {"data": {"type": "apps", "id": app_id}}}}})["data"]
        step(f"Created external group '{group_name}'")
    elif not group["attributes"].get("publicLinkEnabled"):
        group = call("PATCH", f"/v1/betaGroups/{group['id']}", {"data": {
            "type": "betaGroups", "id": group["id"], "attributes": {"publicLinkEnabled": True, "publicLinkLimitEnabled": False}}})["data"]
        step(f"Public link switched on for '{group_name}'")
    else:
        step(f"External group '{group_name}' ready")

    # Newest processed build (or the one asked for).
    query = f"/v1/builds?filter[app]={app_id}&filter[processingState]=VALID&sort=-uploadedDate&limit=1"
    if os.environ.get("BUILD_NUMBER"):
        query = f"/v1/builds?filter[app]={app_id}&filter[version]={os.environ['BUILD_NUMBER']}&limit=1"
    builds = call("GET", query)["data"]
    if not builds:
        print("::error::No processed build found. Wait for Apple to finish processing the latest upload and run again.")
        return 1
    build = builds[0]
    build_id, number = build["id"], build["attributes"]["version"]
    step(f"Using build {number}")

    # "What to test".
    notes = call("GET", f"/v1/builds/{build_id}/betaBuildLocalizations")["data"]
    english_notes = next((n for n in notes if n["attributes"]["locale"].startswith("en")), None)
    if english_notes:
        call("PATCH", f"/v1/betaBuildLocalizations/{english_notes['id']}",
             {"data": {"type": "betaBuildLocalizations", "id": english_notes["id"], "attributes": {"whatsNew": WHAT_TO_TEST}}})
    else:
        call("POST", "/v1/betaBuildLocalizations", {"data": {
            "type": "betaBuildLocalizations", "attributes": {"locale": "en-US", "whatsNew": WHAT_TO_TEST},
            "relationships": {"build": {"data": {"type": "builds", "id": build_id}}}}})
    step("'What to test' filled in")

    call("POST", f"/v1/betaGroups/{group['id']}/relationships/builds",
         {"data": [{"type": "builds", "id": build_id}]}, ok=(200, 201, 204, 409))
    step(f"Build {number} added to '{group_name}'")

    # Submit for Beta App Review unless it already was.
    state = call("GET", f"/v1/builds/{build_id}/buildBetaDetail")["data"]["attributes"].get("externalBuildState")
    if state in (None, "READY_FOR_BETA_SUBMISSION", "MISSING_EXPORT_COMPLIANCE"):
        call("POST", "/v1/betaAppReviewSubmissions", {"data": {
            "type": "betaAppReviewSubmissions",
            "relationships": {"build": {"data": {"type": "builds", "id": build_id}}}}})
        state = "WAITING_FOR_BETA_REVIEW"
        step("Submitted for Beta App Review")
    else:
        step(f"Review state: {state}")

    group = call("GET", f"/v1/betaGroups/{group['id']}")["data"]
    link = group["attributes"].get("publicLink")
    summary = [
        "## TestFlight external testing",
        f"- Build: **{number}**",
        f"- Review state: **{state}**",
        f"- Public link: {link or '(appears once the group has an approved build)'}",
        "",
        "Send the link to testers after the review is approved. Testers install TestFlight, open the link, then the circle invite.",
    ]
    print("\n".join(summary))
    if os.environ.get("GITHUB_STEP_SUMMARY"):
        with open(os.environ["GITHUB_STEP_SUMMARY"], "a") as handle:
            handle.write("\n".join(summary) + "\n")
    return 0


if __name__ == "__main__":
    try:
        sys.exit(main())
    except RuntimeError as error:
        print(f"::error::{error}")
        sys.exit(1)

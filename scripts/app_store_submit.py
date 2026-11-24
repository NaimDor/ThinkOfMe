"""Submits the App Store version being prepared for App Review. Release stays manual:
after approval nothing goes live until someone presses "Release This Version".

Checks first: the version is editable (Prepare for Submission or rejected) and has a build.
Then: reuse an open review submission or create one, add the version, submit.

Environment: ASC_KEY_ID, ASC_ISSUER_ID, ASC_KEY_P8, BUNDLE_ID, optional VERSION (default 1.0).
"""
import os
import sys

from asc import ApiError, app_id, call, step

EDITABLE = {"PREPARE_FOR_SUBMISSION", "DEVELOPER_REJECTED", "REJECTED", "METADATA_REJECTED", "INVALID_BINARY"}
OPEN = {"READY_FOR_REVIEW"}


def main() -> int:
    want = os.environ.get("VERSION") or "1.0"
    app = app_id(os.environ["BUNDLE_ID"])
    versions = call("GET", f"/v1/apps/{app}/appStoreVersions?filter[platform]=IOS&filter[versionString]={want}")["data"]
    if not versions:
        print(f"::error::No iOS version {want} in App Store Connect.")
        return 1
    version = versions[0]
    state = version["attributes"].get("appStoreState") or version["attributes"].get("appVersionState")
    if state not in EDITABLE:
        print(f"::error::Version {want} is {state}; only a version being prepared (or rejected) can be submitted.")
        return 1
    build = call("GET", f"/v1/appStoreVersions/{version['id']}/build").get("data")
    if not build:
        print(f"::error::Version {want} has no build attached. Run the App Store listing workflow first.")
        return 1
    step(f"Version {want} ({state}), build {build['attributes'].get('version')}, release type "
         f"{version['attributes'].get('releaseType', '?')}")

    submissions = call("GET", f"/v1/reviewSubmissions?filter[app]={app}&filter[platform]=IOS&limit=20")["data"]
    submission = next((s for s in submissions if s["attributes"].get("state") in OPEN), None)
    if submission is None:
        submission = call("POST", "/v1/reviewSubmissions", {"data": {
            "type": "reviewSubmissions",
            "attributes": {"platform": "IOS"},
            "relationships": {"app": {"data": {"type": "apps", "id": app}}},
        }})["data"]
        step("Created a review submission")
    else:
        step("Using the open review submission")

    items = call("GET", f"/v1/reviewSubmissions/{submission['id']}/items?include=appStoreVersion")["data"]
    has_version = any(
        ((i.get("relationships") or {}).get("appStoreVersion") or {}).get("data", {}).get("id") == version["id"]
        for i in items
    )
    if not has_version:
        try:
            call("POST", "/v1/reviewSubmissionItems", {"data": {
                "type": "reviewSubmissionItems",
                "relationships": {
                    "reviewSubmission": {"data": {"type": "reviewSubmissions", "id": submission["id"]}},
                    "appStoreVersion": {"data": {"type": "appStoreVersions", "id": version["id"]}},
                },
            }})
        except ApiError as error:
            # Apple lists what's missing (privacy, age rating, screenshots, ...) in the details.
            print(f"::error::Apple won't add version {want} to review yet: {error.details}")
            return 1
        step(f"Added version {want}")

    try:
        call("PATCH", f"/v1/reviewSubmissions/{submission['id']}", {"data": {
            "type": "reviewSubmissions", "id": submission["id"], "attributes": {"submitted": True},
        }})
    except ApiError as error:
        print(f"::error::Submitting failed: {error.details}")
        return 1
    step("Submitted for App Review. Release is manual: press Release This Version after approval.")
    summary = os.environ.get("GITHUB_STEP_SUMMARY")
    if summary:
        with open(summary, "a") as f:
            f.write(f"## Submitted for review\n\nVersion {want}, build {build['attributes'].get('version')}. "
                    "Apple usually reviews within 1–2 days. Release is manual.\n")
    return 0


if __name__ == "__main__":
    sys.exit(main())

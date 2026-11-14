"""Small App Store Connect API client shared by the release scripts.

Environment: ASC_KEY_ID, ASC_ISSUER_ID, ASC_KEY_P8 (the .p8 key contents).
"""
import os
import time

import jwt
import requests

API = "https://api.appstoreconnect.apple.com"
SESSION = requests.Session()


class ApiError(RuntimeError):
    def __init__(self, method: str, path: str, status: int, details: str):
        super().__init__(f"{method} {path} -> {status}: {details}")
        self.status = status
        self.details = details


def token() -> str:
    now = int(time.time())
    return jwt.encode(
        {"iss": os.environ["ASC_ISSUER_ID"], "iat": now, "exp": now + 15 * 60, "aud": "appstoreconnect-v1"},
        os.environ["ASC_KEY_P8"],
        algorithm="ES256",
        headers={"kid": os.environ["ASC_KEY_ID"], "typ": "JWT"},
    )


def call(method: str, path: str, body: dict | None = None, ok=(200, 201, 204)) -> dict:
    SESSION.headers["Authorization"] = f"Bearer {token()}"
    response = SESSION.request(method, API + path, json=body, timeout=60)
    if response.status_code not in ok:
        errors = response.json().get("errors", []) if response.content else []
        details = "; ".join(f"{e.get('title')}: {e.get('detail')}" for e in errors) or response.text[:500]
        raise ApiError(method, path, response.status_code, details)
    return response.json() if response.content else {}


def app_id(bundle_id: str) -> str:
    apps = call("GET", f"/v1/apps?filter[bundleId]={bundle_id}")["data"]
    if not apps:
        raise RuntimeError(f"No app with bundle id {bundle_id} in App Store Connect.")
    return apps[0]["id"]


def step(message: str) -> None:
    print(f"• {message}", flush=True)

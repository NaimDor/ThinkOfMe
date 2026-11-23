"""Small Google Play Developer API (androidpublisher v3) client shared by the Play scripts.

Environment: PLAY_SERVICE_ACCOUNT_JSON (the service account key file contents),
PACKAGE_NAME (default com.thinkofme.app).
"""
import json
import os
import time

import jwt
import requests

API = "https://androidpublisher.googleapis.com/androidpublisher/v3/applications"
UPLOAD = "https://androidpublisher.googleapis.com/upload/androidpublisher/v3/applications"
PACKAGE = os.environ.get("PACKAGE_NAME") or "com.thinkofme.app"
SESSION = requests.Session()
_token: tuple[str, float] | None = None


class PlayError(RuntimeError):
    def __init__(self, method: str, path: str, status: int, message: str):
        super().__init__(f"{method} {path} -> {status}: {message}")
        self.status = status
        self.message = message


def token() -> str:
    global _token
    if _token and _token[1] > time.time() + 60:
        return _token[0]
    account = json.loads(os.environ["PLAY_SERVICE_ACCOUNT_JSON"])
    now = int(time.time())
    assertion = jwt.encode(
        {
            "iss": account["client_email"],
            "scope": "https://www.googleapis.com/auth/androidpublisher",
            "aud": "https://oauth2.googleapis.com/token",
            "iat": now,
            "exp": now + 3600,
        },
        account["private_key"],
        algorithm="RS256",
    )
    response = requests.post(
        "https://oauth2.googleapis.com/token",
        data={"grant_type": "urn:ietf:params:oauth:grant-type:jwt-bearer", "assertion": assertion},
        timeout=60,
    )
    response.raise_for_status()
    body = response.json()
    _token = (body["access_token"], now + int(body.get("expires_in", 3600)))
    return _token[0]


def call(method: str, path: str, body: dict | None = None, *, data: bytes | None = None,
         content_type: str | None = None, upload: bool = False, params: dict | None = None) -> dict:
    url = (UPLOAD if upload else API) + f"/{PACKAGE}{path}"
    headers = {"Authorization": f"Bearer {token()}"}
    if content_type:
        headers["Content-Type"] = content_type
    response = SESSION.request(method, url, json=body, data=data, headers=headers, params=params, timeout=600)
    if response.status_code >= 300:
        try:
            message = response.json().get("error", {}).get("message", response.text[:500])
        except ValueError:
            message = response.text[:500]
        raise PlayError(method, path, response.status_code, message)
    return response.json() if response.content else {}


class Edit:
    """A Play Console edit: every change goes into one, then commit() publishes them together."""

    def __enter__(self):
        self.id = call("POST", "/edits")["id"]
        return self

    def __exit__(self, kind, value, trace):
        if kind is not None:
            try:
                call("DELETE", f"/edits/{self.id}")
            except PlayError:
                pass
        return False

    def commit(self, *, changes_not_sent_for_review: bool = False) -> None:
        params = {"changesNotSentForReview": "true"} if changes_not_sent_for_review else None
        call("POST", f"/edits/{self.id}:commit", params=params)


def step(message: str) -> None:
    print(f"• {message}", flush=True)

"""Revokes the Apple Development certificates that earlier CI builds created.

Each TestFlight run archives on a fresh Mac, and `xcodebuild -allowProvisioningUpdates`
creates a new development certificate there whose private key disappears with the
machine. Apple caps how many a team can have, so after a dozen runs archiving fails
with "Your account has reached the maximum number of certificates".

Only certificates created through the API key ("Created via API") are revoked; a
certificate made in Xcode on your own Mac has your name and is left alone.
Distribution certificates are never touched.

Environment: ASC_KEY_ID, ASC_ISSUER_ID, ASC_KEY_P8.
"""
from asc import call, step

DEVELOPMENT = {"DEVELOPMENT", "IOS_DEVELOPMENT"}


def main() -> None:
    certificates = call("GET", "/v1/certificates?limit=200")["data"]
    stale = [
        c for c in certificates
        if c["attributes"].get("certificateType") in DEVELOPMENT
        and "Created via API" in f'{c["attributes"].get("name", "")} {c["attributes"].get("displayName", "")}'
    ]
    step(f"{len(certificates)} certificates on the team, {len(stale)} left over from CI builds.")
    for c in stale:
        call("DELETE", f'/v1/certificates/{c["id"]}')
        step(f'Revoked {c["attributes"].get("name")} (expires {c["attributes"].get("expirationDate", "?")[:10]}).')


if __name__ == "__main__":
    main()

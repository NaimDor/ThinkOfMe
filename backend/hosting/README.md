# Hosting

Public pages and app-link files, served by Firebase Hosting:

- `/i/<CODE>`: invite landing page (opens the app if installed, otherwise shows the code)
- `/privacy/`, `/terms/`, `/support/`: store-required pages
- `/.well-known/apple-app-site-association` and `/.well-known/assetlinks.json`: generated at deploy
  time by `.github/workflows/deploy-backend.yml` from the `APPLE_TEAM_ID` and `ANDROID_CERT_SHA256`
  secrets, so invite links open the apps directly.

`__SUPPORT_EMAIL__` in the pages is replaced at deploy time with the `SUPPORT_EMAIL` repository variable.
The site is chosen by the `FIREBASE_HOSTING_SITE` repository variable (default: the project's own site).

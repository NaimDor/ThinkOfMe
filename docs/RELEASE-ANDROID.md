# Releasing ThinkOfMe on Google Play

Automated: the signed app bundle, uploads to a testing track, and the store listing (texts in
four languages, icon, feature graphic, screenshots) through **Actions → Google Play release**.
Below are the steps only you can do, in order, with the answers prepared.

Package name: `com.thinkofme.app`. Languages: en-US (default), iw-IL (Hebrew), ar, ru-RU.

## 1. GitHub secrets (one time)

| Secret | Value |
|---|---|
| `ANDROID_UPLOAD_KEYSTORE_B64` | the long line from the upload key file Claude sent |
| `ANDROID_UPLOAD_KEYSTORE_PASSWORD` | the password from the same file |
| `ADMOB_APP_ID_ANDROID`, `ADMOB_NATIVE_UNIT_ANDROID`, `ADMOB_BANNER_UNIT_ANDROID` | already set |
| `GOOGLE_SERVICES_JSON_B64` | the Firebase Android app's `google-services.json`: paste the file's contents as they are (base64 also works). Create the app first: Firebase console → Project settings → Add app → Android, package `com.thinkofme.app`, with the upload key's SHA-1 |
| `PLAY_SERVICE_ACCOUNT_JSON` | step 4 |

Keep the upload key file in a password manager. If it is ever lost, Play Console can reset the
upload key (App integrity → Request upload key reset); your app signing key stays with Google.

The workflow runs from the `main` branch's list of workflows: merge the release branch into
`main` first (or push a tag `android-v1.0` to build and upload from any branch).

## 2. Create the app in Play Console

Home → **Create app**: name `ThinkOfMe: Gift Wish List`, default language **English (United States)**,
**App**, **Free**, accept the declarations.

## 3. App content (Policy → App content), answers

- **Privacy policy**: `https://thinkofme.web.app/privacy/`
- **Ads**: Yes, the app contains ads.
- **App access**: All or some functionality is restricted → "Sign in with any Google account; no
  special credentials are needed. A second Google account can join a circle with the invite link."
- **Content rating** (IARC questionnaire): category **All other app types** (or Social/Communication
  if offered); violence, sexuality, language, controlled substances, gambling: **No**. Users interact
  or exchange content: **Yes**, only with people they invite (no public profiles, no chat with
  strangers). Shares location: **No**. Digital purchases: **No**. Expected: Everyone / PEGI 3.
- **Target audience**: **18 and over** (keeps the app out of the Families program; it has ads and
  is meant for adults buying gifts). Appeal to children: No.
- **News app**: No. **COVID-19**: No. **Government**: No. **Financial features**: none.
  **Health**: none.
- **Data safety**:
  - Collects or shares data: **Yes**. Encrypted in transit: **Yes**. Users can request deletion:
    **Yes**, with the link `https://thinkofme.web.app/support/#delete-account`.
  - Personal info → **Name**, **Email address**: collected, not shared; App functionality, Account
    management; required.
  - Photos and videos → **Photos**: collected, not shared; App functionality; optional.
  - App activity → **App interactions**: collected; Analytics; not shared. **Other user-generated
    content** (wishes, notes, dates, circles): collected; App functionality.
  - App info and performance → **Crash logs**, **Diagnostics**: collected; Analytics, App functionality.
  - Device or other IDs: collected and **shared** (Google AdMob); Advertising or marketing,
    Analytics. (Analytics itself does not read the advertising ID.)
  - Location → **Approximate location**: collected and shared by the ads SDK (from IP); Advertising.
- **Account deletion**: `https://thinkofme.web.app/support/#delete-account`. Requests by email:
  delete the user in Firebase console → Authentication; the backend then removes all their data.

## 4. Service account for automatic uploads

1. Google Cloud console, the **same project as Firebase** → APIs & Services → enable
   **Google Play Android Developer API**.
2. IAM & Admin → Service accounts → **Create**: name `play-publisher`, no roles needed → Keys →
   Add key → **JSON**. Paste the whole file into the GitHub secret `PLAY_SERVICE_ACCOUNT_JSON`.
3. Play Console → **Users and permissions** → Invite new user → the service account's email →
   App permissions: ThinkOfMe → **Release to testing tracks**, **Manage store presence**
   (later also **Release to production**).

## 5. First bundle (by hand, Google requires it once)

1. Actions → **Google Play release** → Run workflow: track **alpha**, upload **off**.
2. Download the `thinkofme-release-aab` artifact (unzip it in Files).
3. Play Console → Testing → **Closed testing** → Create track (or use "Alpha") → Create release →
   accept **Play App Signing** → upload the `.aab` → release notes from
   `store/google-play/release-notes` → Save.
4. Testers: add a Google Group or an email list with your 12+ testers, copy the opt-in link and send it.

From then on: run the workflow with upload **on**; the bundle goes to the closed track and the
listing is pushed in the same run.

## 6. Fingerprints (Google sign-in and invite links)

1. Play Console → Setup → **App signing**: copy the **App signing key** SHA-1 and SHA-256, and the
   **Upload key** SHA-1 and SHA-256 (also in each workflow run's summary).
2. Firebase console → Project settings → Android app `com.thinkofme.app` → **Add fingerprint**:
   add all four. Download the new `google-services.json`, base64 it into `GOOGLE_SERVICES_JSON_B64`.
   Without the app signing key's SHA-1, Google sign-in fails on Play builds.
3. GitHub secret `ANDROID_CERT_SHA256` (or `ANDROID_CERT_SHA`) = the app signing key SHA-256 (comma-separated if you add the
   upload key too), then run **Deploy backend** so invite links open the app.

## 7. Closed test → production

- New personal accounts: **12+ testers opted in for 14 days in a row** on the closed track.
- Then Play Console → Dashboard → **Apply for production** and answer the questionnaire about the
  test. After approval: Production → Create release (or run the workflow with track
  **production**), roll out.

## Updating later

Edit `store/google-play/listings/<language>/*.txt` (limits: title 30, short 80, full 4000) and the
release notes, then run the workflow. Bump `versionName` in `android/app/build.gradle.kts` for a new
version; the version code increases automatically.

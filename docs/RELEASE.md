# Releasing ThinkOfMe on the App Store

Everything that can be automated is: icon, listing text in four languages, screenshots, review
notes and the build are pushed by **Actions → App Store listing**. The steps below are the ones
Apple only lets a person do in App Store Connect, with the answers prepared.

## 1. Before the first submission (one time)

| Step | Where | Notes |
|---|---|---|
| Real ads | AdMob → Apps → Add app (iOS, not yet listed) → ad units: one **Native advanced**, one **Banner** | Add repo **secrets** `ADMOB_APP_ID_IOS`, `ADMOB_NATIVE_UNIT_IOS`, `ADMOB_BANNER_UNIT_IOS`. Without them the build shows Google's test ads. |
| app-ads.txt | AdMob → Account → Publisher ID (`pub-…`) | Add repo **variable** `ADMOB_PUBLISHER_ID`, then run **Deploy backend**. |
| Consent message | AdMob → Privacy & messaging → GDPR and IDFA explainer messages → Publish | The app already shows them through Google's consent SDK. |
| Copyright line (optional) | Repo **variable** `APP_COPYRIGHT`, e.g. `2026 Your Name` | Default is `2026 ThinkOfMe`. |
| Review contact | Repo **secret** `TESTFLIGHT_REVIEW_CONTACT` = `First|Last|Phone|Email` | Already set for TestFlight. |

## 2. Build and listing

1. **Actions → TestFlight → Run workflow** with the ad secrets in place (a fresh build picks them up).
2. When the build has processed (about 15 minutes), run **Actions → App Store listing**. To only look at
   the screenshots first, run it with *Push to App Store Connect* off and download the
   `app-store-screenshots` artifact.

The workflow fills in, for version 1.0: name, subtitle, description, keywords, promotional text,
support/marketing/privacy URLs (English, Hebrew, Arabic, Russian), categories (Lifestyle,
Shopping), copyright, review notes and contact, the newest build, and six screenshots per
language. Release is set to **manual**, so nothing goes live until you press Release.

## 3. In App Store Connect (by hand)

### App Privacy (App → App Privacy → Get Started)
"Do you or your third-party partners collect data from this app?" **Yes.** Then:

| Data type | Purposes | Linked to the user | Used for tracking |
|---|---|---|---|
| Contact Info → Name | App Functionality | Yes | No |
| Contact Info → Email Address | App Functionality | Yes | No |
| User Content → Photos or Videos | App Functionality | Yes | No |
| User Content → Other User Content (wishes, notes, dates, circles) | App Functionality | Yes | No |
| Identifiers → User ID | App Functionality | Yes | No |
| Identifiers → Device ID (advertising identifier, only if allowed; app instance ID for analytics) | Third-Party Advertising, Analytics | No | **Yes** |
| Location → Coarse Location (Google Ads, from IP) | Third-Party Advertising | No | **Yes** |
| Usage Data → Product Interaction (screens opened, wishes/dates/circles added; see `docs/ANALYTICS.md`) | Analytics | No | No |
| Usage Data → Advertising Data | Third-Party Advertising | No | **Yes** |
| Diagnostics → Crash Data | App Functionality, Analytics | No | No |
| Diagnostics → Performance Data | App Functionality, Analytics | No | No |

Privacy policy URL: `https://thinkofme.web.app/privacy/` (already filled in by the workflow).

### Age Rating (App Information → Age Rating → Edit)
Answer **None / No** to every content question (violence, sexual content, profanity, drugs,
gambling, horror, medical, etc.). For the capability questions:
- Unrestricted web access: **No** (links open in Safari, outside the app).
- User-generated content: wishes are shared **only with people the user invites**; there is no
  public feed and no contact with strangers. Answer the way App Store Connect phrases this; if
  asked about messaging or chat with others: **No**.
- Advertising: **Yes** (small banner and native ads, never full screen).
Expected result: **4+**.

### Content Rights (App Information)
"Does your app contain, show, or access third-party content?" **Yes**: users add product links,
photos and prices from stores. "Do you have the necessary rights?" **Yes**: it is user-provided
for personal lists, with the store's own link.

### Pricing and Availability
Price: **Free**. Availability: all countries (or start with Israel only, if you prefer a soft launch).

### Submit
Open version 1.0 → check the build, screenshots and texts → **Add for Review** → **Submit to
App Review**. After approval, press **Release This Version**.

## 4. Strongly recommended before submitting
- **App Check enforcement**: Firebase console → App Check → register the iOS app with
  **DeviceCheck** (and Android with **Play Integrity**). Tell Claude, which switches enforcement on.
- **Native-speaker check** of the Hebrew, Arabic and Russian listing in `store/app-store/metadata`
  and of the screenshot captions in `store/app-store/captions.json`.

## Updating the listing later
Edit the text files in `store/app-store/metadata/<language>/` (CI checks Apple's length limits),
then run **App Store listing** again. For a new version, change `VERSION` in
`.github/workflows/app-store-listing.yml` and `MARKETING_VERSION` in `ios/project.yml`.

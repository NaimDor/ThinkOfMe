# Launch checklist

Status legend: **[me]** = code/config I do, **[you]** = needs your accounts, decisions or devices.

## Done

- Backend deployed: security rules (surprise guarantee tested), triggers, invites, link previews, account deletion, birthday + holiday reminders.
- iOS + Android apps with all core screens in 4 languages, CI on every push, one-tap TestFlight and backend deploys.

## A. Blockers before a public release

| # | Item | Who |
|---|---|---|
| A1 | **Real-world test round** on both phones with real accounts; fix what breaks (iOS has only compiled in CI, never run against the live backend) | you test, me fix |
| A2 | **Domain** for the app (e.g. thinkofme.app): invite links that open the app (Universal Links / App Links), privacy policy + support pages, `app-ads.txt` | you buy, me set up |
| A3 | **Tapping a notification opens the right screen** (person, wish, occasion) | me |
| A4 | **Firebase App Check** so only the real apps can call the backend (protects link previews, invites) | me + you (enable in console) |
| A5 | **Privacy policy, terms, store privacy forms** (App Store privacy label, Play Data safety), AdMob GDPR message configured so the consent form appears | me draft, you publish |
| A6 | **Android release pipeline**: release signing key, Play Console app, internal testing track, Firebase Android app with release SHA-1 (needed for Google sign-in) | me + you |
| A7 | **Store listings**: final name (trademark/availability check), screenshots in 4 languages, descriptions, age rating, review notes | me draft, you submit |
| A8 | **Real AdMob units** and production ad settings | you create, me wire |
| A9 | **Native-speaker review** of Hebrew, Arabic and Russian copy | you / friends |

## B. Strongly recommended for launch quality

| # | Item | Who |
|---|---|---|
| B1 | **Add your own photo to a wish** (camera/gallery → Storage; rules already exist) | me |
| B2 | **iOS Share Extension**: share a product page from Safari/any app (Android already has this) | me |
| B3 | Circle management: remove member, rename, leave on iOS, member roles | me |
| B4 | Edit/delete dates; "bought" vs "planning to buy" on claims; "I got this" → thank-you | me |
| B5 | Crash reporting on iOS (Crashlytics), analytics events for key flows | me |
| B6 | Remote Config: ad frequency + kill switch without an app update | me |
| B7 | Security hardening: IP pinning in link previews (DNS rebinding), TTL cleanup for rate-limit and reminder logs, rules review pass | me |
| B8 | Accessibility pass (VoiceOver/TalkBack, large text), RTL review on iOS | me |
| B9 | Separate **staging** Firebase project so testing never touches real users' data; Firestore backups | me + you |
| B10 | Data export (GDPR "download my data") | me |
| B11 | End-to-end tests against the Firebase emulator; iOS screenshot tests | me |

## C. After launch (v1.1+)

Web viewer for invitees without the app · group chat & chip-in for big gifts · "Remove ads" purchase ·
affiliate links · home-screen widgets · activity feed screen · QR invites · email-link sign-in ·
price-drop hints · Hebrew-calendar birthdays · make the repository private (watch macOS CI minutes).

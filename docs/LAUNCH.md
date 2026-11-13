# Launch checklist

Status legend: **[me]** = code/config I do, **[you]** = needs your accounts, decisions or devices.

## Done

- Backend deployed: security rules (surprise guarantee tested), triggers, invites, link previews, account deletion, birthday + holiday reminders.
- iOS + Android apps with all core screens in 4 languages, CI on every push, one-tap TestFlight and backend deploys.

## A. Blockers before a public release

| # | Item | Who |
|---|---|---|
| A1 | **Real-world test round** on both phones with real accounts; fix what breaks (iOS has only compiled in CI, never run against the live backend) | you test, me fix |
| A2 | **Web address** `thinkofme.web.app` (free Firebase Hosting): invite pages, privacy/terms/support, app-link files; then `app-ads.txt` | set repo variables, deploy |
| A3 | ~~Tapping a notification opens the right screen~~ **Done** | me |
| A4 | **Firebase App Check**: apps send tokens (Play Integrity / DeviceCheck); you register the apps in the console, then I switch on enforcement | you register, me enforce |
| A5 | Privacy policy, terms, support pages **drafted** (`backend/hosting`); still: store privacy forms, AdMob GDPR message, `SUPPORT_EMAIL` variable | you publish / configure |
| A6 | **Android release pipeline**: release signing key, Play Console app, internal testing track, Firebase Android app with release SHA-1 (needed for Google sign-in) | me + you |
| A7 | **Store listings**: final name (trademark/availability check), screenshots in 4 languages, descriptions, age rating, review notes | me draft, you submit |
| A8 | **Real AdMob units** and production ad settings | you create, me wire |
| A9 | **Native-speaker review** of Hebrew, Arabic and Russian copy | you / friends |

## B. Strongly recommended for launch quality

| # | Item | Who |
|---|---|---|
| B1 | ~~Add your own photo to a wish~~ **Done** | me |
| B2 | **iOS Share Extension**: share a product page from Safari/any app (Android already has this) | me |
| B3 | ~~Circle management: rename, remove member, leave (both apps)~~ **Done**; promoting other admins later | me |
| B4 | ~~Edit/delete dates; "bought" vs "planning to buy" on claims~~ **Done**; "I got this" → thank-you later | me |
| B5 | ~~Crash reporting on iOS~~ **Done**; analytics events for key flows | me |
| B6 | Remote Config: ad frequency + kill switch without an app update | me |
| B7 | ~~Link-preview DNS pinning, TTL cleanup~~ **Done**; rules review pass | me |
| B8 | Accessibility pass (VoiceOver/TalkBack, large text), RTL review on iOS | me |
| B9 | Separate **staging** Firebase project so testing never touches real users' data; Firestore backups | me + you |
| B10 | Data export (GDPR "download my data") | me |
| B11 | End-to-end tests against the Firebase emulator; iOS screenshot tests | me |

## C. After launch (v1.1+)

**ThinkOfMe Plus (decided):** subscription, about $1.99/month or $14.99/year: no ads +
"Find me the best price" (Claude with web search, results shared per wish for a few
hours, monthly cap per subscriber, store / price / shipping / link / checked-at shown,
well-known stores in the user's country) + later price-drop alerts. Needs StoreKit 2 and
Play Billing with server-side entitlement, the paid apps agreement in App Store Connect,
and subscription products in both stores. Affiliate links on results as a later step.


Web viewer for invitees without the app · group chat & chip-in for big gifts · "Remove ads" purchase ·
affiliate links · home-screen widgets · activity feed screen · QR invites · email-link sign-in ·
price-drop hints · Hebrew-calendar birthdays · make the repository private (watch macOS CI minutes).

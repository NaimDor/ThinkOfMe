# ThinkOfMe — Product & Engineering Plan

> A shared wish space for the people closest to you. Put down what you'd love,
> see what they'd love, and quietly coordinate so every gift is a surprise.

Status: **Planning** · Last updated: 2026-11-11

---

## 1. Product

### 1.1 Core idea
Everyone keeps **one personal wishlist**. You choose which **Circles** (a couple,
"Mom & me", "Family", "Friends", or a public link) can see each wish. People who
can see your wishes can **secretly claim** them ("I'm getting this"), so others
don't buy the same thing — and you never find out.

Dates (birthdays, anniversaries, custom occasions) drive **reminders**: the
celebrated person is nudged to refresh their list, and everyone else is nudged
to go look at it.

### 1.2 Key concepts

| Concept | Description |
|---|---|
| **Wish** | An item you'd like: title, link(s), photos, price, size/variant, note, priority ("would love" → "dream"), quantity, or an *idea* (non-product: "a cooking class", "anything from this shop"). |
| **Circle** | A group of people. Types: **Pair** (2 people, e.g. a couple), **Group** (3+), **Public** (anyone with the link, view-only + claim). |
| **Visibility** | Per wish: which Circles see it. Default = all your Circles. So a wish can be for your wife but not your mom. |
| **Claim** | "I'm getting this / I got this". Visible to everyone who can see the wish **except its owner**. Optional "chip in" later. |
| **Occasion** | A date tied to a person or a Circle (birthday, anniversary, holiday, custom). Yearly or one-off. |
| **About me** | Sizes (clothes, shoes, ring), favorite colors/brands, allergies, and a **"please don't buy me"** list. |

### 1.3 The surprise guarantee (most important rule in the product)
The owner of a wish must never be able to learn it was claimed — not via UI,
not via notifications, not via the API, not by inference.

- Claims live in a **separate collection** the owner is denied read access to by
  server security rules (not just hidden in the UI).
- A claimed wish looks **identical** to the owner (it doesn't disappear, reorder,
  or change counts).
- No push / activity / badge to the owner ever mentions claims.
- If the owner **deletes or edits** a claimed wish, the claimer is told
  ("Heads-up: a wish you're getting was changed") — the owner is not.
- After the occasion passes, the owner can mark items **Received**; claimers'
  claims are then quietly resolved. Optional "reveal after the event" is a
  per-Circle setting (off by default).
- In a **Pair**, claiming still works — it becomes a private "I already got
  this" reminder for the buyer.

### 1.4 What I'd add to your idea (included in the plan below)
1. **One list, many Circles** instead of a list per group — no duplicate entry,
   fine-grained visibility per wish.
2. **Share from any store** — iOS Share Extension / Android share target. Share a
   product page → ThinkOfMe opens with title, image and price pre-filled
   (server-side link unfurling).
3. **"About me" card** — sizes, colors, allergies, "don't buy me". Buyers need
   this more than the list itself.
4. **Ideas, not only products** — experiences, "anything by this author", gift
   cards, "surprise me with flowers".
5. **Priority & quantity** — "would love" vs "dream gift"; "3 of these".
6. **Change protection for buyers** — if a claimed wish is edited/removed, the
   buyer is alerted.
7. **Received + thank-you** — close the loop after the occasion, send a thank-you.
8. **Home-screen widgets** — next occasion countdown + a rotating wish.
9. **Secret group thread & chip-in** (v1.1) — non-owners coordinate a big gift
   without the owner seeing.
10. **Web view for invitees without the app** (v1.1) — Mom opens a link in the
    browser, sees the list and can claim. Huge for adoption.
11. **Privacy & trust** — account deletion, data export, block/report on public
    lists (also required by App Store / Play policy).
12. **Localization from day one: English, Hebrew, Arabic, Russian** — two RTL
    languages, so every screen is built with logical (leading/trailing) layout,
    mirrored icons where meaningful, and per-script fonts.

### 1.5 Notifications

| Who | When (defaults, configurable) | Message |
|---|---|---|
| Celebrated person | 30 and 14 days before | "Your birthday is in 2 weeks — your list has 3 wishes. Add a few more?" |
| Circle members | 21, 7, 2 days before | "Dana's birthday is in 7 days · 5 wishes, 3 still unclaimed" |
| Circle members | on the day | "It's Dana's birthday today 🎉" |
| Claimer | owner edits/deletes a claimed wish | "A wish you're getting was updated" |
| Owner | someone joins a Circle / adds wishes | Low-key, batched, never claim-related |

Scheduling is **server-side** (daily scheduled function, per-user timezone,
quiet hours, de-duplication) so it works even when the app isn't opened, with
local notifications as an offline fallback for your own occasions.

### 1.6 Scope

**v1.0 (launch)**
- Sign in with Apple, Google, email magic link
- Onboarding (name, avatar, birthday, first wishes)
- Wishlist: add/edit via link paste, share sheet, camera/photo, or manual
- Link unfurling (title, image, price, store)
- Circles: create, invite by link/QR, join, leave, remove member, roles
- Per-wish visibility
- Claims with the surprise guarantee
- Occasions + reminders (push), per-user notification settings
- "About me" card
- Activity feed (claim-safe)
- Widgets (iOS WidgetKit, Android Glance)
- Dark mode, Dynamic Type / font scaling, VoiceOver/TalkBack
- Languages: English, Hebrew, Arabic, Russian (RTL + Cyrillic), locale-aware
  dates, numbers and currencies (₪, $, €, ₽, AED…)
- Account deletion & data export
- Offline-first reading, optimistic writes

**v1.1**
- Web viewer + claim for non-app invitees
- Secret group thread & chip-in tracking
- Public profile page link
- Price-drop hints, "received" thank-you cards

**Later**
- Premium tier (unlimited Circles, themes, extra reminders), alternative
  calendars (e.g. lunar/Hebrew date birthdays)

### 1.7 Monetization — small, respectful ads
Ads ship in **v1.0** via **Google AdMob** (Google Mobile Ads SDK on both
platforms, linked to the Firebase project). There are **no full-screen,
interstitial, rewarded or app-open ads, ever**.

**Formats**
- **Native ad cards**, the main format. They are drawn in our own design
  (same card shape, shadow and type) and look like wish cards, with a clear
  **"Ad"** label as AdMob policy requires. This earns the most and fits the
  3D look much better than a stock banner.
- **Inline adaptive banners** (a thin bar) at the end of long lists.
- No banner pinned above the floating tab bar: it would clash with the
  design and cause accidental taps, which AdMob penalizes.

**Where ads may appear**

| Screen | Ad | Frequency |
|---|---|---|
| Home feed ("New from your people") | Native card | 1 after the 6th item, then every 10 items |
| A person's wishlist grid | Native tile | 1 per 12 wishes, never in the first row |
| Occasions timeline | Inline banner | 1, at the end of the list |
| Circles list | Inline banner | 1, at the end of the list, only with 4+ Circles |

**Never**: onboarding, add/edit wish, wish detail, the "wrap it"
claim flow, invites, settings, notifications, widgets, share extension, or
the day of an occasion on that person's screen.

**Rules**
- Frequency, placements and a global kill switch live in **Firebase Remote
  Config**, so they can be tuned without an app release.
- **Consent**: Google UMP consent form (GDPR/EEA, UK, and US states), iOS
  **App Tracking Transparency** prompt shown once, after onboarding and
  never at first launch. If the user declines, they get non-personalized ads.
- Ads load lazily and off the main thread. If an ad fails to load, the
  layout just closes the gap with no empty boxes, and Reduce Motion and
  dark mode are respected.
- Debug and CI builds always use Google's **test ad unit IDs**, so we never
  click our own live ads, which can get the account banned.
- Privacy disclosures: App Store privacy label and Play Data safety form
  must declare the advertising data; the privacy policy covers AdMob.
- **"Remove ads"** one-time in-app purchase (StoreKit 2 / Play Billing) in
  v1.1. It is cheap to add, and some users happily pay for it.

**Revenue expectations (honest)**: native and banner ads pay little per view
(about $0.20–$3 per 1,000 impressions depending on country). Expect pocket
money until the app has thousands of daily users. **Affiliate links** pay
more for a gift app, because users literally go buy the thing. Store links
can be tagged with affiliate IDs (e.g. Amazon Associates) and labeled with a
clear disclosure. Planned for v1.1, alongside ads.

---

## 2. Design direction: "Ribbon & Paper", calm screens and 3D moments

Goal: warm, calm and uncluttered at rest, and delightful at the moments that
matter. It should feel like a beautifully wrapped present, not a settings
screen. **Not** stock Material or stock iOS lists.

**Decided (v2 mockups, "Calm v2" page on the design canvas):**
- **Colors**: light theme uses A's palette (cream paper, plum ink, coral
  accent); dark theme uses B's palette (velvet aubergine, gold accent).
- **Calm static screens**: one focal point per screen, flat surfaces with at
  most one soft shadow, generous whitespace, hairline dividers, color used
  as an accent only. Real product photos carry the visual richness, so the
  surrounding interface stays quiet.
- **3D only in moments** (see 2.0). The v1 explorations (heavy 3D on every
  screen) were judged too cluttered.

### 2.0 Where 3D and motion live ("moments")
| Moment | What happens |
|---|---|
| **Wrap it** (claiming a wish) | The photo card lifts and tilts in 3D, a gold ribbon wraps it, a bow springs on, sparkles burst, with a haptic sequence. It then settles back flat, keeping a slim ribbon. |
| **Occasion day** | A 3D gift box (RealityKit `Model3D` on iOS; Filament on Android) drops in and opens, with confetti in the person's color. |
| **Adding a wish** | The new card flies into the list with a spring; the add button squashes. |
| **Pull-to-refresh / empty states / onboarding** | A small 3D gift box that reacts to device tilt. |
| **Wish photo** | Subtle tilt parallax only while the user drags or long-presses it, not at rest. |

Everything respects Reduce Motion, which swaps each moment for a simple fade.

### 2.1 Identity
- **Palette** — warm paper canvas (`#FBF6EF`), ink plum text (`#2A1730`),
  ribbon coral accent (`#FF5A4E`), soft gold highlight (`#E8B04B`), sage
  (`#7FA58C`) for "done". Dark mode is "velvet night": deep aubergine canvas,
  same accents tuned for contrast. All pairs checked for WCAG AA.
- **Person hues** — every person gets a stable signature hue (derived from their
  id) used for their avatar ring, list header aura and occasion chips, so you
  instantly know whose wishes you're looking at.
- **Type** — expressive serif for display paired with the platform system
  face for body (SF Pro / Roboto) for legibility and Dynamic Type. Display
  face per script: Fraunces (Latin), a Cyrillic-capable serif such as
  Playfair Display or Lora (Russian), Frank Ruhl Libre (Hebrew), and a Naskh
  face such as Noto Naskh Arabic (Arabic). All OFL-licensed and bundled.
- **Shape**: large radii, flat surfaces, hairline dividers, image-first
  grid for wishlists, simple rows for people and dates.

### 2.2 Motion & "live" feel
- **Hero transitions**: wish card → detail (iOS zoom navigation transition /
  Compose `SharedTransitionLayout`).
- **Claim = wrap it**: the card gets wrapped by an animated ribbon and bow,
  with a satisfying haptic sequence. Unclaim unties it.
- **Occasion countdown ring** on the home screen that fills as the date nears;
  on the day, a confetti burst in the person's hue.
- **Springs everywhere**, interruptible gestures, swipe actions, pull-to-refresh
  with a custom gift-box animation, skeleton shimmer while unfurling links.
- **Live updates**: real-time listeners — a new wish slides into a partner's
  grid while they're looking at it.
- **Haptics** mapped to meaning (light tick: select; success: claim; warning:
  destructive).

### 2.3 Platform-native, same soul
Shared tokens (colors, type scale, spacing, radii, motion curves) live in
`design/tokens.json` and are code-generated into Swift and Kotlin so both apps
stay in sync. Navigation, sheets, menus, back gestures, and text input follow
each platform's conventions (Liquid Glass tab bar on iOS 26+, predictive back
and Material 3 Expressive motion on Android).

### 2.4 Main screens
1. **Home** — upcoming occasion carousel (countdown rings), "From your
   people" (recent wishes from Circles), your list summary.
2. **Person** — a person's wishlist (masonry grid), About-me card, their
   occasions; claim status visible to you (never to them).
3. **My list** — your wishes, filters (priority, Circle), quick add FAB.
4. **Add wish** — paste/share link → live unfurl preview → visibility chips.
5. **Wish detail** — gallery, price, store link, note, claim/"wrap it" button.
6. **Circles** — your Circles, invite (link / QR), members, settings.
7. **Occasions** — timeline/calendar of upcoming dates.
8. **Settings** — notifications, privacy, account, export/delete.

---

## 3. Architecture

### 3.1 Decisions

| Area | Decision | Why |
|---|---|---|
| Clients | **Native**: SwiftUI (iOS) + Jetpack Compose (Android) | Your call; best fidelity & motion. |
| Backend | **Firebase** (Auth, Firestore, Cloud Functions, Storage, FCM, App Check, Remote Config, Crashlytics, Analytics) | First-class native SDKs on both platforms, real-time sync + offline cache built in, push included, server-enforced security rules for the surprise guarantee, no servers to run. |
| Ads | **AdMob** native + inline adaptive banners, Google UMP consent, iOS ATT | Links to Firebase; native ads can be fully styled to the design. |
| Functions | TypeScript, Node 22, 2nd gen | Typed shared models, scheduled jobs. |
| iOS min | **iOS 18** (Liquid Glass extras on 26+) | Observation, `MeshGradient`, zoom transitions; >90% of active devices by late 2026. |
| Android min | **API 26 (Android 8)**, target latest | Broad reach; enhanced effects gated by API level. |
| iOS stack | Swift 6 strict concurrency, Observation, SPM modules, Nuke (images), XcodeGen (no committed `.pbxproj` merge pain), Swift Testing | |
| Android stack | Kotlin 2.x, Compose BOM, Material 3 (heavily themed), Hilt, Coroutines/Flow, Navigation (type-safe), Coil 3, DataStore, Glance, Gradle version catalog, convention plugins | |
| Testing | Unit (domain/view-models), Firestore **rules tests** on the emulator (surprise guarantee is tested), snapshot tests for design system, UI smoke tests | |
| CI | GitHub Actions: Android build+test (Linux), iOS build+test (macOS runners — free while the repo is public), Functions lint+test+rules tests | |

### 3.2 Repository layout
```
ThinkOfMe/
├── ios/                    # SwiftUI app (XcodeGen project.yml)
│   ├── App/                # App target, entry, DI, routing
│   ├── Packages/
│   │   ├── DesignSystem/   # tokens, components, motion, haptics
│   │   ├── Core/           # models, repositories, Firebase services
│   │   └── Features/       # Home, Wishlist, WishDetail, Circles, Occasions, Settings, Onboarding
│   ├── ShareExtension/
│   └── Widgets/
├── android/                # Compose app (multi-module)
│   ├── app/
│   ├── core/{designsystem,model,data,common}
│   ├── feature/{home,wishlist,wish,circles,occasions,settings,onboarding}
│   └── widget/
├── backend/                # Firebase
│   ├── firestore.rules
│   ├── firestore.indexes.json
│   ├── storage.rules
│   └── functions/          # unfurl, invites, reminders, claim fan-out, cleanup
├── design/tokens.json      # single source of truth → Swift & Kotlin
├── docs/
└── .github/workflows/
```

### 3.3 Data model (Firestore)
```
users/{uid}
  displayName, photoUrl, hue, timezone, locale, aboutMe{sizes, colors, allergies, dontBuy[]},
  notificationPrefs{...}, createdAt
users/{uid}/devices/{deviceId}       fcmToken, platform, updatedAt
circles/{circleId}
  name, type: pair|group|public, ownerId, memberIds[], memberCount, emoji, settings{revealAfterEvent}, createdAt
circles/{circleId}/members/{uid}     role: owner|admin|member, joinedAt
invites/{code}                       circleId, createdBy, expiresAt, maxUses, uses
wishes/{wishId}
  ownerId, title, note, urls[], images[], price{amount,currency}, store,
  priority: 1..3, quantity, kind: product|idea, visibleCircleIds[], status: active|received|archived,
  createdAt, updatedAt, sortKey
claims/{wishId}_{claimerUid}          # NEVER readable by wish owner
  wishId, ownerId, claimerId, visibleCircleIds[], quantity, status: planned|bought, anonymous, createdAt
occasions/{occasionId}
  personId | circleId, kind: birthday|anniversary|holiday|custom, title, month, day, year?,
  recurrence: yearly|once, visibleCircleIds[], reminders{selfDays[], othersDays[]}
activity/{circleId}/items/{id}       # claim-free feed, written only by functions
```

**Security rules essentials**
- Wishes readable by owner, or by a member of any circle in `visibleCircleIds`.
- Claims readable/writable only if `request.auth.uid != resource.data.ownerId`
  **and** the caller can see the wish. Owner queries on `claims` are rejected
  by rules (verified by emulator tests).
- `memberIds` changes only via Cloud Functions (invite redemption, leave,
  remove) — clients cannot add themselves to a Circle.
- Activity, invites, and reminder state are server-written only.

### 3.4 Cloud Functions
- `unfurlLink` (callable) — fetch URL, parse OpenGraph/JSON-LD/product schema,
  return title/images/price; rate-limited, SSRF-safe, cached.
- `createInvite` / `redeemInvite` (callable) — join Circles safely.
- `dailyReminders` (scheduled, hourly to cover timezones) — compute due
  reminders per user, send FCM (APNs via FCM), dedupe log.
- `onWishWrite` — notify claimers on edit/delete; write claim-free activity.
- `onUserDelete` — full cleanup (GDPR / App Store requirement).

### 3.5 Client architecture
- **iOS**: Feature modules with `@Observable` view-models, repository protocols
  in Core (Firebase implementations + in-memory fakes for previews/tests),
  `NavigationStack` router per tab, deep links via Universal Links
  (`thinkofme.web.app/i/{code}`).
- **Android**: MVVM + UDF (`StateFlow<UiState>`), repositories in `core:data`
  exposing `Flow`s from Firestore snapshot listeners, Hilt DI, type-safe
  navigation, App Links for invites.
- Both: previews/screens runnable with fake data, no Firebase needed — makes UI
  work and screenshots fast.

---

## 4. Milestones

| # | Milestone | Outcome | Status |
|---|---|---|---|
| M0 | Foundations | Repo layout, tokens + codegen, CI for all three parts, Firebase emulator setup, rules + rules tests for the surprise guarantee | Done |
| M1 | Design system | Both apps: colors, type, buttons, chips, avatars with hue, countdown ring, wrap-it moment, gift-box moment, haptics | Done |
| M2 | Auth & onboarding | Google (both), Apple (iOS), profile, birthday | Done (email link and Apple on Android: later) |
| M3 | Wishlist | CRUD, link unfurl, Android share target, visibility incl. "all my circles" | Done (photo upload and iOS share extension: next) |
| M4 | Circles | Create, invite link, join, members | Done (QR, roles UI, remove member UI: next) |
| M5 | Claims | Wrap-it flow, claim-safe UI everywhere, buyer change alerts | Done |
| M6 | Occasions & push | Occasions UI, FCM, scheduled localized reminders, settings | Done |
| M7 | Polish & ads | AdMob native/banner slots and consent flow done; widgets, activity feed UI, Remote Config ad controls, accessibility pass | In progress |
| M8 | Launch prep | Account deletion done; data export, privacy policy, store assets, TestFlight/Play internal testing, make repo private | Not started |

---

## 5. Account setup checklist (owner)
You already have an **Apple Developer account** and a **Firebase account**.
Development runs on the **Firebase Emulator Suite**, so none of this blocks
coding. It is needed before the first real-device build.

**Identifiers**: bundle ID / application ID `com.thinkofme.app` (both
platforms); App Group `group.com.thinkofme.app`.

**Firebase console**
1. Create project **ThinkOfMe** with Google Analytics enabled (needed for
   AdMob linking).
2. Switch to the **Blaze** plan and set a **budget alert** (e.g. $10/month).
   Scheduled reminders and link unfurling need it; at this scale it costs ~$0.
3. Add an **iOS app** and an **Android app**, both `com.thinkofme.app`.
4. **Authentication** → enable Apple, Google, Email link.
5. **Firestore** → create in production mode. Pick the region closest to most
   users (e.g. `europe-west1`, or `me-west1` Tel Aviv). It cannot be
   changed later.
6. **Storage** → create a bucket in the same region.

**Apple Developer**
7. Register App ID `com.thinkofme.app` with: Sign in with Apple, Push
   Notifications, App Groups, Associated Domains.
8. Create an **APNs Auth Key (.p8)** and upload it in Firebase → Project
   settings → Cloud Messaging. Do not send the key to anyone, including in chat.

**AdMob**
9. Create an AdMob account, link it to the Firebase project, add the iOS
   and Android apps, and create per platform one **Native advanced** and one
   **Banner** ad unit.

**Google Play Console** ($25 one-time). Needed before Android testing on
the Play Store; not needed for development.

**Domain** for invite links / web viewer (e.g. `thinkofme.app`); check the
name's availability and trademark.

**Secrets → GitHub, never into the repo or chat**: in the repository's
Settings → Secrets and variables → Actions, add
`GOOGLE_SERVICE_INFO_PLIST_B64`, `GOOGLE_SERVICES_JSON_B64` (base64 of the
two config files), and `FIREBASE_TOKEN` / service-account JSON for deploys.
AdMob app IDs and ad unit IDs aren't secret (they ship inside the app), so
they go in a config file. The repo uses Google's test IDs until launch.

## 6. Notes & risks
- **Public repo**: free macOS CI minutes are a real benefit; when switching to
  private, macOS minutes become expensive (10× multiplier) — we'll keep iOS CI
  lean (build + unit tests on PRs, full UI tests nightly).
- **Build verification**: Android and backend are built/tested in CI and in
  this environment where possible; iOS is compiled and tested on macOS CI
  runners (no Xcode on Linux).
- **Link unfurling**: some stores (e.g. Amazon) block bots — fall back to
  manual entry gracefully, keep the user's pasted URL.
- **Notification fatigue**: conservative defaults, batching, quiet hours.

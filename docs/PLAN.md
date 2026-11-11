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
- Premium tier (unlimited Circles, themes, extra reminders), affiliate links
  with disclosure, alternative calendars (e.g. lunar/Hebrew date birthdays)

---

## 2. Design direction — "Ribbon & Paper", in 3D

Goal: warm, tactile, alive and **dimensional, not flat**. It should feel like a
beautifully wrapped present, not a settings screen. **Not** stock Material /
stock iOS lists.

Three 3D directions were mocked up for review (A · Ribbon & Paper 3D,
B · Velvet Night, C · Puffy Clay) plus an interactive wish detail and a Hebrew
RTL home. Pending pick; A is the working default and B is a natural dark mode
for it.

### 2.0 How "3D" is achieved
- **Real 3D objects**: the gift box is a true 3D object (SceneKit/RealityKit
  `Model3D` on iOS, Filament or a lightweight GL scene on Android) that spins,
  floats, and reacts to the device's tilt (CoreMotion / SensorManager).
- **Lit materials**: wish "orbs", avatars and buttons use sphere lighting
  (specular highlight + occlusion shading), not flat fills.
- **Layered depth**: cards have multi-layer shadows, inner highlights and sit
  at different Z heights; the hero card leans back in perspective.
- **Parallax**: product images tilt with the phone (gyroscope) and with drag,
  with a moving specular shine.
- **Physical motion**: springs, squash-and-stretch on the add button, the
  ribbon physically wrapping a claimed wish, sparkle burst.

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
- **Shape** — soft "gift tag" cards (large radius, one notched corner),
  generous whitespace, image-first masonry grid.

### 2.2 Motion & "live" feel
- **Hero transitions**: wish card → detail (iOS zoom navigation transition /
  Compose `SharedTransitionLayout`).
- **Claim = wrap it**: the card gets wrapped by an animated ribbon and bow,
  with a satisfying haptic sequence. Unclaim unties it.
- **Occasion countdown ring** on the home screen that fills as the date nears;
  on the day, a confetti burst in the person's hue.
- **Ambient background**: slowly drifting mesh gradient in the current person's
  hue behind headers (iOS `MeshGradient`, Compose `Canvas` shader on API 33+,
  static fallback below). Respects Reduce Motion.
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
  (`thinkofme.app/i/{code}`).
- **Android**: MVVM + UDF (`StateFlow<UiState>`), repositories in `core:data`
  exposing `Flow`s from Firestore snapshot listeners, Hilt DI, type-safe
  navigation, App Links for invites.
- Both: previews/screens runnable with fake data, no Firebase needed — makes UI
  work and screenshots fast.

---

## 4. Milestones

| # | Milestone | Outcome |
|---|---|---|
| M0 | Foundations | Repo layout, tokens + codegen, CI for all three parts, Firebase emulator setup, rules + rules tests for the surprise guarantee |
| M1 | Design system | Both apps: colors, type, cards, buttons, chips, avatars w/ hue, countdown ring, ribbon-wrap animation, mesh background, haptics, component gallery screen |
| M2 | Auth & onboarding | Apple / Google / email link, profile, birthday, first wishes |
| M3 | Wishlist | CRUD, photos, link unfurl, share extension/target, visibility |
| M4 | Circles | Create, invite link/QR, join, members, roles |
| M5 | Claims | Wrap-it flow, claim-safe UI everywhere, buyer change alerts |
| M6 | Occasions & push | Occasions UI, FCM, scheduled reminders, settings |
| M7 | Polish | Widgets, activity feed, empty states, accessibility pass, RTL, localization |
| M8 | Launch prep | Account deletion/export, privacy policy, store assets, TestFlight/Play internal testing, make repo private |

---

## 5. What I need from you (can't be done from here)
1. **Firebase project** (free Spark plan to start; Blaze needed for scheduled
   functions/outbound fetch — usage at this scale is ~free).
2. **Apple Developer account** ($99/yr) — APNs key, Sign in with Apple, App
   Group for widgets/share extension, Universal Links.
3. **Google Play Console** ($25 one-time).
4. A **domain** for invite links / web viewer (e.g. `thinkofme.app` — check
   availability and trademark for the name).

Config files (`GoogleService-Info.plist`, `google-services.json`) and keys are
**never committed** (the repo is public) — CI gets them from GitHub Secrets;
local builds use a documented placeholder.

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

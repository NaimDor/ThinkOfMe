# Usage analytics

Both apps send usage events to **Firebase Analytics** (Google Analytics for Firebase), with the same
event and screen names on iPhone and Android, so one report covers both.

**What is counted:** that something happened, plus kinds, yes/no flags and counts.
**What is never sent:** wish titles, notes, links, prices, names, emails, circle names or any ids.
Analytics does not read the advertising identifier (iOS uses `FirebaseAnalyticsCore`, Android sets
`google_analytics_adid_collection_enabled=false`) and shares nothing with ads. Demo builds and the
store-screenshot runs send nothing.

Code: `android/core/data/.../Telemetry.kt`, `ios/App/UI/Telemetry.swift`; events are logged in the
Firebase data layer (`FirebaseRepositories.kt`, `FirebaseBackend.swift`) after the action succeeds.

## Where to look

Firebase console → your project → **Analytics**:
- **Realtime**: people using the app right now and the events they send (within a minute or two).
- **Dashboard**: daily/monthly active users, retention, engagement time, countries, app versions.
- **Events**: counts per event; mark `wish_add`, `gift_claim` and `invite_accept` as **key events**
  (the star) to see them as conversions.
- **Funnels / Explorations** (in the linked Google Analytics property): e.g.
  `sign_up → profile_save → wish_add → invite_create`.

Standard reports fill in after about 24 hours; Realtime and DebugView are immediate.

To watch your own phone live (**Analytics → DebugView**):
- Android: `adb shell setprop debug.firebase.analytics.app com.thinkofme.app`
- iOS: in Xcode, Edit Scheme → Run → Arguments → add `-FIRDebugEnabled`

If the Analytics section is missing, turn it on: Project settings → **Integrations → Google
Analytics → Enable**.

## Collected automatically

`first_open` (install), `session_start` and `user_engagement` (opens and time in app), `app_update`,
`os_update`, `app_remove` (Android), `notification_open` / `notification_receive` (Firebase
Messaging), plus device model, OS, app version, language and country (from IP; the IP itself is not
stored).

## Screens (`screen_view`, parameter `screen_name`)

| Name | Screen |
|---|---|
| `sign_in`, `onboarding` | Before the app (sign-in, first profile) |
| `home` | Home: next occasion, your people |
| `my_list` | My list |
| `person` | Someone's page and their wishes |
| `wish` | One wish |
| `add_wish`, `edit_wish` | New wish / editing one |
| `circles`, `join` | Circles; accepting an invite |
| `dates` | Dates and holidays |
| `settings` | Settings |

## Events

| Event | When | Parameters |
|---|---|---|
| `sign_up` / `login` | Signed in (first time / again) | `method`: google, apple |
| `profile_save` | Name and "about me" saved | `first`: 1 in onboarding |
| `wish_add` / `wish_edit` | A wish was saved | `kind` (product, idea), `priority` (nice, love, dream), `has_link`, `has_photo`, `has_price`, `has_note`, `shared` (0/1) |
| `wish_delete`, `wish_received` | Deleted / marked as received | |
| `link_read` | A product link was read to fill the wish | `success`, `has_title`, `has_price` |
| `photo_read` | A photo was read by AI | `success`, `is_product`, `has_price` |
| `photo_add` | A photo was uploaded to a wish | |
| `gift_claim` / `gift_unclaim` | "I'm getting this" / undo | |
| `gift_bought` | "I already bought it" toggled | `bought` |
| `circle_create` | New circle | `kind` (pair, group) |
| `invite_create` / `invite_accept` | Invite link made / someone joined with one | |
| `circle_leave` | Left a circle | |
| `date_add` / `date_edit` / `date_delete` | Dates | `kind` (birthday, anniversary, holiday, custom) |
| `reminders_set` | Reminders turned on/off | `enabled` |
| `account_delete` | Account deleted | |

Numbers to watch: daily active users, `wish_add` per active user, share of people who `invite_create`
and how many `invite_accept` follow, `gift_claim` count, `link_read` success rate, day-1/day-7
retention.

## Store privacy answers this adds

**App Store → App Privacy:** add **Usage Data → Product Interaction**, purpose **Analytics**, not
linked to the user, not used for tracking; also tick **Analytics** as a purpose on **Diagnostics →
Crash Data / Performance Data** and **Identifiers → Device ID**. (Already in `docs/RELEASE.md`.)

**Google Play → Data safety:** **App activity → App interactions**: collected, not shared, purpose
**Analytics**; **Device or other IDs**: add purpose **Analytics**.

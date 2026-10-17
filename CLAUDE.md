# CLAUDE.md — Expensify for Android

## What this is

The native Android client of **Expensify**, a personal money tracker: income, expenses and
transfers across your own accounts, with budgets, recurring entries, reports and alerts.
Amounts are Nepali rupees, written "Rs 12,34,567.50".

It is one of three clients of a single Firebase backend, and it does not own the backend:

| Client | Where | Role |
| --- | --- | --- |
| React web app | `../expensify` (sibling folder of this repo) | **Owns the data contract**: `docs/requirements.md`, `firestore.rules`, `src/features/*/*.types.ts`, `*.api.ts`, `*.domain.ts` |
| Angular web app `expensify-web` | Not on this machine | Older client, same contract |
| **This repo** | Kotlin + Jetpack Compose, single `:app` module | Same Firebase project, same Firestore documents. A user sees identical data on phone and web |

There is no server of our own. Firebase Authentication (email + password) and Cloud Firestore
are called straight from the device; the security rules in the web repo keep each user's data
theirs alone and refuse any write whose fields have the wrong shape.

## Read before building a feature

| Need | Read |
| --- | --- |
| How this app is built: layers, repositories, session, navigation, background work, decision log | [`docs/architecture.md`](docs/architecture.md) in this repo |
| What a feature does, exact wording, edge cases | `../expensify/docs/requirements.md` §3 (requirement IDs like `TXN-06`), §5 user stories, §13 screens |
| Balance, period, budget and recurring math | requirements §4, rules `BR-01`…`BR-18`. Port exactly; the dashboard, budgets and reports must agree with the web to the paisa |
| Firestore documents and per-field rules | requirements §8 and `../expensify/firestore.rules` |
| How each read and write is done (transactions, batches, error codes) | requirements §9 and the matching `../expensify/src/features/<feature>/<feature>.api.ts` |
| Pure logic to port 1:1 with unit tests | `../expensify/src/features/<feature>/<feature>.domain.ts`, `src/utils/date.ts`, `src/utils/amount.ts` |
| Default categories, account type icons and colors | requirements Appendix A, `category.domain.ts` |
| Every user-facing error sentence | `../expensify/src/lib/errors.ts` — reuse the same text |
| Screen copy and labels | `../expensify/src/features/<feature>/<feature>.labels.ts` |
| Creating a Firebase project, running the emulators | `../expensify/docs/firebase.md` |

**Precedence.** `requirements.md` wins on data and business rules. This file wins on Android
architecture and UI. This repo never changes the contract: if Android needs a new field, write
or collection, it is designed in the web repo first (rules + types + api + requirements §8/§9 in
one change) and deployed with `npm run deploy:rules` there, then used here. Never edit or copy
`firestore.rules` into this repo.

## State of the repo (October 2026)

Phase 0 and the first slices of phases 1–3 are built and verified on the `Pixel_10` AVD against
the emulators: the design system (`designsystem/`), the navigation shell with the bottom bar,
Hilt and Firebase wiring, `core/` (Money, Periods, Today, AppError, UiText, Messages) with unit
tests, **Login** and **Sign up** (`AuthNavHost` while signed out; the signup batch runs with the session
held signed out until the profile lands; the profile is read live for preferences), the full **Home**
dashboard (below), the full **Accounts** feature (list with total and archived section, add/edit form, account
page with its entries grouped by day, reconcile sheet, archive/restore, delete), the full
**Categories** feature under More (expense/income tabs, add/edit with the 48-icon picker and
palette color, archive/restore, delete, "Add default categories"), and the full **Budgets**
feature (list with live meters, add/edit form, budget page with results by period and the
expenses behind them, pause/resume, delete, and the foreground budget-alerts observer that
records `lastAlert` and delivers the alert through the notifier).
The full **Transactions** feature is built (list with period chips, search, filters sheet and
chips, totals strip, day groups; add/edit/duplicate form with the category grid, account picker,
date chips and "Save and add another"; entry page with Make recurring / Open recurring rule;
delete with balance moves in one Firestore transaction; last-used account in DataStore), and so
is **Recurring** under More (list with "Waiting for you", active, paused and ended; rule page with
next dates and the entries added; rule form with the next-dates preview; Confirm / Skip / Edit
for ask-first rules; the foreground runner that adds automatic rules' due entries, oldest first,
100 per write; a daily WorkManager catch-up job while the app is closed; the waiting-count badge
on More). **Reports** under More is built: the five reports (cash flow, spending by category,
category trends, accounts and net worth, budget history) with filters kept in the route's saved
state, tiles, Canvas bar and donut charts, tables with a pinned first column, and drill-downs into
the transaction list; `feature/reports/domain/ReportRules.kt` and
`feature/dashboard/domain/DashboardRules.kt` are the 1:1 ports with tests. Excel/PDF export is not
built (phase 4), so the report cards don't mention it. **Settings** is built: profile (photo as a
128 px JPEG data URL, name, email change with the verification link, read-only username), change
password, theme (applied at once and cached in DataStore for the first frame), the notification
switches with the reminder time and the phone's notification permission, delete all transactions
with the count, and delete account with typed DELETE + password, and a **Security** section
(17 October 2026) with the app lock: `feature/auth/lock/` keeps the signed-in shell behind the
phone's fingerprint, face or screen lock (`androidx.biometric`, `BIOMETRIC_WEAK | DEVICE_CREDENTIAL`)
when the app opens and when it comes back after a minute away; the switch is device-local in
DataStore keyed to the uid, the Firebase session is untouched, and `MainActivity` is a
`FragmentActivity` for the prompt; `AuthRepository` now has the
web's `updateProfile`/`updatePreferences`/`changeEmail`/`changePassword`/`deleteAccount` and the
profile-load repairs (`syncLogin`). The **Dashboard** is built (`feature/dashboard/`): the period
in `Routes.Home`'s arguments with This month / Last month / Custom chips pinned on scroll, the
privacy eye (masked on each open, DSH-11), the alerts bell with its unread badge, summary tiles
with their change from the previous period, balances, the spending donut with a Table toggle,
up to 5 budgets with meters, the 6-period trend bars (a bar selects its period), recent entries
with long-press Edit/Delete, upcoming recurring entries with Confirm/Skip, and the one
"Nothing logged in {period}" card for an empty period. **Notifications** are built
(`feature/notifications/`): the alert list behind the bell (`Routes.Alerts`, DataStore JSON, ≤ 50,
≤ 30 days, cleared for a different login), `Notifier` (snackbar with View while on screen,
otherwise a system notification on the `budget_alerts` / `bill_reminders` / `daily_reminder`
channels), the daily-reminder and recurring-reminder foreground observers, `ReminderWorker` for
both while the app is closed, and `expensify://` links from notifications into the budget, the
Recurring page or the list. None of this has been looked at on a device yet: this machine has no
AVD. `icon.png` (512 px, violet receipt) is the source art for the launcher icon; the adaptive layers under
`res/mipmap-*/` (background gradient, keyed receipt foreground, monochrome silhouette, legacy and round PNGs)
were generated from it with `java tools/IconGen.java icon.png app/src/main/res`; rerun that rather than
editing them if the art changes.

The `applicationId` is settled (16 October 2026): **`com.sandipshakya.expensify`**, with debug builds
at `com.sandipshakya.expensify.debug` so a release install never replaces the dev one. It is registered
in the real Firebase project `expensify-13477` as the Android app "Expensify Android"; its config is
`app/src/release/google-services.json` (git-ignored; refetch with `firebase apps:sdkconfig ANDROID
<appId> --project expensify-13477 -o app/src/release/google-services.json`, where `firebase apps:list
--project expensify-13477` prints the app id). The namespace and the Kotlin packages are still
`com.example.expensify`; renaming them is a separate, mechanical change. Release builds are signed with
`app/release.keystore` through `keystore.properties` at the repo root (both git-ignored; **back them up**,
a phone only accepts an update signed with the same key, so a lost keystore means uninstall and reinstall)
and R8 is on. The signed release APK was installed on `Pixel_10` and reached the real project (a wrong login
was refused with the right sentence). The app is **not going to Play** (17 October 2026): it is installed on
the user's own phone from `dist/` by `build-release.ps1` (Windows; `build-release.cmd` wraps it for cmd.exe or a
locked execution policy) or `build-release.sh` (Linux), which run the preflight checks, `assembleRelease`, and
with `-Install` / `--install` push the APK over adb.

## Toolchain and build

| Thing | Value | Notes |
| --- | --- | --- |
| AGP | 9.4.1 | **Built-in Kotlin.** Do not add `org.jetbrains.kotlin.android`; it is incompatible with the AGP 9 DSL. `compileSdk { version = release(37) }` is the new DSL, keep it |
| Gradle | 9.6, wrapper | Configuration cache on; JDK 25 toolchain auto-provisioned via foojay (`gradle/gradle-daemon-jvm.properties`) |
| Kotlin | 2.2.10 | KSP must be the matching `2.2.10-x.y.z` line |
| Compose | BOM 2026.02.01, Material 3 | Compose compiler via `org.jetbrains.kotlin.plugin.compose` |
| SDK | minSdk 24, target/compile 37 | Installed locally: platform `android-37`, build-tools 36.0.0 |
| Java target | 11 | |
| Release | `optimization { enable = true }`, signed from `keystore.properties` | R8 on; keep rules go in `app/src/main/keepRules/` (AGP 9 location), not `proguard-rules.pro`. Libraries ship their own consumer rules; the project file is still empty |
| KSP on AGP 9 | `android.disallowKotlinSourceSets=false` in `gradle.properties` | KSP 2.2.10-2.0.2 still registers its generated sources through `kotlin.sourceSets`, which built-in Kotlin forbids; the flag is the documented escape hatch. Remove it once a KSP release stops needing it |
| Firebase BOM | **34.x** (34.19.0), not 35 | BOM 35's `firebase-auth` 25.0.0 ships Kotlin 2.4 metadata, which the 2.2.10 compiler refuses as a hard error whenever the jar is on the classpath. Bump the BOM only together with Kotlin ≥ 2.4 (and KSP, Compose and serialization plugins) |
| Firebase entry points | `FirebaseAuth.getInstance()`, `FirebaseFirestore.getInstance()` | The Java API; `Firebase.auth` is one of the Kotlin extensions in that jar. `Query.snapshots()` from `firebase-firestore` is fine |

Every dependency goes through `gradle/libs.versions.toml`; never a literal coordinate in
`app/build.gradle.kts`. Use the Firebase BOM and the Compose BOM; pin everything else.

```powershell
.\gradlew.bat assembleDebug              # compile
.\gradlew.bat testDebugUnitTest          # JVM unit tests (domain, formatters, ViewModels)
.\gradlew.bat lint                       # Android lint
.\gradlew.bat installDebug               # onto the running emulator or device
.\gradlew.bat connectedDebugAndroidTest  # Compose UI tests; needs an emulator running
.\gradlew.bat assembleRelease           # signed, R8-shrunk APK; needs keystore.properties and app/src/release/google-services.json
.\build-release.ps1 [-Install]          # preflight + assembleRelease, APK copied to dist/; -Install pushes it to the phone over adb (./build-release.sh --install on Linux)
```

Local machine: SDK at `$env:LOCALAPPDATA\Android\Sdk`; `adb` and `emulator` are **not on PATH**,
call them by full path. One AVD exists, `Pixel_10`:

```powershell
& "$env:LOCALAPPDATA\Android\Sdk\emulator\emulator.exe" -avd Pixel_10
& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" devices
# Debug builds install as com.sandipshakya.expensify.debug; the classes keep the com.example.expensify namespace:
& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" shell am start -n com.sandipshakya.expensify.debug/com.example.expensify.MainActivity
# Verify UI work by looking at it: screenshot into the scratchpad and Read the PNG.
& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" exec-out screencap -p > "<scratchpad>\screen.png"
```

Git-ignore and never commit: `google-services.json`, `local.properties`, `*.keystore`, `.env*`.

## Stack decisions

| Concern | Choice | Why |
| --- | --- | --- |
| UI | Jetpack Compose, Material 3, single Activity, edge-to-edge | |
| Architecture | Package-by-feature mirroring the web's `features/`; `data` → `domain` → `ui`; MVVM with unidirectional data flow | A feature here reads like the same feature in the web repo |
| DI | Hilt ≥ 2.60 with KSP | Hilt 2.59+ requires AGP 9 (2.58 fails on it); `@HiltViewModel`, `@AndroidEntryPoint` |
| Navigation | Navigation Compose with type-safe `@Serializable` routes (`kotlinx-serialization` plugin) | Deep links and Back with state, like the web keeps state in the URL |
| Async | Coroutines + Flow; one `StateFlow<UiState>` per ViewModel | |
| Firebase | `firebase-bom`, `firebase-auth`, `firebase-firestore`; `com.google.gms.google-services` plugin | Same project as the web app; register an Android app in its console |
| Reads | Firestore snapshot listeners wrapped as `Flow` (`callbackFlow`), scoped to the screen | Live updates across devices; the web's 60 s query cache is a web detail, not contract |
| Writes | `runTransaction` / `WriteBatch` exactly as requirements §9 says | Balances and entries commit together |
| Device state | DataStore (Preferences) | Theme for first frame, last-used account, privacy mode, alert list and reminder state, owner uid |
| Charts | Compose `Canvas`, in-house donut and bar with a table toggle | Matches the web; no chart library |
| Photos | Profile photo is a base64 `data:image/jpeg` URL ≤ 200,000 chars; decode with `BitmapFactory`, scale picked images to 128 px JPEG | No image library needed |
| Background | WorkManager (phase 3): recurring catch-up and the daily reminder as local notifications | An Android improvement over the web, which only runs while a tab is open; same Firestore writes |
| Tests | JUnit 4 + kotlinx-coroutines-test + Turbine for domain and ViewModels; Compose UI tests; repository tests against the Firebase emulators (project `demo-android-test`) | Domain tests port the web's acceptance criteria (§5); one runner for JVM and instrumented tests |

Exports (Excel, PDF) and App Check (Play Integrity) come last; don't start them before phases 0–2.

## Package layout

```
com.example.expensify            (the namespace; the applicationId is com.sandipshakya.expensify)
├── ExpensifyApp.kt              @HiltAndroidApp
├── MainActivity.kt              enableEdgeToEdge(); setContent { ExpensifyTheme { AppNavHost() } }
├── core/
│   ├── firebase/                FirebaseModule, emulator wiring, UserPaths (users/{uid}/…), toEntity helpers
│   ├── money/                   Money formatting (Rs 12,34,567.50, Rs 1.2L), thousandths arithmetic
│   ├── date/                    LocalDate ↔ "YYYY-MM-DD", periods (BR-05, BR-06), Today provider
│   ├── error/                   AppError(code, field) + Firebase code → sentence mapping
│   └── device/                  DataStore-backed device state
├── designsystem/
│   ├── theme/                   Color.kt, Type.kt, Shape.kt, Theme.kt, ExpensifyColors (semantic extras)
│   ├── icons/                   IconMap: stored lumen-ui icon name → ImageVector, with a fallback
│   └── components/              AmountText, EntryRow, CategoryBadge, AccountBadge, StatTile, BudgetMeter,
│                                PeriodChips, EmptyState, SkeletonRow, SectionHeader, charts/
├── feature/<name>/              auth, dashboard, transactions, accounts, categories, budgets, recurring,
│   ├── data/                    reports, notifications, settings
│   │   └── XxxRepository.kt     THE ONLY FIRESTORE CODE for the feature (= the web's *.api.ts)
│   ├── domain/                  Pure Kotlin, no Android imports (= *.domain.ts), fully unit-tested
│   └── ui/                      XxxScreen (stateless, takes UiState + callbacks), XxxViewModel, XxxUiState
└── navigation/                  Routes.kt, AppNavHost.kt, BottomBar.kt
```

Rules that keep it clean:

- Only `feature/*/data` and `core/firebase` import `com.google.firebase.*`. ViewModels talk to
  repositories; screens never see Firestore types.
- `domain/` has no Android or Firebase imports, so it runs in plain JVM tests. Port each web
  `*.domain.ts` function with the same name and the same tests.
- Screens are stateless composables with a `@PreviewLightDark` preview using fake state.
  State hoisting all the way up; a composable never creates a ViewModel below screen level.
- One `UiState` data class per screen with `Loading` / `Empty` / `Content` / `Error` modelled
  explicitly; never a bag of nullable fields.
- All UI text in `res/values/strings.xml`, wording copied from the web's `*.labels.ts` and
  `errors.ts`. No hardcoded strings in composables.

## Firestore contract: the Android gotchas

The full model is requirements §8/§9. These are the places an Android port goes wrong:

| Topic | Do this |
| --- | --- |
| Paths | `users/{uid}/{accounts\|categories\|transactions\|budgets\|recurringRules}/{id}` and `usernames/{username}`. `uid` is always `FirebaseAuth.currentUser.uid`; throw `AppError(signed-out)` when null |
| Field names | camelCase exactly as §8. The rules use `keys().hasOnly([...])`: an extra or misspelled field is `PERMISSION_DENIED`. Write **every** field, `null` when it doesn't apply |
| Whole numbers | `interval`, `dayOfMonth`, `maxCount`, `occurrences`, `alertThresholds[]`, `lastAlert.threshold` are checked with `is int`. Write them as `Int`/`Long`, never `Double` (`2.0` is a double in Firestore and is refused) |
| Money | `amount`, `openingBalance`, `currentBalance`, `creditLimit` are `Double` with ≤ 2 decimals, never strings. Reading: Firestore hands back `Long` for integral values and `Double` otherwise, so always `(value as Number).toDouble()`. Sum in thousandths (`Long`) or `BigDecimal`; show rounded to the paisa |
| Dates | `date`, `startDate`, `endDate`, `nextDueDate`, `lastAlert.periodStart` are `"YYYY-MM-DD"` strings of the user's **local** calendar date. `LocalDate.toString()` / `LocalDate.parse()`. Never a `Timestamp`, never UTC |
| Timestamps | `createdAt` and `updatedAt`: `FieldValue.serverTimestamp()`; `createdAt` on create, `updatedAt` on every write. Read with `ServerTimestampBehavior.ESTIMATE` as `Instant` |
| IDs | `collection.document().id` generated on the device before the write, so one batch can write a document and refer to it |
| Balances | `FieldValue.increment(delta)` on each touched account in the **same** `runTransaction` as the entry write. Effects per §4: income +, expense −, transfer − source / + destination. Edit = reverse old + apply new; delete = reverse. An account deleted since the entry was written is skipped |
| `accountIds` | On every transaction: `[accountId]` or `[accountId, toAccountId]` for a transfer. Strip it when mapping to the domain type |
| Queries | At most **one** filter per query (`date` range, `accountIds array-contains`, or `recurringRuleId ==`), no `orderBy`; sort on the device (date, createdAt, id, all descending). There are no composite indexes and we don't add any |
| Signup | Check `usernames/{u}` is free → `createUserWithEmailAndPassword` → one batch: profile + `usernames/{u}` + 25 default categories. If the batch fails, `user.delete()` so the email can retry. Username: trimmed, lowercase, `[a-z0-9._-]{3,30}`, not `__…__` |
| Login | Value with "@" is an email; otherwise `get usernames/{value}` (allowed unauthenticated) → try `email`, then `pendingEmail`. Any failure: "Wrong username, email or password." |
| Icons | `icon` fields store **lumen-ui icon names** (`cart`, `train`, `wallet_alt`, …; 48 category + 7 account names in `category.domain.ts` and Appendix A). Keep storing those names so the web renders them; map to Material Symbols in `IconMap` with a generic fallback for unknown names |
| Colors | `#RRGGBB` strings; new categories pick from the 12-color palette, accounts take their type's color. Parse with `Color(android.graphics.Color.parseColor(hex))` |
| Errors | Map `FirebaseAuthException` / `FirebaseFirestoreException` codes to the sentences in `errors.ts`; Firebase's own messages never reach the screen. `AppError` codes: `wrong-password`, `taken`(field), `stale`, `not-found`, `signed-out` with the same screen behaviour as §9.1 |
| Recurring | Add-occurrences is a `runTransaction` that re-reads the rule and refuses (`stale`) when `dates[0] != nextDueDate` or the rule is paused; ≤ 100 dates per write. Same on Android, so two devices never double-add |
| Offline | Firestore's on-device cache stays **on** (Android default): lists read offline. Transactions and batches need a connection and fail with "Can't reach the server. Check your connection and try again."; the form stays open |
| Snapshots | Take `snapshots()` as the SDK raises them; never drop one for `metadata.isFromCache`. Online with nothing cached the SDK already holds the first event back, and the empty from-cache snapshot it does raise (a query synced empty before, or the client offline) is the only event coming: under `MetadataChanges.EXCLUDE` the server's "still empty" is a metadata-only change and is never delivered. A filter there left Home, Transactions and Budgets on skeletons for good (removed 17 October 2026) |

### Firebase wiring

- `app/src/release/google-services.json` is the Android app's config from the real project (see
  "State of the repo" for how to refetch it), git-ignored. It lives in the release source set only;
  the `com.google.gms.google-services` plugin runs with `missingGoogleServicesStrategy = IGNORE`, so
  debug builds have no json and keep the hand-made emulator init. The web config's `VITE_*` values
  are not used here: an Android app needs its own registration, not the web app's id.
- Emulators run from the web repo: `cd ../expensify; npm run emulators` (Auth 9099, Firestore
  8080, UI on http://localhost:4000, project `demo-expensify`). From an AVD the host is
  `10.0.2.2`: `FirebaseAuth.getInstance().useEmulator("10.0.2.2", 9099)` and
  `FirebaseFirestore.getInstance().useEmulator("10.0.2.2", 8080)`, called once before any other
  Firebase use, only when a `debug` build-config flag `USE_EMULATORS` is true. The emulators are
  plain HTTP, so the debug build needs a network security config allowing cleartext to
  `10.0.2.2` only. Release never points at emulators.
- Sign-up on the emulators gives a user with the 25 default categories and no accounts; the
  web app at http://localhost:5173 against the same emulators shows the same data, which is the
  quickest cross-client check. Without the web app, `python tools/seed_emulator.py` creates
  `sita.r` / `password123` with four accounts, four categories and six entries.
- **Android 17 (API 37) Local Network Protection.** Apps targeting SDK 37 can't reach local
  addresses, `10.0.2.2` included, without the runtime permission
  `android.permission.ACCESS_LOCAL_NETWORK`; connections just sit in `SYN_SENT` and Firestore
  reports "Backend didn't respond within 10 seconds". `src/debug/AndroidManifest.xml` declares
  it and `MainActivity` asks for it once when `USE_EMULATORS` is on; for scripted runs grant it
  with `adb shell pm grant com.sandipshakya.expensify.debug android.permission.ACCESS_LOCAL_NETWORK`. Release
  builds never need it. `adb shell` is exempt, so `nc` from the shell proves nothing about the app.
- The recurring catch-up also runs as a daily WorkManager job while the app is closed. Its first
  window opens ~23 h after sign-in and WorkManager refuses to run a periodic job early, so to check
  the closed-app path kill the app in the background (`adb shell am kill com.sandipshakya.expensify.debug`,
  never `force-stop`, which drops the jobs) and run the debug-only hook:
  `adb shell am broadcast -n com.sandipshakya.expensify.debug/com.example.expensify.feature.recurring.work.DebugCatchUpReceiver`.
- `ExpensifyApp` initialises Firebase by hand for the demo project in debug builds (no json there);
  release builds get the default `FirebaseApp` from `google-services.json` and never touch the emulators.
- A debug build with `-PuseEmulators=false` has neither json nor emulator init and would crash on the
  first Firebase call; use the release build to talk to the real project.

## Design system

The brief: it should feel like a seasoned Android designer shipped it. Calm, confident,
numbers-first. One primary action per screen. Generous whitespace, tonal surfaces instead of
shadows, nothing decorative that doesn't carry information. Fast: an expense is logged in under
five seconds (§1 goals), and the dashboard answers "where did it go, am I within budget, am I
saving more than last month" at a glance.

### Principles

1. **Numbers lead.** The amount is the largest thing on any row, tile or page. Amounts use
   tabular figures so columns align.
2. **Never color alone.** Income is green `+`, expense red `−`, transfer neutral with an arrow
   icon, always sign + color + icon (NFR-06). Budget states carry a chip label as well as a hue.
3. **Reach matters.** Primary actions sit in the bottom third: FAB, bottom bar, pinned Save.
   The top app bar is small and mostly empty.
4. **Empty states teach.** Every list and card has an empty state with its next action
   ("Add your first expense"), never a blank area.
5. **Gentle by default.** Archive and pause need no confirmation; delete always does, in a
   dialog that says what happens to the balance. No undo exists, so say so.
6. **Match the web's words.** Same labels, same error sentences, sentence case, no jargon.

### Color

Dynamic color is **off**: brand consistency, and green/red must mean money, not wallpaper.
Tokens come from the web's `src/styles/_colors.scss`, so both clients look like one product.

| Role | Light | Dark |
| --- | --- | --- |
| primary / onPrimary | `#2456E6` / `#FFFFFF` | `#3A68E9` / `#F9FAFB` |
| primaryContainer / onPrimaryContainer | `#EBEFFC` / `#1C43B4` | `#1A2340` / `#93ADF5` |
| background (page) | `#F7F7F7` | `#131416` |
| surface, cards, sheets | `#FEFEFE` | `#1B1C1F` |
| surfaceContainerLow / High | `#F6F7F5` / `#EBEDEB` | `#17181B` / `#2A2C30` |
| onSurface / onSurfaceVariant | `#07090F` / `#555755` | `#E9EBEE` / `#B6B9BE` |
| outline / outlineVariant (hairlines) | `#979996` / `#E5E7EB` | `#75787E` / `#2F3135` |
| error / errorContainer | `#FF4530` / `#FFECEA` | `#FF6B5C` / `#3A1C19` |
| **ExpensifyColors.income** / bg | `#36B37E` / `#EBF7F2` | `#3FBF87` / `#13291F` |
| **ExpensifyColors.expense** / bg | `#FF4530` / `#FFECEA` | `#FF6B5C` / `#3A1C19` |
| **ExpensifyColors.warn** / bg | `#FFAB00` / `#FFF7E6` | `#F5B53D` / `#2E2513` |
| **ExpensifyColors.info** / bg | `#00B8D9` / `#E6F0FC` | `#2EC4DF` / `#11282D` |
| **ExpensifyColors.transfer** | `#646663` | `#9B9EA4` |

`ExpensifyColors` is a `CompositionLocal` beside `MaterialTheme.colorScheme`. User data colors
(category and account hex) are lifted 35 % toward white in dark mode (`lerp(color, White, 0.35f)`)
so dark hues stay visible; icon badges use the color on a 12 % tint of itself.

Theme preference (`light` · `dark` · `system`) comes from the profile and is cached in DataStore
so the first frame is right; the login screen uses the cached value.

### Type

**The app follows the system font.** Xiaomi/HyperOS (Themes → Fonts), Samsung One UI (Font
size and style), OnePlus/OPPO (Personalizations → Font) and others let people install a custom
font; it works by replacing the platform's default `sans-serif` typeface, and only apps that use
that default pick it up. So:

- Every `TextStyle` uses `FontFamily.Default`. **No `res/font/`, no `Font(...)`, no Google
  Fonts provider, no `FontFamily.Serif`/`Monospace`** — any of these overrides the user's choice.
  On a stock device this is Roboto; on a themed device it is whatever they chose.
- Weights are **400, 500 and 700 only** (Normal, Medium, Bold). Custom fonts often lack 600, and
  the synthesized fallback looks wrong. The Android 12+ "Bold text" setting is honoured
  automatically as long as weights aren't forced beyond that.
- Amounts set `FontFeatureSettings = "tnum"` as a progressive enhancement: Roboto and most
  OEM fonts align digits with it, some ignore it. Layout never depends on it — amounts are
  right-aligned and columns size to content, so a proportional font still reads cleanly.
- System font size and display size are respected (see the checklist); no fixed-height text
  containers, since a custom font can be taller or wider than Roboto.

| Token | Use |
| --- | --- |
| displaySmall 700 | Hero amount on a transaction, account or budget page; the amount keypad display |
| headlineMedium 700 | Summary tiles (income, expense, net), total balance |
| titleMedium 500 | Screen titles, card titles, row primary text |
| bodyLarge / bodyMedium 400 | Row text, form values, dialog body |
| labelLarge 500 | Buttons, chips |
| labelSmall 400 | Captions, "Across 3 accounts · 1 not included", dates on rows |

Testing: a stock AVD can't install OEM fonts, so check on a real Samsung or Xiaomi device with
a custom font set, and at minimum `grep` that no `fontFamily`, `Font(` or `res/font` exists.

### Shape, space, elevation, motion

- Corners: 12 dp controls, 16 dp cards, 28 dp sheets and dialogs; FAB default.
- 4 dp grid. 16 dp screen gutters, 12 dp between cards, 16 dp card padding, 56 dp list rows
  (72 dp with a second line), 48 dp minimum touch targets.
- Elevation by tone (`surfaceContainer*`), not shadows. Hairline `outlineVariant` dividers only
  inside grouped lists; cards never have borders.
- Motion: Material 3 easing, 200–300 ms. `animateContentSize` on expanding cards,
  `animateItem` on list changes, crossfade between loading/content, shared-element transition
  from a row to its detail page where cheap. No bouncy or decorative animation.
- Haptic tick on Save and on Confirm/Skip of a recurring occurrence.

### Information architecture

Phone-first; the web's sidebar becomes a bottom bar.

| Bottom bar | Holds | Web route |
| --- | --- | --- |
| Home | Dashboard | `/` |
| Transactions | Day-grouped list, period, search, filters | `/transactions` |
| Accounts | Total balance, account cards, archived | `/accounts` |
| Budgets | Budget cards, paused | `/budgets` |
| More | Reports, Recurring (badge: waiting count), Categories, Settings, account menu | `/reports`, `/recurring`, `/categories`, `/settings` |

- A **FAB** on Home, Transactions and Accounts opens *Add entry*; on an account page it is
  preset to that account.
- *Add / edit entry*, and the account, category, budget and recurring-rule forms are
  **full-screen** (they need the keyboard and a pinned Save). Pickers (category grid, account,
  date) are **modal bottom sheets**. Confirmations are **AlertDialogs**. Detail pages
  (`/transactions/:id`, `/accounts/:id`, `/budgets/:id`, `/recurring/:id`) are pushed screens.
- Period and filter state lives in the route arguments so Back and deep links restore it, the
  way the web keeps it in the URL.
- Predictive back enabled; edge-to-edge with correct insets; a splash screen via the
  `core-splashscreen` API; adaptive launcher icon generated from `icon.png`.

### Key screens

**Home.** No app bar: a greeting line, the period chips (This month · Last month · Custom)
pinned on scroll, a privacy eye that masks every amount as "••••", the alerts bell with an
unread dot. Then: summary tiles (income, expense, net, savings rate, each with its change arrow
vs the previous period), total balance with the account strip, spending donut with top-5 +
Other and a Table toggle, up to 5 budgets with meters, the 6-period trend bars, recent entries,
upcoming recurring with Confirm/Skip. A period with nothing logged collapses the summary,
spending, trend and recent cards into one "Nothing logged in {period}" card with Add.

**Add entry.** Type segmented control (Expense · Income · Transfer) at top; a large amount
display with the Rs prefix and the numeric keyboard up immediately; category grid (8 tiles, then
"Show all N"), account chip (last-used account preselected), To-account for transfers, date chips
Today · Yesterday · pick, note. Save pinned bottom; "Save and add another" as a text button next
to it keeps type, account and date. Saving shows a snackbar "Saved · Rs 450 · Food" and returns.

**Transactions.** Period selector, search, filter button with a count badge and removable
chips, totals strip (income, expense, net) that updates with filters, day-grouped list with each
day's net in the header. Rows: category icon badge, description (note or category), account
below, amount right with sign and color; "Upcoming" and "Recurring" chips where §3.3 says.

**Account page.** Hero balance ("Owed" for cards and loans), opening balance and credit limit
with a usage bar (accent < 80 %, warn ≥ 80 %, error ≥ 100 %), then its entries grouped by day.
Actions: Add (preset), Reconcile (sheet showing in-app figure and the adjustment), Edit, Archive.

**Budgets.** Cards with name, spent of limit, meter, state chip (On track · Warning · Over),
% used, "Rs X/day safe to spend". Budget page: current period plus past periods (8 weeks, 6
months or 2 years) and the expenses behind a selected period.

**Settings.** Profile (photo, name, email with pending-link note, read-only username),
Security (the app lock switch: fingerprint, face or screen lock on open and after a minute away),
Appearance (theme), Notifications (switches, reminder time, system permission state), Data and
privacy (delete all transactions with count, delete account with typed DELETE + password).

### Accessibility and polish checklist (every screen)

- 48 dp targets; `contentDescription` on every icon-only control; amounts have semantics that
  read as "minus 450 rupees", not "Rs −450".
- Works at 200 % font scale without clipping; long names ellipsize, amounts never do.
- Follows the system font, font size, display size and "Bold text" settings; nothing in the
  app pins a typeface or a `sp`-less text size.
- 4.5:1 text contrast in both themes; focus order top-to-bottom, left-to-right.
- Skeleton rows on first load only; thereafter content stays while it refreshes.
- Keyboard: amount fields open the numeric pad, IME action "Done" saves, forms scroll above the
  keyboard (`adjustResize` is already set).
- Previews for light and dark, with empty, loading and content states.

## Money and dates on Android

- Format with `android.icu.text.DecimalFormat("#,##,##0.00", DecimalFormatSymbols(Locale("en","IN")))`
  (ICU supports the lakh/crore secondary grouping; `java.text.DecimalFormat` does not), then
  prefix `Rs ` and add the sign yourself: `Rs 12,34,567.50`, `−Rs 450.00`, `+Rs 5,000.00`.
- Compact for chart axes: `Rs 1.2Cr` ≥ 1 crore, `Rs 1.2L` ≥ 1 lakh, `Rs 45K` ≥ 1,000, one decimal.
- Unit-test the formatter against the exact examples in requirements BR-03 and `amount.ts`.
- "Today" is `LocalDate.now()` on the device, re-read on resume and every minute while
  visible (`core/date/Today`), so periods roll over at midnight. Weeks are Monday–Sunday.

## Working rules for Claude

1. Before a feature, read its requirements section, its `*.api.ts` and `*.domain.ts`. Port,
   don't reinterpret. Quote requirement IDs in commit messages and test names (`TXN-06`).
2. Never invent a field, a query or a write. If the contract doesn't cover what a screen needs,
   say so and point to the web repo instead of working around it.
3. Domain first: port the pure functions with unit tests, then the repository, then the
   ViewModel, then the screen with previews. A PR for a feature includes all four.
4. Every new screen or component ships with a `@PreviewLightDark` preview and empty/loading
   states. Check it on `Pixel_10` and look at the screenshot before calling UI done.
5. Keep the version catalog tidy; don't bump AGP, Kotlin or Gradle casually, they are coupled
   (AGP 9 ↔ Kotlin 2.2.10 ↔ Hilt ≥ 2.59 ↔ KSP 2.2.10-x).
6. `.\gradlew.bat assembleDebug` and `testDebugUnitTest` pass before any commit. Commit only
   when asked; never commit `google-services.json`.
7. No amounts, notes or names in logs (NFR-08).

## Build order

| Phase | Scope | Done when |
| --- | --- | --- |
| 0 Foundation | Final `applicationId`; design system (theme, type, components, IconMap); nav shell with bottom bar; Hilt; Firebase wiring + emulator switch; money/date/error cores with tests | App boots to the shell in light and dark on `Pixel_10`; formatter and period tests green |
| 1 Sign-in | Login (username or email), signup with the batch, session watch, logout, missing-profile repair | US-08 and US-09 pass against the emulators |
| 2 Core money | Accounts, categories, transactions (add, edit, duplicate, delete, list, filters, detail), dashboard | US-01…US-03, US-07 pass; dashboard figures equal the web's for the same data |
| 3 Plans | Budgets with alerts, recurring rules with the runner and ask-first flow, settings; WorkManager catch-up and local notifications | US-04…US-06 pass; a rule due at 00:05 adds its entry without opening the app |
| 4 Reports and extras | The five reports with charts and tables, Excel/PDF export, App Check (Play Integrity), release build with R8 | Report totals equal the web's; signed release installs clean |

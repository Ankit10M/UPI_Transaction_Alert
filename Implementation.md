# Implementation Record — UPI Voice Alert (MVP)

This document records everything scaffolded for the MVP described in `CLAUDE.md`,
plus the exact commands needed to build and run the application.

---

## 1. What was implemented

A complete, Gradle-based Android project (Kotlin + Jetpack Compose + Hilt + Room +
DataStore + WorkManager) implementing the full MVP (Phases 1–5 of `CLAUDE.md`
Section 10). It is fully offline — zero network calls.

| Area | Deliverable |
|---|---|
| Build system | Gradle Kotlin DSL, version catalog (`gradle/libs.versions.toml`), Gradle 8.9 wrapper, AGP 8.5.2, Kotlin 2.0.21 |
| App identity | `applicationId` / namespace `com.upivoicealert`, `minSdk 26`, `targetSdk 34` |
| Capture | `UpiNotificationListenerService` (package-gated to GPay/PhonePe/Paytm/BHIM, thin routing) |
| Filtering | `NotificationFilter` (configurable keyword resource) + `TransactionClassifier` |
| Parsing | Versioned parser scaffold (`GPayParserV1`, `PhonePeParserV1`, `PaytmParserV1`, `BhimParserV1`), `ParserVersionResolver`, `TransactionValidator`, Unparsed Queue |
| Persistence | Room DB (`transactions`, `unparsed_notifications`), hybrid dedup (reference-ID + 2-min fuzzy window) |
| Voice | Offline `TextToSpeech` engine (English + Hindi), number-to-words (Indian numbering), templates, speech rate |
| UI | Compose M3: mandatory Consent → Privacy → Permission onboarding, Dashboard, History (+detail dialog), Settings, Debug (unparsed) screen |
| Background | `CleanupWorker` + `RetryFailedParseWorker` (periodic only, never real-time) |
| Tests | `AmountToWordsConverterTest`, `TransactionClassifierTest`, `NotificationFilterTest` |

Project tree is under `app/src/main/java/com/upivoicealert/` with `data` / `domain`
/ `service` / `filter` / `parser` / `voice` / `ui` / `work` / `di` / `utils`
layers exactly as specified in `CLAUDE.md` Section 5.

## 2. Environment prerequisites

| Tool | Version required | Purpose |
|---|---|---|
| JDK | 17 | Compile / Gradle daemon |
| Android Studio | Latest stable (Koala+ recommended) | IDE, SDK manager, emulator |
| Android SDK | Platform 34 (compileSdk) | Build + runtime |
| Gradle | None to install — wrapper `gradlew.bat` / `gradlew` downloads 8.9 automatically | Build |

> The Gradle wrapper JAR is already committed under `gradle/wrapper/gradle-wrapper.jar`
> and `gradle-wrapper.properties` pins Gradle 8.9.

## 3. First-time setup

### 3.1 Open in Android Studio (recommended path)
1. `File > Open` → select the project root (`D:\UPI_Notification_Alert`).
2. Let Gradle sync (first sync downloads dependencies).
3. Verify SDK: `Settings > Languages & Frameworks > Android SDK` → API 34 platform installed.

### 3.2 Or build from the command line

PowerShell (Windows):

```powershell
# 1) Verify Java 17 is on PATH
java -version

# 2) Build the debug APK (first run downloads Gradle 8.9 + dependencies)
.\gradlew.bat :app:assembleDebug
```

Bash / macOS / Linux:

```bash
java -version
./gradlew :app:assembleDebug
```

Output APK:

```
app/build/outputs/apk/debug/app-debug.apk
```

## 4. Install and run

### 4.1 Prepare a device
- Enable Developer options + USB debugging on the device, and connect it, **or**
  start an emulator in Android Studio.
- Confirm the device is visible:

```powershell
adb devices
```

### 4.2 Install

```powershell
adb install -r app\build\outputs\apk\debug\app-debug.apk
```

### 4.3 Launch the app

```powershell
adb shell am start -n com.upivoicealert/.MainActivity
```

### 4.4 First-run onboarding (non-negotiable, per CLAUDE.md §7)
1. **Consent Screen** — Accept & Continue.
2. **Privacy Explanation** — Accept & Continue.
3. **Permission Setup** — tap *Open settings* and grant **Notification access**
   (`Settings > Apps > Special app access > Notification access`), then Finish.
   Battery exemption is optional.
4. Granting access binds `UpiNotificationListenerService`; the Dashboard indicator
   should switch to "Notification listener is active".

## 5. Useful commands

```powershell
# Clean build
.\gradlew.bat clean

# Build debug APK
.\gradlew.bat :app:assembleDebug

# Run all unit tests (parser/filter/converter — pure JVM, no device needed)
.\gradlew.bat :app:testDebugUnitTest

# Run a single test class
.\gradlew.bat :app:testDebugUnitTest --tests "com.upivoicealert.AmountToWordsConverterTest"

# Reinstall + relaunch in one shot
adb install -r app\build\outputs\apk\debug\app-debug.apk; adb shell am start -n com.upivoicealert/.MainActivity

# Watch live listener/parser logging from the service
adb logcat -s UpiListenerService:V ProcessTransaction:W

# Pull raw capture logs during Phase 1 validation
adb logcat -d -s UpiListenerService:V
```

## 6. Validating each MVP phase on a device

| Phase | How to verify |
|---|---|
| 1 — Notification capture | With Notification Access granted, receive a UPI payment → `UpiListenerService` logs appear in Logcat |
| 2 — Parsing | Send/receive a real payment → transaction appears in **History**; unparseable ones land in **Settings > Debug mode > Unparsed notifications** |
| 3 — Voice | With voice enabled in Settings, a saved RECEIVED payment is spoken (English/Hindi per language setting) |
| 4 — History/Dashboard | Dashboard totals + latest payment update live from Room Flow |
| 5 — Settings/Onboarding | Voice toggle, language, speech speed slider, permission status, debug mode |

## 7. Current limitations (intentionally stubbed — do NOT guess)

- **Parsers are non-matching stubs.** `canParse()` returns `false` in every
  `*ParserV1` until real notification samples are collected into
  `notification_samples/` (per `CLAUDE.md` Phase 2 / Rule 4). Unmatched
  notifications go to the Unparsed Queue instead of being guessed at.
- **Package names** in `utils/PackageNames.kt` must be verified against installed
  apps (`adb shell pm list packages`) before shipping (`CLAUDE.md` Module 1 note).
- **TTS audio stream** is `STREAM_MUSIC`; per `CLAUDE.md` Module 3 this must be
  confirmed on real devices.
- **Foreground-service mode** is off (`Constants.USE_FOREGROUND_SERVICE=false`);
  enabling it on Android 13+ requires the `POST_NOTIFICATIONS` runtime flow
  (`CLAUDE.md` §7.5).
- **Not built:** SENT/REFUND/FAILED/PENDING announcements, any network/sync,
  Play Store publishing (MVP ships sideloaded).

## 8. Files changed in this implementation session

All files under the repo root are new in this session:

```
build.gradle.kts / settings.gradle.kts / gradle.properties / .gitignore
gradle/libs.versions.toml
gradle/wrapper/gradle-wrapper.{jar,properties}
gradlew / gradlew.bat
app/build.gradle.kts, app/proguard-rules.pro
app/src/main/AndroidManifest.xml
app/src/main/res/**            (strings, themes, filter keywords, icons, backup rules)
app/src/main/java/com/upivoicealert/**   (65 Kotlin sources, see tree in §1)
app/src/test/java/com/upivoicealert/*Test.kt
notification_samples/README.md
Implementation.md              (this file)
```

## 9. Next steps before real usage

1. Install on a device and validate **Phase 1 capture** (Logcat) with real UPI apps.
2. Collect real notification samples into `notification_samples/` (success,
   failed, pending, promo per app).
3. Implement each `*ParserV1` regex against those samples + add parser unit tests.
4. Verify TTS stream/language behavior and, if needed, enable the
   foreground-service path (Section 7 above).

---

# 10. Debugging Analysis — Why a real ₹10 UPI payment produced NO voice alert

**Date:** 2026-08-06 · **Device:** Xiaomi · **Package:** `com.upivoicealert`
**Observed:** APK installed; ₹10 UPI payment received on device; no voice alert;
Logcat filtered for `UPI_ALERT` shows nothing, only `AndroidRuntime` lines.

This section records a full code-inspection of the current implementation,
the confirmed root cause, and the ordered debugging plan. **No code was
modified during this analysis.**

---

## 10.1 A. Complete project architecture (verified from source)

### 10.1.1 Project layout
Single-module Android app. Root: `D:\UPI_Notification_Alert`.

- `:app` — the only Gradle module (`settings.gradle.kts`).
- Build: Gradle 8.9 wrapper, AGP 8.5.2, Kotlin 2.0.21, KSP, Hilt 2.52,
  Compose BOM 2024.10.01, Room 2.6.1, DataStore 1.1.1, Work 2.9.1.
  Version catalog: `gradle/libs.versions.toml`.
- `minSdk 26` / `targetSdk 34` / `compileSdk 34`, Java 17.
- Source root: `app/src/main/java/com/upivoicealert/`.

### 10.1.2 Package structure (as actually built)

```
com.upivoicealert
├── UpiVoiceAlertApplication.kt        @HiltAndroidApp, Configuration.Provider (WorkManager), schedules periodic workers
├── MainActivity.kt                    @AndroidEntryPoint Compose Activity; routes to Onboarding or Main based on consent flag
├── di/
│   ├── AppModule.kt                    filter keywords (R.array.notification_filter_keywords) + parser list (4 V1 stubs)
│   ├── DatabaseModule.kt               Room AppDatabase + both DAOs (no exportSchema, version 1)
│   └── RepositoryModule.kt             @Binds TransactionRepository / SettingsRepository
├── data/
│   ├── database/
│   │   ├── AppDatabase.kt              entities = [TransactionEntity, UnparsedNotificationEntity], version 1
│   │   ├── TransactionEntity.kt        transactions table (11 cols)
│   │   ├── TransactionDao.kt           observeAll/observeReceivedSuccess/observeLatest/observeCount/observeCountSince/
│   │   │                               findByReferenceId/findFuzzyDuplicate/insert(IGNORE)/clearAll
│   │   ├── UnparsedNotificationEntity.kt  unparsed_notifications table
│   │   └── UnparsedNotificationDao.kt  observeAll/getAll/insert(IGNORE)/deleteById/clearAll/deleteOlderThan
│   ├── repository/
│   │   ├── TransactionRepositoryImpl.kt  hybrid dedup (reference-ID match, else amount+sender+app ±2min window)
│   │   └── SettingsRepositoryImpl.kt     pass-through to SettingsDataStore
│   ├── datastore/SettingsDataStore.kt    keys: voice_enabled, language, speech_rate, debug_mode,
│   │                                     has_accepted_privacy_disclosure, tts_fallback_occurred, mobile_number
│   └── model/TransactionMappers.kt       Entity <-> domain mappers (enumValueOf based)
├── domain/
│   ├── model/  Transaction, TransactionType, TransactionStatus, ParseStatus,
│   │           UnparsedNotification, VoiceLanguage (ENGLISH, HINDI, MARATHI)
│   ├── repository/  TransactionRepository (interface), SettingsRepository (interface)
│   └── usecases/
│       ├── ProcessTransactionUseCase.kt  @Singleton orchestrator of the whole pipeline (filter→classify→resolve→parse→validate→dedup→save→voice)
│       ├── GetTransactionHistoryUseCase.kt  receivedSuccess()/all()
│       ├── CheckDuplicateUseCase.kt      suspend invoke -> repository.isDuplicate
│       └── RetryUnparsedQueueUseCase.kt  deletes then reprocesses queue; returns count saved
├── service/UpiNotificationListenerService.kt  @AndroidEntryPoint NotificationListenerService (thin capture+routing)
├── filter/
│   ├── NotificationFilter.kt            package whitelist + keyword substring rejection
│   └── TransactionClassifier.kt         type/status keyword classifier (defaults ambiguous → SENT)
├── parser/
│   ├── TransactionParser.kt             interface + ParserException
│   ├── ParserVersionResolver.kt         groupBy packageName; first canParse() wins
│   ├── ParsedTransaction.kt             data class + toTransaction() mapper (hardcodes RECEIVED+SUCCESS)
│   ├── TransactionValidator.kt          amount>0, sender non-blank, upiApp in labels
│   ├── gpay/GPayParserV1.kt             canParse() == false (STUB)
│   ├── phonepe/PhonePeParserV1.kt       canParse() == false (STUB)
│   ├── paytm/PaytmParserV1.kt           canParse() == false (STUB)
│   └── bhim/BhimParserV1.kt             canParse() == false (STUB)
├── voice/
│   ├── VoiceAnnouncementEngine.kt       @Singleton TTS wrapper (init on first construction, STREAM_MUSIC, QUEUE_ADD)
│   ├── AmountToWordsConverter.kt        EN/HI/MR number-to-words (Indian numbering)
│   └── AnnouncementTemplates.kt         phrase templates per language
├── ui/
│   ├── navigation/AppNavigation.kt      MainNavHost (3 bottom tabs + debug) / OnboardingNavHost (4 screens)
│   ├── dashboard/DashboardScreen.kt + DashboardViewModel.kt
│   ├── history/HistoryScreen.kt + HistoryViewModel.kt
│   ├── settings/SettingsScreen.kt + SettingsViewModel.kt
│   ├── debug/UnparsedNotificationsScreen.kt + UnparsedNotificationsViewModel.kt
│   ├── onboarding/ConsentScreen.kt, PrivacyExplanationScreen.kt, MobileNumberScreen.kt, PermissionSetupScreen.kt
│   └── theme/Theme.kt, Color.kt
├── work/
│   ├── WorkScheduler.kt                 schedulePeriodic: CleanupWorker (24h), RetryFailedParseWorker (12h)
│   ├── CleanupWorker.kt                 delete unparsed older than 30 days
│   └── RetryFailedParseWorker.kt        retryAll()
└── utils/
    ├── PackageNames.kt                  whitelist + labels (GPAY/PHONEPE/PAYTM/BHIM)
    ├── NotificationAccessHelper.kt      reads Settings.Secure enabled_notification_listeners
    ├── BatteryOptimizationHelper.kt     isIgnoringBatteryOptimizations + requestExemption
    ├── DateTimeUtils.kt                 formatters, startOfToday, ₹ currency format
    └── Constants.kt                     DEDUP_WINDOW_MS=2min, UNPARSED_RETENTION_DAYS=30,
                                        CLEANUP/PERIOD, USE_FOREGROUND_SERVICE=false
```

### 10.1.3 DI setup (Hilt)
- `@HiltAndroidApp` Application; `@AndroidEntryPoint` on MainActivity and on the
  NotificationListenerService.
- SingletonComponent modules: AppModule (filter keywords + parser list),
  DatabaseModule (Room), RepositoryModule (binds).
- Worker injection via `HiltWorkerFactory` + manual WorkManager initializer
  removal in the manifest (the `<provider>` block with `tools:node="remove"`).
  `UpiVoiceAlertApplication implements Configuration.Provider`.

### 10.1.4 Manifest / permissions (`app/src/main/AndroidManifest.xml`)
- `POST_NOTIFICATIONS` declared but never requested at runtime (no
  `requestPermissions` call anywhere).
- `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` declared; requested only via the
  onboarding / Settings deep link.
- Service declaration:
  ```xml
  <service
      android:name=".service.UpiNotificationListenerService"
      android:permission="android.permission.BIND_NOTIFICATION_LISTENER_SERVICE"
      android:exported="true">
      <intent-filter>
          <action android:name="android.service.notification.NotificationListenerService" />
      </intent-filter>
  </service>
  ```
- No `FOREGROUND_SERVICE` permission, no foreground service started
  (`Constants.USE_FOREGROUND_SERVICE = false` and the service never calls
  `startForeground()`).
- `android:allowBackup="false"`, custom data-extraction rules.
- Only activity: `.MainActivity` (LAUNCHER, exported).

---

## 10.2 B. Current runtime flow (what actually happens in the code)

```
User installs + opens app (MainActivity)
        ↓
Onboarding (first launch only): Consent → Privacy → Mobile number → Permission setup
        ↓
Notification access granted manually via Settings deep link (PermissionSetupScreen)
        ↓
System binds UpiNotificationListenerService  → onListenerConnected() logs "Notification listener connected"
        ↓
UPI app (GPay/PhonePe/Paytm/BHIM) posts "payment received" notification
        ↓
onNotificationPosted(sbn)  [service/UpiNotificationListenerService.kt:40]
        ↓ package gated: packageName in PackageNames.ALL (hardcoded 4 packages)
        ↓ extractRawText(): title + text + bigText joined (extras only)
        ↓ launch coroutine → ProcessTransactionUseCase.processNotification(pkg, text, postTime)
            ├─ filter.isPaymentCandidate(pkg, text)      → package check + keyword substring check
            ├─ classifier.classify(text)                 → must be RECEIVED + SUCCESS
            ├─ resolver.resolve(pkg, text)               → parser where canParse(text)==true
            │      *** ALL PARSERS RETURN false → resolver returns null ***
            │      → addUnparsedNotification(reason="no parser matched") → RETURN (never announced)
            │      (this branch ALWAYS executes for every valid received notification today)
            ├─ parser.parse(text, postTime)              → (unreachable today)
            ├─ validator.validate(...)                   → (unreachable today)
            ├─ repo.insertTransactionIfNotDuplicate()    → (unreachable today)
            └─ voiceEngine.prepare + speak(announcement) → (unreachable today)
        ↓
Dashboard/History update via Room Flow          → (never happens today)
```

**Key structural fact:** `ProcessTransactionUseCase.processNotification` is
written correctly, but the parser stage is guaranteed to fail for every
notification, so the entire downstream chain (validate → dedup → Room insert →
TTS) is **dead code at runtime today**.

---

## 10.3 C. Files responsible for each stage

| Stage | File(s) |
|---|---|
| App entry / DI root | `UpiVoiceAlertApplication.kt`, `MainActivity.kt` |
| Onboarding + permission flow | `ui/onboarding/*.kt`, `utils/NotificationAccessHelper.kt`, `utils/BatteryOptimizationHelper.kt` |
| Notification capture | `service/UpiNotificationListenerService.kt` (`onNotificationPosted` :40, `extractRawText` :66) |
| Package whitelist | `utils/PackageNames.kt` |
| Filtering | `filter/NotificationFilter.kt` (+ `res/values/notification_filter_keywords.xml`) |
| Classification | `filter/TransactionClassifier.kt` |
| Parser resolution | `parser/ParserVersionResolver.kt` (+ `di/AppModule.kt` parser list) |
| Parsing | `parser/gpay/GPayParserV1.kt`, `parser/phonepe/PhonePeParserV1.kt`, `parser/paytm/PaytmParserV1.kt`, `parser/bhim/BhimParserV1.kt` |
| Validation | `parser/TransactionValidator.kt` |
| Orchestration | `domain/usecases/ProcessTransactionUseCase.kt` |
| Dedup + persistence | `data/repository/TransactionRepositoryImpl.kt`, `data/database/TransactionDao.kt` |
| Storage schema | `data/database/AppDatabase.kt`, `TransactionEntity.kt`, `UnparsedNotificationEntity.kt` |
| Unparsed queue | `domain/model/UnparsedNotification.kt`, `ui/debug/*.kt`, `work/RetryFailedParseWorker.kt`, `work/CleanupWorker.kt` |
| Voice | `voice/VoiceAnnouncementEngine.kt`, `voice/AnnouncementTemplates.kt`, `voice/AmountToWordsConverter.kt` |
| UI (read from Room Flow) | `ui/dashboard/*`, `ui/history/*`, `ui/settings/*` |
| Settings storage | `data/datastore/SettingsDataStore.kt`, `data/repository/SettingsRepositoryImpl.kt` |

---

## 10.4 D. Bugs / issues found (code inspection)

### D.1 CONFIRMED BLOCKER — all parsers are non-matching stubs
- `GPayParserV1.canParse()` → `false` (`GPayParserV1.kt:21`)
- `PhonePeParserV1.canParse()` → `false` (`PhonePeParserV1.kt:18`)
- `PaytmParserV1.canParse()` → `false` (`PaytmParserV1.kt:18`)
- `BhimParserV1.canParse()` → `false` (`BhimParserV1.kt:18`)

Consequence: `ParserVersionResolver.resolve()` always returns `null`, so every
`RECEIVED + SUCCESS` notification takes the "no parser matched" branch
(`ProcessTransactionUseCase.kt:58-70`), is written to the **Unparsed Queue**,
and is never saved or spoken. This is deterministic — even a fully working
listener, filter, classifier, Room and TTS cannot produce a voice alert with the
current parser code. **This alone explains the missing voice alert.**

### D.2 Logging gap — "UPI_ALERT" tag does not exist; capture is silent
The entire app contains only 7 `Log` calls:
- `UpiNotificationListenerService.kt` (TAG `UpiListenerService`): "connected"(:32),
  "disconnected"(:37), "error handling notification"(:52), "notification
  removed"(:58)
- `ProcessTransactionUseCase.kt` (TAG `ProcessTransaction`): parser failure(:75),
  "skipping announcement: mobile number mismatch"(:127), voice failure(:131)

There is **no log on successful capture** of a UPI notification, no log for the
filter/classifier decisions, and no log for the "no parser matched" path (that
only goes to Room). Searching Logcat for `UPI_ALERT` therefore finds nothing
regardless of whether the pipeline ran. The user's negative Logcat result is
**expected and uninformative** with the current code.

### D.3 Classifier may silently drop valid "received" text
`TransactionClassifier` defaults to `SENT` when neither "received" nor
"credited" appears (`TransactionClassifier.kt:29-34`). Any real-world success
notification phrased without those two words (e.g. "₹10 got from Rahul",
"You got ₹10", "Money in", app-localized variants) is classified `SENT` and
returns `IGNORED` before the parser is even consulted (`ProcessTransactionUseCase.kt:54`).
It will not even reach the Unparsed Queue — it is silently dropped.

### D.4 Filter keyword "collect" can false-drop legitimate received payments
`notification_filter_keywords.xml` contains `collect`. The filter drops any
notification whose lowercased text contains "collect". Notifications like
"₹10 collected from Rahul" or "Money collected" (received) would be dropped as
promotional. This is a real false-drop risk for some apps' phrasing.

### D.5 Mobile-number announcement filter can silently skip valid payments
`ProcessTransactionUseCase.process()` (:116-129): if the user configured a mobile
number during onboarding (`MobileNumberScreen`), the announcement only runs when
`transaction.rawNotification.contains(configuredMobile)`. UPI "received"
notifications usually do **not** contain the receiver's own mobile number; they
contain the sender's name. So a user who entered their number will never hear
anything, and the skip is only a `Log.d` (level debug, easily missed).
Also the number matching is naive substring — "+91 " formatting differences break it.

### D.6 Text extraction misses `EXTRA_TEXT_LINES`
`extractRawText()` reads only `EXTRA_TITLE`, `EXTRA_TEXT`, `EXTRA_BIG_TEXT`
(`UpiNotificationListenerService.kt:68-70`). Several apps render the amount/
sender in `EXTRA_TEXT_LINES` (CharSequence[]) for messaging-style notifications;
those fields would be lost, producing a possibly-parseable-but-empty string.

### D.7 Xiaomi/MIUI background constraints (environment, not code)
- The service is a **plain** `NotificationListenerService` (not foreground).
  `Constants.USE_FOREGROUND_SERVICE = false`; nothing calls `startForeground()`.
- On MIUI, without (a) Autostart permission (Security app), (b) battery saver
  exclusion, and (c) recent app-kill protection, the listener can be killed and
  **not** automatically rebound; `onListenerDisconnected` only logs.
- `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` is offered but is not sufficient on
  MIUI; the app must also be added to Autostart.
- No reboot receiver / boot-persistence; after a reboot the service is not
  restarted until the user opens the app again or the system rebinds it.

### D.8 TTS readiness race + silent failure (secondary, unreachable today)
`VoiceAnnouncementEngine` builds `TextToSpeech` in its `init` and sets
`ready` asynchronously via `OnInitListener`. If a transaction arrives before the
engine reports ready, `speak()` silently returns (`VoiceAnnouncementEngine.kt:61`).
Because the engine is a singleton created lazily on first injection into the
service process, the first alert after process start is the most likely to be
silently dropped. Also uses `STREAM_MUSIC` — if media volume is muted, alerts are
inaudible (documented in CLAUDE.md as needing device testing).

### D.9 Minor
- `TransactionClassifier` order: "failed"/"declined" → FAILED checked first,
  but a notification containing both "received" and "failed" (e.g. a refund
  notice) would be classified FAILED and ignored — acceptable for MVP.
- `CheckDuplicateUseCase` and `GetTransactionHistoryUseCase` are wired but
  `CheckDuplicateUseCase` is not used by the pipeline (repo does dedup directly);
  harmless.
- `PackageNames` verified correct as of 2026 (Paytm still `net.one97.paytm`).

---

## 10.5 E. Recommended debugging steps (in order)

> The goal of step 1–3 is to determine whether capture works at all (Xiaomi
> concern) before fixing the guaranteed parser blocker.

1. **Verify listener binding** (confirms D.7 vs everything else)
   - `adb logcat -s UpiListenerService:V ProcessTransaction:V`
   - Re-open the app, then toggle Notification access off/on in MIUI settings.
   - Look for `Notification listener connected`. If absent, the service is not
     running/bound → MIUI autostart/battery issue (step 2).

2. **Fix MIUI background restrictions** (if step 1 shows no "connected")
   - Grant **Autostart** for `UPI Voice Alert` in Security app.
   - Exclude from battery saver; set battery to "No restrictions".
   - Grant Notification access again and confirm step 1 now shows "connected".

3. **Confirm capture reaches the pipeline** — check the Unparsed Queue, which
   is the intended diagnostic:
   - Settings → Debug mode ON → "Unparsed notifications".
   - Send a real ₹10 payment.
   - If new entries appear with `no parser matched` → capture, filter and
     classifier all work; the pipeline dies at the parser (root cause D.1).
   - If nothing appears → capture/filter/classifier is failing; check step 1-2,
     then add a temporary `Log.i` in `onNotificationPosted` to see whether the
     package check and `extractRawText` return anything for the real app used.

4. **Collect real samples** (needed before any parser work)
   - From the Unparsed Queue copy the exact `rawNotification` strings for the
     actual app used (or use the debug detail dialog). Save under
     `notification_samples/<app>/success_001.txt` etc.

5. **Implement parsers** against the real samples (do not guess formats):
   - Replace each `*ParserV1` stub `canParse()`/`parse()` with patterns matched
     to the collected text; keep the existing interface contract
     (throws `ParserException` on failure).
   - Add parser unit tests per app (success/failed/promo).

6. **Re-test end-to-end** with a real payment:
   - Expect: Room insert → Dashboard/History update → voice announcement.
   - Verify voice on `STREAM_MUSIC` (turn media volume up) and that the language
     voice pack exists; check Settings TTS-fallback note if Hindi selected.

7. **Only after voice works**, address secondary issues: remove/soften the
   `collect` filter keyword (D.4), review the mobile-number filter semantics
   (D.5), add `EXTRA_TEXT_LINES` extraction (D.6), and consider the
   foreground-service/reboot path for Xiaomi reliability (D.7).

---

## 10.6 F. Exact files to modify (AFTER confirmation from step 3)

Primary (root cause — parser implementation):
- `app/src/main/java/com/upivoicealert/parser/gpay/GPayParserV1.kt`
- `app/src/main/java/com/upivoicealert/parser/phonepe/PhonePeParserV1.kt`
- `app/src/main/java/com/upivoicealert/parser/paytm/PaytmParserV1.kt`
- `app/src/main/java/com/upivoicealert/parser/bhim/BhimParserV1.kt`
- (new) `app/src/test/java/com/upivoicealert/parser/...Test.kt` for each parser
- (data) `notification_samples/<app>/*.txt` collected samples

Diagnostics (optional but recommended for Xiaomi triage):
- `app/src/main/java/com/upivoicealert/service/UpiNotificationListenerService.kt`
  → add capture logs (e.g. `Log.i(TAG, "Captured ${packageName}: $rawText")`).

Secondary issues (fix after end-to-end works):
- `app/src/main/res/values/notification_filter_keywords.xml` → revisit `collect` (D.4)
- `app/src/main/java/com/upivoicealert/domain/usecases/ProcessTransactionUseCase.kt`
  → mobile-number filter semantics (D.5)
- `app/src/main/java/com/upivoicealert/service/UpiNotificationListenerService.kt`
  → `EXTRA_TEXT_LINES` extraction (D.6)
- `app/src/main/java/com/upivoicealert/voice/VoiceAnnouncementEngine.kt`
  → readiness gating (D.8)
- `app/src/main/AndroidManifest.xml` + `utils/Constants.kt` → foreground-service
  / reboot persistence for MIUI reliability (D.7)

---

## 10.7 Answer to the failure-point question (Q9)

**Based on code inspection alone: D — Parser issue.**

Every `*ParserV1.canParse()` returns `false`, so `ParserVersionResolver` always
returns `null`, so `ProcessTransactionUseCase` always diverts to the Unparsed
Queue with "no parser matched" and never reaches save/TTS. This failure is
**guaranteed by the code**, independent of whether the service runs, permissions
are granted, or TTS is healthy.

Remaining candidates are secondary and can only be confirmed on-device:
- A (service not running) / B (permission) / C (filter/classifier) — the
  on-device diagnostic that distinguishes these is the Unparsed Queue (step E3)
  plus the `UpiListenerService` "connected" log (step E1).
- E (TTS) is unreachable today; nothing ever reaches `speak()`.
- The user's "no `UPI_ALERT` logs" evidence is expected: that tag is never
  logged anywhere in the codebase (D.2).

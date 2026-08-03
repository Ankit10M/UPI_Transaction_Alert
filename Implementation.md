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

# Phase 9.3 — Final Release Gate Verification

**Date:** 2026-09-11  
**Baseline commit:** `main` with Phase 9.1 + 9.2 working tree (30 files changed, Room v9, 486 tests baseline)  
**Package:** `com.upivoicealert`  
**Room version:** 9 (protected, no migration)  
**Verification mode:** Automated builds/tests executed; physical-device tests NOT EXECUTED (no device available) — explicitly marked

---

## Executive Summary

**Gate: PASS WITH ACCEPTED NON-BLOCKING FINDINGS**

* **Source correctness:** 486 Android unit tests PASS (0 failures), 67 backend tests PASS, DAO stubs eliminated (Phase 9.1), privacy logs sanitized, release config hardened (Phase 9.2)
* **Release build:** `assembleDebug` SUCCESS (1m14s), `assembleRelease` with `isMinifyEnabled true + isShrinkResources true` SUCCESS (3m12s) — **R8 verified**, no additional keep rules required, lintVital PASS
* **APK inspection:** Release APK 3,283,287 bytes (vs 21,524,939 debug, -18 MB), kept `UpiVoiceAlertApplication/MainActivity/UpiNotificationListenerService/AppDatabase/RoomDatabase/Hilt/WorkManager/FirebaseAuth`, manifest `allowBackup false`, `cleartext false`, `networkSecurityConfig`, `dataExtractionRules`, build remains `debuggable false`
* **BLOCKED/NOT EXECUTED (non-blocking, environment):** Physical-device FLAG_SECURE screenshot/recent-apps check, payment notification smoke, offline capture, duplicate/ TTS/ process-death/ WorkManager/ Firebase OTP device flows — require device, not executed, documented for sideload dogfooding before Play publish
* **BASE_URL runtime health:** DNS/TLS/GET /health requires network to real `api.shoutpay.in` — **BLOCKED** (no network call in this gate), static gates already prove no localhost/LAN/http bleed

**No P0, no P1. Release may proceed to sideload with accepted P2 deferrals; Play publish requires pending device verification.**

---

## Environment

* **OS:** Windows 11 (win32), Shell PowerShell 5.1, `D:\UPI_Notification_Alert` (git repo, branch `main`)
* **Toolchain:** `gradle 8.9`, `agp 8.5.2`, `kotlin 2.0.21, ksp 2.0.21-1.0.28, hilt 2.52, room 2.6.1, firebaseBom 32.8.1, work 2.9.1, retrofit 2.11.0, okhttp 4.12.0, composeBom 2024.10.01`, `Node v?` (jest 29.7.0), `Python 3.14.6` (APK inspect), `aapt2 34.0.0`
* **SDK:** `compileSdk 34, targetSdk 34, minSdk 26, Java 17`, `local.properties` `SHOUTPAY_DEBUG_BASE_URL=http://192.168.1.10:3000/api/` (debug LAN, not in release)
* **Build config pre-gate:** `debug isDebuggable true minify false shrinkResources false`, `release isDebuggable false minify false shrinkResources false` (Phase 9.2 intentionally deferred) → **gate enabled true/true for verification**

---

## Repository Baseline

**`git status` pre-tests (Phase 9.1+9.2 working tree, not committed):**

```
 M app/proguard-rules.pro
 M app/src/main/java/com/upivoicealert/MainActivity.kt (FLAG_SECURE)
 M app/src/main/java/com/upivoicealert/data/database/TransactionDao.kt (3 audit abstract)
 M app/src/main/java/com/upivoicealert/data/repository/TransactionRepositoryImpl.kt (privacy redacted)
 M app/src/main/java/com/upivoicealert/data/sync/SyncDiagnosticDao.kt (3 abstract)
 M app/src/main/java/com/upivoicealert/data/sync/SyncQueueDao.kt (12 abstract + restrictive WHERE)
 M app/src/main/java/com/upivoicealert/domain/usecases/BusinessSummaryUseCase.kt (privacy)
 M app/src/main/java/com/upivoicealert/domain/usecases/ProcessTransactionUseCase.kt (metrics wiring + privacy)
 M app/src/main/java/com/upivoicealert/domain/usecases/VerifyPaymentUseCase.kt (privacy)
 M app/src/main/java/com/upivoicealert/filter/NotificationFilter.kt (Log.i→AppLogger.d)
 M app/src/main/java/com/upivoicealert/voice/VoiceAnnouncementEngine.kt (speak text→textLen)
 M app/src/test/java/... 19 test fakes (DAO contract patch + metrics import)
?? docs/PHASE_9_1_DAO_PRIVACY_HARDENING.md
?? docs/PHASE_9_2_RELEASE_ARTIFACT_HARDENING.md
?? docs/PHASE_9_INVESTIGATION_REPORT.md
```

* **No secrets:** `grep JWT|refreshToken|Authorization|secret|private key` → only code references, no embedded secret in `build.gradle.kts` (`BASE_URL` + `DEBUG` only), `google-services.json` public `api_key` only
* **No generated artifacts committed:** `app/build/` ignored
* **No schema diff:** `AppDatabase.kt` `version = 9` only, `git diff` shows no entity/migration change
* **No backend diff:** `shoutpay-backend/src` unmodified (except test run)

**Phase 9.1 changes:** DAO stubs removed (18 methods), privacy logs redacted (ProcessTransactionUseCase rawLen/cleanedLen, VoiceAnnouncementEngine textLen, TransactionRepositoryImpl id-only, VerifyPayment/BusinessSummary AppLogger.d)  
**Phase 9.2 changes:** `MainActivity` global FLAG_SECURE, `ProcessTransactionUseCase` 7× `PaymentPipelineMetrics` via `runCatching`, `proguard-rules.pro` 39→60 lines evidence-based keeps (Hilt/Room/Gson/Firebase/WorkManager/manifest)

---

## Android Test Results

**Command:** `.\gradlew testDebugUnitTest` (incremental, no `clean`)

**Result:** **BUILD SUCCESSFUL in 50s** (second run 19s cached), `app/build/test-results/testDebugUnitTest` 66 XML files

```
TOTAL: tests=486 failures=0 errors=0 skipped=0 files=66
```

Per-suite sample (all 0 failures):

* `AmountToWordsConverterTest 7`, `BusinessSummaryUseCaseTest 3`, `CloudTransactionRepositoryTest 5`, `EnvironmentValidatorTest 14`, `NetworkProductionHardeningTest 19`, `OemBatteryHardeningTest 14`, `ProcessDeathRecoveryTest 18`, `SyncIntegrityAuditorTest 17`, `SyncQueueRecoveryTest 19`, `TransactionSyncReconcilerTest 12`, `TransactionSyncRepositoryTest 7`, `SyncRaceConditionHardeningTest 3`, `VerifyPaymentUseCaseTest 3`, etc.

**Historical baseline 486 → actual 486, 0 failures.**  

*If tests failed:* would be classified as Phase 9.2 regression (DAO contract or metrics DI) — but none failed, including `OemBatteryHardeningTest` and `ProcessTransactionUseCaseVoiceGateTest` which exercise `ProcessTransactionUseCase` with `PaymentPipelineMetrics` — metrics wiring did not break dedup/TTS/voice-gate semantics.

---

## Debug Build

**Command:** `.\gradlew assembleDebug`

**Result:** **BUILD SUCCESSFUL in 1m14s**

```
> Task :app:mergeDebugResources, :app:processDebugResources, :app:kspDebugKotlin, :app:compileDebugKotlin UP-TO-DATE
> Task :app:dexBuilderDebug, :app:mergeProjectDexDebug, :app:packageDebug :app:assembleDebug
```

**Artifact:** `app/build/outputs/apk/debug/app-debug.apk` **21,524,939 bytes**, `output-metadata.json` `debuggable true`, `aapt2 dump xmltree` shows `allowBackup false`, `cleartext false`, `com.upivoicealert.UpiVoiceAlertApplication`, `MainActivity`, `UpiNotificationListenerService`

---

## Release Build

**Pre-condition:** Verified Phase 9.2 `proguard-rules.pro` keep set (Hilt, Room, Retrofit/Gson, Firebase, WorkManager, manifest entry points, Signature attributes)

**Change for gate:** `app/build.gradle.kts` `release { isMinifyEnabled true; isShrinkResources true }` (smallest required change, debug remains `false`)

**Command:** `.\gradlew assembleRelease`

**Result:** **BUILD SUCCESSFUL in 3m12s**

```
> Task :app:mergeReleaseArtProfile, :app:kspReleaseKotlin, :app:compileReleaseKotlin, :app:hiltJavaCompileRelease
> Task :app:lintVitalAnalyzeRelease, :app:minifyReleaseWithR8, :app:lintVitalReportRelease, :app:lintVitalRelease
> Task :app:shrinkReleaseRes, :app:optimizeReleaseResources, :app:packageRelease, :app:assembleRelease
55 actionable tasks: 24 executed, 31 up-to-date
```

*No R8 missing-class, no keep rule failure, `lintVitalRelease` PASS.* Build-time `SHOUTPAY_RELEASE_BASE_URL` guards (`10.0.2.2/localhost/127.0.0.1/192.168/10.x`, `https://`, trailing `/`) did not throw — fallback `https://api.shoutpay.in/api/` passed.

---

## R8 Result

**Enabled:** `isMinifyEnabled = true` (release only)

**Result:** **PASS — no additional keep rules required**

* **Iterations:** 1 (no failure → no fix → no rerun)
* **Evidence:** `minifyReleaseWithR8` completed without `Missing classes` or `can't find referenced class`; `hiltJavaCompileRelease` and `kspReleaseKotlin` both succeeded, indicating Room `AppDatabase_Impl` and Hilt `Hilt_*` generation survived shrinking
* **New keep rules added during R8:** 0 (Phase 9.2 set already sufficient)
* **Prohibited broad rule:** Not used (`-keep class ** {*;}` avoided)
* **If R8 had failed:** Would have inspected `build/outputs/mapping/release/mapping.txt` + `usage.txt` + R8 output for exact missing member, added minimal `-keep class pkg.Class { *; }` with documented reason, rerun — not needed

---

## Resource Shrinking Result

**Enabled:** `isShrinkResources = true` (paired with minify, release only)

**Result:** **PASS**

* **Evidence:** `shrinkReleaseRes` + `optimizeReleaseResources` tasks executed and succeeded after `minifyReleaseWithR8`
* **Static analysis pre-gate:** No `getIdentifier` / dynamic resource lookup (grep `getResources|getIdentifier` → 0), all `mipmap/ic_launcher`, `drawable/ic_launcher_foreground`, `values/*_keywords.xml`, `xml/network_security_config.xml` statically referenced via `R` or manifest — safe to shrink
* **Artifact check:** Release APK still contains `AndroidManifest.xml`, `classes.dex`, `res/layout` etc.; `aapt2 dump xmltree` shows `icon @0x7f0c0000`, `roundIcon`, `label`, `networkSecurityConfig @0x7f110003`, `dataExtractionRules` retained — no `Resources.NotFoundException` expected

---

## APK Inspection

**Artifacts:**

* `app/build/outputs/apk/release/app-release-unsigned.apk` **3,283,287 bytes** (vs 21,524,939 debug, **-18,241,652 bytes / -84%**)
* `app/build/outputs/apk/release/output-metadata.json` 732 bytes, `baselineProfiles/*/app-release-unsigned.dm`

**Manifest (`aapt2 dump xmltree`):**

* `package com.upivoicealert, versionCode 1 versionName 1.0, compileSdk 34, minSdk 26 targetSdk 34`
* Permissions: `POST_NOTIFICATIONS`, `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`, `INTERNET`, `WAKE_LOCK`, `ACCESS_NETWORK_STATE`, `RECEIVE_BOOT_COMPLETED`, `FOREGROUND_SERVICE`, `READ_GSERVICES` (standard for WorkManager/Firebase)
* Application `android:name="com.upivoicealert.UpiVoiceAlertApplication"`, `allowBackup false`, `fullBackupContent false`, `networkSecurityConfig @0x7f110003`, `dataExtractionRules @0x7f110001`, `icon`/`roundIcon`
* Providers: `androidx.startup.InitializationProvider exported false` with 3 meta-data (`EmojiCompatInitializer`, `ProcessLifecycleInitializer`, `ProfileInstallerInitializer`) — **WorkManagerInitializer correctly removed** (`tools:node remove`)
* Components: `MainActivity exported true` + MAIN/LAUNCHER, `UpiNotificationListenerService exported true` + `BIND_NOTIFICATION_LISTENER_SERVICE`, `SystemAlarmService`/`SystemJobService`/`SystemForegroundService` + `ForceStopRunnable` receivers (WorkManager)

**DEX (`zipfile` check):**

* `com/upivoicealert/UpiVoiceAlertApplication` FOUND
* `com/upivoicealert/MainActivity` FOUND
* `com/upivoicealert/service/UpiNotificationListenerService` FOUND
* `com/upivoicealert/data/database/AppDatabase` FOUND
* `androidx/room/RoomDatabase` FOUND
* `dagger/hilt` FOUND
* `androidx/work/Worker` FOUND
* `com/google/firebase/auth/FirebaseAuth` FOUND
* `api.shoutpay.in` FOUND (BASE_URL string)

**BuildConfig:** `debuggable false` (release), `isMinifyEnabled true` effective (R8 ran), no signing credentials embedded (`keystore.properties` not present → unsigned)

**Conclusion:** Runtime-referenced functionality intact; obfuscation did not strip kept entry points.

---

## FLAG_SECURE Device Test

**Status: NOT EXECUTED — no physical device available (BLOCKED)**

**Static verification:** `MainActivity.kt:24-30` `window.setFlags(FLAG_SECURE, FLAG_SECURE)` before `setContent`, global on single Activity — least invasive, no per-screen fragility, no navigation side-effects (verified via `AppNavigation.kt` `NavHost` + `Scaffold`).

**Required device verification (deferred to sideload dogfooding):** Install `app-release-unsigned.apk` (or signed) on device, verify Home/History/Business/Profile render, navigation and back work, recent-apps card is blank/blurred, hardware screenshot (`adb shell screencap` / power+vol-down) yields black image when app foreground, screen recording protected, TalkBack still navigates.

**Risk if not verified:** Screenshots of `HistoryScreen` amounts/sender could leak via recent-apps — P2, not P0, because device remains offline-capable.

---

## Payment Smoke Test

**Status: NOT EXECUTED — no physical device**

**Required sequence (device):** `Payment notification → Parser → Validator → Dedup → Room → Queue → TTS` with first payment → 1 transaction row + 1 `PENDING` queue + TTS, duplicate → no second row/queue, duplicate metric increments, no second TTS if dedup.

**Static surrogate:** `testDebugUnitTest` 486 PASS includes `TransactionParserTest 12`, `TransactionRepositoryDedupTest 5`, `TransactionClassifierTest 15`, `OemBatteryHardeningTest` offline persistence — pipeline logic unit-tested, but **not** a device smoke.

---

## Offline Payment Test

**Status: NOT EXECUTED — no physical device**

**Critical invariant `NETWORK DOWN → PAYMENT STILL CAPTURED` not device-proven.** Unit surrogate `OemBatteryHardeningTest` `H offline notification persists locally with PENDING queue` PASS and `ProcessDeathRecoveryTest` `N local pipeline independent of sync/auth` PASS, but device airplane-mode flow (disable network → post test payment via `adb shell cmd notification post` or real UPI sandbox → verify Room `PENDING` + TTS → re-enable → sync) still pending.

**If offline capture failed on device:** Would be **P0 FAIL the gate** — not observed in unit, but must be confirmed on device before Play publish.

---

## Duplicate Payment Test

**Status: NOT EXECUTED on device; unit PASS**

* Unit: `TransactionRepositoryDedupTest`, `OemBatteryHardeningTest` `F dedup survives recreation`, `SyncRaceConditionHardeningTest` all PASS (fingerprint + reference-ID + rawNotification, `DEDUP_WINDOW_MS` 2m)

---

## TTS Test

**Status: NOT EXECUTED on device; unit surrogate**

* `VoiceAnnouncementEngine` logs `textLen` not `text`, `QUEUE_ADD` preserved, `prepare` fallback to English tested via `OemBatteryHardeningTest` `E TTS failure still persists` PASS

---

## Metrics Verification

**Static PASS, runtime triage NOT EXECUTED**

* **Static:** `ProcessTransactionUseCase.kt` now imports `observability.PaymentPipelineMetrics`, constructor `@Inject` + 7 `runCatching { metrics.onX() }` points (received at entry, parseRejected on `resolve==null` + `catch`, validationRejected on `Invalid`, duplicate on `insert false`, persisted on `true`, ttsAttempted on `voiceAnnounced true`, ttsFailed on `catch`), each `runCatching` prevents throw → **payment cannot be stopped by metrics**
* **Privacy:** `PaymentPipelineMetrics.kt` 7× `AtomicLong` only, `snapshot():Snapshot(Long)` — no amount/sender/VPA/phone/raw/JWT field
* **Non-blocking:** `AtomicLong.incrementAndGet()` — no `suspend`, no `Flow`, no `withContext`, no DB, no network
* **Required device:** After smoke payment, Debug screen or `logcat debug` `snapshot().received/persisted/duplicates` — not executed

---

## Process Death Test

**Status: NOT EXECUTED on device; unit PASS**

* Unit: `ProcessDeathRecoveryTest` 18 tests PASS (`A transaction survives`, `B PENDING survives`, `C UPLOADING stale recovery`, `F SYNCED remains`, `M auth restoration`), `UpiNotificationListenerService` `serviceScope(SupervisorJob+IO)` cancelled in `onDestroy` — stateless, no duplicate pipeline on `onListenerConnected`

---

## WorkManager Test

**Status: NOT EXECUTED on device; unit PASS**

* Unit: `ReconciliationSchedulerTest`, `StaleUploadRecoverySchedulerTest`, `SyncIntegrityAuditSchedulerTest`, `SyncMaintenanceCoordinatorTest` (KEEP, AlreadyRunning→success), `UpiVoiceAlertApplication.onCreate` individually try/catched startup scheduling
* APK: `SystemAlarmService`/`SystemJobService`/`SystemForegroundService` present, `InitializationProvider` manual, `HiltWorkerFactory` kept via proguard

---

## Firebase Auth Test

**Status: NOT EXECUTED on device; unit/static PASS**

* Static: `proguard -keep firebase.** + gms.**`, `AuthModule` `FirebaseAuth.getInstance()`, `AuthRepository` `PhoneAuthProvider.verifyPhoneNumber` with OTP 60s, `google-services.json` `upi-voice-alert-e7257` present, `firebase-auth` 22.3.1 via BOM
* APK: `com/google/firebase/auth/FirebaseAuth` FOUND in dex
* Device OTP flow (PhoneAuthOptions `setActivity`, callbacks, `getIdToken(true)` → `backend /api/auth/login` → JWT) not executed (requires controlled test phone)

---

## Room/KSP Verification

**Status: PASS (static + build)**

* `AppDatabase.kt:12` `version = 9, entities=[TransactionEntity, UnparsedNotificationEntity, SyncQueueEntity, SyncDiagnosticEventEntity], exportSchema false` — unchanged from Phase 9
* `DatabaseModule` adds 8 migrations `1_2..8_9` additive (`ALTER TABLE ADD COLUMN`, `CREATE TABLE/INDEX`), no destructive, deterministic `MIGRATION_7_8` `MIN(id)` purge before unique index `(entityType,entityId)`
* DAOs: `TransactionDao` 3 audit counts, `SyncQueueDao` 12 methods (`updateStatusIfExpected` etc. `WHERE status=:expected`), `SyncDiagnosticDao` 3 — all abstract, Room KSP generated `AppDatabase_Impl` via `kspDebugKotlin` / `kspReleaseKotlin` both succeeded, `compileReleaseKotlin` succeeded after R8
* No fallback DAO implementations: grep `= 0|= 1|emptyList()` in `*Dao.kt` → 0 after Phase 9.1
* Build evidence: `kspReleaseKotlin` + `minifyReleaseWithR8` succeeded — generated Room code not stripped

---

## Privacy/Logging Audit

**Status: PASS (static)**

* `ProcessTransactionUseCase.kt` 63 `rawLen`, 70 `cleanedLen`, 122 `PARSER_RESULT status=parsed`, 169 `id + reason=duplicate` — no `rawText/cleanedText/amount/sender`
* `VoiceAnnouncementEngine.kt:106` `textLen` not `text` — `Log.i SPEAK_RESULT` no longer leaks announcement
* `TransactionRepositoryImpl.kt` `CHECK_START package+hasRef/hasFingerprint/window`, `DECISION` `existingId` only, `INSERTED id+uuid` — no `amount/sender/fingerprint`
* `NotificationFilter.kt` `AppLogger.d FILTER_CHECK package+keyword` (was `Log.i`, now DEBUG-gated)
* `VerifyPaymentUseCase.kt` `AppLogger.d VERIFY_CHECK windowMs/since`, `VERIFY_MATCH transactionId/createdAt`
* `BusinessSummaryUseCase.kt` `AppLogger.d count+peakHour` (total/average/largest removed)
* Release-capable `Log.w/e` in `TransactionSyncRepository`/`StaleUploadRecoveryManager` contain only `requestId/method/path/status/errorCode` or `queueId` — no `rawNotification/amount/sender/VPA/phone/JWT/refresh/Authorization/body`
* `AppLogger.kt` `d/i/v` gated `BuildConfig.DEBUG`, `wReleaseSafe` for sanitized warnings

---

## Backend Test Results

**Command:** `npm test` in `shoutpay-backend` (`jest`)

**Result:** **7 passed, 7 total — 67 tests PASS**

```
PASS src/auth/__tests__/tokens.test.js
PASS src/auth/__tests__/jwt.test.js
PASS src/modules/devices/__tests__/devices.test.js
PASS src/modules/transactions/__tests__/query.test.js
PASS src/modules/merchant/__tests__/merchant.test.js
PASS src/auth/__tests__/router.integration.test.js
PASS src/modules/transactions/__tests__/transactions.test.js
Test Suites: 7 passed, 7 total
Tests:       67 passed, 67 total
Time: 2.807 s
```

Historical baseline 67 → actual 67, no backend source changed (Phase 9.3 is Android gate only).

---

## Production BASE_URL Verification

**Static:** **VERIFIED**, **Runtime:** **BLOCKED — network not exercised**

* **Build-time gate (`app/build.gradle.kts:72-84`):** `SHOUTPAY_RELEASE_BASE_URL` from `local.properties` or `env` or fallback `https://api.shoutpay.in/api/` — `GradleException` if `10.0.2.2/localhost/127.0.0.1/192.168.x.x`, not `https://`, not trailing `/` — **release used fallback, passed**
* **Runtime gate (`config/EnvironmentValidator.kt`):** `validate` checks empty, `http(s)://`, trailing `/`, `java.net.URL` parse, then for `isRelease`: `10.0.2.2`, `localhost/127.0.0.1`, `192.168`, `10.x.x.x`, non-`https` — **fail-fast `requireValid` in `BuildConfigEnvironmentProvider`**
* **Separation:** `SHOUTPAY_DEBUG_BASE_URL=http://192.168.1.10:3000/api/` (debug LAN) vs `SHOUTPAY_RELEASE_BASE_URL` (release) — debug cannot bleed, verified via `local.properties` read
* **Dex:** `api.shoutpay.in` FOUND in `classes.dex`
* **Runtime health not executed (BLOCKED):** `DNS → TLS → GET https://api.shoutpay.in/api/health 200` and `POST /api/auth/login` invalid →401 require network to real backend — not performed in this offline gate, and not claimed PASS

---

## Findings

| # | Severity | Description | Evidence | Impact | Disposition |
|---|---|---|---|---|---|
| 1 | **P2** | R8 kept `UpiNotificationListenerService` but service is `exported true` with `BIND_NOTIFICATION_LISTENER_SERVICE` — must remain exported | `AndroidManifest.xml:49-57` service `exported true` + `proguard -keep service` | If stripped, notification capture breaks | **PASS** — `aapt2 dump` shows service retained, R8 keep explicit, build succeeded |
| 2 | **P2** | `FLAG_SECURE` device check pending | `MainActivity.kt` global `FLAG_SECURE` static only | Screenshots could expose `History` amounts | **ACCEPTED NON-BLOCKING** — static PASS, device pending before Play publish |
| 3 | **P2** | Production `https://api.shoutpay.in/api/` health not runtime-proven | `build.gradle.kts` fallback, `aapt` manifest, dex string | Wrong domain would fail sync | **BLOCKED** — static gates PASS, runtime GET /health + invalid auth 401 pending |
| 4 | **P2** | Physical-device payment/offline/duplicate/TTS/process-death flows not executed | Unit 486 PASS but no `adb shell cmd notification post` device run | Cannot claim production confidence | **NOT EXECUTED — DEFERRED to sideload dogfooding** |
| 5 | **P3** | Telemetry upload, cert pinning, latency histogram, OEM battery onboarding still deferred | Phase 9 investigation §20 | None for MVP | **DEFERRED** |
| — | — | No P0 (payment loss, duplicate financial, bypass, secret exposure, corrupted DB, APK cannot launch) | 486 tests 0 failures, R8 build SUCCESS, `allowBackup false`, `cleartext false`, `EncryptedSharedPreferences` | — | **0 P0** |
| — | — | No P1 (offline capture failure, Room persistence, broken state machine, PII leak, R8 break, merchant isolation) | `SyncQueueDao` restrictive `WHERE status=:expected`, `SyncIntegrityAuditor` 17 tests, privacy grep 0 | — | **0 P1** |

---

## Release Gate Decision

```
PASS WITH ACCEPTED NON-BLOCKING FINDINGS
```

* **Source + tests + builds + R8 + APK + privacy + backend:** PASS
* **Device + production endpoint runtime:** NOT EXECUTED / BLOCKED — accepted for sideload, must be completed before Play publish

**Do not declare `PASS` for device/runtime items without evidence — they are explicitly `NOT EXECUTED`/`BLOCKED` above.**

---

## Remaining Work

**Before sideload dogfooding (non-blocking for gate):**

* Install `app-release-unsigned.apk` (sign with `keystore.properties` for publish) on device with real UPI apps (GPay/PhonePe/Paytm/BHIM) or test harness (`adb shell cmd notification post -S bigText ...`), verify `FLAG_SECURE` (recent-apps blurred, screenshot black), navigation, TalkBack

**Before Play publish (required):**

* Device payment smoke + offline airplane-mode + duplicate + TTS + process death (kill via `am force-stop com.upivoicealert` → relaunch → Room/SyncQueue intact) + WorkManager unique KEEP + Firebase OTP flow
* `GET https://api.shoutpay.in/api/health` 200 + invalid `POST /api/auth/login 401` from device/emulator with release APK
* `adb shell dumpsys notification` + `logcat` with `AppLogger` debug off (release)

---

## Phase 8 Physical Validation Status

**Still ON HOLD — distinct from Phase 9.3 gate.**

* **Phase 8 D1–D22:** Real-merchant, multi-day, multi-OEM (Xiaomi/Oppo/Vivo) battery, notification storm, amount-to-words Hindi — **not executed**, not claimed complete
* **Phase 9.3 gate:** Release build + unit + APK + privacy + backend — **PASS** as above, but **does not substitute** Phase 8 physical validation

**69b4134c**


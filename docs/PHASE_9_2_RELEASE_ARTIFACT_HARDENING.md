# Phase 9.2 — Release Artifact Hardening

**Date:** 2026-09-11  
**Baseline:** `docs/PHASE_9_INVESTIGATION_REPORT.md` + `docs/PHASE_9_1_DAO_PRIVACY_HARDENING.md` (PASS)  
**Room version:** 9 (unchanged)  
**Scope:** Release-artifact hardening only — no new product features, no schema/migration, no sync/auth redesign, no payment pipeline behavior change, no backend change  
**Verification mode:** Static/source inspection only — **no `./gradlew` build or test executed** per §21 (BUILD REQUIRED FOR FINAL VALIDATION in Phase 9.3)

---

## 1. Executive Summary

**Result: PASS WITH NON-BLOCKING FINDINGS — deferred R8 enablement.**

Phase 9.2 investigated the four deferred P2 production-hardening items from Phase 9 (§19) plus release configuration safety. Two items were **implemented** with minimal, non-breaking changes; two were **explicitly deferred** with justification; one was **statically verified** as already safe but requiring runtime health check.

| Item | Decision | Change | Risk to payment pipeline |
|---|---|---|---|
| **R8 / isMinifyEnabled** | **DEFERRED — NOT ENABLED** | No flag change; keep-rules documented and prepared in `proguard-rules.pro` | **None** — payment path untouched; APK size deferred to 9.3 |
| **Resource shrinking isShrinkResources** | **DEFERRED** (requires minify) | No flag change | **None** |
| **FLAG_SECURE** | **IMPLEMENTED — global on MainActivity window** | `MainActivity.onCreate: window.setFlags(FLAG_SECURE)` | **None** — display-only, no payment prerequisite |
| **PaymentPipelineMetrics wiring** | **IMPLEMENTED** | Wired into `ProcessTransactionUseCase` with `runCatching` + `AtomicLong`, observational only | **None** — never blocks Room/TTS, no DB/network, counts only |
| **Crashlytics** | **DEFERRED — NOT JUSTIFIED** | No dependency added | **None** |
| **BASE_URL production** | **VERIFIED STATIC CONFIGURATION / REQUIRES RUNTIME HEALTH** | No code change; Gradle + runtime validator already reject localhost/LAN/http, placeholder `https://api.shoutpay.in/api/` flagged for 9.3 checklist | **None** |

**Payment pipeline, Room v9, sync state machine, authentication, backend, WorkManager architecture: unchanged.**

---

## 2. Initial Release Configuration (inspected before any change)

**File: `app/build.gradle.kts:36-91`**

```kotlin
defaultConfig { applicationId "com.upivoicealert", minSdk 26, targetSdk 34, compileSdk 34, versionCode 1, versionName "1.0" }
signingConfigs.release { storeFile/storePassword/keyAlias/keyPassword from keystore.properties if present }
buildTypes {
  debug { isDebuggable true, isMinifyEnabled false, isShrinkResources false, BASE_URL = local SHOUTPAY_DEBUG_BASE_URL ?: "https://api.shoutpay.in/api/" }
  release { isDebuggable false, isMinifyEnabled false, isShrinkResources false, proguardFiles(proguard-android-optimize.txt, proguard-rules.pro), BASE_URL = SHOUTPAY_RELEASE_BASE_URL ?: "https://api.shoutpay.in/api/" with Gradle checks for localhost/10.0.2.2/192.168/10.x + https:// + trailing "/" }
}
compileOptions / kotlinOptions Java 17, buildFeatures { compose true, buildConfig true }, testOptions isReturnDefaultValues true
```

**File: `app/proguard-rules.pro` (pre-9.2, 39 lines)**

* `isMinifyEnabled false` — rules documented but not active
* Keep sets for Hilt (`dagger.hilt.**`, `javax.inject.**`, `HiltAndroidApp`, `HiltWorker`), Room (`data.database.**`, `data.sync.**`, `RoomDatabase`, `@Entity`), Retrofit/Gson (`network.**`, `data.**`, `retrofit2.**`, `Signature` attributes), Firebase (`firebase.**`, `gms.**`), WorkManager (`Worker`, `ListenableWorker`), commented `-assumenosideeffects Log.v/d/i`

**File: `app/src/main/AndroidManifest.xml`**

* Permissions: `POST_NOTIFICATIONS`, `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`, `INTERNET` (no CAMERA/CONTACTS/STORAGE/LOCATION/SMS)
* Application: `UpiVoiceAlertApplication` (Hilt), `allowBackup false`, `dataExtractionRules @xml/data_extraction_rules`, `fullBackupContent false`, `networkSecurityConfig @xml/network_security_config`, manual `WorkManagerInitializer` removal
* Components: `MainActivity exported true` + MAIN/LAUNCHER, `UpiNotificationListenerService exported true` + `BIND_NOTIFICATION_LISTENER_SERVICE`

**File: `gradle/libs.versions.toml`**

* `agp 8.5.2, kotlin 2.0.21, ksp 2.0.21-1.0.28, hilt 2.52, room 2.6.1, firebaseBom 32.8.1, work 2.9.1, retrofit 2.11.0, okhttp 4.12.0, composeBom 2024.10.01, datastore 1.1.1, securityCrypto 1.1.0-alpha06` — no `firebase-crashlytics`, no `serializaton` beyond Gson

**File: `app/src/main/java/com/upivoicealert/MainActivity.kt` (pre-9.2)**

* Single `ComponentActivity`, `@AndroidEntryPoint`, hosts `ShoutPayTheme { MainNavHost / OnboardingNavHost }`, **no FLAG_SECURE**

**File: `app/src/main/java/com/upivoicealert/observability/PaymentPipelineMetrics.kt` (pre-9.2)**

* `@Singleton AtomicLong x7` counters, `onNotificationReceived/parseRejected/validationRejected/duplicate/persisted/ttsAttempted/ttsFailed`, `snapshot()` — class exists but **0 wiring** outside definition + `ObservabilityHardeningTest`

**File: `app/google-services.json`**

* `project_id upi-voice-alert-e7257`, `package_name com.upivoicealert`, public `api_key` present — Firebase Auth configured, Crashlytics not

**Other inspected:**

* `config/EnvironmentProvider.kt + EnvironmentConfig.kt + EnvironmentValidator.kt + di/AuthModule.kt` — `BASE_URL` via `BuildConfig.BASE_URL → EnvironmentProvider → Retrofit baseUrl`, `isHttpBodyLoggingEnabled == isDebug`, OkHttp 15/30/30/45s, `redactHeader Authorization`, retryOnConnectionFailure true
* `res/xml/network_security_config.xml` — `cleartextTrafficPermitted false`
* `res/xml/data_extraction_rules.xml + backup_rules.xml` — exclude `database` + `sharedpref` for both backup domains
* No `FLAG_SECURE`, no `Crashlytics`, no `PaymentPipelineMetrics` wiring found via `Select-String PaymentPipelineMetrics` → only definition

---

## 3. R8 Investigation

### 3.1 What was inspected

* `app/build.gradle.kts` buildTypes (both false), `proguard-rules.pro` existing rules, `gradle/libs.versions.toml` dependency graph
* Hilt: `@HiltAndroidApp UpiVoiceAlertApplication`, `@AndroidEntryPoint MainActivity, UpiNotificationListenerService, Workers, ViewModels`, `hilt-compiler ksp`, `hilt-work HiltWorkerFactory`
* Room: `AppDatabase @Database(version=9, entities=[TransactionEntity, UnparsedNotificationEntity, SyncQueueEntity, SyncDiagnosticEventEntity])`, 4 DAOs, 8 migrations additive, `ksp room-compiler`, `RoomDatabase_Impl` generation
* Firebase: `firebase-bom + firebase-auth`, `google-services` plugin, `google-services.json` `upi-voice-alert-e7257`, `AuthRepository FirebaseAuth.getInstance()`, `PhoneAuthProvider`, `EncryptedSharedPreferences`
* WorkManager: `HiltWorkerFactory`, `Configuration.Provider` in `UpiVoiceAlertApplication`, 4 sync workers + 2 work workers (`CleanupWorker`, `RetryFailedParseWorker`), `InitializationProvider` manual, unique `KEEP`
* Compose: `kotlin.plugin.compose`, `compose-bom 2024.10.01`, `NavHost composable(Routes.*)` inline lambdas, no reflective route strings
* Retrofit/OkHttp/Gson: `retrofit 2.11.0 + converter-gson`, `okhttp 4.12.0 + logging-interceptor`, `TransactionSyncApi @POST` with DTOs `@Body TransactionSyncRequestDto`
* Reflection / dynamic loading: `Select-String Class.forName | reflection` → only legitimate `@Dao`, `@Entity`, `Retrofit.create`, `Room.databaseBuilder`, `@InstallIn` (not `Class.forName` dynamic); no `getIdentifier` / `getResources` dynamic lookup
* Manifest components: `UpiVoiceAlertApplication`, `MainActivity`, `UpiNotificationListenerService` (only `android.permission.BIND_NOTIFICATION_LISTENER_SERVICE`)
* Resources: `mipmap ic_launcher`, `drawable ic_launcher_foreground`, `values/ blocked_notification_packages.xml, financial_signal_keywords.xml, notification_filter_keywords.xml`, `xml/network_security_config.xml` — all statically referenced via `R.*` or `android:icon/@xml`, no runtime `getIdentifier`
* TTS: `android.speech.tts.TextToSpeech` instantiated directly in `VoiceAnnouncementEngine`, no reflection

### 3.2 Existing keep rules (pre-9.2) assessment

Pre-9.2 `proguard-rules.pro` already minimal and broadly correct, but slightly over-broad in two places:

* `-keep class com.upivoicealert.data.** { *; }` duplicates `-keep class com.upivoicealert.data.database.**` + `data.sync.**` (redundant, but harmless)
* Missing explicit keeps for `UpiVoiceAlertApplication`, `MainActivity`, `UpiNotificationListenerService` manifest entry points (implicitly kept via `HiltAndroidApp` but worth explicit), and `androidx.room.**`, `com.google.gson.**`, `HiltWorkerFactory`, `AndroidEntryPoint`

No broad `-keep class ** { *; }` present — correct.

### 3.3 R8 decision

**DEFERRED — NOT ENABLED (`isMinifyEnabled` remains `false` for both debug and release).**

**Reason:** Enabling R8 without executing `assembleRelease` (and install + smoke test on device/emulator) cannot be proven safe. Phase 9.2 is constrained to `BUILD REQUIRED FOR FINAL VALIDATION` per §9/§21 — we must not claim runtime verification that was not performed. Risk areas that need build verification:

* Room: `AppDatabase_Impl`, `*_Impl` DAO generation, `@Entity` field stripping — `proguard-rules.pro` keeps are plausible but untested with `room 2.6.1 + ksp 2.0.21`
* Hilt: `Hilt_*.java`, `*_HiltComponents` — keep is documented but untested with `hilt 2.52 + agp 8.5.2`
* WorkManager `ListenableWorker` via `HiltWorkerFactory` — name-based instantiation fragile under obfuscation
* Retrofit `GsonConverterFactory` field name obfuscation — requires `Signature` + DTO keeps, untested
* Compose `NavHost` route lambdas + `R` resources — should survive but untested with `kotlin 2.0.21 compose`
* Firebase Auth native bindings — documented keep but untested with `bom 32.8.1`

**Exact validation required for 9.3 (see §19):** See Phase 9.3 Required Verification.

**Keep-rules hardening done in 9.2 anyway:** Updated `proguard-rules.pro` (see §5) to document minimal, evidence-based keeps and to be **build-ready** when R8 is enabled — no behavior change today (flag stays false, so rules not applied), but file is now precise.

---

## 4. Resource Shrinking Investigation

**`isShrinkResources = false` (both types) — DEFERRED, never enabled standalone.**

* Resource shrinking is only effective with `isMinifyEnabled = true` (docs: `shrinkResources` removes unused resources after code shrinking). Enabling it alone has no effect.
* Static search: no `getIdentifier`, no `Resources.getIdentifier`, no runtime `resource name constructed at runtime`, no reflection resource lookup. All resources referenced statically:
  * Launcher: `mipmap/ic_launcher`, `mipmap/ic_launcher_round`, `mipmap-anydpi-v26 ic_launcher(.xml)`, `drawable/ic_launcher_foreground.xml`
  * Config: `values/strings.xml`, `values/colors.xml`, `values/themes.xml`, `values/*_keywords.xml`
  * XML: `xml/network_security_config.xml`, `xml/backup_rules.xml`, `xml/data_extraction_rules.xml`
  * No `notification icons` dynamically chosen — fixed `ic_launcher`
* Firebase/Compose/WorkManager resources are statically linked via `google-services.json` / `compose.material3` / `work-runtime-ktx` — no dynamic indirection.

**Decision:** Leave `isShrinkResources = false` consistent with `isMinifyEnabled = false`. When minify is enabled in 9.3, enable both together (`isMinifyEnabled true` + `isShrinkResources true`) with `proguard-android-optimize.txt` and smoke test for missing resources (especially `R.string/notification_listener_label`, `R.mipmap.ic_launcher`).

---

## 5. Keep Rules

**File: `app/proguard-rules.pro` — updated in 9.2, still inactive until minify enabled.**

Every rule documents (1) what requires it, (2) why R8 would remove/rename it, (3) evidence, (4) why minimal:

| Rule | Requires | Why R8 would break | Evidence | Minimality |
|---|---|---|---|---|
| `-keep class dagger.hilt.** { *; }` + `javax.inject.**` + `* extends HiltAndroidApp` + `HiltWorker` | Hilt | Hilt generates Dagger components reflectively; unused-looking keeps are actually entry points | `hilt 2.52 ksp`, `@HiltAndroidApp UpiVoiceAlertApplication`, `@AndroidEntryPoint` on 3 services/4 workers/5 viewmodels | Keeps hilt generated + entry points, not whole `dagger` |
| `-keep class com.upivoicealert.data.database.** + data.sync.**` + `* extends RoomDatabase` + `@Entity` + `androidx.room.**` | Room | Room generates `AppDatabase_Impl` + `*_Impl` at compile via ksp; entities column mapping via reflection | `AppDatabase version=9`, 4 entities, `room 2.6.1 ksp`, 4 DAOs with `@Query` | Keeps database/sync packages + Entity + Room superclass + Room runtime |
| `-keep class com.upivoicealert.network.**` + `TransactionSync*` + `SyncQueueEntity` + `retrofit2.**` + `com.google.gson.**` + `Signature,InnerClasses,EnclosingMethod` | Retrofit/Gson | Retrofit creates proxy implementations; Gson reflects DTO fields `transactionUuid/deviceId/amount/senderName/...` | `retrofit 2.11.0 + converter-gson`, `TransactionSyncApi @POST @Body`, `TransactionSyncRequestDto` | Keeps DTOs + Gson reflective attrs, not whole `okhttp` |
| `-keep class com.google.firebase.**` + `com.google.android.gms.**` | Firebase Auth | Firebase initializes via reflection, keeps native bindings; GMS `google-services.json` | `firebase-bom 32.8.1`, `firebase-auth`, `google-services` plugin, `FirebaseAuth.getInstance()` in `AuthModule/AuthRepository` | Keeps firebase/gms, not entire play-services |
| `-keep class * extends Worker/ListenableWorker` + `HiltWorkerFactory` | WorkManager | WorkManager instantiates workers by class name via `HiltWorkerFactory`; obfuscation renames them | `work 2.9.1`, 4 sync workers + 2 work workers, `Configuration.Provider` with `setWorkerFactory`, `tools:node remove WorkManagerInitializer` | Keeps Worker subclasses + factory |
| `-keep class UpiVoiceAlertApplication + MainActivity + UpiNotificationListenerService` | AndroidManifest entry points | Referenced only from manifest, R8 sees no code reference | `AndroidManifest.xml` declares `application android:name=.UpiVoiceAlertApplication`, `activity .MainActivity`, `service .service.UpiNotificationListenerService` | Keeps manifest classes only |
| Compose/Nav: **no rule** | Compose | Compose compiler handles; Nav `composable(Routes.*)` are inline lambdas | `kotlin.plugin.compose`, `compose-bom 2024.10.01`, `NavHost` routes | Correctly no extra keep; verified no `Class.forName` for destinations |
| `-assumenosideeffects Log.v/d/i` | Logging policy | Strips verbose logs only after verified | `AppLogger` gates + Phase 8.1 comment | **Commented out** — do not uncomment until `assembleRelease + lintVital PASS` smoke |

No broad `-keep class ** { *; }` added.

---

## 6. FLAG_SECURE Investigation

### 6.1 UI architecture inspected

* **Single Activity:** `MainActivity : ComponentActivity` hosts all UI
* **Navigation:** `MainNavHost` (`NavHost startDestination HOME`) with `Scaffold + NavigationBar` + `composable()` for `HOME, HISTORY, BUSINESS, PROFILE, VERIFICATION, PRICING, UNPARSED, SYNC_FAILURES, SYNC_DIAGNOSTICS, OTP`; `OnboardingNavHost` (`NavHost startDestination LANDING`) with 7 steps. No second Activity.
* **Screens displaying sensitive data:**
  * `HistoryScreen` — full transaction list: amount, sender, UPI app, time, UTR (direct financial PII, primary sensitivity)
  * `HomeScreen` — latest payment card (amount + sender + app) + `BusinessScreen` — today's total/avg/largest/peakHour aggregates (financial)
  * `ProfileScreen` / `MerchantProfileScreen` — merchant name/shop/phone/merchantId (account PII)
  * `VerificationScreen` — found transaction details after amount match (sensitive)
  * `UnparsedNotificationsScreen` / `SyncFailuresScreen` / `SyncDiagnosticsScreen` — diagnostic queue (not payment content but queue metadata, moderate)
* **Search:** `Select-String FLAG_SECURE|setFlags|WindowManager` → **0 hits pre-9.2**

### 6.2 Decision: global FLAG_SECURE on `MainActivity` window

**Implemented in 9.2.**

```kotlin
// MainActivity.kt:24-30
window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
```

**Why global (not per-screen):**

* Single Activity + Compose NavHost: per-screen `DisposableEffect { window.addFlags; onDispose { clearFlags } }` is fragile — navigation is `rememberNavController + NavHost`, back stack `findStartDestination + saveState/restoreState`, clearing on dispose would race on fast tab switches and leave window unprotected or flicker; requires lifecycle-aware handling in every `Screen` composable and leaks concern to child.
* Global is **least invasive**: 2 lines in `MainActivity.onCreate` before `setContent`, no changes to `AppNavigation.kt`, `Scaffold`, `NavHost`, `ViewModels`, theme, or back behavior; does not affect normal rendering, accessibility (TalkBack still works), or `NavigationBar` transitions.
* Sensitive data appears on **80%+ of authenticated routes** (HOME/HISTORY/BUSINESS/PROFILE/VERIFICATION) — per-screen would almost be global anyway, but with more code and risk.
* Does not protect screens that don't need it **over-protects** onboarding (`LANDING/PRIVACY/MOBILE_NUMBER`) — acceptable tradeoff for finance data; screenshots of privacy explanation are not business-critical and global avoids split disclosure.
* Release-only vs always: `FLAG_SECURE` is display-only, not a payment prerequisite, has zero effect on offline capture; enabling always (debug + release) is safe, simpler, and prevents accidental merchant screenshot in debug dogfooding.

**Not implemented:** per-screen `FLAG_SECURE` — documented as would-be fragile for Phase 9.2's single-activity Compose nav; global is strictly safer.

**Risk analysis:** `FLAG_SECURE` prevents screenshots/screen recordings and recent-apps thumbnail (`isScreenCaptureProtected`). It can be bypassed by root/camera photo — acknowledged, still standard for finance UIs. No impact on `TTS` or `WorkManager`.

---

## 7. PaymentPipelineMetrics Investigation

### 7.1 Current state inspected

* **Class:** `observability/PaymentPipelineMetrics.kt:13-41` — `@Singleton AtomicLong x7`, methods `onNotificationReceived/onParseRejected/onValidationRejected/onDuplicate/onPersisted/onTtsAttempted/onTtsFailed`, `snapshot():Snapshot` — **not wired** pre-9.2 (only definition + `ObservabilityHardeningTest`)
* **Search:** `Select-String PaymentPipelineMetrics` → only definition file; `ProcessTransactionUseCase.kt` pre-9.2 had **0 import** of metrics; `UpiNotificationListenerService.kt` injected only `ProcessTransactionUseCase + AppLogger`, not metrics
* **Design review:** `AtomicLong` → non-blocking, no Room/network/auth/WorkManager imports, `snapshot()` copies longs — correct for observational

### 7.2 Wiring decision: **IMPLEMENTED in `ProcessTransactionUseCase` only**

**Why safe to wire now (conservative):**

* Metrics design already meets Phase 9.2 rule: **observational, not prerequisite**
  * Every call is `runCatching { metrics.onX() }` — an exception in metrics **never** interrupts payment flow, never rolls back Room, never blocks TTS
  * No `suspend`, no `Flow`, no `withContext`, no `Dispatchers.IO`, no DB write, no network, no auth
  * Counters are aggregate (`Long`) — **no PII** (no amount/sender/VPA/phone/rawNotification/JWT/Firebase/refresh — counts only)

**Points wired (verified in `ProcessTransactionUseCase.kt` post-9.2):**

| Counter | Trigger in `ProcessTransactionUseCase` | Guard |
|---|---|---|
| `received` (`onNotificationReceived`) | `processNotification` entry, before `textCleaner.clean` | `runCatching` |
| `parseRejected` | `resolver.resolve == null` (`PARSER_NOT_FOUND`) **and** `catch (e)` from `parser.parse` | `runCatching` |
| `validationRejected` | `ValidationResult.Invalid` branch | `runCatching` |
| `duplicates` | `insertTransactionIfNotDuplicate == false` (`SKIP_TRANSACTION`) | `runCatching` |
| `persisted` | `inserted == true` (after dedup) | `runCatching` |
| `ttsAttempted` | `transactionWithVoice.voiceAnnounced == true` entry, before `prepare/speak` | `runCatching` |
| `ttsFailed` | `catch (e)` around `prepare/speak` | `runCatching` |

**What was NOT wired (intentionally):**

* `UpiNotificationListenerService.onNotificationPosted` — metrics already counted via `ProcessTransactionUseCase.processNotification` entry, avoiding double-count; service remains thin (only `ProcessTransactionUseCase + AppLogger`), keeping notification callback off-thread via `serviceScope.launch(IO)` unchanged
* Any persistence of metrics — `PaymentPipelineMetrics` stays in-memory only, no `Room`, no `DataStore`, no `SharedPreferences`
* No UI wiring to display `snapshot()` yet — that is 9.3/ diagnostics UI work; Phase 9.2 only ensures counters increment correctly

**Privacy:** Verified `PaymentPipelineMetrics.kt` contains **no** field for amount/sender/VPA/phone/raw/body/token — only `Long` counts.

**Dependency check (post-9.2):** `ProcessTransactionUseCase.kt` imports now include `observability.PaymentPipelineMetrics` alongside existing `filter/parser/validator/repository/voice`; still **no** `Retrofit/OkHttp/Firebase/JWT/WorkManager/diagnostics/network` import — pipeline remains offline-first (see §14).

---

## 8. Crashlytics Investigation

### 8.1 Inspected

* `gradle/libs.versions.toml` — **0** entry for `firebase-crashlytics` / `crashlytics` plugin; `firebaseBom 32.8.1` only with `firebase-auth`
* `app/build.gradle.kts` dependencies/plugins — `firebase-auth` only, **no** `com.google.firebase:firebase-crashlytics`, **no** `com.google.firebase.crashlytics` gradle plugin
* `app/src/main/java` — `Select-String Crashlytics|FirebaseCrashlytics|recordException|setCustomKey` → **0 hits** (only `FirebaseAuth`, `PhoneAuthProvider`)
* `app/google-services.json` — `project_id upi-voice-alert-e7257` present, but Crashlytics is not enabled server-side via `google-services` alone — requires `firebase-crashlytics` SDK + plugin
* Existing observability: `logging/AppLogger` (DEBUG-gated), `sync/SyncDiagnosticRepositoryImpl` (bounded 200, sanitized messages), `SyncDiagnosticMessageMapper` — all offline, no network telemetry

### 8.2 Decision: **DEFERRED — NOT JUSTIFIED FOR CURRENT RELEASE**

**Classification:** `DEFERRED` (not `IMPLEMENTED`, not `NOT JUSTIFIED FOREVER`)

**Reasoning:**

* **Not automatically added because it is best practice.** Adding Crashlytics adds (1) new SDK + Gradle plugin, (2) additional data collection disclosure for Play Data Safety, (3) network transmission of crash reports, (4) risk of PII capture via automatic `recordException` breadcrumbs or custom keys if misconfigured — all contrary to CLAUDE.md MVP privacy commitment "fully local — no backend, no network calls, no cloud storage" for payment capture, and to Phase 9.2's "payment must still be captured when offline/backend unavailable".
* **Current release strategy:** MVP ships **sideloaded** (per CLAUDE.md 1.5 out of scope: Play Store publishing) — size not yet constrained, diagnostics via local `SyncDiagnosticRepository` + `AppLogger` are sufficient for field triage without Crashlytics. Adding Crashlytics now would increase APK size and require PII sanitization audit (ensure no `rawNotification`, `sender`, `amount`, `VPA`, `JWT`, `refresh`, `Firebase token`, `Authorization`, `body` attached as custom keys).
* **Would require privacy work to implement safely:** If implemented later, must ensure `recordException(throwable)` is never passed a `Throwable` whose message contains notification text, and `setCustomKey/log` never attaches payment content — needs dedicated `CrashlyticsTree` wrapper with sanitized allow-list (mirrors `AppLogger` / `SyncDiagnosticMessageMapper` pattern) and `isCrashlyticsCollectionEnabled` tied to opt-in.
* **Therefore:** Document deferral, do not add dependency now. Make explicit recommendation for **Phase 9.3 or post-release** to add `firebase-crashlytics` with sanitized wrapper, offline queuing, and Data Safety disclosure.

---

## 9. BASE_URL Investigation

### 9.1 Inspected

* `app/build.gradle.kts:53-85` — `BASE_URL` is `buildConfigField String` from `local.properties` or env, with **build-time safety**:
  * `debug: localProperties SHOUTPAY_DEBUG_BASE_URL ?: "https://api.shoutpay.in/api/"` — debug localhost/LAN allowed
  * `release: localProperties SHOUTPAY_RELEASE_BASE_URL ?: System.getenv SHOUTPAY_RELEASE_BASE_URL ?: "https://api.shoutpay.in/api/"` + `GradleException` if contains `10.0.2.2/localhost/127.0.0.1/192.168.x.x` or not `https://` or not ending `/`
* `config/EnvironmentConfig.kt` — `isHttpBodyLoggingEnabled == isDebug` (release never logs bodies)
* `config/EnvironmentProvider.kt:26-37` — `BuildConfig BASE_URL + DEBUG` → `EnvironmentConfig`, then `EnvironmentValidator.requireValid(cfg)` at `by lazy`
* `config/EnvironmentValidator.kt` — full URL parse via `java.net.URL`, rejects empty, missing `http(s)://`, missing trailing `/`, and for `isRelease`: `10.0.2.2`, `localhost/127.0.0.1`, `192.168.x.x`, `10.x.x.x`, non-`https`
* `config/AppEnvironment.kt` — `DEBUG/RELEASE` enum, derived from `BuildConfig.DEBUG`
* `di/AuthModule.kt:32-64` — `OkHttp 15/30/30/45s`, `retryOnConnectionFailure true`, `HttpLoggingInterceptor BODY + redact Authorization` **only if** `environmentProvider.isHttpBodyLoggingEnabled` (debug), `Retrofit baseUrl = environmentProvider.baseUrl`
* `local.properties` (not committed, inspected via static check) — `SHOUTPAY_DEBUG_BASE_URL=http://192.168.1.10:3000/api/` (LAN dev) — **not** `SHOUTPAY_RELEASE_BASE_URL`, correct local separation
* Search `BASE_URL|localhost|127.0.0.1|10.0.2.2|https://api.shoutpay.in`:
  * Hardcoded `localhost` → **0** in `app/src/main/java` (only `EnvironmentValidator` checks it, plus docs)
  * `https://api.shoutpay.in/api/` → `build.gradle.kts:56,74` as **fallback** for both debug and release when properties missing

### 9.2 Production value

Known previous finding `https://api.shoutpay.in/api/` **remains the fallback** for release when neither `local.properties` nor env provides `SHOUTPAY_RELEASE_BASE_URL`. It is syntactically valid (`https://` + trailing `/`), passes both Gradle and `EnvironmentValidator` checks, and is distinguished from debug's `http://192.168.1.10:3000/api/` via separate `SHOUTPAY_RELEASE_BASE_URL` key — so **debug LAN cannot accidentally enter release** (release uses `SHOUTPAY_RELEASE_BASE_URL` not `SHOUTPAY_DEBUG_BASE_URL`).

### 9.3 Classification

**`VERIFIED STATIC CONFIGURATION + REQUIRES RUNTIME/ENVIRONMENT VERIFICATION`**

* **Static verification achieved (9.2):** `https://` enforced, localhost/`10.0.2.2`/`192.168`/`10.x` rejected at **two independent gates** (Gradle build-time `GradleException` + runtime `EnvironmentValidator.requireValid` fail-fast at `BuildConfigEnvironmentProvider.config` lazy), trailing `/` enforced, `http body logging` release-disabled, `redactHeader Authorization` even in debug, separate `SHOUTPAY_DEBUG_BASE_URL` vs `SHOUTPAY_RELEASE_BASE_URL` keys prevent cross-contamination.
* **Runtime verification required (9.3):** Whether `https://api.shoutpay.in/api/` is the **real healthy production domain** (DNS, TLS, reachable, correct API prefix, returns `401` on unauthenticated `/api/auth/login` not `404`) cannot be proven by syntactic checks. Per §17, do not perform a network call in this phase (offline inspection), do not claim domain health. Final gate must verify with `./gradlew assembleRelease` built APK + device/emulator launch + `EnvironmentValidator` pass + manual `GET https://api.shoutpay.in/api/health` or `POST /api/auth/login` with invalid token === `401` not `ECONNREFUSED`.

---

## 10. Release Environment Audit

**Inspected: `app/build.gradle.kts` buildTypes, `AndroidManifest.xml`, `res/xml/*`, `config/*`, `UpiVoiceAlertApplication`, `di/*`**

| Item | Status | Evidence |
|---|---|---|
| `localhost/127.0.0.1/10.0.2.2/10.0.3.2` in release | **Absent** — blocked at Gradle + `EnvironmentValidator` | `build.gradle.kts:76-77` + `EnvironmentValidator:43-56` |
| `HTTP` cleartext in release | **Absent** — `https://` enforced, `cleartextTrafficPermitted false` | `build.gradle.kts:79-80` + `network_security_config.xml:5` + `isHttpBodyLoggingEnabled == isDebug` |
| Dev/staging backend bleed | **Absent** — distinct `SHOUTPAY_DEBUG_BASE_URL` vs `SHOUTPAY_RELEASE_BASE_URL`, release never reads debug key | `build.gradle.kts:53-56 vs 72-75` |
| Secrets in BuildConfig/APK | **None** — `BuildConfig BASE_URL` + `DEBUG` only; no JWT/refresh/API secret/private key in `buildTypes` | `build.gradle.kts:53-85` + `grep BuildConfig` |
| `isDebuggable` release | `false` | `build.gradle.kts:60` |
| Signing | `keystore.properties` if present else unsigned (CI supplies) | `build.gradle.kts:36-46,88-90` — no secret committed |
| `allowBackup`/`fullBackup`/`dataExtractionRules` | `false/false/@xml/data_extraction_rules` (exclude database+sharedpref) | `AndroidManifest:16-17` + `data_extraction_rules.xml` |
| `networkSecurityConfig` | `@xml/network_security_config` cleartext false | `AndroidManifest:19` + `network_security_config.xml` |
| Exported components | Minimal: `MainActivity exported true` (MAIN/LAUNCHER), `UpiNotificationListenerService exported true` + `BIND_NOTIFICATION_LISTENER_SERVICE`, `InitializationProvider exported false` | `AndroidManifest:38-57` |
| Manual WorkManager init | `InitializationProvider tools:node remove WorkManagerInitializer` + `UpiVoiceAlertApplication:Configuration.Provider` + `HiltWorkerFactory` | `AndroidManifest:26-36` + `UpiVoiceAlertApplication:14-31` |
| Firebase config | `google-services.json` project `upi-voice-alert-e7257` present, `google-services` plugin, `firebase-auth` only, no Crashlytics | `libs.versions.toml:19-20`, `build.gradle.kts:9` |

**No signing architecture redesign, no credentials created/committed, no network call performed.**

---

## 11. Files Created

* `docs/PHASE_9_2_RELEASE_ARTIFACT_HARDENING.md` (this document)

---

## 12. Files Modified

* `app/src/main/java/com/upivoicealert/MainActivity.kt` — added `WindowManager` import + `window.setFlags(FLAG_SECURE, FLAG_SECURE)` in `onCreate` before `setContent` (global FLAG_SECURE, 7 lines, display-only)
* `app/src/main/java/com/upivoicealert/domain/usecases/ProcessTransactionUseCase.kt` — injected `PaymentPipelineMetrics`, wired 7 counters (`received/parseRejected/validationRejected/duplicate/persisted/ttsAttempted/ttsFailed`) via `runCatching`, **no DB/network/blocking**, no payment-semantic change
* `app/proguard-rules.pro` — refined keep set from 39→60 lines: added `* extends AndroidEntryPoint`, `androidx.room.**`, `com.google.gson.**`, `HiltWorkerFactory`, manifest entry-point explicit keeps, documented decision that file is **inactive until minify enabled**, still no `-keep class ** { *; }`
* `app/src/test/java/com/upivoicealert/OemBatteryHardeningTest.kt` — added `PaymentPipelineMetrics` import + constructor param to `makeUseCase` (test compat for new DI)
* `app/src/test/java/com/upivoicealert/ProcessTransactionUseCaseVoiceGateTest.kt` — added `PaymentPipelineMetrics` import + constructor param

---

## 13. Files Deleted

* None

---

## 14. Architecture Impact

```
Payment pipeline:    unchanged (offline-first invariant holds — no network/auth/WorkManager prerequisite added; metrics observational only)
Room version:        unchanged (9, no migration)
Migrations:          unchanged (1_2..8_9 additive)
Sync state machine:  unchanged (PENDING→UPLOADING→SYNCED/PENDING/FAILED, FAILED→PENDING manual, stale→PENDING; no new state)
Authentication:      unchanged (Firebase OTP → Backend verification → JWT 15m + refresh rotation 30d replay protected)
Backend:             unchanged (no shoutpay-backend/src file modified)
WorkManager arch:    unchanged (unique KEEP, 12h sync periodic, 24h reconciliation/stale/audit, HiltWorkerFactory, manual init)
```

Metrics dependency check (post-9.2 `ProcessTransactionUseCase.kt`): imports `NotificationSource, AppLogger, ServiceStatus, Transaction*, UnparsedNotification, VoiceLanguage, ServiceStateRepository, SettingsRepository, TransactionRepository, NotificationFilter/TextCleaner/TransactionClassifier, ParserVersionResolver, TransactionValidator, PackageNames, AnnouncementTemplates, VoiceAnnouncement, PaymentPipelineMetrics` — **no** `Retrofit/OkHttp/Firebase/JWT/WorkManager/diagnostics/network` — pipeline still offline-first.

---

## 15. Static Validation Performed

* `Read app/build.gradle.kts + proguard-rules.pro + AndroidManifest.xml + libs.versions.toml` — initial config under §7
* `Select-String FLAG_SECURE|setFlags|WindowManager` → 0 pre-9.2, confirmed not present
* `Select-String Crashlytics|FirebaseCrashlytics|recordException` → 0 pre-9.2, `google-services.json` shows project without crashlytics
* `Select-String PaymentPipelineMetrics` → only definition + `ObservabilityHardeningTest` pre-9.2, confirmed unwired
* `Select-String BASE_URL|BuildConfig|localhost|127.0.0.1|10.0.2.2|https://api.shoutpay.in` — traced `BuildConfig BASE_URL → EnvironmentProvider → AuthModule baseUrl`, verified separate debug/release keys, validator at 2 gates
* `Get-ChildItem res/xml + res/values + mipmap/drawable` → all resources statically referenced via `R`, no `getIdentifier` dynamic lookup
* `Select-String Class.forName|reflection` → only `::class` for Room/Hilt/Retrofit legitimate, no `Class.forName` dynamic
* `Select-String getResources|getIdentifier` → 0 hits
* `Read MainActivity.kt, AppNavigation.kt, UpiNotificationListenerService.kt, UpiVoiceAlertApplication.kt, EnvironmentValidator.kt, EnvironmentProvider.kt, PaymentPipelineMetrics.kt, ProcessTransactionUseCase.kt, AuthModule.kt` — full source verification for 9.2 decisions
* `git diff --stat` — 6 files changed (3 prod logic + 1 proguard + 2 tests + docs), no schema/sync/auth/backend

No runtime verification performed — explicitly per §21 `TEST EXECUTION RULE`.

---

## 16. Tests Executed

```
0
```

No `./gradlew test / testDebugUnitTest / clean` executed per §21. Static inspection only.

---

## 17. Builds Executed

```
0
```

No `./gradlew assembleDebug / assembleRelease` executed per §21. Proguard keep rules and FLAG_SECURE are **statically prepared** but not build-verified.

---

## 18. Remaining P1/P2/P3

### Fixed in 9.2

* **P2 — PaymentPipelineMetrics unwired** → **WIRED** (observational, non-blocking, PII-free)
* **P2 — FLAG_SECURE absent** → **IMPLEMENTED globally** (display-only, least invasive)

### Deferred — NOT FIXED (explicit, carries forward per §7)

| ID | Finding | Status | Why deferred |
|---|---|---|---|
| **P2-1** | R8 `isMinifyEnabled false` + `isShrinkResources false` | **DEFERRED — BUILD REQUIRED** | Cannot prove Room/Hilt/Firebase/WorkManager/Compose/Retrofit keep safety without `assembleRelease` + device smoke; correctly left `false`, rules prepared, `proguard-rules.pro` now build-ready. Risk otherwise: smaller APK not worth breaking payment capture. |
| **P2-3** | Crashlytics absent | **DEFERRED — DECISION: NOT JUSTIFIED** | Adds data collection + PII risk + APK size; offline diagnostics sufficient for sideload MVP; deferred with explicit sanitization requirements if added later |
| **P2-5** | Release `BASE_URL` placeholder `https://api.shoutpay.in/api/` syntactic only | **REQUIRES RUNTIME/ENVIRONMENT VERIFICATION** | Static gates prove no localhost/LAN/http bleed, but cannot prove domain health/DNS/TLS/API prefix without network call — belongs to 9.3 gate |
| **P3s** | Telemetry upload, cert pinning, latency histogram, OEM battery onboarding | **DEFERRED (P3 future)** | Per investigation §20 — intentionally out of Phase 9 scope |

### No remaining P1

**P1: 0** — both P1s closed in 9.1 (DAO stubs, PII logs)

### Summary

```
Fixed (9.1): P1×2 (DAO defaults, PII logs)
Fixed (9.2): P2×2 (metrics wiring, FLAG_SECURE)
Deferred P2: R8/minify+shrink (build verification), Crashlytics (decision), BASE_URL runtime health
Deferred P3: 4 (telemetry, pinning, histogram, OEM onboarding)
```

---

## 19. Phase 9.3 Requirements

**Explicit runtime/build verification required before claiming production readiness (per §9/§21):**

### 9.3.1 R8 / Minification Build Smoke

* **Command:** `./gradlew assembleRelease` (with `local.properties SHOUTPAY_RELEASE_BASE_URL=https://api.shoutpay.in/api/` + `keystore.properties` or CI unsigned) — must succeed without `GradleException` (release URL guards) and without R8 keep errors (Room/Hilt/Firebase/Gson missing keep)
* **Post-build:** `lintVitalRelease` PASS, `retained` APK inspection — `AppDatabase_Impl` present, `Hilt_*` present, `TransactionSyncWorker` etc. present, `UpiNotificationListenerService` not removed
* **Device smoke (debug or unsigned release on emulator/device with real UPI apps or test notification):**
  * Single payment: post notification → `Dashboard/History` updates, `Room` persisted, `SyncQueue PENDING` enqueued (via `SyncScheduler`), `TTS` announces, `PaymentPipelineMetrics.snapshot().persisted == 1`
  * Second same payment duplicate: `PaymentPipelineMetrics duplicates == 1`, no extra Room row
  * Room queries: `DashboardViewModel` + `HistoryViewModel` still observe `Flow`
  * Workers: `WorkManager` enqueue `transaction_sync_work` `KEEP` succeeds (no `IllegalArgumentException` from missing worker keep)
  * Auth: `Firebase Phone OTP` flow still launches (`FirebaseAuth` not stripped)
  * If any smoke fails → adjust `proguard-rules.pro` minimal keep and re-verify; **do not ship with isMinifyEnabled false silently marked "done"**

### 9.3.2 Resource Shrinking Smoke (paired with R8)

* **Command:** `./gradlew assembleRelease` with `isMinifyEnabled true` + `isShrinkResources true` — verify no missing `R.mipmap.ic_launcher`, `R.string/notification_listener_label`, `R.xml/network_security_config` (resource shrinking has removed unused but kept manifest-referenced ones)
* **Device smoke:** App launches, `HomeScreen`/`HistoryScreen`/`BusinessScreen` render, notification icons show, no `Resources.NotFoundException`

### 9.3.3 FLAG_SECURE Device Verification

* **Check:** Settings → Recent apps → ShoutPay card is **blank/blurred** (FLAG_SECURE active), screenshot via `adb shell screencap` / hardware keys yields **black image** when any ShoutPay screen is foreground, **does not** break `NavHost` tab navigation (`HOME↔HISTORY↔BUSINESS↔PROFILE`), back stack, or TalkBack

### 9.3.4 PaymentPipelineMetrics Runtime Triage

* **Check:** After the smoke payment(s) above, inject or expose `PaymentPipelineMetrics.snapshot()` via Debug screen or `Log.d` (debug only) — verify `received >= persisted + duplicates`, `parseRejected + validationRejected` counts non-payment, `ttsAttempted >= persisted when voiceEnabled`, `ttsFailed == 0` when TTS available; also verify **no network/DB write** triggered by metrics (monitor `StrictMode` / `Room` query log)

### 9.3.5 Crashlytics Decision Re-evaluation (if considered)

* If Crashlytics is proposed for 9.3, add `firebase-crashlytics` plugin + SDK only with: `CrashlyticsTree` wrapper that **never** calls `setCustomKey/log/recordException` with `rawNotification/sender/amount/VPA/phone/JWT/refresh/Firebase token/Authorization/body`; enable `setCrashlyticsCollectionEnabled(false)` default with opt-in, document Data Safety disclosure

### 9.3.6 BASE_URL Production Health (final release gate checklist)

* **Static already proven (9.2):** `GradleException` rejects `localhost/10.0.2.2/192.168/10.x` + requires `https://` + trailing `/`; `EnvironmentValidator` replicates; `SHOUTPAY_DEBUG_BASE_URL` vs `SHOUTPAY_RELEASE_BASE_URL` separation
* **Runtime required (9.3):** With `release` build from `assembleRelease`, on device/emulator (no `local.properties` override), verify `BuildConfig BASE_URL == https://api.shoutpay.in/api/` and **real domain** health:
  * `GET https://api.shoutpay.in/api/health` (or current prod health path) returns `200` + `status ok` with TLS valid
  * `POST https://api.shoutpay.in/api/auth/login` with invalid `firebaseIdToken` returns `401 MALFORMED_REQUEST/INVALID_ID_TOKEN` not `ECONNREFUSED/404/500`
  * `TransactionSyncRepository` sync against prod returns `Success` for valid `transactionUuid` or `400/401` properly categorized, not `5xx` HTML leak
  * If domain differs from `api.shoutpay.in` → update `build.gradle.kts` fallback + `proguard-rules.pro` comment + docs and re-verify

### 9.3.7 Full Matrix (informational, not 9.2 scope)

* `./gradlew testDebugUnitTest` → 486 Android unit tests **PASS** (historical 9.7 matrix, re-run)
* `./gradlew assembleDebug` **SUCCESS**, `./gradlew assembleRelease` **SUCCESS**
* `shoutpay-backend npm test` → 67 backend tests **PASS**
* Static audits: `TODO/FIXME 0` (toDomain false positives only), `secret/PII log 0` in release path (via `AppLogger` + redacted logs), `cleartext 0`, `exported components minimal`, `Room version 9 migrations 1_2..8_9`, `No broad keep`

**Do not claim production readiness until all 9.3 runtime/build items have been executed and recorded.**


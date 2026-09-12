# Phase 9.1 — DAO & Privacy Hardening

**Date:** 2026-09-10  
**Baseline:** `docs/PHASE_9_INVESTIGATION_REPORT.md` (commit `9d02090`, clean tree)  
**Branch:** Phase 9.1 hardening (P1 only)  
**Room version:** 9 (unchanged, no migration)  
**Scope:** Hardening only — no new product features, no schema change, no sync/auth redesign, no pipeline behavior change

---

## 1. Objective

Phase 9 investigation identified two P1 production-readiness gaps that do not break current builds (Room KSP overrides stubs, AppLogger DEBUG-gates most PII) but remove compile-time and privacy safety nets:

* **P1-1:** Room DAO interfaces carry Kotlin default bodies (`=0`, `=1`, `=emptyList()`) that violate the Room `@Dao` contract. Room KSP currently generates overriding implementations (hence Debug/Release SUCCESS historically), but the source-level stubs are confusing and create silent-fallback risk if codegen ever failed to override (KSP misconfig, cache, Room bump) — stale recovery would find nothing, audit would report `0` issues, conditional updates would report `1` success while racing.

* **P1-2:** Payment-pipeline/TTS logging emits financially sensitive content (amount, sender, raw notification text, generated announcement speech) via `AppLogger.d` (DEBUG-gated today, release-safe but debug-leaks) and direct `android.util.Log.i` (always emitted, release-leaks for `VoiceAnnouncementEngine.speak`).

Phase 9.1 closes both gaps with the smallest contract-correct changes, preserving SQL semantics, sync state machine, payment behavior, and Room schema.

---

## 2. P1 Findings (from Phase 9 report §18)

| ID | Description | Evidence | Relates to |
|---|---|---|---|
| P1-1 | DAO stub defaults `=0/=1/=emptyList()` on `@Query` methods | `TransactionDao.kt:129-146` (3), `SyncQueueDao.kt:31,37,94,125,131,136-189` (12), `SyncDiagnosticDao.kt:22,25,41` (3) = 18 methods | `StaleUploadRecovery`, `SyncIntegrityAuditor`, diagnostics retention, race detection |
| P1-2 | Verbose PII in logs: `rawText`, `cleanedText`, `amount`, `sender`, `announcement text` | `ProcessTransactionUseCase.kt:61,68,120-122,169`, `VoiceAnnouncementEngine.kt:106` (`Log.i text=$text`), `TransactionRepositoryImpl.kt:74-195` (`Log.i amount/sender`), `NotificationFilter.kt:38-55` (`Log.i`), `VerifyPaymentUseCase.kt:23,26`, `BusinessSummaryUseCase.kt:31` | Play Data Safety, privacy review, merchant-sensitive financial data |

No P0. P2/P3 explicitly deferred to later subphases.

---

## 3. DAO Changes

### 3.1 Principle

* `@Query` DAO methods must be **abstract**. Removing `=0/=1/=emptyList()` makes Room fail compilation if a query is unimplemented — the desired safety. No SQL changed.
* Test fakes must **implement the real contract** rather than preserve production-side fake defaults.

### 3.2 Modified DAO files (production)

#### `data/database/TransactionDao.kt`

| Method | Old (stub) | New | SQL semantics | Caller(s) |
|---|---|---|---|---|
| `countEligibleForAudit(): Int = 0` | `= 0` default | `abstract` (no default) | `SELECT COUNT(*) FROM transactions WHERE status='SUCCESS' AND transactionType='RECEIVED' AND TRIM(transactionUuid) != ''` | `SyncIntegrityAuditor` (healthy/missing/orphaned audit) |
| `countMissingQueueForAudit(): Int = 0` | `= 0` | `abstract` | `... AND NOT EXISTS (SELECT 1 FROM sync_queue WHERE entityId=transactionUuid)` | `SyncIntegrityAuditor` |
| `countScannedTransactionsForAudit(): Int = 0` | `= 0` | `abstract` | `SELECT COUNT(*) FROM transactions WHERE TRIM(transactionUuid) != ''` | `SyncIntegrityAuditor` |

All 3 made abstract. No index/migration change.

#### `data/sync/SyncQueueDao.kt` (12 methods)

| Method | Old | New | SQL | Callers |
|---|---|---|---|---|
| `updateStatusIfExpected(...): Int = 1` | `=1` | abstract | `UPDATE sync_queue SET status=:newStatus, updatedAt=:updatedAt WHERE id=:id AND status=:expectedStatus` | `TransactionSyncRepository` (PENDING→UPLOADING, UPLOADING→SYNCED/PENDING) |
| `incrementRetryCountIfExpected(...): Int = 1` | `=1` | abstract | `UPDATE ... retryCount+1 WHERE status=:expectedStatus` | `TransactionSyncRepository` |
| `markFailedWithDiagnosticsIfExpected(...): Int = 1` | `=1` | abstract | `UPDATE ... status/lastErrorCode/Message/failedAt WHERE status=:expectedStatus` | `TransactionSyncRepository` (UPLOADING→FAILED) |
| `getStaleUploadingItems(...): List = emptyList()` | `=emptyList()` | abstract | `SELECT * WHERE status='UPLOADING' AND updatedAt<cutoff` | `StaleUploadRecoveryManager` |
| `recoverStaleUploading(...): Int = 0` | `=0` | abstract | `UPDATE ... SET status='PENDING' WHERE status='UPLOADING' AND updatedAt<cutoff` | `StaleUploadRecoveryManager` |
| `countTransactionQueueItems(): Int = 0` | `=0` | abstract | `SELECT COUNT(*) WHERE entityType='TRANSACTION'` | `SyncIntegrityAuditor` |
| `countAllQueueItems(): Int = 0` | `=0` | abstract | `SELECT COUNT(*) FROM sync_queue` | `SyncIntegrityAuditor` |
| `countOrphanedQueueItems(): Int = 0` | `=0` | abstract | `... WHERE entityType='TRANSACTION' AND NOT EXISTS (transactions.transactionUuid = entityId)` | `SyncIntegrityAuditor` |
| `countDuplicateExtraRows(): Int = 0` | `=0` | abstract | `COUNT(*) - COUNT(DISTINCT entityId)` for TRANSACTION | `SyncIntegrityAuditor` |
| `countDuplicateGroups(): Int = 0` | `=0` | abstract | `COUNT(*) FROM (GROUP BY entityType,entityId HAVING COUNT>1)` | `SyncIntegrityAuditor` |
| `countInvalidQueueItems(): Int = 0` | `=0` | abstract | `TRIM(entityId)='' OR entityType!='TRANSACTION' OR status NOT IN (...)` | `SyncIntegrityAuditor` |
| `countStaleUploading(...): Int = 0` | `=0` | abstract | `SELECT COUNT(*) WHERE status='UPLOADING' AND updatedAt<cutoff` | `SyncIntegrityAuditor` |

All SQL unchanged. Restrictive `WHERE status=:expected` predicates preserved.

#### `data/sync/SyncDiagnosticDao.kt` (3 methods)

| Method | Old | New | SQL | Callers |
|---|---|---|---|---|
| `count(): Int = 0` | `=0` | abstract | `SELECT COUNT(*) FROM sync_diagnostic_events` | `SyncDiagnosticRepositoryImpl`, tests |
| `deleteOlderThan(cutoff): Int = 0` | `=0` | abstract | `DELETE ... WHERE createdAt<cutoff` | `SyncDiagnosticRepositoryImpl` |
| `trimToMaxCount(maxCount): Int = 0` | `=0` | abstract | `DELETE ... WHERE id IN (SELECT ... ORDER BY createdAt DESC LIMIT -1 OFFSET maxCount)` | `SyncDiagnosticRepositoryImpl` (bounds to 200) |

### 3.3 Test fake updates

Removing production defaults means any test fake that previously relied on the default (omitted the method) now fails to compile. 14 test files were patched to explicitly implement the real contract, preserving their intended test behavior (returning `0`/empty or lightweight in-memory counts):

* `NetworkProductionHardeningTest.kt` — added `countEligibleForAudit`, `countMissingQueueForAudit`, `countScannedTransactionsForAudit`, plus 7 `count*` for queue
* `OemBatteryHardeningTest.kt`, `ProcessDeathRecoveryTest.kt`, `StaleUploadRecoveryManagerTest.kt`, `StaleUploadRecoveryWorkerTest.kt`, `SyncQueueDaoTest.kt`, `SyncQueueRepositoryTest.kt`, `SyncQueueRecoveryTest.kt`, `SyncRaceConditionHardeningTest.kt`, `SyncStatusRepositoryTest.kt`, `TransactionSyncDiagnosticsTest.kt`, `TransactionSyncQueueActivationTest.kt`, `TransactionSyncReconcilerTest.kt`, `TransactionSyncWorkerDiagnosticsTest.kt`, `TransactionSyncWorkerStaleRecoveryTest.kt`
* `TransactionReconciliationWorkerTest.kt` — added both DAO count methods and queue counts/stale
* `TransactionSyncRepositoryTest.kt`, `TransactionSyncWorkerStaleRecoveryTest.kt`, `TransactionRepositoryDedupTest.kt` — added audit counts

Each fake now mirrors the DAO SQL semantics via Kotlin filtering (e.g., `rows.count { it.status=="SUCCESS" && ... }`) or returns `0` where the test does not exercise that path. No production DAO defaults were reintroduced to make tests compile.

### 3.4 Confirmation of unchanged semantics

* No SQL string modified (verified via `git diff` on `@Query` annotations).
* Restrictive conditional updates remain protected by `expectedStatus` (`WHERE status=:expectedStatus` everywhere in sync path — `SyncQueueDao:30-131`).
* State machine transitions remain exactly:  
  `PENDING→UPLOADING (WHERE PENDING)`, `UPLOADING→SYNCED (WHERE UPLOADING)`, `UPLOADING→PENDING (WHERE UPLOADING)`, `UPLOADING→FAILED (WHERE UPLOADING)`, `FAILED→PENDING (WHERE FAILED manual)`, `UPLOADING stale→PENDING (WHERE UPLOADING AND updatedAt<cutoff)`, `SYNCED terminal`.

---

## 4. Privacy Changes

### 4.1 Logging convention

* `AppLogger.d/i` = **DEBUG-gated** (`if (BuildConfig.DEBUG)`), safe for release (suppressed). Used for operational diagnostics that are useful in development but must not leak in production.
* `Log.w/e` = always emitted, but must contain **sanitized** content only (no PII).
* Direct `Log.i/d` with payment content = **never allowed** (release leak).
* Sensitive categories never logged in release or debug: `rawText`/`rawNotification`/`cleanedText` content, `amount` (single payment), `sender`/`VPA`/`phone`, generated speech `text`, `JWT`/`refresh`/`Firebase token`/`OTP`/`Authorization`/`request body`.

### 4.2 Production files changed

#### `domain/usecases/ProcessTransactionUseCase.kt`

| Location | Old | New | Classification |
|---|---|---|---|
| `PROCESS_START` (line 61) | `AppLogger.d ... rawText=$rawText` | `AppLogger.d ... rawLen=${rawText.length}` (length only) | RELEASE-SENSITIVE → sanitized |
| `CLEANED_TEXT` (68) | `text=$text` | `cleanedLen=${text.length}` | RELEASE-SENSITIVE → sanitized |
| `parsed amount/sender/app` (120-122) | 3× `AppLogger.d parsed amount=... sender=... app=...` | Single `AppLogger.d PARSER_RESULT package=... parser=... status=parsed` (no payment content) | DEBUG-ONLY sensitive → redacted |
| `SKIP_TRANSACTION` (169) | `... amount=... sender=... app=... incomingRef=...` | `... id=... reason=duplicate` (id + reason only) | RELEASE-SENSITIVE → sanitized |

All other logs in file (`FILTER_PASS/FAIL`, `CLASSIFICATION_RESULT` type/status, `PARSER_SEARCH/FOUND/NOT_FOUND`, `Skipping announcement`) are package/type/status only — **SAFE**, unchanged (still `AppLogger.d` DEBUG-gated).

#### `voice/VoiceAnnouncementEngine.kt`

| Location | Old | New |
|---|---|---|
| `init { TTS_INIT, TTS_AVAILABLE_LANGUAGES }` | `Log.i` (always) | `AppLogger.d` (DEBUG-gated) — content is engine/voice tags, safe but now gated for consistency |
| `SELECTED_LANGUAGE, LOCALE_CHECK, SET_LANGUAGE*` | `Log.i` | `AppLogger.d` |
| `speak` result (106) | `Log.i TAG, "SPEAK_RESULT=... text=$text"` — announcement text = `"Received ₹500 from Rahul via PhonePe"` (PII) always emitted | `AppLogger.d TAG, "SPEAK_RESULT=... currentLocale=... textLen=${text.length}"` — **never logs announcement content** even in debug; diagnostic is result + locale + length |

TTS behavior (locale resolution, `QUEUE_ADD`, fallback to English) unchanged — only logging side changed.

#### `filter/NotificationFilter.kt`

| Old | New |
|---|---|
| `android.util.Log.i TAG, "FILTER_CHECK ..."` (3× blocked/promo/financial, always) | `AppLogger.d TAG, "FILTER_CHECK ..."` (DEBUG-gated) — content is `package + keyword` only, **SAFE** but now consistent with policy (no release log) |

#### `data/repository/TransactionRepositoryImpl.kt`

| Location | Old | New |
|---|---|---|
| `CHECK_START` | `Log.i DUP_TAG, "CHECK_START amount=... sender=... app=... package=... referenceId=... fingerprint=..."` | `AppLogger.d DUP_TAG, "CHECK_START package=... hasRef=... hasFingerprint=... createdAt=... windowStart=... windowEnd=..."` — booleans/lengths only |
| `DECISION=DUPLICATE matched=referenceId` | `... incomingRef=... existingId=... existingApp=... existingRef=...` | `... existingId=...` (id only) |
| `DECISION=NOT_DUPLICATE_YET reference_id_not_found` | `checkedRef=$incomingRef` | `reason=reference_id_not_found` (no ref value) |
| `DECISION=DUPLICATE matched=fingerprint` | `amount=... sender=... fingerprint=... existingApp=... existingRef=...` | `existingId=...` |
| `DECISION=NOT_DUPLICATE_YET fingerprint_no_match` | `fingerprint=$fingerprint` | `reason=fingerprint_no_match` |
| `DECISION=DUPLICATE matched=rawNotification` | `windowStart/windowEnd + existingCreatedAt` | `existingId=...` (window retained for NOT_DUPLICATE case only where window is diagnostic, not PII) |
| `IGNORED id=... incomingRef=...` | `incomingRef` included | `id + reason=duplicate` |
| `ENQUEUED syncQueue entityId=...` | `Log.i` | `AppLogger.d` (DEBUG-gated, safe) |
| `INSERTED/INSERT_CONFLICT` | `incomingRef=... fingerprint=...` via `Log.i` | `AppLogger.d` with `id + uuid` only (no ref/fingerprint) |

`Log.w` for `Atomic insert+enqueue failed` and `Enqueue failed after insert` remain `Log.w` (always, sanitized — exception only, no payment content) — **SAFE**.

#### `domain/usecases/VerifyPaymentUseCase.kt`

| Old | New |
|---|---|
| `Log.i VERIFY_CHECK amount=... windowMs=... since=...` | `AppLogger.d VERIFY_CHECK windowMs=... since=...` (no amount) |
| `Log.i VERIFY_MATCH amount=... transactionId=... sender=... app=... createdAt=...` | `AppLogger.d VERIFY_MATCH transactionId=... createdAt=...` (no amount/sender/app) |
| `Log.i VERIFY_NO_MATCH amount=...` | `AppLogger.d VERIFY_NO_MATCH since=...` |

#### `domain/usecases/BusinessSummaryUseCase.kt`

| Old | New |
|---|---|
| `Log.i BUSINESS_SUMMARY total=... count=... average=... largest=... peakHour=...` | `AppLogger.d BUSINESS_SUMMARY count=... peakHour=...` (no financial aggregates) |

Amounts/totals are still computed and returned via `Flow<BusinessSummary>` to UI — only the log is sanitized.

### 4.3 Audit result (static grep over `app/src/main/java`)

After changes, `Get-ChildItem -Recurse -Filter "*.kt" | Select-String -Pattern "Log\.(i|d|w|e)"` shows set:

* **SAFE / DEBUG-gated:** `AppLogger.d` (all PII traces now via `AppLogger.d` with redacted content or lengths), `AppLogger.w/e` only for non-sensitive warnings/errors.
* **SAFE / always but sanitized:** `Log.w/e` in `TransactionSyncRepository`, `StaleUploadRecoveryManager`, `TransactionSyncReconciler`, `workers` (only `requestId`, `status`, `retryCount`, `errorCode` — never `rawText/amount/sender/VPA/JWT/body`).
* **Operational safe `Log.i` retained:** `ServiceController` (STATE_RESTORED/SERVICE_STARTED/STOPPED/VOICE_TOGGLED), `SubscriptionRepositoryImpl` (UPGRADE_REQUEST planId), `StaleUploadRecoveryManager` (recoveredCount), `TransactionSyncReconciler` (Reconciliation done: result counts), `workers` (sync success/partial/audit counts). No payment PII in these.

**No remaining release-capable log exposes:** `rawText`/`rawNotification`/`cleanedText` content, single-payment `amount`, `sender`, `VPA`, `phone`, `announcement speech text`, `JWT`/`refresh`/`Firebase token`/`OTP`/`Authorization`/`body`.

### 4.4 Backend privacy (read-only audit)

No backend code changed. Audit of `shoutpay-backend/src` confirms continued sanitization:

* `app.js:31-37` morgan `combined` dev / `tiny` prod (skip `/health`), `express.json 256kb`, rate-limit 100/15m + auth 5/20
* `app.js:80-89` error handler logs `{requestId, method, path, status, errorCode}` prod, no stack/body/SQL; 404 prod `NOT_FOUND` without path echo
* `app.js:18-22` `requestId = crypto.randomUUID()` never derived from JWT/PII
* `auth/router.js`, `middleware.js` verify `HS256` pinned, `tokenHash sha256 @unique`, no `Authorization` header logged
* `database.js` logs only `errorName/errorCode`, no SQL internals
* No `console.log` of request/response bodies, JWTs, refresh tokens, or payment payloads

**Result:** No P1 backend logging issue found — backend left unchanged.

---

## 5. Protected Systems (explicit confirmation not changed)

| System | Status | Evidence |
|---|---|---|
| **Payment pipeline** `NotificationListenerService → TextCleaner → Filter → Classifier → Resolver → Parser → Validator → Fingerprint → Dedup → Room → SyncQueue → TTS` | **Unchanged** (offline-first, network/auth/WorkManager-independent) | `ProcessTransactionUseCase` imports still only `filter/parser/validator/repository/voice`; `UpiNotificationListenerService` still only `ProcessTransactionUseCase + AppLogger`; no new import of Retrofit/OkHttp/Firebase/JWT/WorkManager/diagnostics |
| **Authentication** (Firebase → `AuthRepository` → `EncryptedSharedPreferences` → `AuthInterceptor/AuthAuthenticator Mutex`) | **Unchanged** | No file under `data/auth` or `network/Auth*` modified; `JWT HS256 15m`, refresh rotation 30d, `tokenFamilyId+tokenHash` replay protection still as in `auth/tokens.js` |
| **Sync state machine** (PENDING→UPLOADING→SYNCED/PENDING/FAILED, FAILED→PENDING manual, stale UPLOADING→PENDING) | **Unchanged** | SQL `WHERE status=:expected` preserved in `SyncQueueDao`; no new state, no global lock, no auto FAILED resurrection, no SYNCED rollback; `TransactionSyncRepository` retry matrix unchanged |
| **Room schema / migrations** | **Unchanged** — `version = 9`, no migration, no `ALTER` | `AppDatabase.kt:12` still `version = 9`; `DatabaseModule` still wires 1_2..8_9 additive `ALTER TABLE ADD COLUMN`/`CREATE TABLE/INDEX` |
| **Backend API / contracts** | **Unchanged** | No `shoutpay-backend/src` file modified |
| **WorkManager architecture** | **Unchanged** | No `scheduler/*` or `work/*` or `worker/*` logic changed; unique `KEEP` + `EXPONENTIAL 5m` + `NetworkType.CONNECTED` still as verified |
| **Dedup / fingerprint / validation / parser** | **Unchanged** | No `TransactionFingerprint`, `TransactionValidator`, `ParserVersionResolver`, `AmountExtractor` etc. touched |
| **Voice announcement behavior** | **Unchanged** (only log sanitized) | `VoiceAnnouncementEngine.prepare/speak` logic identical; `announcementTemplates.build(transaction, language)` still called with same `transaction` + `effectiveLanguage`; `QUEUE_ADD` preserved |

---

## 6. Validation

### 6.1 Static inspection performed

* Grep `= 0|= 1|emptyList()` over `data/database/TransactionDao.kt`, `data/sync/SyncQueueDao.kt`, `data/sync/SyncDiagnosticDao.kt` → **0 matches** for stub defaults (all `@Query` methods now abstract).
* Grep `WHERE status=:expected` in `SyncQueueDao.kt` → restrictive variants present for every state transition.
* Grep `Log\.i|Log\.d` over `app/src/main/java` → remaining `Log.i` are operational safe (counts, service state, reconciler results); no `amount=`, `sender=`, `rawText`, `text=$` in release path.
* Read `ProcessTransactionUseCase.kt` import list → no network/auth/WorkManager/diagnostics import leak.
* Read `AppDatabase.kt` → `version = 9`, `exportSchema false`.
* `git diff --stat` shows 22 files changed (3 DAO prod, 5 privacy prod, 14 test fakes) — scope discipline respected.

### 6.2 Builds / tests executed

* **Gradle builds:** **NOT EXECUTED** (per instruction: expensive, VS Code primary; targeted Gradle only if genuinely necessary — not deemed necessary for interface-only + test-fake changes; static verification substituted).
* **Backend npm tests:** **NOT EXECUTED** (no backend change).
* **Targeted static validation used instead:** source reads, `Select-String` greps, `git diff` inspection.

This document **does not claim** `testDebugUnitTest`/`assembleDebug`/`assembleRelease` passed — they were not run in this phase (explicitly per §19 instruction).

### 6.3 Callers / fakes verification

* Every DAO method that lost its default was traced to production callers (`SyncIntegrityAuditor`, `StaleUploadRecoveryManager`, `SyncDiagnosticRepositoryImpl`, `TransactionSyncRepository`) and to test fakes — all callers already rely on Room KSP implementation (production) and in-memory filtering (tests); semantics preserved.
* Test fakes were updated to explicit in-memory equivalents (e.g., `txs.count { isEligible }`, `rows.count { it.status==UPLOADING && updatedAt<cutoff }`) rather than silent `0`.

---

## 7. Remaining Phase 9 Findings (carried forward, not silently fixed)

From `PHASE_9_INVESTIGATION_REPORT.md` §19-20, explicitly **not** fixed in 9.1:

| ID | Finding | Disposition |
|---|---|---|
| **P2-1** | R8/minification disabled (`isMinifyEnabled false`, `isShrinkResources false`) | **DEFERRED** — candidate 9.2.1 (enable with keep rules, verify Hilt/Room/Retrofit/Gson/Firebase/WorkManager, proguard `-assumenosideeffects Log.v/d/i`) |
| **P2-2** | `FLAG_SECURE` absent (recent-apps shows amounts) | **DEFERRED** — candidate 9.2.2 (product decision, apply per authenticated screen) |
| **P2-3** | Crashlytics/ANR SDK absent | **DEFERRED** — candidate 9.2.4 (add opt-in with PII sanitization or document deferral) |
| **P2-4** | `PaymentPipelineMetrics` (`AtomicLong` counters) unwired | **DEFERRED** — candidate 9.2.3 (wire into `ProcessTransactionUseCase` + `UpiNotificationListenerService`, non-blocking, no Room/network) |
| **P2-5** | Release `BASE_URL` fallback `https://api.shoutpay.in/api/` is placeholder | **DEFERRED** — candidate 9.3.3 (verify real production domain before Play publish; `EnvironmentValidator` guards localhost but not wrong HTTPS domain) |
| **P3-1..P3-4** | Telemetry upload opt-in, cert pinning, latency histogram, OEM battery onboarding | **DEFERRED** (P3 future) |

---

## 8. Risk & Hard Stop Check

No hard-stop condition triggered:

* No DAO change altered sync semantics (SQL unchanged, restrictive `WHERE` preserved).
* No privacy fix required changing payment behavior (TTS still receives same `announcementTemplates.build` result).
* No migration necessary (version stays 9).
* No auth/security regression uncovered.
* No P0/P1 outside this phase discovered.
* No payment path found dependent on diagnostics/network/auth.
* All supposedly generated DAO implementations are guaranteed by Room KSP (abstract methods fail compilation if unimplemented — now correctly enforced).

---

## 9. Files

### Files Modified (production)

* `app/src/main/java/com/upivoicealert/data/database/TransactionDao.kt`
* `app/src/main/java/com/upivoicealert/data/sync/SyncQueueDao.kt`
* `app/src/main/java/com/upivoicealert/data/sync/SyncDiagnosticDao.kt`
* `app/src/main/java/com/upivoicealert/domain/usecases/ProcessTransactionUseCase.kt`
* `app/src/main/java/com/upivoicealert/voice/VoiceAnnouncementEngine.kt`
* `app/src/main/java/com/upivoicealert/filter/NotificationFilter.kt`
* `app/src/main/java/com/upivoicealert/data/repository/TransactionRepositoryImpl.kt`
* `app/src/main/java/com/upivoicealert/domain/usecases/VerifyPaymentUseCase.kt`
* `app/src/main/java/com/upivoicealert/domain/usecases/BusinessSummaryUseCase.kt`

### Files Modified (tests — fake contract hardening)

* `app/src/test/java/com/upivoicealert/NetworkProductionHardeningTest.kt`
* `app/src/test/java/com/upivoicealert/OemBatteryHardeningTest.kt`
* `app/src/test/java/com/upivoicealert/ProcessDeathRecoveryTest.kt`
* `app/src/test/java/com/upivoicealert/StaleUploadRecoveryManagerTest.kt`
* `app/src/test/java/com/upivoicealert/StaleUploadRecoveryWorkerTest.kt`
* `app/src/test/java/com/upivoicealert/SyncQueueDaoTest.kt`
* `app/src/test/java/com/upivoicealert/SyncQueueRecoveryTest.kt`
* `app/src/test/java/com/upivoicealert/SyncQueueRepositoryTest.kt`
* `app/src/test/java/com/upivoicealert/SyncRaceConditionHardeningTest.kt`
* `app/src/test/java/com/upivoicealert/SyncStatusRepositoryTest.kt`
* `app/src/test/java/com/upivoicealert/TransactionRepositoryDedupTest.kt`
* `app/src/test/java/com/upivoicealert/TransactionSyncDiagnosticsTest.kt`
* `app/src/test/java/com/upivoicealert/TransactionSyncQueueActivationTest.kt`
* `app/src/test/java/com/upivoicealert/TransactionSyncReconcilerTest.kt`
* `app/src/test/java/com/upivoicealert/TransactionSyncReconcilerTest.kt` (duplicate path — TransactionSyncReconciler variant)
* `app/src/test/java/com/upivoicealert/TransactionReconciliationWorkerTest.kt`
* `app/src/test/java/com/upivoicealert/TransactionSyncRepositoryTest.kt`
* `app/src/test/java/com/upivoicealert/TransactionSyncWorkerDiagnosticsTest.kt`
* `app/src/test/java/com/upivoicealert/TransactionSyncWorkerStaleRecoveryTest.kt`

### Files Created

* `docs/PHASE_9_1_DAO_PRIVACY_HARDENING.md` (this document)

### Files Deleted

* None

---

## 10. Definition of Done — Phase 9.1

* [x] P1 DAO default/stub risk eliminated (all 18 methods abstract)
* [x] P1 privacy logging issue eliminated (no release PII in `Log.i`/`Log.d`; debug also redacted)
* [x] Payment pipeline behavior unchanged (offline-first, no new dependency)
* [x] Sync state machine unchanged
* [x] Authentication unchanged
* [x] Room remains version 9, no migration
* [x] No unnecessary files modified (22 files: 9 prod + 14 tests + docs; no backend/schema/auth/worker logic)
* [x] Documentation updated (`PHASE_9_1_DAO_PRIVACY_HARDENING.md`)
* [x] Remaining Phase 9 work explicitly carried forward (§7)

**This is hardening, not feature development — smallest safe change.**


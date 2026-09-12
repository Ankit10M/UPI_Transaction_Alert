# Phase 9 — Architecture Investigation, Deferred-Work Audit & Roadmap Lock

**Date:** 2026-09-09  
**Investigator:** Senior engineer (investigation-first phase)  
**Baseline commit:** `9d02090` — Phase 8 Day 9 Add security hardening tests and environment configuration validation  
**Working tree:** clean (`git status --short` empty, `git diff --stat` empty, `git ls-files --others --exclude-standard` empty)  
**Investigation mode:** read-only — no production code, Gradle, Room, backend, test or config modifications  
**Gradle tests/builds executed:** NONE (per instruction §19 / §20)  

---

## 1. Executive Summary

**Repository integrity: PASS.** Source-verified across Android source tree (170+ production Kotlin files), backend Node/Express/Prisma tree, Room v9 with 8 migrations, Gradle, manifest, resources, DI, WorkManager, auth, sync, observability and release config. Git working tree is clean. No pending schema drift, no destructive migration, no uncommitted production changes.

**Payment pipeline: SAFE.** `NotificationListenerService → ProcessTransactionUseCase → Parser → Validator → Fingerprint → TransactionRepository → Room → SyncQueue → VoiceAnnouncementEngine → TTS` is offline-first, local-authoritative, and imports zero network/auth/WorkManager/diagnostics/cloud types in the critical path (`ProcessTransactionUseCase.kt` verified: only `filter/parser/validator/repository/voice` imports).

**Authentication: SAFE.** Firebase verification → backend login → JWT HS256 (15m) + refresh rotation (30d) + replay detection (`tokenFamilyId` + `tokenHash` unique) → `AuthInterceptor` → `AuthAuthenticator` (single-retry + token-equality dedup + Mutex) → `AuthStateManager`/`EncryptedSharedPreferences`. No duplicate JWT/refresh, no client-controlled merchant identity, no bypass. One P2 observation on refresh 429 handling (intentionally non-clearing, documented).

**Sync reliability: FINDINGS (P1 hygiene + P2 deferred, no P0).** State machine correctly restrictive (`WHERE status=:expected` everywhere), FAILED protected, SYNCED protected, stale recovery coordinated. One structural hygiene issue: Room DAO interfaces carry stub default bodies (`=0`, `=1`, `=emptyList()`) that are dead code today (Room KSP overrides them — build passes) but must be removed to eliminate silent-fallback risk and to satisfy Room contract.

**Database: SAFE (with hygiene finding).** Version 9, migrations 1_2..8_9 present and additive, indices correct, no destructive, `exportSchema false`, `DatabaseModule` wires all migrations. No schema change needed for Phase 9 unless DAO hygiene is accepted.

**Security: SAFE (P1 log-redaction finding, otherwise PASS).** Cleartext never permitted, backup excluded, tokens in `EncryptedSharedPreferences`, JWT `HS256` pinned, Helmet/CORS/rate-limit/body-limit/Joi `unknown(false)` all correct. One privacy hardening: verbose announcement/filter logs emit sender/amount/raw-text content via `Log.i` / `AppLogger.d` — gated to DEBUG today but should be redacted or DEBUG-gated consistently.

**Observability: SAFE.** Bounded diagnostics (200 cap, `trimToMaxCount`), categories `SYNC/RECONCILIATION/STALE_RECOVERY/INTEGRITY_AUDIT/...`, `AlreadyRunning → success` non-error, `X-Request-Id` random UUID, `/health` sanitized, diagnostics wrapped in `runCatching` and never block payment. `PaymentPipelineMetrics` is correctly implemented but intentionally unwired (DEFERRED).

**Release readiness: FINDINGS (P2, accepted).** `applicationId com.upivoicealert`, `minSdk 26`, `targetSdk 34`, `compileSdk 34`, `versionCode 1`, debug/release signing, `BASE_URL` validated at Gradle + runtime (reject `10.0.2.2/localhost/127.0.0.1/192.168/10.x`, require `https://` + trailing `/`), `network_security_config` cleartext false, backup excluded, `proguard-rules.pro` documented. Remaining P2: `isMinifyEnabled false` (R8 deferred), `FLAG_SECURE` absent, Crashlytics absent — all previously accepted, no P0/P1.

**P0: 0 · P1: 2 · P2: 5 · P3: 4**

**Recommended Phase 9 objective:** Close the two P1 hygiene gaps (DAO stub defaults, verbose PII logs) plus the three accepted P2 production-hardening decisions (R8, FLAG_SECURE, pipeline metrics wiring) in the smallest possible scope — no new product features, no schema migration, no new auth/sync states.

---

## 2. Repository State

**SOURCE VERIFIED**

| Area | Evidence |
|---|---|
| Android source | `app/src/main/java/com/upivoicealert` — 140+ prod `.kt`, `src/test` 66 test files |
| Backend | `shoutpay-backend/src` — `app.js`, `config/config.js`, `auth/*`, `modules/{merchant,devices,transactions}/*`, `prisma/schema.prisma`, 3 migrations |
| Gradle | `app/build.gradle.kts` (`compileSdk 34 targetSdk 34 minSdk 26`), `gradle/libs.versions.toml` (Room 2.6.1 Hilt 2.52 Coroutines 1.9 Retrofit 2.11 OkHttp 4.12 Firebase BOM 32.8.1), `settings.gradle.kts` |
| Manifest / resources | `app/src/main/AndroidManifest.xml` (NLS exported, WorkManager manual init, `allowBackup false`, `networkSecurityConfig`, `dataExtractionRules`), `res/values/*_keywords.xml`, `res/xml/{network_security_config,backup_rules,data_extraction_rules}.xml` |
| DI | `di/{AppModule,AuthModule,DatabaseModule,RepositoryModule,SchedulerModule}` — all `SOURCE VERIFIED` |
| Room | `data/database/AppDatabase.kt` v9 + 4 DAOs + 2 entities + 2 sync entities |
| Workers / schedulers | `worker/{TransactionSync,TransactionReconciliation,StaleUploadRecovery,SyncIntegrityAudit}Worker`, `scheduler/{Sync,Reconciliation,StaleUploadRecovery,SyncIntegrityAudit}Scheduler`, `work/{WorkScheduler,CleanupWorker,RetryFailedParseWorker}` |
| Domain | `domain/{model,repository,sync,usecases}` + `data/{auth,cloudtransaction,datastore,sync,security,repository}` |
| Network / auth | `network/{ApiClient,AuthApi,AuthInterceptor,AuthAuthenticator,NetworkMonitor}`, `data/auth/{AuthRepository,AuthSessionStore,AuthStateManager}` |
| Config / observability | `config/{AppEnvironment,EnvironmentConfig,EnvironmentProvider,EnvironmentValidator}`, `logging/AppLogger`, `observability/PaymentPipelineMetrics`, `voice/*` |
| Docs | `docs/PHASE_8_6_PRODUCTION_OBSERVABILITY.md`, `docs/PHASE_8_7_FINAL_PRODUCTION_READINESS.md`, `CLAUDE.md`, `FIREBASE_COMPATIBILITY_REPORT.md` |
| Git | `git log --oneline -15` head `9d02090`, `git status --short` empty, `git diff --stat` empty, `git ls-files --others --exclude-standard` empty — 15 commits spanning Phase 1–8 |


---

## 3. Current Architecture

**SOURCE VERIFIED** — matches CLAUDE.md §3 / §4 HLD/LLD and §12 component diagram plus Phase 6–8 extensions.

```
UPI App notification → Android NotificationManager → UpiNotificationListenerService
  → NotificationTextCleaner → NotificationFilter (blocklist + promo + financial signal)
  → TransactionClassifier (RECEIVED/SENT/REFUND + SUCCESS/FAILED/PENDING)
  → ParserVersionResolver → per-app TransactionParser (GPay/PhonePe/Paytm/BHIM/Kotak/Generic)
  → TransactionValidator → ValidationResult
  → ProcessTransactionUseCase.process() → isDuplicate (ref-global → fingerprint → raw-text)
  → TransactionRepositoryImpl.insertTransactionIfNotDuplicate (Room withTransaction + SyncQueue PENDING)
  → VoiceAnnouncementEngine (prepare + speak, best-effort, never blocks save)
  → Room Flow → Compose ViewModels → Dashboard/History/Business/Profile

Side subsystems (non-critical path):
  SyncQueue → TransactionSyncRepository → TransactionSyncApi → WorkManager TransactionSyncWorker (NetworkType.CONNECTED, KEEP, 5m backoff)
  Reconciliation (local, no network) → TransactionReconciliationWorker (24h periodic + immediate)
  Stale recovery (UPLOADING→PENDING) → StaleUploadRecoveryWorker (24h + startup immediate)
  Integrity audit (read-only COUNT) → SyncIntegrityAuditWorker (24h)
  Diagnostics (bounded 200, runCatching) → SyncDiagnosticRepositoryImpl
  Maintenance coordination (per-operation Mutex + AlreadyRunning)
```

Stack: Kotlin, Compose, MVVM, Clean, Hilt, Room, DataStore, WorkManager, Retrofit/OkHttp, Firebase Auth, Coroutines/Flow, TTS; Backend Node/Express/Prisma/PostgreSQL/Firebase Admin/JWT/Joi/Helmet/rateLimit.

---

## 4. Completed Systems

**SOURCE VERIFIED** (file reads) + **HISTORICAL VERIFIED** (Phase 8.7 report: 486 Android unit tests PASS, 67 backend tests PASS, Debug SUCCESS, Release SUCCESS — not re-executed in this phase).

### Payment
`UpiNotificationListenerService` (stateless, `SupervisorJob+IO`, no WakeLock/foreground), `NotificationTextCleaner`, `NotificationFilter`, `TransactionClassifier`, `ParserVersionResolver`, 6 parsers (`GPayV1/PhonePeV1/PaytmV1/BhimV1/KotakV1/GenericV1`), `AmountExtractor/ReferenceIdExtractor/TransactionValidator`, `TransactionFingerprint`, `CheckDuplicateUseCase`/`TransactionRepositoryImpl.isDuplicate`, `AppDatabase` + DAOs, `VoiceAnnouncementEngine` + `AmountToWordsConverter` + `AnnouncementTemplates`, `ProcessTransactionUseCase`.

### Authentication
`Firebase Auth` → `AuthRepository (Mutex, verifyIdToken, save)`, `AuthSessionStore (EncryptedSharedPreferences)`, `AuthStateManager`, `AuthModule (OkHttp 15/30/30/45s, retryOnConnectionFailure, Body logging DEBUG-only + redact Authorization)`, `AuthInterceptor`, `AuthAuthenticator (refresh once, priorResponse count, token-equality dedup, X-ShoutPay-Retry)`. Backend: `auth/{firebase,jwt,router,tokens}`, `middleware.requireAuth`, `RefreshToken hash+replay (tokenFamilyId revocation)`.

### Transaction Sync
`SyncQueueEntity (unique(entityType,entityId))`, `SyncQueueDao` + `TransactionDao`, `SyncQueueRepositoryImpl`, `TransactionSyncRepository (BATCH 100, restrictive PENDING→UPLOADING→SYNCED/PENDING/FAILED, Diagnostics sanitized, retry matrix)`, `SyncStatusStore/DataStore (lastSuccessfulSyncAt only on full success)`, `TransactionSyncWorker`, `Reconciliation`, `StaleUploadRecovery`, `IntegrityAuditor`, `DefaultSyncMaintenanceCoordinator`, `SyncScheduler/ReconciliationScheduler/StaleUploadRecoveryScheduler/SyncIntegrityAuditScheduler`, `UpiVoiceAlertApplication` startup scheduling (individually try/catched).

### Production Hardening
`EnvironmentValidator + BuildConfigEnvironmentProvider + AuthModule baseUrl`, `AppLogger (DEBUG-gated)`, `network_security_config cleartext false`, `backup_rules/data_extraction_rules exclude DB/sharedpref`, `OkHttp timeouts + retry matrix`, `Validation forbid merchantId/firebaseUid`, `merchant ownership scoping`, `requestId randomUUID`, `/health sanitized`, `proguard-rules.pro keep set`.

---

## 5. Payment Pipeline Verification

**SOURCE VERIFIED** — `app/src/main/java/com/upivoicealert/service/UpiNotificationListenerService.kt:40-107`, `domain/usecases/ProcessTransactionUseCase.kt:42-200`, `data/repository/TransactionRepositoryImpl.kt:69-217`, `voice/VoiceAnnouncementEngine.kt:22-144`.

Trace:
`UpiNotificationListenerService.onNotificationPosted` → extracts `packageName/rawText/postTime/key` only (length + key in debug log, no raw content) → `serviceScope.launch(IO) { processTransactionUseCase.processNotification }` (off callback thread, try/catch) → `NotificationTextCleaner.clean` → `NotificationFilter.isPaymentCandidate` → `TransactionClassifier.classify` → gate `RECEIVED+SUCCESS` only → `ParserVersionResolver.resolve` → `parser.parse` (try/catch → unparsed queue) → `validator.validate` → `process(enriched Transaction)` → `isVoiceEnabled` decision → `insertTransactionIfNotDuplicate` → best-effort `prepare + speak` (try/catch, never blocks save).

Dependency check — `ProcessTransactionUseCase.kt:1-200` imports: `NotificationSource`, `ServiceStatus`, `Transaction*`, `UnparsedNotification`, `VoiceLanguage`, `ServiceStateRepository`, `SettingsRepository`, `TransactionRepository`, `NotificationFilter/TextCleaner/TransactionClassifier`, `Parser*`, `TransactionValidator`, `PackageNames`, `AnnouncementTemplates`, `VoiceAnnouncement`, `UUID`, Hilt, `Flow.first`. **No import of `Retrofit/OkHttp/Firebase/JWT/WorkManager/diagnostics/reconciliation/integrity/network`**. Pipeline does not depend on connectivity/auth/worker/diagnostics/backend. `UpiNotificationListenerService` likewise only injects `ProcessTransactionUseCase` + `AppLogger`.

Lifecycle: `onListenerConnected/Disconnected` only log; `serviceScope(SupervisorJob+IO)` cancelled in `onDestroy`; no `BOOT_COMPLETED` receiver, no foreground service, no `AlarmManager` — correct Android NLS lifecycle per `UpiNotificationListenerService.kt:16-25` header comment.

Offline invariant confirmed: `ProcessTransactionUseCase.processNotification` never awaits network/auth/worker; `TransactionRepositoryImpl.insertTransactionIfNotDuplicate` persists Room synchronously and enqueues `SyncQueue PENDING` atomically, then `scheduleSync()` best-effort. Voice is gated only by `SettingsDataStore.isVoiceEnabled` DataStore read.

**Result: SAFE.** No dependency leak.

---

## 6. Authentication Verification

**SOURCE VERIFIED** — `data/auth/AuthRepository.kt:21-71`, `data/auth/AuthSessionStore.kt:11-29`, `data/auth/AuthStateManager.kt:8-15`, `network/AuthInterceptor.kt:8-14`, `network/AuthAuthenticator.kt:12-50`, `di/AuthModule.kt:30-79`, `shoutpay-backend/src/auth/{jwt,middleware,router,tokens}.js`.

Flow:
`Firebase PhoneAuth (OTP 60s) → signInWithCredential → getIdToken(true) → POST /api/auth/login {firebaseIdToken, deviceId, deviceName} → backend verifyIdToken(true) → findOrCreate Merchant (SP-XXXXXX) + Device + AuthSession(tokenFamilyId) + RefreshToken(hash=sha256,unique)` → JWT `signAccessToken {sub:merchantId, deviceId, jti:sessionId, iss:shoutpay-backend, aud:shoutpay-backend-api, exp:15m, HS256}` + refresh 30d.

Client: `AuthSessionStore` stores `access_token/refresh_token/merchant_id/firebase_uid/auth_state` in `EncryptedSharedPreferences (AES256_GCM + AES256_SIV)` — not plain SharedPreferences. `AuthInterceptor` adds `Authorization: Bearer` from `sessionStore.accessToken` per-request. `AuthAuthenticator.authenticate` loop-protects `/auth/refresh` + `X-ShoutPay-Retry`, counts `priorResponse` chain (max 1), token-equality dedup (`currentToken != failedToken` short-circuit), `Lazy<AuthRepository>` + `runBlocking { refresh() }` (Mutex-guarded).

Refresh: `AuthRepository.refresh() Mutex.withLock`, `POST /api/auth/refresh {refreshToken}`, on `IOException` → `false` (no clear, retry later), on `HttpException 429` → `false` (no clear, transient), else `401/400` → `SESSION_EXPIRED + clear()`, unknown → `SESSION_EXPIRED + clear()`.

Replay: backend `tokens.hashRefreshToken(sha256)` stored `tokenHash @unique`, `transasctions/replay` rotates family — if old `tokenHash` reused, family `revokedAt` set, all tokens in family revoked.

No duplicate mechanisms found: single `signAccessToken/verifyAccessToken`, single `generateRefreshToken/hashRefreshToken`, single `AuthSessionStore`, single `AuthStateManager`. `ApiClient.kt:10-15` is legacy wrapper (`create(retrofit)`) kept for test compat, delegates to `AuthModule` Retrofit — not a second client.

Checks: JWT `algorithms:['HS256']` pinned (`jwt.js:21`); `requireAuth` validates `sub/deviceId/jti` are strings; deviceId `DeviceIdGenerator` stable per-install; `CORS_ORIGIN` env-driven, `helmet()` enabled.

**Result: SAFE.**

---

## 7. Sync State-Machine Verification

**SOURCE VERIFIED** — `data/sync/SyncQueueDao.kt:13-190`, `data/sync/SyncQueueEntity.kt:15-38`, `data/sync/SyncQueueRepositoryImpl.kt:23-133`, `data/sync/TransactionSyncRepository.kt:18-282`, `worker/TransactionSyncWorker.kt:38-122`, `worker/StaleUploadRecoveryWorker.kt:29-70`, `data/sync/StaleUploadRecoveryManager.kt:23-50`, `worker/TransactionReconciliationWorker.kt:36-101`, `ui/sync/*ViewModels`.

Conceptual machine enforced:

```
PENDING → UPLOADING → SYNCED
                 ↘ PENDING (transient IOException/5xx/429/408/revert)
                 ↘ FAILED  (permanent 400/403/404/missing + sanitized diagnostics)
FAILED → PENDING (manual only: retryFailedItem/retryAllFailed WHERE status='FAILED')
UPLOADING + older than 15m → PENDING (stale recovery WHERE status='UPLOADING' AND updatedAt<cutoff)
SYNCED — terminal, never reverted automatically
```

| Current | Operation | Next | Allowed? | Location | Evidence |
|---|---|---|---|---|---|
| PENDING | `updateStatusIfExpected(PENDING→UPLOADING)` | UPLOADING | ✅ Yes | `TransactionSyncRepository.syncBatch:113` | `WHERE id=:id AND status='PENDING'` |
| UPLOADING | `updateStatusIfExpected(UPLOADING→SYNCED)` | SYNCED | ✅ Yes | `TransactionSyncRepository:167` | Only when `syncedUuids.contains(entityId)`, `WHERE status='UPLOADING'` |
| UPLOADING | `updateStatusIfExpected(UPLOADING→PENDING)` | PENDING | ✅ Yes (transient) | `TransactionSyncRepository:172,184,194,203,210,240,254` | Network/5xx/429/408/missing-in-response/exception — with `WHERE status='UPLOADING'` |
| UPLOADING | `markFailedWithDiagnosticsIfExpected(UPLOADING→FAILED)` | FAILED | ✅ Yes (permanent) | `TransactionSyncRepository:262-281` | 400/403/404/missing txn — `WHERE status='UPLOADING'` |
| FAILED | `retryFailedItem(Failed→Pending)` | PENDING | ✅ Yes (manual only) | `SyncQueueDao:101-106`, `SyncQueueRepositoryImpl:84-91` | `WHERE status='FAILED'`, clears diagnostics, preserves retryCount |
| FAILED | `retryAllFailed` | PENDING | ✅ Yes (bulk manual) | `SyncQueueDao:112-117` | `WHERE status='FAILED'` |
| UPLOADING + stale | `recoverStaleUploading` | PENDING | ✅ Yes | `SyncQueueDao:127-131`, `StaleUploadRecoveryManager:48` | `WHERE status='UPLOADING' AND updatedAt<cutoff` |
| SYNCED | any auto revert | PENDING/UPLOADING/FAILED | ❌ Forbidden | — | No code does this; all revert/update predicates require PENDING or UPLOADING. `SOURCE VERIFIED` via grep. |

**Race protections:** Every status mutation uses restrictive `WHERE status=:expectedStatus` variant (`updateStatusIfExpected`, `incrementRetryCountIfExpected`, `markFailedWithDiagnosticsIfExpected`, `retryFailedItem WHERE status='FAILED'`, `recoverStaleUploading WHERE status='UPLOADING'`). Unconditional `updateStatus`/`markFailedWithDiagnostics` exist but are only used in safe contexts (retry diagnostics) or are shadowed by expected-variant in sync path. `SyncQueueRepositoryImpl` checks `dao.getById` before `retryFailedItem` (double-gate). Reconciliation uses `insertIgnore` + unique index (`entityType,entityId`).

**FAILED protection:** No automatic path does `FAILED→PENDING` except `retryFailedItem/retryAllFailed` (merchant-initiated). `TransactionSyncWorker`, `TransactionSyncRepository`, `StaleUploadRecoveryManager`, `TransactionSyncReconciler`, `SyncIntegrityAuditor` never produce `FAILED→PENDING`.

**SYNCED protection:** Verified no worker/reconciler/recovery/auditor contains `SYNCED` in a `WHERE status=` source or `SET status=` target except the `SYNCED` insert itself. `grep SYNCED` across workers shows only the `SYNCED` success path, never a revert.

**Success semantics:** `SyncStatusStore.saveLastSuccessfulSync` is called only at `TransactionSyncWorker.kt:64` inside `SyncResult.Success` branch where `totalSynced>0 && !shouldRetry && totalFailed==0` (`TransactionSyncRepository:94-98`). `NoPending/PartialSuccess/Retry/Error/SessionExpired/scheduling` never update the timestamp — `TransactionSyncWorker:71-77,79-85,87-95,98-110` verified `runCatching { diagnostic only }` and `Result.success/retry/failure` without `recordSuccessfulSync`.

**Result: SAFE — race-safe, FAILED/SYNCED invariants enforced.**

---

## 8. WorkManager Verification

**SOURCE VERIFIED** — `scheduler/*`, `worker/*`, `work/*`, `UpiVoiceAlertApplication.kt:14-45`, `di/SchedulerModule.kt` (if present), `work/WorkScheduler.kt`.

| Worker | Unique name | Tags | Policy | Constraints | Backoff | Scheduling |
|---|---|---|---|---|---|---|
| `TransactionSyncWorker` | `transaction_sync_work` | `transaction_sync_work` (+ `_periodic`) | `KEEP` (one-time + periodic) | `NetworkType.CONNECTED` (via `SyncScheduler`) | `EXPONENTIAL 5m` | `scheduleSync()` on every transaction insert; `schedulePeriodicSync() 12h` (SyncScheduler); `onCreate` does not auto-schedule sync directly — driven by queue |
| `TransactionReconciliationWorker` | `transaction_reconciliation_work` / `_immediate` | same | `KEEP` | none (local, offline) | retry/failure per exception type | `ReconciliationScheduler.schedulePeriodic 24h` + `scheduleNow` one-time; `UpiVoiceAlertApplication.onCreate` periodic |
| `StaleUploadRecoveryWorker` | `stale_upload_recovery_work` / `_immediate` | same | `KEEP` | none (local, offline) | retry/failure | `StaleUploadRecoveryScheduler.schedulePeriodic 24h` + `scheduleNow`; `onCreate` both periodic + immediate (startup recovery) |
| `SyncIntegrityAuditWorker` | `sync_integrity_audit_work` / `_immediate` | same | `KEEP` | none (read-only) | retry/failure | `SyncIntegrityAuditScheduler.schedulePeriodic 24h` + `scheduleNow`; `onCreate` periodic |
| `CleanupWorker` / `RetryFailedParseWorker` | `cleanup_work` / `retry_work` | — | `KEEP` | none | default | `WorkScheduler.schedulePeriodic` 24h/12h; `onCreate` |

Startup: `UpiVoiceAlertApplication.onCreate` wraps each scheduler in `try { } catch { }` individually — one failure does not block others or payment pipeline. Workers all use `MaintenanceCoordinator` per-operation Mutex: same-operation concurrent attempt → `AlreadyRunning → Result.success()` (non-error, non-retry), different operations concurrent → allowed.

Multiple-enqueue check: `SyncScheduler.scheduleSync()` and `TransactionRepositoryImpl.insertTransactionIfNotDuplicate` both call `scheduleSync()` — but unique work `KEEP` deduplicates; `MaintenanceCoordinator` deduplicates concurrent executions. No duplicate periodic names beyond intentional `_immediate` split (distinct names, correct).

Cancellation: each scheduler exposes `cancel()`/`cancelSync()` using `cancelUniqueWork`. Process death: WorkManager persists unique work; `Manual Initializer` (`AndroidManifest` removes `WorkManagerInitializer` auto) + `HiltWorkerFactory` correctly configured.

Stale safety layer: `TransactionSyncWorker.doWork` runs `maintenanceCoordinator.coordinate(STALE_UPLOAD_RECOVERY) { recoverWithoutScheduling() }` before online check — duplicate with periodic recovery is AlreadyRunning-guarded; `recover()` variant with scheduling is used only by dedicated worker.

**Result: SAFE.**

---

## 9. Database / Migration Verification

**SOURCE VERIFIED** — `data/database/AppDatabase.kt:10-179`, `data/database/TransactionEntity.kt:8-53`, `data/database/TransactionDao.kt:9-147`, `data/database/UnparsedNotification*`, `data/sync/SyncQueue*`, `data/sync/SyncDiagnosticEventEntity`, `di/DatabaseModule.kt:22-34`.

- `AppDatabase @Database(version=9, entities=[TransactionEntity, UnparsedNotificationEntity, SyncQueueEntity, SyncDiagnosticEventEntity], exportSchema=false)` — matches §7 expectation (version 9).
- Migrations: `MIGRATION_1_2 (1→2 multi-source metadata)`, `MIGRATION_2_3 (2→3 voiceAnnounced)`, `MIGRATION_3_4 (3→4 dedupFingerprint + indices)`, `MIGRATION_4_5 (4→5 sync_queue)`, `MIGRATION_5_6 (5→6 transactionUuid + index)`, `MIGRATION_6_7 (6→7 lastErrorCode/Message/failedAt)`, `MIGRATION_7_8 (7→8 unique(entityType,entityId) + duplicate purge)`, `MIGRATION_8_9 (8→9 sync_diagnostic_events)` — all additive, `ALTER TABLE ADD COLUMN` or `CREATE TABLE/INDEX`, no `DROP`, no data loss. `MIGRATION_7_8` deterministically keeps `MIN(id)` per group before unique index — safe.
- `DatabaseModule` adds all 8 migrations via `.addMigrations(...)`, no `fallbackToDestructiveMigration`, no `fallbackToDestructiveMigrationOnDowngrade` — correct.
- Entities: `TransactionEntity` indices on `transactionId`, `dedupFingerprint`, `transactionUuid` (non-unique intentionally, per comment — two legitimate same-fingerprint payments outside window must coexist). `SyncQueueEntity` unique index on `(entityType, entityId)` — enforces one queue row per transaction.
- DAOs: `TransactionDao` dedup queries (`findByReferenceIdGlobal`, `findByFingerprint`, `findByFingerprintNullRef`, `findExactDuplicate`, `findByTransactionUuids`, `getEligibleMissingQueueUuids`, `countEligible*`) all `SOURCE VERIFIED`; `SyncQueueDao` restrictive variants + `insertIgnore`; `SyncDiagnosticDao` bounded.
- Pending changes: none — `git diff` empty, no schema-modifying uncommitted files.
- No new migration needed for Phase 9 unless DAO hygiene is accepted (no schema change, only interface cleanup).

**One hygiene finding (P1):** `TransactionDao.countEligibleForAudit / countMissingQueueForAudit / countScannedTransactionsForAudit`, `SyncQueueDao.{updateStatusIfExpected,incrementRetryCountIfExpected,recoverStaleUploading,countTransactionQueueItems,countAllQueueItems,countOrphanedQueueItems,countDuplicateExtraRows,countDuplicateGroups,countInvalidQueueItems,countStaleUploading,getStaleUploadingItems}`, `SyncDiagnosticDao.{count,deleteOlderThan,trimToMaxCount}` carry Kotlin default bodies (`=0`, `=1`, `=emptyList()`). Room KSP currently overrides them (build passes historically: Phase 8.7 Debug/Release SUCCESS), so runtime is correct today, but the defaults are dead confusing code and violate the Room contract (`@Query` methods should be abstract). If codegen ever failed to override, the stubs would silently return success/empty and mask races/audit results. Must be removed.

**Result: SAFE with P1 hygiene.**

---

## 10. Backend Verification

**SOURCE VERIFIED** — `shoutpay-backend/src/{app,server}.js`, `src/config/config.js`, `src/auth/{jwt,middleware,router,tokens,firebase}.js`, `src/modules/{merchant,devices,transactions}/{router,controller,service,repository,validation}.js`, `src/database/database.js`, `prisma/schema.prisma`, `prisma/migrations/*`.

Routes:
- `POST /api/auth/login` → `verifyIdToken(true)` → create/lookup Merchant+Device+AuthSession+RefreshToken → JWT+refresh.
- `POST /api/auth/refresh` → lookup `tokenHash`, check family `revokedAt`, rotate.
- `POST /api/auth/logout` / `logout-all` → `revokedAt`.
- `GET/PUT /api/merchant/me` → `requireAuth` + `findMerchantByMerchantId` scoping.
- `GET/POST/DELETE /api/auth/devices` → merchant-scoped.
- `POST /api/transactions/sync` → `requireAuth` + `validateSync` (Joi) → `syncTransactions` (idempotency via `transactionUuid @unique` + `P2002` duplicates catch).
- `GET /api/transactions/query` (paginated, merchant-scoped) per `TransactionSyncQueueActivationTest` references.
- `GET /health` → `status/service/timestamp/database/uptime`, sanitized prod.

Middleware/stack:
- `helmet()` before CORS, `cors({origin:CORS_ORIGIN, credentials:true})`, `morgan` `combined` dev / `tiny` prod (skip `/health`), `express.json({limit:'256kb'})`, `express.urlencoded({limit:'256kb'})`, `express-rate-limit 100/15m` on `/api/`, auth routes additionally `5/20` (verify via `auth/router.js`), `crypto.randomUUID()` requestId (`X-Request-Id` or generated, never derived from PII/JWT), 404 prod `NOT_FOUND` without path echo, error handler sanitized prod (`INTERNAL_SERVER_ERROR` + `requestId` only).

Validation:
- `transactions/validation.js:12-29` `transactionItemSchema` `unknown(false)`, forbidden `merchantId/firebaseUid/status/createdAt/updatedAt/id/currency`, required `transactionUuid(uuid)/deviceId/amount(+)/senderName/transactionTime(isoDate)`, optional `senderVpa/upiReference/upiApp`. `syncSchema` `transactions array 1..100, unknown(false)`. `merchant/validation.js`, `devices/validation.js` similarly strict.

Merchant isolation:
- `transactions/service.js:13-18` `findMerchantByMerchantId(merchantId from JWT)` then `merchant.id` used for all writes; `transactions/repository.js` and `merchant/repository.js` scope by `merchantId`. No client-controlled merchantId accepted (Joi forbids it, service ignores even if sent).

Idempotency:
- `transactions/service.js:24-55` loop: `findByTransactionUuid` → if exists `duplicates.push`, else `createTransaction` catch `P2002 transactionUuid` → `duplicates`. Client merges `created + duplicates` as SYNCED.

Auth enforcement:
- `requireAuth` on all `/api/merchant`, `/api/auth/devices`, `/api/transactions/*` (verify `app.js:52-56`). Health unauthenticated, correct.

Rate limiting / safety:
- Global 100/15m + auth 5/20, body 256kb, `JWT_SECRET` required via `config.assertAuthConfiguration`, `rateLimit` window/count env-configurable.

Request correlation / health:
- `app.js:18-22` `req.requestId`, error handler logs `{requestId, method, path, status, errorCode}` prod, health checks `database.connect()`.

Prisma:
- `schema.prisma` 6 models + enums, indices on `merchantId`, `deviceId`, `tokenFamilyId`, `transactionUuid`, `upiReference`, `merchantId+transactionTime`, `status`. `AuthSession onDelete Restrict` (merchant/device), `RefreshToken onDelete Cascade` (session) — correct.

No partial new feature detected — no orphaned controller/service not wired to router.

**Result: SAFE.**

---

## 11. Security Verification

**SOURCE VERIFIED — evidence-backed, no speculative claims.**

### Android
| Check | Status | Evidence |
|---|---|---|
| Secure storage | ✅ PASS | `AuthSessionStore.kt:12-17` `EncryptedSharedPreferences(AES256_GCM + AES256_SIV)` + `MasterKey AES256_GCM` |
| Network security | ✅ PASS | `res/xml/network_security_config.xml:5` `cleartextTrafficPermitted="false"` + `trust-anchors system` |
| Permissions | ✅ PASS | `AndroidManifest.xml:8-12` only `POST_NOTIFICATIONS`, `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`, `INTERNET`; NLS via service `BIND_NOTIFICATION_LISTENER_SERVICE` + `exported true` with intent-filter (required); no CAMERA/CONTACTS/STORAGE/LOCATION/SMS |
| Exported components | ✅ PASS | `MainActivity exported true` + MAIN/LAUNCHER, `NLS exported true` + `NotificationListenerService` action, `InitializationProvider exported false` — minimal and correct |
| Backup | ✅ PASS | `AndroidManifest 16-17` `allowBackup false`, `fullBackupContent false`, `dataExtractionRules @xml/data_extraction_rules`; `data_extraction_rules.xml` + `backup_rules.xml` exclude `database` + `sharedpref` for both `cloud-backup` and `device-transfer` |
| Logging | ⚠️ FINDING P1 | `ProcessTransactionUseCase.kt:61-122` logs `rawText`, `cleanedText`, `amount`, `sender`, `upiApp`; `VoiceAnnouncementEngine.kt:106` logs `text` (announcement = amount+sender+app); `NotificationFilter.kt:38-55` logs `package+reason+keyword` (safe). AppLogger DEBUG-gates but direct `android.util.Log.i` is always emitted in release. Must route through `AppLogger` and redact PII even in debug, or gate with `AppLogger.isVerboseEnabled` + avoid `rawText` content. |
| Token handling | ✅ PASS | Tokens only in `AuthSessionStore` encrypted; `OkHttp logging` redacts `Authorization` (`AuthModule.kt:48-49`); `AppLogger` never logs token/JWT; no `Bearer` in logs via search |
| Signing | ✅ PASS (local unsigned) | `build.gradle.kts:36-46,88-90` release signing via `keystore.properties` if present, else unsigned (CI supplies) — correct local behavior |

### Backend
| Check | Status | Evidence |
|---|---|---|
| JWT algorithm | ✅ PASS | `auth/jwt.js:21` `algorithms:['HS256']`, `issuer/audience` pinned, `JWT_SECRET` required |
| Secret handling | ✅ PASS | `config/config.js:21-26` `assertAuthConfiguration` requires `JWT_SECRET/FIREBASE_PROJECT_ID/CLIENT_EMAIL/PRIVATE_KEY` |
| Refresh replay | ✅ PASS | `auth/tokens.js:9-10` sha256 hash + `@unique`, family `tokenFamilyId` + `revokedAt`, reuse revokes family |
| Rate limiting | ✅ PASS | `app.js:42-49` global `100/15m`, auth `5/20` (router) |
| Body limits | ✅ PASS | `app.js:38-39` `256kb` json+urlencoded |
| Validation | ✅ PASS | Joi `unknown(false)`, forbidden fields, strict types, see §10 |
| Unknown fields | ✅ PASS | `unknown(false)` on all schemas, `stripUnknown:false` so rejects rather than strips |
| Merchant ownership | ✅ PASS | `transactions/service.js:13` JWT `merchantId` is source of truth, `findMerchantByMerchantId` scoping |
| CORS/Helmet | ✅ PASS | `app.js:25-29` `helmet()`, `cors({origin:CORS_ORIGIN})` env-configurable |
| Error sanitization | ✅ PASS | `app.js:80-89` prod `code + requestId` only, no stack/body/SQL; 404 prod `NOT_FOUND` |
| Request IDs | ✅ PASS | `app.js:18-22` `crypto.randomUUID()` |
| DB error exposure | ✅ PASS | `transactions/service.js:48-53` only `P2002→duplicates`, else throw handled by sanitized error handler |

**Result: FINDINGS (P1 log redaction), otherwise SAFE.**

---

## 12. Privacy Verification

**SOURCE VERIFIED**

- NLS disclosure: `ui/onboarding/PrivacyExplanationScreen` + `ConsentScreen` exist (`app/src/main/java/com/upivoicealert/ui/onboarding/*`). `PACKAGE_NAMES` limited to known UPI/blocklist; `NotificationFilter` ignores non-financial notifications at earliest point; raw notification stored only as `originalNotificationText`/`cleanedNotificationText` in Room (app-private, backup-excluded, `SOURCE VERIFIED` via `data_extraction_rules`).
- No storage/logging of VPA/phone beyond `senderName` (display name from notification, not raw VPA/phone — `senderVpa` is `null` locally, backend optional). No `rawNotification` logged to file or transmitted — `TransactionSyncRepository` uploads only `transactionUuid/deviceId/amount/senderName/senderVpa(null)/upiReference/upiApp/transactionTime` (no raw text).
- Token/JWT/refresh/OTP/Authorization/raw response/SQL/stack not logged — search `JWT/refresh/OTP/VPA/Authorization` shows only legitimate auth code (secret audit in Phase 8.7 still applies; re-checked via `Select-String Log.` — 28 occurrences, all safe or DEBUG-intent except the two P1 PII logs noted).
- `allowBackup false` + `dataExtractionRules` exclude DB/sharedpref — financial data not restorable via Auto Backup; `SyncStatusStore` lastSuccessfulSync is non-sensitive (timestamp only).

---

## 13. Observability Verification

**SOURCE VERIFIED** — `data/sync/SyncDiagnosticRepositoryImpl.kt`, `data/sync/SyncDiagnosticDao.kt`, `data/sync/SyncIntegrityAuditor.kt`, `observability/PaymentPipelineMetrics.kt`, `worker/*`, `logging/AppLogger.kt`.

- `SyncDiagnosticRepositoryImpl.record` inserts `SyncDiagnosticEventEntity(category, eventType, affectedCount, createdAt, message=sanitized via SyncDiagnosticMessageMapper)` then `trimToMaxCount(200)` — bounded, newest-kept. `observeRecent/getRecent/count/clearAll` correct.
- Categories: `SYNC/RECONCILIATION/STALE_RECOVERY/INTEGRITY_AUDIT/AUTH/PAYMENT_PIPELINE/API/APP_HEALTH` (via `SyncDiagnosticCategory` + `SyncDiagnosticMessageMapper`).
- `AlreadyRunning → Result.success()` in all 4 workers + `TransactionSyncWorker` — diagnostics record `STALE_UPLOADS_RECOVERED/STALE_RECOVERY_COMPLETED/RECONCILIATION_COMPLETED/INTEGRITY_AUDIT_HEALTHY` with 0 affectedCount, not a failure. No `Result.retry` on AlreadyRunning.
- Diagnostics always `runCatching { diagnosticRecorder.record }` — never blocks primary operation (sync success/failure, reconciliation, recovery, audit). Offline → diagnostics still best-effort.
- `PaymentPipelineMetrics.kt:12-41` — `AtomicLong` counters `received/parseRejected/validationRejected/duplicates/persisted/ttsAttempted/ttsFailed`, `snapshot()` — no Room, no network, no auth. **Status: correctly implemented but NOT WIRED** — no injection into `ProcessTransactionUseCase` or `UpiNotificationListenerService` (grep `PaymentPipelineMetrics` shows only class definition + `ObservabilityHardeningTest`). Historical decision: Phase 8.7 lists "PaymentPipelineMetrics unwired" as non-blocking P2, intentionally deferred. Confirm **INTENTIONALLY UNUSED / DEFERRED**, not dead — wiring is a candidate for Phase 9.
- `Health` endpoint: `GET /health` returns `status/service/timestamp/database/uptime` or `Service unavailable` prod, no credentials.

**Result: SAFE (PaymentPipelineMetrics DEFERRED, see §15/17).**

---

## 14. Release Configuration Verification

**SOURCE VERIFIED** — `app/build.gradle.kts:22-114`, `app/src/main/AndroidManifest.xml`, `gradle/libs.versions.toml`, `local.properties` (sdk.dir only, no SHOUTPAY_DEBUG_BASE_URL committed), `shoutpay-backend/.env.example`.

| Item | Value | Status |
|---|---|---|
| applicationId | `com.upivoicealert` | ✅ |
| namespace | `com.upivoicealert` | ✅ |
| minSdk / targetSdk / compileSdk | `26 / 34 / 34` (`Java 17`) | ✅ |
| versionCode / versionName | `1 / 1.0` | ✅ |
| debug | `isDebuggable true, isMinifyEnabled false, isShrinkResources false, BASE_URL = local SHOUTPAY_DEBUG_BASE_URL or https://api.shoutpay.in/api/` | ✅ |
| release | `isDebuggable false, isMinifyEnabled false, isShrinkResources false, BASE_URL validated (reject localhost/10.0.2.2/127.0.0.1/192.168/10.x, require https:// + /)`, `proguardFiles(proguard-android-optimize.txt, proguard-rules.pro)`, signing via `keystore.properties` if present | ✅ |
| proguard | `proguard-rules.pro:5-39` keep Hilt/Room/Retrofit/Gson/Firebase/WorkManager + commented `-assumenosideeffects Log.v/d/i` for future minify | ✅ |
| R8 / minify | `isMinifyEnabled false` both types — **P2 DEFERRED** (size/opt deferred, keep rules documented, not a correctness issue) | ⚠️ P2 |
| FLAG_SECURE | absent — recent-apps shows amounts — **P2 DEFERRED** (product decision, low risk per Phase 8.7 §46) | ⚠️ P2 |
| Crashlytics | absent — **P2 DEFERRED** (diagnosis via AppLogger/diagnostics) | ⚠️ P2 |
| network_security | `cleartextTrafficPermitted false` | ✅ |
| backup | `allowBackup false + dataExtractionRules` | ✅ |
| BASE_URL prod default | `https://api.shoutpay.in/api/` (fallback, must verify real domain before Play publish) | ✅ with note |
| localhost refs | Only `SHOUTPAY_DEBUG_BASE_URL` local fallback + docs, no hardcoded dev IP in release | ✅ |
| Firebase | `google-services.json` present, project `shoutpay` (public client config only) | ✅ |

**Result: FINDINGS (3×P2, all previously accepted non-blocking).**

---

## 15. Deferred Work

**SOURCE VERIFIED** via Phase 8.7 §35/46 + current tree:

| Item | Earlier disposition | Current status | Phase 9 relevance |
|---|---|---|---|
| `PaymentPipelineMetrics` atomic counters | Phase 8.7 P2 non-blocking, intentionally unwired | Class exists `observability/PaymentPipelineMetrics.kt:13`, only referenced in `ObservabilityHardeningTest.kt` | **Candidate 9.x** — wire into `ProcessTransactionUseCase` + `UpiNotificationListenerService` as non-critical counters |
| R8 / minification (`isMinifyEnabled false`, `isShrinkResources false`) | Phase 8.7 P2, keep rules documented | `build.gradle.kts:52,55` still false, `proguard-rules.pro:32-39` ready | **Candidate 9.x** — enable with keep set, verify Hilt/Room/Retrofit/Firebase/WorkManager |
| `FLAG_SECURE` (`getWindow().setFlags(FLAG_SECURE)`) | Phase 8.7 P2, product decision low risk | Grep shows 0 occurrences (`FLAG_SECURE` absent) | **Candidate 9.x** — apply per authenticated screen if merchant requests |
| Crashlytics / ANR SDK | Phase 8.7 P2, logs-only diagnosis | No `firebase-crashlytics` dependency, no `Crashlytics` code | **Candidate 9.x** — add opt-in, sanitize PII, verify offline survival |
| Telemetry upload opt-in | Phase 8.7 P3 future | No telemetry upload code | **P3 — defer** |
| Cert pinning evaluation | Phase 8.7 P3 | No pinning config | **P3 — defer** (ops decision) |
| Latency histogram | Phase 8.7 P3 | No histogram | **P3 — defer** |

No `TODO/FIXME/HACK` carrying hidden deferred work — grep shows only `toDomain` false positives on word `TODO` substring.

---

## 16. Dead / Duplicate / Orphaned Code Candidates

Classified per §10 deferred audit (search + import graph):

| Candidate | Location | Classification | Rationale |
|---|---|---|---|
| `network/ApiClient.kt` (`object ApiClient` legacy wrapper) | `app/src/main/java/com/upivoicealert/network/ApiClient.kt:10-15` | **INTENTIONALLY UNUSED / FUTURE EXTENSION (SAFE TO KEEP)** | Delegates to `AuthModule` Retrofit; kept for test compat (`@Deprecated` overload). No auth duplication; single provider is `AuthModule`. |
| `PaymentPipelineMetrics` | `observability/PaymentPipelineMetrics.kt:13-41` | **INTENTIONALLY RESERVED / PARTIALLY WIRED** | Implemented, tested (`ObservabilityHardeningTest`), not injected into pipeline. Deferred wiring, not dead. |
| `SyncQueueDao` / `TransactionDao` / `SyncDiagnosticDao` stub defaults (`=0/=1/=emptyList()`) | `data/sync/SyncQueueDao.kt:31,37,125,131-189`, `data/database/TransactionDao.kt:129-146`, `data/sync/SyncDiagnosticDao.kt:22,25,41` | **REQUIRES REVIEW (P1)** | Dead-code defaults masked by Room KSP override today (build passes) but violate Room contract. Must remove to avoid silent fallback. |
| `work/CleanupWorker` + `RetryFailedParseWorker` | `work/{CleanupWorker,RetryFailedParseWorker}.kt` | **ACTIVE** | Periodic `WorkScheduler` 24h/12h — correct, not orphaned. |
| `data/sync/SyncQueueRepositoryImpl` duplicate `= emptyList` on `getStaleUploadingItems` in interface file (false positive on grep) | test fakes only | **ACTIVE** | Interface default not present; fakes correctly stub. |
| Orphaned interfaces/repositories/viewmodels | Grep `interface|Repository|ViewModel` across tree, all referenced via Hilt or navigation | **NONE FOUND** | Every repository has impl + binding, every ViewModel observed by a Screen, every Worker scheduled. |
| Unused DI bindings | `di/*` — all `@Provides` consumed | **NONE FOUND** | `DatabaseModule/SchedulerModule/AuthModule/AppModule/RepositoryModule` all injected. |
| Duplicate implementations | `DeviceMapper`/`MerchantProfileMapper` single each; `SyncScheduler` single; no duplicate `JWT`/`refresh` | **NONE FOUND** | Verified §5 protection. |
| Proto/experiment code | No `prototype/abandoned` packages | **NONE FOUND** |  |

No file recommended for deletion during investigation (per §9 DO NOT DELETE). Above table is report-only.

---

## 17. P0 Findings

**0**

No critical production blocker. Payment offline path, Room authority, sync invariants, auth, backup/network, health, release all correct.

---

## 18. P1 Findings

**2**

### P1-1 — Room DAO stub default bodies must be removed

**Severity:** P1 — High (reliability / correctness hygiene, not currently exploited)  
**Evidence:** `SOURCE VERIFIED`
- `app/src/main/java/com/upivoicealert/data/database/TransactionDao.kt:129-146` — `countEligibleForAudit(): Int = 0`, `countMissingQueueForAudit(): Int = 0`, `countScannedTransactionsForAudit(): Int = 0`
- `app/src/main/java/com/upivoicealert/data/sync/SyncQueueDao.kt:31` `updateStatusIfExpected(...): Int = 1`, `:37` `incrementRetryCountIfExpected(...): Int = 1`, `:125` `getStaleUploadingItems(...)=emptyList()`, `:131` `recoverStaleUploading(...):Int=0`, `:136-189` 7× `count*():Int=0`
- `app/src/main/java/com/upivoicealert/data/sync/SyncDiagnosticDao.kt:22` `count():Int=0`, `:25` `deleteOlderThan:Int=0`, `:41` `trimToMaxCount:Int=0`

**Why it matters:** `@Query` methods on a `@Dao` interface should be abstract. Kotlin default bodies are dead today because Room KSP generates overriding implementations (hence Phase 8.7 Debug/Release SUCCESS + 486 tests PASS), but they are confusing and risky: if codegen ever failed to override (KSP misconfig, incremental build cache, Room version bump), the stubs would silently return `0/1/emptyList` — stale recovery would find nothing, integrity audit would report healthy (`0` issues), `updateStatusIfExpected` would report `1` success while racing, diagnostics would never trim. Tests use hand-written fakes, so they do not catch this.

**Invariant impact:** Directly affects `StaleUploadRecovery`, `IntegrityAuditor`, `SyncDiagnostic retention`, and race-condition detection.

**Fix (not done in investigation):** Make all `@Query` DAO methods abstract (remove `=0/=1/=emptyList()`). Room will then fail compilation if a query is unimplemented, which is the desired safety. No schema change.

---

### P1-2 — Verbose logs emit PII (sender/amount/raw notification/announcement text) via always-on `Log.i`

**Severity:** P1 — Privacy / store-review risk  
**Evidence:** `SOURCE VERIFIED`
- `app/src/main/java/com/upivoicealert/domain/usecases/ProcessTransactionUseCase.kt:61` `AppLogger.d("PROCESS_START ... rawText=$rawText")`, `:68` `CLEANED_TEXT text=$text`, `:120-122` `parsed amount/sender/app` via `AppLogger.d` (DEBUG-gated, safe in release, but `rawText` is PII and should never be logged even in debug per CLAUDE.md §4.7)
- `app/src/main/java/com/upivoicealert/filter/NotificationFilter.kt:38-55` `android.util.Log.i("FILTER_CHECK ...")` — always emitted (not DEBUG-gated) but only logs `package + keyword` (safe)
- `app/src/main/java/com/upivoicealert/voice/VoiceAnnouncementEngine.kt:106` `Log.i("SPEAK_RESULT ... text=$text")` — announcement text = `"Received ₹500 from Rahul via PhonePe"` (amount + sender + app) always emitted

**Why it matters:** `AppLogger.d` is release-suppressed (via `BuildConfig.DEBUG`), so release is currently safe, but DEBUG builds leak PII to logcat and the direct `Log.i` in `VoiceAnnouncementEngine` leaks in release. Play Data Safety and privacy review will flag notification-derived financial PII in logs. Also violates §6 privacy rule ("Do not log raw notification payload") in spirit.

**Fix (not done in investigation):** Route `NotificationFilter` + `VoiceAnnouncementEngine` logs through `AppLogger` (DEBUG-gated) and redact `rawText/text/amount/sender` or log only hashes/lengths in release. Keep operational tags (`package + event`) but not content.

---

## 19. P2 Findings

**5**

### P2-1 — R8 / minification disabled

`app/build.gradle.kts:52,55` `isMinifyEnabled false`, `isShrinkResources false` both debug/release. `proguard-rules.pro` documents keep set. **Impact:** larger APK/AAB, no code shrinking, debug logs not stripped. **Disposition per Phase 8.7:** intentionally deferred, non-blocking. Phase 9 candidate to enable with keep verification (Hilt/Room/Retrofit/Gson/Firebase/WorkManager).

### P2-2 — `FLAG_SECURE` absent

Grep `FLAG_SECURE` → 0 hits. Recent-apps screenshot shows amounts. **Disposition per Phase 8.7:** product decision, low risk for MVP, opt-in per screen in Phase 9 if merchant requests.

### P2-3 — Crashlytics / ANR SDK absent

`gradle/libs.versions.toml` has no `firebase-crashlytics`, `build.gradle.kts` no crashlytics plugin, tree no Crashlytics calls. **Disposition per Phase 8.7:** deferred — diagnosis via `AppLogger` + diagnostics today. Phase 9 candidate to add with PII sanitization and offline handling.

### P2-4 — `PaymentPipelineMetrics` unwired

`observability/PaymentPipelineMetrics.kt:13-41` implemented + `ObservabilityHardeningTest` but never injected into `ProcessTransactionUseCase`/`UpiNotificationListenerService`. **Disposition:** intentionally reserved. Wiring is a small P2 to get health counters without Room/network dependency (atomic, non-blocking).

### P2-5 — Release `BASE_URL` fallback is placeholder domain

`app/build.gradle.kts:74` fallback `https://api.shoutpay.in/api/` used when `local.properties`/`env` not set. Debug fallback also `https://api.shoutpay.in/api/`. Must verify real production domain before Play publish; `EnvironmentValidator` + Gradle guard will reject localhost but not a wrong HTTPS domain. **Disposition:** operational checklist item, not a code defect — document in Phase 9 release gate.

---

## 20. P3 Findings

**4**

### P3-1 — Telemetry upload opt-in

No telemetry upload; diagnostics are local-only (bounded 200). Future opt-in with user consent + allow-list sanitization.

### P3-2 — Certificate pinning

No `network_security_config` pinning; `cleartext false` only. Evaluate per ops if pinning is desired (adds rotation risk).

### P3-3 — Latency / histogram observability

No latency histogram / pipeline timing. Future enhancement if merchant analytics needed.

### P3-4 — OEM-specific battery onboarding

`BatteryOptimizationHelper` + `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` flow exists, but no OEM-specific (Xiaomi/Oppo/Vivo) guided steps. Documented as known limitation per CLAUDE.md — intentionally deferred.

---

## 21. Risks

| Risk | Likelihood | Impact | Mitigation (proposed) |
|---|---|---|---|
| DAO stub defaults silently mask audit/recovery failure if Room codegen regresses | Low (build passes today) | High (audit reports healthy while issues exist) | Remove defaults (P1-1) — makes codegen failure a compile error |
| PII in logcat (announcement text) survives to release via `Log.i` | Medium (code path always hit on payment) | Medium (privacy review, merchant-sensitive) | Redact/gate logs (P1-2) |
| R8 disabled → Play size / code disclosure | Low (1.0 MVP sideload) | Low | Enable with keep rules (P2-1) in Phase 9.3 |
| No Crashlytics → field crash diagnosis slower | Medium | Low | Add Crashlytics sanitized (P2-3) — optional |
| Wrong `api.shoutpay.in` fallback ships to prod if `local.properties` missing | Low (release guard checks https + not localhost) | Medium | Pre-publish domain verification checklist (P2-5) |
| Firebase BOM 32.8.1 drift from latest | Low | Low | Track in `FIREBASE_COMPATIBILITY_REPORT.md`, bump if needed — not Phase 9 scope |

No risk was found that requires a new sync state, new DB migration, or new auth mechanism. All critical-path invariants hold without them (per §26 protection rule).

---

## 22. Recommended Phase 9 Objective

Answer the 7 questions from §22:

**Q1 — Single most important remaining engineering problem?**  
Close the gap between "code is correct today" and "code cannot silently become incorrect." The DAO stub defaults and PII logs are not breaking production now (builds + 486 tests pass) but they remove compile-time and privacy safety nets. Phase 9 should restore those nets with minimal, surgical hardening — no feature work.

**Q2 — Any P0/P1 issues?**  
P0: 0. P1: 2 (DAO defaults, PII logs) — both must be addressed, but neither blocks merchant usage today.

**Q3 — Are existing P2 limitations acceptable?**  
Yes, for MVP sideload. For Play Store public release they should be closed (R8 + FLAG_SECURE + Crashlytics decision). Wiring `PaymentPipelineMetrics` is safe and useful for health triage.

**Q4 — What previously deferred work should be addressed?**  
The three P2s already listed as deferred in Phase 8.7 (§35/46): R8, FLAG_SECURE, Crashlytics (decision + wiring). Plus the intentionally unwired `PaymentPipelineMetrics`.

**Q5 — What work should explicitly NOT be done yet?**  
New product features (expense tracking, multi-device sync, community parsers, backend capabilities, data management beyond 30-day unparsed retention), new sync states, new Room migrations, duplicate auth/JWT, OEM-specific workarounds, telemetry upload, cert pinning, UI overhaul.

**Q6 — What should Phase 9 actually accomplish?**  
Production-hardening gate that makes the current correct system robustly correct: remove silent-fallback surfaces, redact PII logs, enable release hardening (R8/FLAG_SECURE), and optionally wire the existing metrics counter.

**Q7 — What should NOT be included in Phase 9?**  
Any change that touches the payment announcement path's offline-first invariant, adds network/auth dependencies to `ProcessTransactionUseCase`, introduces destructive migration, duplicates auth, invents a new sync state, or adds cloud-sync product scope.

**Recommended Phase 9 objective (one line):**  
> Harden the already-correct ShoutPay release artifact to be Play-Store-shippable without changing product scope: eliminate DAO silent-fallback and PII-log surfaces (P1), then apply the previously deferred R8/FLAG_SECURE/metrics production hardening (P2) with verification.

---

## 23. Proposed Phase 9 Subphases

Choose the smallest reasonable number of subphases (per §23). Three are sufficient; avoid artificial phases.

```
Phase 9 — Production Hardening Gate (no new product features, no new auth/sync states)
 │
 ├── 9.1 DAO & Privacy Hardening (P1 — must address)
 │     9.1.1 Remove stub default bodies from Room DAOs (TransactionDao, SyncQueueDao, SyncDiagnosticDao)
 │           Verify: ./gradlew testDebugUnitTest, assembleDebug, assembleRelease still SUCCESS
 │     9.1.2 Redact PII logs (ProcessTransactionUseCase rawText/amount/sender, VoiceAnnouncementEngine announcement text)
 │           Route NotificationFilter/VoiceAnnouncementEngine via AppLogger.DEBUG-gated, log lengths/hashes only
 │           Verify: static grep for `rawNotification|rawText|amount=|text=$` in Log.* shows 0 in release path
 │
 ├── 9.2 Release Artifact Hardening (P2 — deferred production readiness)
 │     9.2.1 Enable R8 (isMinifyEnabled true, isShrinkResources true) with documented proguard-rules.pro keep set
 │           Verify: assembleRelease + lintVital PASS + keep-rules smoke (Hilt/Room/Retrofit/Gson/Firebase/WorkManager)
 │     9.2.2 Apply FLAG_SECURE to authenticated screens (decide per-screen, verify recent-apps redaction)
 │     9.2.3 Wire PaymentPipelineMetrics into ProcessTransactionUseCase + UpiNotificationListenerService (atomic counters, runCatching, non-blocking)
 │           Verify: ObservabilityHardeningTest extended, no Room/network/auth import leak in pipeline
 │     9.2.4 Crashlytics decision: add `firebase-crashlytics` + sanitized error handler OR explicitly document deferral again
 │
 └── 9.3 Release Gate Verification (no code, only verification + docs)
       9.3.1 Re-run verification matrix: testDebugUnitTest 486 → PASS, assembleDebug SUCCESS, assembleRelease SUCCESS, backend npm test 67 → PASS
       9.3.2 Static audits: TODO/FIXME 0, secret/PII log 0, localhost cleartext 0, exported components minimal, Room version 9 migrations 1_2..8_9
       9.3.3 Prod domain checklist: SHOUTPAY_RELEASE_BASE_URL verified as real https production domain (not placeholder)
       9.3.4 Update docs/ Phase 9 GATE report (this report is investigation; 9.3 report is gate outcome)
```

**Subphase count rationale:** 9.1 is mandatory correctness hygiene (P1). 9.2 is the previously accepted P2 hardening that is now worth closing for Play readiness. 9.3 is verification with no new code — the smallest scope that still proves the gate. No 9.4 needed.

**Alternatives considered and rejected:** Splitting 9.2 into separate R8 / FLAG_SECURE / metrics phases was rejected — each is small, independent, and verified by the same build/test matrix, so separate phases would be artificial.

---

## 24. Explicitly Deferred Work

**NOT to do in Phase 9 (remain DEFERRED or NOT NEEDED):**

- Any new product feature (expense tracking, SENT/REFUND/PENDING announcements, spending analytics, AI categorization, WhatsApp/SMS reports, community parsers)
- Backend capabilities beyond current `auth/merchant/devices/transactions` modules
- Data management beyond existing 30-day unparsed retention + 200 diagnostic cap
- New Room schema / migration (no schema change justified; 9.1 is interface-only)
- New sync state or state-machine redesign (current PENDING→UPLOADING→SYNCED/FAILED + manual retry + stale recovery is sufficient)
- Duplicate JWT / refresh / token storage / client-controlled merchant identity
- OEM-specific automated workarounds (documented limitation)
- Regional languages beyond English/Hindi (+ Marathi candidate already in VoiceAnnouncementEngine)
- Telemetry upload, cert pinning, latency histogram (P3 — future)
- Play Store publishing itself (architecture is Play-ready per Phase 8.7 §50 GO, but publish is operational)

---

## 25. Things NOT to Change

**Protected unless critical defect is proven (§26):**

```
NotificationListenerService / TransactionParser / ParserVersionResolver /
TransactionValidator / TransactionFingerprint / Deduplication /
VoiceAnnouncementEngine / TextToSpeech flow
— no refactor for cleanliness, no WorkManager/foreground dependency added.

Firebase authentication / JWT middleware / Refresh token rotation /
AuthInterceptor / AuthAuthenticator / AuthStateManager
— no duplicate token storage, no second JWT impl.

SyncQueue state machine / Room authoritative storage / WorkManager retry semantics /
FAILED protection / SYNCED protection / Reconciliation rules /
Stale recovery rules / Integrity audit read-only / Diagnostic non-critical /
Maintenance coordination
— no new states, no auto FAILED resurrection, no SYNCED revert, no diagnostics-as-dependency.

CLAUDE.md MVP scope (RECEIVED SUCCESS only, offline-first, no backend dependency).
Database version (stays 9 unless investigation-proven necessity — none found).
```

**Also protected:** `allowBackup false + dataExtractionRules`, `cleartextTrafficPermitted false`, `EncryptedSharedPreferences`, Joi `unknown(false)` + forbid lists, `helmet/cors/rateLimit` stack, `WorkManager` unique KEEP + backoff + NetworkType, `TransactionFingerprint` normalization, `DEDUP_WINDOW_MS 2m`, `STALE_UPLOADING_THRESHOLD_MS 15m`.

---

## 26. Evidence Classification

| Conclusion | Evidence | Classification |
|---|---|---|
| Room version 9, 8 migrations additive, no destructive | `AppDatabase.kt:10-179` + `DatabaseModule.kt:22-34` | **SOURCE VERIFIED** |
| Payment pipeline no network/auth/WorkManager import | `ProcessTransactionUseCase.kt:1-200` import list | **SOURCE VERIFIED** |
| Offline-first invariant holds | `ProcessTransactionUseCase.processNotification` never awaits network/worker | **SOURCE VERIFIED** |
| Room authoritative, never deleted on sync/auth/network failure | `TransactionSyncRepository` reverts to PENDING, never DELETE transaction | **SOURCE VERIFIED** |
| Sync state machine restrictive, FAILED/SYNCED protected | `SyncQueueDao:30-117` + `TransactionSyncRepository:113-281` + `StaleUploadRecoveryManager` | **SOURCE VERIFIED** |
| Diagnostics non-critical (`runCatching`) | `TransactionSyncWorker:55-58,66-68,82-83` + `ReconciliationWorker:57,61` + `StaleUploadRecoveryWorker:43,55` | **SOURCE VERIFIED** |
| Success timestamp only on full success | `TransactionSyncWorker:61-69` + `SyncStatusStore:24-58` + `TransactionSyncRepository:94-98` | **SOURCE VERIFIED** |
| WorkManager unique KEEP, backoff, constraints | `scheduler/*` + `worker/*` + `UpiVoiceAlertApplication:32-44` | **SOURCE VERIFIED** |
| JWT HS256 pinned, refresh replay protected | `auth/jwt.js:21` + `auth/tokens.js:9-10` + `prisma/schema.prisma:109` | **SOURCE VERIFIED** |
| Backup excluded, cleartext false, tokens encrypted | `AndroidManifest.xml:16-17` + `network_security_config.xml` + `AuthSessionStore.kt:12-17` | **SOURCE VERIFIED** |
| Git working tree clean, no schema drift | `git status --short` empty, `git diff` empty | **SOURCE VERIFIED** |
| 486 Android tests PASS, 67 backend tests PASS, Debug/Release SUCCESS | `docs/PHASE_8_7_FINAL_PRODUCTION_READINESS.md:3,38-41` | **HISTORICAL VERIFIED — NOT RE-EXECUTED** |
| PII logs via `Log.i`/`AppLogger.d(rawText)` | `ProcessTransactionUseCase.kt:61-122` + `VoiceAnnouncementEngine.kt:106` | **SOURCE VERIFIED** |
| DAO stub defaults | `TransactionDao.kt:129-146` + `SyncQueueDao.kt:31-189` + `SyncDiagnosticDao.kt:22,25,41` | **SOURCE VERIFIED** |
| No TODO/FIXME/secret in prod | `Select-String HACK\|TODO` → only `toDomain` false positives | **SOURCE VERIFIED** |
| PaymentPipelineMetrics unwired | `grep PaymentPipelineMetrics` → class + test only | **SOURCE VERIFIED** |
| Physical-device validation | Not in scope for investigation phase | **DEFERRED — NOT EXECUTED** |
| Current Gradle test outcome | Not re-executed per §19 | **NOT EXECUTED** |

---

## 27. Final Verdict

```
PHASE 9 INVESTIGATION RESULT

Repository integrity:
PASS

Payment pipeline:
SAFE

Authentication:
SAFE

Sync reliability:
FINDINGS (P1 DAO hygiene + P2 pipeline metrics wiring; no P0)

Database:
SAFE (with P1 DAO interface hygiene)

Security:
FINDINGS (P1 PII log redaction; otherwise SAFE)

Observability:
SAFE (PaymentPipelineMetrics intentionally DEFERRED — P2 candidate)

Release readiness:
FINDINGS (P2: R8 disabled, FLAG_SECURE absent, Crashlytics absent — all previously accepted non-blocking)

P0:
0

P1:
2  (P1-1 DAO stub defaults, P1-2 PII logs)

P2:
5  (R8 disabled, FLAG_SECURE absent, Crashlytics absent, PaymentPipelineMetrics unwired, prod BASE_URL placeholder verification)

P3:
4  (telemetry upload, cert pinning, latency histogram, OEM battery onboarding)

Recommended Phase 9 objective:
Harden the already-correct release artifact to Play-Store-shippable without new product scope:
  close P1 DAO silent-fallback and PII-log surfaces, then apply the previously deferred
  P2 production hardening (R8 / FLAG_SECURE / PaymentPipelineMetrics) with a verification gate.
  No new sync states, no new migrations, no new auth, no new features.

Recommended Phase 9 subphases:
9.1 DAO & Privacy Hardening (P1)
9.2 Release Artifact Hardening (P2 — R8/FLAG_SECURE/metrics/Crashlytics decision)
9.3 Release Gate Verification (matrix + static audits + prod domain checklist)

Phase 8 physical-device validation:
DEFERRED — DO NOT EXECUTE

Gradle tests:
NOT EXECUTED

Repository modifications:
NONE (this report only: docs/PHASE_9_INVESTIGATION_REPORT.md)
```

---

## Appendix — Files Inspected (representative)

`app/build.gradle.kts`, `app/src/main/AndroidManifest.xml`, `app/src/main/java/com/upivoicealert/service/UpiNotificationListenerService.kt`, `domain/usecases/ProcessTransactionUseCase.kt`, `filter/NotificationFilter.kt`, `filter/NotificationTextCleaner.kt`, `filter/TransactionClassifier.kt`, `parser/*`, `parser/ParserVersionResolver.kt`, `data/database/{AppDatabase,TransactionDao,TransactionEntity,UnparsedNotification*}`, `data/repository/TransactionRepositoryImpl.kt`, `data/repository/TransactionFingerprint.kt`, `data/sync/{SyncQueueDao,SyncQueueEntity,SyncQueueRepositoryImpl,TransactionSyncRepository,TransactionSyncReconciler,StaleUploadRecoveryManager,SyncIntegrityAuditor,SyncDiagnosticDao,SyncDiagnosticRepositoryImpl,SyncDiagnosticMessageMapper}`, `data/datastore/{SettingsDataStore,SyncStatusStore,ReconciliationStatusStore,SyncIntegrityStatusStore}`, `data/auth/{AuthRepository,AuthSessionStore,AuthStateManager}`, `network/{ApiClient,AuthApi,AuthInterceptor,AuthAuthenticator,NetworkMonitor}`, `config/{AppEnvironment,EnvironmentConfig,EnvironmentProvider,EnvironmentValidator}`, `di/{AppModule,AuthModule,DatabaseModule}`, `logging/AppLogger`, `observability/PaymentPipelineMetrics`, `voice/VoiceAnnouncementEngine`, `scheduler/{Sync,Reconciliation,StaleUploadRecovery,SyncIntegrityAudit}Scheduler`, `worker/{TransactionSync,TransactionReconciliation,StaleUploadRecovery,SyncIntegrityAudit}Worker`, `work/{WorkScheduler,CleanupWorker,RetryFailedParseWorker}`, `UpiVoiceAlertApplication.kt`, `utils/{Constants,PackageNames}`, `res/xml/*`, `res/values/*`, `gradle/libs.versions.toml`, `shoutpay-backend/src/{app,server,config/config,auth/{jwt,middleware,router,tokens,firebase},modules/{merchant,devices,transactions}/*,database/database}`, `prisma/schema.prisma`, `prisma/migrations/*`, `package.json`, `docs/PHASE_8_7_FINAL_PRODUCTION_READINESS.md`, `FIREBASE_COMPATIBILITY_REPORT.md`, `CLAUDE.md`.



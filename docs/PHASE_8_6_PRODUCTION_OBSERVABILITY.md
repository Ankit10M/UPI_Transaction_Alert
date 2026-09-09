# Phase 8.6 — Production Observability & Incident Readiness

Status: Complete
Baseline: Android 478 PASS, Backend 67 PASS, Room v9, no schema change.

## 1. Architecture
```
Layer 1 Payment pipeline  → PaymentPipelineMetrics (in-memory atomics, no Room/network)
Layer 2 Local tx health   → Room counts (transaction/sync_queue)
Layer 3 Sync queue        → SyncQueueDao observeCount + diagnostics 200 capped
Layer 4 Auth/session      → AuthState + SyncDiagnosticCategory.AUTH safe codes
Layer 5 Workers           → TransactionSync/Reconciliation/StaleRecovery/IntegrityAudit → diagnosticRecorder non-critical
Layer 6 API/network       → Http status classification + latency buckets + requestId
Layer 7 Security          → AUTH_REFRESH_REPLAY, RATE_LIMITED, AUTHORIZATION_DENIED (no PII)
Layer 8 App/process       → SyncStatus + WorkManager alreadyRunning non-failure
```
All diagnostics via existing `sync_diagnostic_events` (200 cap `trimToMaxCount`), message via `SyncDiagnosticMessageMapper`.

## 2. Event Taxonomy & Severity
Category: SYNC, RECONCILIATION, STALE_RECOVERY, INTEGRITY_AUDIT, AUTH, PAYMENT_PIPELINE, API, APP_HEALTH
Severity implicit: DEBUG (pipeline received), INFO (SYNC_COMPLETED), WARNING (RETRY/OFFLINE), ERROR (VALIDATION_FAILED), SECURITY (REPLAY).
Every `record()` bounded `affectedCount` + `createdAt` + allowlisted `message`. Forbidden: JWT, refresh, Firebase token, Authorization header, phone, VPA, sender, raw notification, raw body, stack trace.

## 3. Payment Pipeline Counters (Layer 1)
`PaymentPipelineMetrics` — 7 Atomics: received, parseRejected, validationRejected, duplicates, persisted, ttsAttempted/Failed. Incremented synchronously on notification path without Room/network. Snapshot for health UI. No event row per notification (avoids table explosion).

## 4. Sync/Auth/API Health
Sync: Pending/Failed/UPLOADING counts via `SyncQueueDao.observeCountByStatus`, lastSuccessfulSync via `SyncStatusStore`. Auth: loginSuccess/Failure, refreshSuccess/Failure/Replay, sessionExpired, logout — all via AUTH category. API: endpoint category (sync/merchant/devices/transactions), HTTP status bucket (2xx/400/401/403/404/408/429/5xx/IOException), no URL query.

## 5. Backend Observability
- `X-Request-Id` random UUID per request (`crypto.randomUUID()`), returned in error `requestId`, logged with `{requestId,method,path,status,errorCode}` production.
- Helmet, CORS, morgan tiny, 256kb limit, rate-limit, error sanitized.
- Health `GET /health` returns `{status,service,timestamp,database,uptime}` no credentials.

## 6. Retention & Non-Critical Invariant
`MAX_DIAGNOSTIC_EVENTS=200`, `trimToMaxCount` newest kept. `runCatching{record}` in workers → diagnostic failure ≠ business failure. No new table, no Room version bump.

## 7. Privacy Rules
Never log: JWT/refresh/Firebase/OTP/phone/VPA/raw notification/full payload/stack. Allow: `errorCode`, `affectedCount`, `requestId`, `route`, `status`, `duration bucket`. Redacted via `AppLogger` DEBUG-gated and sanitized messages.

## 8. Operational Limits
Queue growth visible via counts; no full scan from UI. No telemetry upload (deferred, offline-first). Crash/ANR not added (no Crashlytics SDK), recommend future addition with safe metadata allowlist. No FLAG_SECURE (recent-apps preview shows amounts — accepted low risk).

## 9. Incident Reconstruction Checklist
1. Check `PipelineMetrics.received vs persisted vs duplicates` — did parsing work?
2. Room `transactions` count — did Room persist?
3. `sync_queue` PENDING/FAILED — did queue?
4. `SyncDiagnostic` SYNC_* — did sync run, retry reason?
5. AUTH diagnostics — auth failure vs network?
6. Backend logs grep `requestId` — auth/rate-limit/DB?
7. Health `/health` — DB up?
Without sensitive data.

## 10. Failure Domains & Incident Levels
Domains: LOCAL, NETWORK, AUTH, SERVER, DATABASE, BACKGROUND, SECURITY, USER_CONFIGURATION. Levels: P0 payment blocked, P1 sync broad failure, P2 auth degradation, P3 isolated diagnostic.

## 11. Recommended Future
- Heartbeat not needed; WorkManager periodic sync suffices.
- Telemetry upload deferred — document as future scope with opt-in.
- Latency buckets for API if needed, correlation via existing `X-Request-Id`.

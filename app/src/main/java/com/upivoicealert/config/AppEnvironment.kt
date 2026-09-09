package com.upivoicealert.config

/**
 * Phase 8.1 — Explicit environment boundary.
 *
 * Two variants only. No dev/staging/qa flavors — keep Phase 8.1 focused.
 * The value is derived from [com.upivoicealert.BuildConfig.DEBUG] at runtime
 * via [EnvironmentProvider], never scattered as `if (BuildConfig.DEBUG)` in
 * repositories or business logic.
 */
enum class AppEnvironment {
    DEBUG,
    RELEASE
}

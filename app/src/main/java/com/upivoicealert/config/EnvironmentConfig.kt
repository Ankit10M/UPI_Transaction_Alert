package com.upivoicealert.config

/**
 * Centralized environment configuration — single source of truth for
 * API environment decisions (Phase 8.1 Core Architecture).
 *
 * Flow: BuildConfig → EnvironmentProvider → Network Module → Retrofit
 *
 * Repositories see only API interfaces, never BuildConfig.
 *
 * @param baseUrl fully-qualified API base URL ending with '/'
 * @param environment explicit DEBUG vs RELEASE boundary
 */
data class EnvironmentConfig(
    val baseUrl: String,
    val environment: AppEnvironment
) {
    val isDebug: Boolean get() = environment == AppEnvironment.DEBUG
    val isRelease: Boolean get() = environment == AppEnvironment.RELEASE

    /** Whether verbose network body logging is permitted. Release never. */
    val isHttpBodyLoggingEnabled: Boolean get() = isDebug
}

package com.upivoicealert.logging

import android.util.Log
import com.upivoicealert.BuildConfig

/**
 * Phase 8.1 — Centralized logging policy.
 *
 * - Debug builds: all levels emitted (verbose development observability).
 * - Release builds: DEBUG/VERBOSE/INFO suppressed; WARN/ERROR only.
 *
 * Sensitive data (JWT, tokens, phone, VPA, full notification text, auth
 * headers, response bodies) must NEVER be logged regardless of build type.
 * Callers are responsible for not passing sensitive values — this gate is
 * an additional safety net, not a substitute for discipline.
 *
 * Usage: replace direct android.util.Log calls with AppLogger calls.
 */
object AppLogger {

    private const val DEFAULT_TAG = "ShoutPay"

    /** Whether verbose logs are permitted (debug only). */
    val isVerboseEnabled: Boolean get() = BuildConfig.DEBUG

    fun v(tag: String = DEFAULT_TAG, msg: String) {
        if (BuildConfig.DEBUG) Log.v(tag, msg)
    }

    fun d(tag: String = DEFAULT_TAG, msg: String) {
        if (BuildConfig.DEBUG) Log.d(tag, msg)
    }

    fun i(tag: String = DEFAULT_TAG, msg: String) {
        if (BuildConfig.DEBUG) Log.i(tag, msg)
    }

    fun w(tag: String = DEFAULT_TAG, msg: String, throwable: Throwable? = null) {
        Log.w(tag, msg, throwable)
    }

    fun e(tag: String = DEFAULT_TAG, msg: String, throwable: Throwable? = null) {
        Log.e(tag, msg, throwable)
    }

    /**
     * Release-safe: always emitted, but must not contain sensitive data.
     * Use for operational warnings that are safe in production.
     */
    fun wReleaseSafe(tag: String, msg: String, throwable: Throwable? = null) {
        Log.w(tag, msg, throwable)
    }
}

package com.upivoicealert.utils

object Constants {

    /** Deduplication fuzzy-match window (CLAUDE.md Module 4): 2 minutes. */
    const val DEDUP_WINDOW_MS = 2 * 60 * 1000L

    /** Retention for the failed-parse diagnostic queue. */
    const val UNPARSED_RETENTION_DAYS = 30L

    /** WorkManager periodic intervals (cleanup / retry only — never real-time detection). */
    const val CLEANUP_PERIOD_HOURS = 24L
    const val RETRY_PERIOD_HOURS = 12L

    /**
     * Set to true to run the listener as a foreground service (requires
     * POST_NOTIFICATIONS on Android 13+). MVP ships as a plain listener service;
     * the foreground-service pattern remains available for reliability testing
     * (CLAUDE.md Section 7.5).
     */
    const val USE_FOREGROUND_SERVICE = false
}
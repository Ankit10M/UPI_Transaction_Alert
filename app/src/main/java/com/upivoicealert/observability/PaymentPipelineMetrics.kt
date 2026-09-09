package com.upivoicealert.observability

import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Bounded in-memory counters for Layer 1 payment pipeline.
 * No Room writes per notification; no network; no auth dependency.
 * Exposed for health UI, never logs PII.
 */
@Singleton
class PaymentPipelineMetrics @Inject constructor() {
    private val received = AtomicLong(0)
    private val parseRejected = AtomicLong(0)
    private val validationRejected = AtomicLong(0)
    private val duplicates = AtomicLong(0)
    private val persisted = AtomicLong(0)
    private val ttsAttempted = AtomicLong(0)
    private val ttsFailed = AtomicLong(0)

    fun onNotificationReceived() { received.incrementAndGet() }
    fun onParseRejected() { parseRejected.incrementAndGet() }
    fun onValidationRejected() { validationRejected.incrementAndGet() }
    fun onDuplicate() { duplicates.incrementAndGet() }
    fun onPersisted() { persisted.incrementAndGet() }
    fun onTtsAttempted() { ttsAttempted.incrementAndGet() }
    fun onTtsFailed() { ttsFailed.incrementAndGet() }

    data class Snapshot(
        val received: Long,
        val parseRejected: Long,
        val validationRejected: Long,
        val duplicates: Long,
        val persisted: Long,
        val ttsAttempted: Long,
        val ttsFailed: Long
    )

    fun snapshot() = Snapshot(received.get(), parseRejected.get(), validationRejected.get(), duplicates.get(), persisted.get(), ttsAttempted.get(), ttsFailed.get())
}

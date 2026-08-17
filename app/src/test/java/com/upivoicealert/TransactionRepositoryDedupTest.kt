package com.upivoicealert

import com.upivoicealert.data.database.TransactionDao
import com.upivoicealert.data.database.TransactionEntity
import com.upivoicealert.data.database.UnparsedNotificationDao
import com.upivoicealert.data.database.UnparsedNotificationEntity
import com.upivoicealert.data.repository.TransactionRepositoryImpl
import com.upivoicealert.domain.model.NotificationSource
import com.upivoicealert.domain.model.ParseStatus
import com.upivoicealert.domain.model.Transaction
import com.upivoicealert.domain.model.TransactionStatus
import com.upivoicealert.domain.model.TransactionType
import com.upivoicealert.utils.Constants
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * BUG #2 regression tests at the repository boundary.
 *
 * The real production failure: the same payment reported by two sources (e.g.
 * Google Pay and a bank app) was stored twice because the reference-ID lookup
 * required the SAME app and the text fallback required identical text. These
 * tests pin the fixed hybrid dedup (global reference match + cross-source
 * fingerprint within the window) without blocking genuine repeated payments.
 */
class TransactionRepositoryDedupTest {

    private val now = 1_700_000_000_000L
    private val min = 60_000L

    @Test
    fun `same UTR from different apps is stored once`() = runTest {
        val repo = repo()

        // Google Pay reports the payment with UTR 111.
        assertTrue(
            repo.repository.insertTransactionIfNotDuplicate(
                txn(
                    id = "gpay",
                    amount = 10.0,
                    sender = "RAHUL",
                    upiApp = "Google Pay",
                    packageName = "com.google.android.apps.nbu.paisa.user",
                    referenceId = "111",
                    createdAt = now
                )
            )
        )

        // The bank app reports the SAME payment (same UTR) moments later.
        assertFalse(
            repo.repository.insertTransactionIfNotDuplicate(
                txn(
                    id = "bank",
                    amount = 10.0,
                    sender = "RAHUL",
                    upiApp = "Kotak 811",
                    packageName = "com.kotak811mobilebankingapp",
                    referenceId = "111",
                    createdAt = now + 2_000
                )
            )
        )

        assertEquals(1, repo.transactions.size)
    }

    @Test
    fun `same amount same sender 5 minutes apart are two transactions`() = runTest {
        val repo = repo()

        // 10:00 — Rahul pays ₹10.
        assertTrue(
            repo.repository.insertTransactionIfNotDuplicate(
                txn(id = "first", amount = 10.0, sender = "RAHUL", createdAt = now)
            )
        )
        // 10:05 — the same Rahul pays ₹10 again: outside the 2-minute dedup window.
        assertTrue(
            repo.repository.insertTransactionIfNotDuplicate(
                txn(id = "second", amount = 10.0, sender = "RAHUL", createdAt = now + 5 * min)
            )
        )

        assertEquals(2, repo.transactions.size)
    }

    @Test
    fun `gpay and bank with no UTR are deduped via fingerprint`() = runTest {
        val repo = repo()

        // GPay notification without a UTR is stored.
        assertTrue(
            repo.repository.insertTransactionIfNotDuplicate(
                txn(
                    id = "gpay",
                    amount = 1.0,
                    sender = "PRIYA BRIJESH MISHRA",
                    upiApp = "Google Pay",
                    packageName = "com.google.android.apps.nbu.paisa.user",
                    referenceId = null,
                    createdAt = now
                )
            )
        )

        // Bank "received from" notification for the SAME payment (no UTR either):
        // different text, same amount + sender, within the window -> duplicate.
        assertFalse(
            repo.repository.insertTransactionIfNotDuplicate(
                txn(
                    id = "bank",
                    amount = 1.0,
                    sender = "PRIYA BRIJESH MISHRA",
                    upiApp = "Kotak 811",
                    packageName = "com.kotak811mobilebankingapp",
                    referenceId = null,
                    createdAt = now + 3_000
                )
            )
        )

        assertEquals(1, repo.transactions.size)
    }

    @Test
    fun `bank first then gpay with UTR is still deduped`() = runTest {
        val repo = repo()

        // Bank notification arrives first and carries no UTR.
        assertTrue(
            repo.repository.insertTransactionIfNotDuplicate(
                txn(
                    id = "bank",
                    amount = 10.0,
                    sender = "RAHUL",
                    upiApp = "Kotak 811",
                    referenceId = null,
                    createdAt = now
                )
            )
        )

        // GPay notification for the same payment arrives with a UTR. The UTR is
        // not found, but the fingerprint matches the stored bank row (which has
        // no reference) within the window -> duplicate.
        assertFalse(
            repo.repository.insertTransactionIfNotDuplicate(
                txn(
                    id = "gpay",
                    amount = 10.0,
                    sender = "RAHUL",
                    upiApp = "Google Pay",
                    referenceId = "123456789012",
                    createdAt = now + 1_500
                )
            )
        )

        assertEquals(1, repo.transactions.size)
    }

    @Test
    fun `two distinct payments with different UTRs within the window are both stored`() = runTest {
        val repo = repo()

        assertTrue(
            repo.repository.insertTransactionIfNotDuplicate(
                txn(id = "a", amount = 10.0, sender = "RAHUL", referenceId = "111", createdAt = now)
            )
        )
        // A genuinely different payment (different UTR) 30s later must NOT be
        // blocked — the existing row carries a reference, so it is not a match.
        assertTrue(
            repo.repository.insertTransactionIfNotDuplicate(
                txn(id = "b", amount = 10.0, sender = "RAHUL", referenceId = "222", createdAt = now + 30_000)
            )
        )

        assertEquals(2, repo.transactions.size)
    }

    private fun repo(): FakeBoundRepo {
        val dao = FakeTransactionDao()
        return FakeBoundRepo(
            TransactionRepositoryImpl(
                transactionDao = dao,
                unparsedNotificationDao = FakeUnparsedNotificationDao()
            ),
            dao
        )
    }

    private fun txn(
        id: String,
        amount: Double,
        sender: String,
        upiApp: String = "TestApp",
        packageName: String = "com.example.app",
        referenceId: String? = null,
        createdAt: Long
    ) = Transaction(
        id = id,
        amount = amount,
        sender = sender,
        upiApp = upiApp,
        transactionType = TransactionType.RECEIVED,
        status = TransactionStatus.SUCCESS,
        transactionId = referenceId,
        rawNotification = "raw for $id",
        parserVersion = "TestParserV1",
        parseStatus = ParseStatus.PARSED,
        createdAt = createdAt,
        sourceType = NotificationSource.UPI_APP,
        packageName = packageName
    )

    private class FakeBoundRepo(
        val repository: TransactionRepositoryImpl,
        val dao: FakeTransactionDao
    ) {
        val transactions: List<TransactionEntity> get() = dao.rows
    }
}

private class FakeTransactionDao : TransactionDao {

    val rows = mutableListOf<TransactionEntity>()

    override fun observeAll(): Flow<List<TransactionEntity>> = flowOf(rows.toList())
    override fun observeReceivedSuccess(): Flow<List<TransactionEntity>> = flowOf(rows.toList())
    override fun observeReceivedSuccessSince(since: Long): Flow<List<TransactionEntity>> =
        flowOf(rows.filter { it.createdAt >= since })
    override suspend fun findRecentReceivedSuccess(amount: Double, since: Long): TransactionEntity? = null
    override fun observeLatest(): Flow<TransactionEntity?> = flowOf(rows.maxByOrNull { it.createdAt })
    override fun observeCount(): Flow<Int> = flowOf(rows.size)
    override fun observeCountSince(since: Long): Flow<Int> = flowOf(rows.count { it.createdAt >= since })

    override suspend fun findByReferenceIdGlobal(transactionId: String): TransactionEntity? =
        rows.firstOrNull { it.transactionId == transactionId }

    override suspend fun findByFingerprint(
        fingerprint: String,
        windowStart: Long,
        windowEnd: Long
    ): TransactionEntity? =
        rows.firstOrNull { it.dedupFingerprint == fingerprint && it.createdAt in windowStart..windowEnd }

    override suspend fun findByFingerprintNullRef(
        fingerprint: String,
        windowStart: Long,
        windowEnd: Long
    ): TransactionEntity? =
        rows.firstOrNull {
            it.dedupFingerprint == fingerprint &&
                (it.transactionId == null || it.transactionId!!.trim().isEmpty()) &&
                it.createdAt in windowStart..windowEnd
        }

    override suspend fun findExactDuplicate(
        rawNotification: String,
        windowStart: Long,
        windowEnd: Long
    ): TransactionEntity? =
        rows.firstOrNull {
            it.rawNotification.trim() == rawNotification.trim() && it.createdAt in windowStart..windowEnd
        }

    override suspend fun insert(entity: TransactionEntity): Long {
        rows.add(entity)
        return rows.size.toLong()
    }

    override suspend fun markVoiceAnnounced(id: String) = Unit
    override suspend fun clearAll() {
        rows.clear()
    }
}

private class FakeUnparsedNotificationDao : UnparsedNotificationDao {
    override fun observeAll(): Flow<List<UnparsedNotificationEntity>> = flowOf(emptyList())
    override suspend fun getAll(): List<UnparsedNotificationEntity> = emptyList()
    override fun observeCountSince(since: Long): Flow<Int> = flowOf(0)
    override suspend fun insert(entity: UnparsedNotificationEntity): Long = 1L
    override suspend fun deleteById(id: String) = Unit
    override suspend fun clearAll() = Unit
    override suspend fun deleteOlderThan(before: Long) = Unit
}
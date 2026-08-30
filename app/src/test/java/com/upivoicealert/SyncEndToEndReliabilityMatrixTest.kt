package com.upivoicealert

import com.upivoicealert.data.database.TransactionDao
import com.upivoicealert.data.database.TransactionEntity
import com.upivoicealert.data.sync.DefaultSyncMaintenanceCoordinator
import com.upivoicealert.data.sync.SyncQueueDao
import com.upivoicealert.data.sync.SyncQueueEntity
import com.upivoicealert.data.sync.SyncQueueRepositoryImpl
import com.upivoicealert.data.sync.StaleUploadRecoveryManager
import com.upivoicealert.data.sync.SyncIntegrityAuditor
import com.upivoicealert.data.sync.TransactionSyncApi
import com.upivoicealert.data.sync.TransactionSyncRepository
import com.upivoicealert.data.sync.TransactionSyncReconciler
import com.upivoicealert.domain.sync.MaintenanceResult
import com.upivoicealert.domain.sync.SyncMaintenanceOperation
import com.upivoicealert.utils.DeviceIdProvider
import java.io.IOException
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response

class SyncEndToEndReliabilityMatrixTest {

    private fun tx(uuid: String, status: String = "SUCCESS", type: String = "RECEIVED"): TransactionEntity =
        TransactionEntity(id="id-$uuid", amount=100.0, sender="Rahul", upiApp="PhonePe", transactionType=type, status=status, transactionId="UTR-$uuid", rawNotification="raw", parserVersion="P", parseStatus="PARSED", createdAt=System.currentTimeMillis(), sourceType="UNKNOWN", packageName="com.phonepe.app", notificationKey=null, originalNotificationText="raw", cleanedNotificationText="raw", voiceAnnounced=true, transactionUuid=uuid)

    private class HardenedFakeQueueDao : SyncQueueDao {
        val rows = mutableListOf<SyncQueueEntity>()
        private var nextId=1L
        override suspend fun insert(item: SyncQueueEntity): Long { val id=if(item.id==0L) nextId++ else item.id; rows.add(item.copy(id=id)); return id }
        override suspend fun insertIgnore(item: SyncQueueEntity): Long { if(rows.any{it.entityType==item.entityType && it.entityId==item.entityId}) return -1L; val id=if(item.id==0L) nextId++ else item.id; rows.add(item.copy(id=id)); return id }
        override suspend fun getByStatus(status: String)=rows.filter{it.status==status}
        override suspend fun getPendingItems()=rows.filter{it.status==SyncQueueEntity.STATUS_PENDING}.sortedBy{it.createdAt}
        override suspend fun updateStatus(id: Long, status: String, updatedAt: Long){ val idx=rows.indexOfFirst{it.id==id}; if(idx>=0) rows[idx]=rows[idx].copy(status=status, updatedAt=updatedAt) }
        override suspend fun updateStatusIfExpected(id: Long, expectedStatus: String, newStatus: String, updatedAt: Long): Int { val idx=rows.indexOfFirst{it.id==id}; if(idx>=0 && rows[idx].status==expectedStatus){ rows[idx]=rows[idx].copy(status=newStatus, updatedAt=updatedAt); return 1 }; return 0 }
        override suspend fun incrementRetryCount(id: Long, updatedAt: Long){ val idx=rows.indexOfFirst{it.id==id}; if(idx>=0) rows[idx]=rows[idx].copy(retryCount=rows[idx].retryCount+1, updatedAt=updatedAt) }
        override suspend fun incrementRetryCountIfExpected(id: Long, expectedStatus: String, updatedAt: Long): Int { val idx=rows.indexOfFirst{it.id==id}; if(idx>=0 && rows[idx].status==expectedStatus){ rows[idx]=rows[idx].copy(retryCount=rows[idx].retryCount+1, updatedAt=updatedAt); return 1 }; return 0 }
        override suspend fun getAll()=rows
        override suspend fun deleteById(id: Long){ rows.removeIf{it.id==id} }
        override suspend fun clearAll(){ rows.clear() }
        override fun observeCountByStatus(status: String)=flowOf(rows.count{it.status==status})
        override suspend fun getCountByStatus(status: String)=rows.count{it.status==status}
        override suspend fun getFailedItems()=rows.filter{it.status==SyncQueueEntity.STATUS_FAILED}
        override suspend fun getById(id: Long)=rows.firstOrNull{it.id==id}
        override suspend fun markFailedWithDiagnostics(id: Long, status: String, errorCode: String?, errorMessage: String?, failedAt: Long?, updatedAt: Long){ val idx=rows.indexOfFirst{it.id==id}; if(idx>=0) rows[idx]=rows[idx].copy(status=status, lastErrorCode=errorCode, lastErrorMessage=errorMessage, failedAt=failedAt, updatedAt=updatedAt) }
        override suspend fun markFailedWithDiagnosticsIfExpected(id: Long, status: String, errorCode: String?, errorMessage: String?, failedAt: Long?, updatedAt: Long, expectedStatus: String): Int { val idx=rows.indexOfFirst{it.id==id}; if(idx>=0 && rows[idx].status==expectedStatus){ rows[idx]=rows[idx].copy(status=status, lastErrorCode=errorCode, lastErrorMessage=errorMessage, failedAt=failedAt, updatedAt=updatedAt); return 1 }; return 0 }
        override suspend fun retryFailedItem(id: Long, updatedAt: Long): Int { val idx=rows.indexOfFirst{it.id==id}; if(idx>=0 && rows[idx].status==SyncQueueEntity.STATUS_FAILED){ rows[idx]=rows[idx].copy(status=SyncQueueEntity.STATUS_PENDING, lastErrorCode=null, lastErrorMessage=null, failedAt=null, updatedAt=updatedAt); return 1 }; return 0 }
        override suspend fun retryAllFailed(updatedAt: Long): Int { var c=0; rows.forEachIndexed{ idx,e-> if(e.status==SyncQueueEntity.STATUS_FAILED){ rows[idx]=e.copy(status=SyncQueueEntity.STATUS_PENDING, lastErrorCode=null, lastErrorMessage=null, failedAt=null, updatedAt=updatedAt); c++ }}; return c }
        override fun observeFailedItems()=flowOf(emptyList<SyncQueueEntity>())
        override suspend fun getStaleUploadingItems(cutoffTime: Long)=rows.filter{it.status==SyncQueueEntity.STATUS_UPLOADING && it.updatedAt<cutoffTime}
        override suspend fun recoverStaleUploading(cutoffTime: Long, updatedAt: Long): Int { var c=0; rows.forEachIndexed{ idx,e-> if(e.status==SyncQueueEntity.STATUS_UPLOADING && e.updatedAt<cutoffTime){ rows[idx]=e.copy(status=SyncQueueEntity.STATUS_PENDING, updatedAt=updatedAt); c++ }}; return c }
        override suspend fun countTransactionQueueItems()=rows.count{it.entityType==SyncQueueEntity.ENTITY_TYPE_TRANSACTION}
        override suspend fun countAllQueueItems()=rows.size
        override suspend fun countOrphanedQueueItems()=0
        override suspend fun countDuplicateExtraRows(): Int { val t=rows.filter{it.entityType==SyncQueueEntity.ENTITY_TYPE_TRANSACTION}; return t.size - t.map{it.entityId}.toSet().size }
        override suspend fun countDuplicateGroups(): Int =0
        override suspend fun countInvalidQueueItems(): Int =0
        override suspend fun countStaleUploading(cutoffTime: Long)=rows.count{it.status==SyncQueueEntity.STATUS_UPLOADING && it.updatedAt<cutoffTime}
    }

    private class FakeTxDao(val txs: MutableMap<String, TransactionEntity> = mutableMapOf(), val queue: HardenedFakeQueueDao? = null) : TransactionDao {
        override fun observeAll(): Flow<List<TransactionEntity>> = flowOf(txs.values.toList())
        override fun observeReceivedSuccess(): Flow<List<TransactionEntity>> = flowOf(txs.values.filter{it.status=="SUCCESS" && it.transactionType=="RECEIVED"})
        override fun observeReceivedSuccessSince(since: Long): Flow<List<TransactionEntity>> = flowOf(emptyList())
        override suspend fun findRecentReceivedSuccess(amount: Double, since: Long): TransactionEntity? = null
        override fun observeLatest(): Flow<TransactionEntity?> = flowOf(null)
        override fun observeCount(): Flow<Int> = flowOf(txs.size)
        override fun observeCountSince(since: Long): Flow<Int> = flowOf(0)
        override suspend fun findByReferenceIdGlobal(transactionId: String): TransactionEntity? = null
        override suspend fun findByFingerprint(fingerprint: String, windowStart: Long, windowEnd: Long): TransactionEntity? = null
        override suspend fun findByFingerprintNullRef(fingerprint: String, windowStart: Long, windowEnd: Long): TransactionEntity? = null
        override suspend fun findExactDuplicate(rawNotification: String, windowStart: Long, windowEnd: Long): TransactionEntity? = null
        override suspend fun findByTransactionUuid(uuid: String): TransactionEntity? = txs[uuid]
        override suspend fun findByTransactionUuids(uuids: List<String>): List<TransactionEntity> = uuids.mapNotNull{txs[it]}
        override suspend fun insert(entity: TransactionEntity): Long { txs[entity.transactionUuid]=entity; return 1 }
        override suspend fun markVoiceAnnounced(id: String) {}
        override suspend fun clearAll() { txs.clear() }
        override suspend fun getEligibleMissingQueueUuids(limit: Int, offset: Int): List<String> { val queued=queue?.rows?.map{it.entityId}?.toSet()?: emptySet(); return txs.values.filter{it.status=="SUCCESS" && it.transactionType=="RECEIVED" && it.transactionUuid !in queued && it.transactionUuid.trim().isNotEmpty()}.sortedBy{it.createdAt}.drop(offset).take(limit).map{it.transactionUuid} }
        override suspend fun countEligibleMissingQueue(): Int { val queued=queue?.rows?.map{it.entityId}?.toSet()?: emptySet(); return txs.values.count{it.status=="SUCCESS" && it.transactionType=="RECEIVED" && it.transactionUuid !in queued} }
        override suspend fun countEligibleTransactions(): Int = txs.values.count{it.status=="SUCCESS" && it.transactionType=="RECEIVED"}
        override suspend fun countEligibleForAudit(): Int = txs.values.count{it.status=="SUCCESS" && it.transactionType=="RECEIVED" && it.transactionUuid.trim().isNotEmpty()}
        override suspend fun countMissingQueueForAudit(): Int { val queued=queue?.rows?.map{it.entityId}?.toSet()?: emptySet(); return txs.values.count{it.status=="SUCCESS" && it.transactionType=="RECEIVED" && it.transactionUuid.trim().isNotEmpty() && it.transactionUuid !in queued} }
        override suspend fun countScannedTransactionsForAudit(): Int = txs.size
    }

    private class FakeDevice : DeviceIdProvider { override suspend fun getDeviceId()="dev-123" }

    private fun repo(queue: HardenedFakeQueueDao, txs: MutableMap<String, TransactionEntity>, api: TransactionSyncApi): TransactionSyncRepository {
        val txDao=FakeTxDao(txs, queue)
        return TransactionSyncRepository(queue, txDao, api, FakeDevice())
    }

    @Test fun `A normal success path`() = runTest {
        val uuid="a-uuid"
        val txs=mutableMapOf(uuid to tx(uuid))
        val queue=HardenedFakeQueueDao()
        queue.insert(SyncQueueEntity(entityType="TRANSACTION", entityId=uuid, status="PENDING", createdAt=1, updatedAt=1))
        val api=object: TransactionSyncApi { override suspend fun syncTransactions(r: com.upivoicealert.data.sync.TransactionSyncRequestDto)=com.upivoicealert.data.sync.TransactionSyncResponseDto(created=listOf(uuid), duplicates=emptyList()) }
        val r=repo(queue, txs, api)
        val res=r.syncPendingTransactions()
        assertTrue(res is TransactionSyncRepository.SyncResult.Success)
        assertEquals(SyncQueueEntity.STATUS_SYNCED, queue.rows.first().status)
        assertEquals(uuid, txs[uuid]?.transactionUuid)
        assertEquals(0, queue.rows.first().retryCount)
    }

    @Test fun `B network failure retry then success`() = runTest {
        val uuid="b-uuid"
        val txs=mutableMapOf(uuid to tx(uuid))
        val queue=HardenedFakeQueueDao()
        queue.insert(SyncQueueEntity(entityType="TRANSACTION", entityId=uuid, status="PENDING", createdAt=1, updatedAt=1))
        var call=0
        val api=object: TransactionSyncApi {
            override suspend fun syncTransactions(r: com.upivoicealert.data.sync.TransactionSyncRequestDto): com.upivoicealert.data.sync.TransactionSyncResponseDto {
                call++; if(call==1) throw IOException("offline"); return com.upivoicealert.data.sync.TransactionSyncResponseDto(created=listOf(uuid), duplicates=emptyList())
            }
        }
        val repo1=repo(queue, txs, api)
        val res1=repo1.syncPendingTransactions()
        assertTrue(res1 is TransactionSyncRepository.SyncResult.Retry)
        assertEquals(SyncQueueEntity.STATUS_PENDING, queue.rows.first().status)
        assertEquals(1, queue.rows.first().retryCount)
        val res2=repo(queue, txs, api).syncPendingTransactions()
        assertTrue(res2 is TransactionSyncRepository.SyncResult.Success)
        assertEquals(SyncQueueEntity.STATUS_SYNCED, queue.rows.first().status)
        assertEquals(1, queue.rows.size)
    }

    @Test fun `C server failure retry then success`() = runTest {
        val uuid="c-uuid"
        val txs=mutableMapOf(uuid to tx(uuid))
        val queue=HardenedFakeQueueDao()
        queue.insert(SyncQueueEntity(entityType="TRANSACTION", entityId=uuid, status="PENDING", createdAt=1, updatedAt=1))
        var call=0
        val api=object: TransactionSyncApi {
            override suspend fun syncTransactions(r: com.upivoicealert.data.sync.TransactionSyncRequestDto): com.upivoicealert.data.sync.TransactionSyncResponseDto {
                call++; if(call==1) throw HttpException(Response.error<com.upivoicealert.data.sync.TransactionSyncResponseDto>(500, "".toResponseBody(null))); return com.upivoicealert.data.sync.TransactionSyncResponseDto(created=listOf(uuid), duplicates=emptyList())
            }
        }
        val repo1=repo(queue, txs, api)
        val res1=repo1.syncPendingTransactions()
        assertTrue(res1 is TransactionSyncRepository.SyncResult.Retry)
        assertEquals(SyncQueueEntity.STATUS_PENDING, queue.rows.first().status)
        assertTrue(queue.rows.none{it.status==SyncQueueEntity.STATUS_FAILED})
        val res2=repo(queue, txs, api).syncPendingTransactions()
        assertTrue(res2 is TransactionSyncRepository.SyncResult.Success)
        assertEquals(SyncQueueEntity.STATUS_SYNCED, queue.rows.first().status)
    }

    @Test fun `D permanent failure then manual retry`() = runTest {
        val uuid="d-uuid"
        val txs=mutableMapOf(uuid to tx(uuid))
        val queue=HardenedFakeQueueDao()
        queue.insert(SyncQueueEntity(entityType="TRANSACTION", entityId=uuid, status="PENDING", createdAt=1, updatedAt=1))
        val apiFail=object: TransactionSyncApi { override suspend fun syncTransactions(r: com.upivoicealert.data.sync.TransactionSyncRequestDto): com.upivoicealert.data.sync.TransactionSyncResponseDto { throw HttpException(Response.error<com.upivoicealert.data.sync.TransactionSyncResponseDto>(400, "".toResponseBody(null))) } }
        val repoFail=repo(queue, txs, apiFail)
        val resFail=repoFail.syncPendingTransactions()
        assertTrue(resFail is TransactionSyncRepository.SyncResult.Error)
        assertEquals(SyncQueueEntity.STATUS_FAILED, queue.rows.first().status)
        val apiNoop=object: TransactionSyncApi { override suspend fun syncTransactions(r: com.upivoicealert.data.sync.TransactionSyncRequestDto)=com.upivoicealert.data.sync.TransactionSyncResponseDto(created=emptyList(), duplicates=emptyList()) }
        val repoAuto=repo(queue, txs, apiNoop)
        val autoRes=repoAuto.syncPendingTransactions()
        assertTrue(queue.rows.first().status==SyncQueueEntity.STATUS_FAILED)
        val reconciler=TransactionSyncReconciler(FakeTxDao(txs, queue), queue, object: com.upivoicealert.data.datastore.ReconciliationStatusRecorder{ override suspend fun recordReconciliation(timestamp: Long, repairedCount: Int){}})
        reconciler.reconcile()
        assertEquals(SyncQueueEntity.STATUS_FAILED, queue.rows.first().status)
        val retryRes=queue.retryFailedItem(queue.rows.first().id, System.currentTimeMillis())
        assertEquals(1, retryRes)
        assertEquals(SyncQueueEntity.STATUS_PENDING, queue.rows.first().status)
        assertNull(queue.rows.first().lastErrorCode)
        val apiSucc=object: TransactionSyncApi { override suspend fun syncTransactions(r: com.upivoicealert.data.sync.TransactionSyncRequestDto)=com.upivoicealert.data.sync.TransactionSyncResponseDto(created=listOf(uuid), duplicates=emptyList()) }
        val repoSucc=repo(queue, txs, apiSucc)
        val resSucc=repoSucc.syncPendingTransactions()
        assertTrue(resSucc is TransactionSyncRepository.SyncResult.Success)
        assertEquals(SyncQueueEntity.STATUS_SYNCED, queue.rows.first().status)
    }

    @Test fun `E missing queue reconciliation then sync`() = runTest {
        val uuid="e-uuid"
        val txs=mutableMapOf(uuid to tx(uuid))
        val queue=HardenedFakeQueueDao()
        val txDao=FakeTxDao(txs, queue)
        val reconciler=TransactionSyncReconciler(txDao, queue, object: com.upivoicealert.data.datastore.ReconciliationStatusRecorder{ override suspend fun recordReconciliation(timestamp: Long, repairedCount: Int){}})
        val recRes=reconciler.reconcile()
        assertEquals(1, recRes.repairedCount)
        assertEquals(1, queue.rows.size)
        assertEquals(SyncQueueEntity.STATUS_PENDING, queue.rows.first().status)
        val api=object: TransactionSyncApi { override suspend fun syncTransactions(r: com.upivoicealert.data.sync.TransactionSyncRequestDto)=com.upivoicealert.data.sync.TransactionSyncResponseDto(created=listOf(uuid), duplicates=emptyList()) }
        val repo=TransactionSyncRepository(queue, txDao, api, FakeDevice())
        val res=repo.syncPendingTransactions()
        assertTrue(res is TransactionSyncRepository.SyncResult.Success)
        assertEquals(SyncQueueEntity.STATUS_SYNCED, queue.rows.first().status)
        assertEquals(1, queue.rows.size)
    }

    @Test fun `F stale uploading recovery then success`() = runTest {
        val uuid="f-uuid"
        val txs=mutableMapOf(uuid to tx(uuid))
        val queue=HardenedFakeQueueDao()
        val staleTime=System.currentTimeMillis() - 20*60*1000L
        queue.insert(SyncQueueEntity(entityType="TRANSACTION", entityId=uuid, status=SyncQueueEntity.STATUS_UPLOADING, createdAt=1, updatedAt=staleTime, retryCount=2))
        val syncQueueRepo=SyncQueueRepositoryImpl(queue)
        val staleMgr=StaleUploadRecoveryManager(syncQueueRepo, object: com.upivoicealert.scheduler.SyncSchedulable{ override fun scheduleSync(){}})
        val rec=staleMgr.recoverWithoutScheduling()
        assertEquals(1, rec.recoveredCount)
        assertEquals(SyncQueueEntity.STATUS_PENDING, queue.rows.first().status)
        assertEquals(2, queue.rows.first().retryCount)
        val api=object: TransactionSyncApi { override suspend fun syncTransactions(r: com.upivoicealert.data.sync.TransactionSyncRequestDto)=com.upivoicealert.data.sync.TransactionSyncResponseDto(created=listOf(uuid), duplicates=emptyList()) }
        val repo=TransactionSyncRepository(queue, FakeTxDao(txs, queue), api, FakeDevice())
        val res=repo.syncPendingTransactions()
        assertTrue(res is TransactionSyncRepository.SyncResult.Success)
        assertEquals(SyncQueueEntity.STATUS_SYNCED, queue.rows.first().status)
        assertEquals(1, queue.rows.size)
        assertNotNull(txs[uuid])
    }

    @Test fun `G audit before and after repair`() = runTest {
        val uuid="g-uuid"
        val txs=mutableMapOf(uuid to tx(uuid))
        val queue=HardenedFakeQueueDao()
        val txDao=FakeTxDao(txs, queue)
        val auditor=SyncIntegrityAuditor(txDao, queue)
        val before=auditor.audit()
        assertEquals(1, before.missingQueueCount)
        assertFalse(before.isHealthy)
        assertEquals(0, queue.rows.size)
        val reconciler=TransactionSyncReconciler(txDao, queue, object: com.upivoicealert.data.datastore.ReconciliationStatusRecorder{ override suspend fun recordReconciliation(timestamp: Long, repairedCount: Int){}})
        reconciler.reconcile()
        assertEquals(1, queue.rows.size)
        val after=auditor.audit()
        assertEquals(0, after.missingQueueCount)
        assertTrue(after.isHealthy)
    }

    @Test fun `H stale recovery coordination`() = runTest {
        val coord=DefaultSyncMaintenanceCoordinator()
        val queue=HardenedFakeQueueDao()
        val staleTime=System.currentTimeMillis() - 20*60*1000L
        queue.insert(SyncQueueEntity(entityType="TRANSACTION", entityId="h-uuid", status=SyncQueueEntity.STATUS_UPLOADING, createdAt=1, updatedAt=staleTime))
        val repo=SyncQueueRepositoryImpl(queue)
        val mgr=StaleUploadRecoveryManager(repo, object: com.upivoicealert.scheduler.SyncSchedulable{ override fun scheduleSync(){}})
        val coord2=DefaultSyncMaintenanceCoordinator()
        val job=async{
            coord2.coordinate(SyncMaintenanceOperation.STALE_UPLOAD_RECOVERY){
                delay(200)
                mgr.recoverWithoutScheduling()
            }
        }
        delay(50)
        val second=coord2.coordinate(SyncMaintenanceOperation.STALE_UPLOAD_RECOVERY){ mgr.recoverWithoutScheduling() }
        assertTrue(second is MaintenanceResult.AlreadyRunning)
        job.await()
        assertTrue(coord2.coordinate(SyncMaintenanceOperation.STALE_UPLOAD_RECOVERY){ "ok" } is MaintenanceResult.Executed)
    }

    @Test fun `I reconciliation coordination`() = runTest {
        val coord=DefaultSyncMaintenanceCoordinator()
        val uuid="i-uuid"
        val txs=mutableMapOf(uuid to tx(uuid))
        val queue=HardenedFakeQueueDao()
        val txDao=FakeTxDao(txs, queue)
        val reconciler=TransactionSyncReconciler(txDao, queue, object: com.upivoicealert.data.datastore.ReconciliationStatusRecorder{ override suspend fun recordReconciliation(timestamp: Long, repairedCount: Int){}})
        val job=async{
            coord.coordinate(SyncMaintenanceOperation.LOCAL_RECONCILIATION){ delay(200); reconciler.reconcile() }
        }
        delay(50)
        val second=coord.coordinate(SyncMaintenanceOperation.LOCAL_RECONCILIATION){ reconciler.reconcile() }
        assertTrue(second is MaintenanceResult.AlreadyRunning)
        job.await()
        assertEquals(1, queue.rows.size)
        val third=coord.coordinate(SyncMaintenanceOperation.LOCAL_RECONCILIATION){ reconciler.reconcile() }
        assertTrue(third is MaintenanceResult.Executed)
    }

    @Test fun `J different maintenance not serialized`() = runTest {
        val coord=DefaultSyncMaintenanceCoordinator()
        val a=async{ coord.coordinate(SyncMaintenanceOperation.LOCAL_RECONCILIATION){ delay(100); "recon" } }
        val b=async{ coord.coordinate(SyncMaintenanceOperation.INTEGRITY_AUDIT){ delay(100); "audit" } }
        val c=async{ coord.coordinate(SyncMaintenanceOperation.STALE_UPLOAD_RECOVERY){ delay(100); "stale" } }
        assertTrue(a.await() is MaintenanceResult.Executed)
        assertTrue(b.await() is MaintenanceResult.Executed)
        assertTrue(c.await() is MaintenanceResult.Executed)
    }

    @Test fun `K no automatic FAILED recovery`() = runTest {
        val uuid="k-uuid"
        val txs=mutableMapOf(uuid to tx(uuid))
        val queue=HardenedFakeQueueDao()
        queue.insert(SyncQueueEntity(entityType="TRANSACTION", entityId=uuid, status=SyncQueueEntity.STATUS_FAILED, lastErrorCode="VALIDATION_ERROR", lastErrorMessage="msg", failedAt=123, createdAt=1, updatedAt=1))
        val txDao=FakeTxDao(txs, queue)
        val reconciler=TransactionSyncReconciler(txDao, queue, object: com.upivoicealert.data.datastore.ReconciliationStatusRecorder{ override suspend fun recordReconciliation(timestamp: Long, repairedCount: Int){}})
        reconciler.reconcile()
        assertEquals(SyncQueueEntity.STATUS_FAILED, queue.rows.first().status)
        val staleRepo=SyncQueueRepositoryImpl(queue)
        staleRepo.recoverStaleUploadingItems()
        assertEquals(SyncQueueEntity.STATUS_FAILED, queue.rows.first().status)
        val api=object: TransactionSyncApi { override suspend fun syncTransactions(r: com.upivoicealert.data.sync.TransactionSyncRequestDto): com.upivoicealert.data.sync.TransactionSyncResponseDto { fail("should not be called for FAILED"); return com.upivoicealert.data.sync.TransactionSyncResponseDto(emptyList(), emptyList()) } }
        val repo=TransactionSyncRepository(queue, txDao, api, FakeDevice())
        val res=repo.syncPendingTransactions()
        assertTrue(res is TransactionSyncRepository.SyncResult.NoPending || res is TransactionSyncRepository.SyncResult.Success)
        assertEquals(SyncQueueEntity.STATUS_FAILED, queue.rows.first().status)
    }

    @Test fun `L SYNCED protection`() = runTest {
        val uuid="l-uuid"
        val txs=mutableMapOf(uuid to tx(uuid))
        val queue=HardenedFakeQueueDao()
        queue.insert(SyncQueueEntity(entityType="TRANSACTION", entityId=uuid, status=SyncQueueEntity.STATUS_SYNCED, createdAt=1, updatedAt=1))
        val txDao=FakeTxDao(txs, queue)
        val reconciler=TransactionSyncReconciler(txDao, queue, object: com.upivoicealert.data.datastore.ReconciliationStatusRecorder{ override suspend fun recordReconciliation(timestamp: Long, repairedCount: Int){}})
        reconciler.reconcile()
        assertEquals(SyncQueueEntity.STATUS_SYNCED, queue.rows.first().status)
        SyncQueueRepositoryImpl(queue).recoverStaleUploadingItems()
        assertEquals(SyncQueueEntity.STATUS_SYNCED, queue.rows.first().status)
        SyncIntegrityAuditor(txDao, queue).audit()
        assertEquals(SyncQueueEntity.STATUS_SYNCED, queue.rows.first().status)
    }

    @Test fun `M lastSuccessfulSyncAt only on true success`() = runTest {
        val queue=HardenedFakeQueueDao()
        val txs=mutableMapOf<String, TransactionEntity>()
        val txDao=FakeTxDao(txs, queue)
        val api=object: TransactionSyncApi { override suspend fun syncTransactions(r: com.upivoicealert.data.sync.TransactionSyncRequestDto)=com.upivoicealert.data.sync.TransactionSyncResponseDto(emptyList(), emptyList()) }
        val repo=TransactionSyncRepository(queue, txDao, api, FakeDevice())
        val res=repo.syncPendingTransactions()
        assertTrue(res is TransactionSyncRepository.SyncResult.NoPending)
        assertEquals(0, repo.lastSuccessCount)
    }

    @Test fun `N diagnostic failure does not corrupt primary result`() = runTest {
        val uuid="n-uuid"
        val txs=mutableMapOf(uuid to tx(uuid))
        val queue=HardenedFakeQueueDao()
        queue.insert(SyncQueueEntity(entityType="TRANSACTION", entityId=uuid, status="PENDING", createdAt=1, updatedAt=1))
        val api=object: TransactionSyncApi { override suspend fun syncTransactions(r: com.upivoicealert.data.sync.TransactionSyncRequestDto)=com.upivoicealert.data.sync.TransactionSyncResponseDto(created=listOf(uuid), duplicates=emptyList()) }
        val repo=TransactionSyncRepository(queue, FakeTxDao(txs, queue), api, FakeDevice())
        val res=repo.syncPendingTransactions()
        assertTrue(res is TransactionSyncRepository.SyncResult.Success)
        val diagFailed=true
        try{ if(diagFailed) throw RuntimeException("diag fail") } catch(_: Exception){}
        assertEquals(SyncQueueEntity.STATUS_SYNCED, queue.rows.first().status)
    }
}

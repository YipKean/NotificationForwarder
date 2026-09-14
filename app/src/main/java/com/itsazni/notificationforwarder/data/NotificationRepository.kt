package com.itsazni.notificationforwarder.data

import android.content.Context
import com.itsazni.notificationforwarder.settings.FilterMode
import com.itsazni.notificationforwarder.settings.SettingsStore
import com.itsazni.notificationforwarder.worker.DeliveryCoordinator
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.security.MessageDigest

class NotificationRepository(private val context: Context) {
    private val dao = AppDatabase.getInstance(context).queueDao()
    private val settingsStore = SettingsStore(context)

    fun canCapture(packageName: String): Boolean {
        val settings = settingsStore.readAll()
        return settings.forwardingEnabled &&
            settings.filterMode == FilterMode.WHITELIST &&
            settings.filterPackages.contains(packageName) &&
            settings.webhookUrl.isNotBlank()
    }

    suspend fun enqueue(
        packageName: String,
        appName: String,
        title: String,
        text: String,
        postedAt: Long,
        notificationKey: String,
        capturedPolicyRevision: Long,
        capturedAt: Long
    ) {
        purgeExpired(capturedAt)
        val payload = NotificationPayload(packageName, appName, title, text, postedAt, notificationKey)
        DeliveryCoordinator.withState {
            val settings = settingsStore.readAll()
            if (settings.policyRevision != capturedPolicyRevision || !canCaptureWith(settings, packageName)) {
                return@withState
            }
            recalculateRetentionLocked(settings.retentionHours)
            if (dao.countRows() > 0 && !QueueCrypto.hasUsableKey()) {
                handleKeyLossLocked()
                return@withState
            }
            val encrypted = try {
                QueueCrypto.encrypt(payload)
            } catch (_: QueueKeyMissingException) {
                handleKeyLossLocked()
                return@withState
            } catch (_: Exception) {
                return@withState
            }
            dao.ensureMetrics()
            dao.insert(
                QueueItem(
                    encryptedPayload = encrypted.ciphertext,
                    iv = encrypted.iv,
                    encryptionVersion = QueueCrypto.FORMAT_VERSION,
                    notificationKeyDigest = digest(notificationKey),
                    policyRevision = settings.policyRevision,
                    nextRetryAt = capturedAt,
                    expiresAt = settings.retentionHours?.let { capturedAt + it * 60L * 60L * 1000L },
                    createdAt = capturedAt,
                    updatedAt = capturedAt
                )
            )
        }
    }

    suspend fun getPending(limit: Int): List<PendingQueueItem> {
        val now = System.currentTimeMillis()
        purgeExpired(now)
        val pending = mutableListOf<PendingQueueItem>()
        dao.getPending(now, limit).forEach { row ->
            when (val result = decrypt(row)) {
                is DecryptionResult.Success -> pending += result.item(row)
                DecryptionResult.KeyMissing -> {
                    handleKeyLoss()
                    return emptyList()
                }
                DecryptionResult.Corrupt -> DeliveryCoordinator.withState {
                    DeliveryCoordinator.cancelRegisteredCallsLocked(setOf(row.id))
                    dao.deleteCorruptAndCount(row.id)
                }
            }
        }
        return pending
    }

    suspend fun getForDelivery(id: Long): PendingQueueItem? {
        return DeliveryCoordinator.withState { getForDeliveryLocked(id) }
    }

    suspend fun getForDeliveryLocked(id: Long): PendingQueueItem? {
        val row = dao.findById(id) ?: return null
        if (row.status != QueueStatus.SENDING) {
            return null
        }
        return when (val result = decrypt(row)) {
            is DecryptionResult.Success -> result.item(row)
            DecryptionResult.KeyMissing -> {
                handleKeyLossLocked()
                null
            }
            DecryptionResult.Corrupt -> {
                DeliveryCoordinator.cancelRegisteredCallsLocked(setOf(id))
                dao.deleteCorruptAndCount(id)
                null
            }
        }
    }

    suspend fun markSending(ids: List<Long>) = DeliveryCoordinator.withState {
        dao.markSending(ids, System.currentTimeMillis())
    }

    suspend fun recoverSending() = DeliveryCoordinator.withState {
        dao.recoverSending(System.currentTimeMillis())
    }

    suspend fun markSent(id: Long) = DeliveryCoordinator.withState { markSentLocked(id) }

    suspend fun markSentLocked(id: Long) = dao.markSentAndDelete(id)

    suspend fun markFailure(id: Long, attemptCount: Int, maxRetry: Int, errorCode: String) {
        DeliveryCoordinator.withState { markFailureLocked(id, attemptCount, maxRetry, errorCode) }
    }

    suspend fun markFailureLocked(id: Long, attemptCount: Int, maxRetry: Int, errorCode: String) {
        if (attemptCount >= maxRetry) {
            dao.markFailedAndDelete(id)
            return
        }
        val now = System.currentTimeMillis()
        dao.updateFailure(id, QueueStatus.PENDING, attemptCount, now + calculateBackoff(attemptCount), errorCode, now)
    }

    fun observeStats(): Flow<QueueStats> = dao.observeStats()

    fun observeRecent(limit: Int): Flow<List<QueueEntry>> = dao.observeRecent(limit).map { rows ->
        purgeExpired()
        val now = System.currentTimeMillis()
        val retention = settingsStore.readAll().retentionHours
        val entries = mutableListOf<QueueEntry>()
        for (row in rows) {
            if (DeliveryCoordinator.withState {
                    expireIfNeededLocked(row.id, row.createdAt, retention, now)
                } || (row.expiresAt != null && row.expiresAt <= now)
            ) {
                continue
            }
            when (val result = decrypt(row)) {
                is DecryptionResult.Success -> entries += QueueEntry(row.id, result.payload, row.status, row.attemptCount, row.lastErrorCode)
                DecryptionResult.KeyMissing -> {
                    handleKeyLoss()
                    return@map emptyList()
                }
                DecryptionResult.Corrupt -> DeliveryCoordinator.withState {
                    DeliveryCoordinator.cancelRegisteredCallsLocked(setOf(row.id))
                    dao.deleteCorruptAndCount(row.id)
                }
            }
        }
        entries
    }

    suspend fun deleteQueueItem(id: Long) = DeliveryCoordinator.withState { deleteQueueItemLocked(id) }

    suspend fun deleteQueueItemLocked(id: Long): Int {
        DeliveryCoordinator.cancelRegisteredCallsLocked(setOf(id))
        return dao.deleteById(id)
    }

    suspend fun clearQueue() = DeliveryCoordinator.withState { clearQueueLocked() }

    suspend fun clearQueueLocked() {
        DeliveryCoordinator.cancelRegisteredCallsLocked()
        dao.clearAll()
    }

    suspend fun purgeExpired(now: Long = System.currentTimeMillis()) {
        DeliveryCoordinator.withState { purgeExpiredLocked(now) }
    }

    suspend fun purgeExpiredLocked(now: Long = System.currentTimeMillis()) {
        val expiredIds = dao.findExpiredIds(now)
        if (expiredIds.isNotEmpty()) {
            DeliveryCoordinator.cancelRegisteredCallsLocked(expiredIds.toSet())
        }
        dao.deleteExpiredAndCount(now)
    }

    suspend fun recalculateRetention(retentionHours: Int?) {
        DeliveryCoordinator.withState { recalculateRetentionLocked(retentionHours) }
    }

    suspend fun recalculateRetentionLocked(retentionHours: Int?) {
        val retentionMillis = retentionHours?.let { it * 60L * 60L * 1000L } ?: -1L
        dao.updateExpiry(retentionMillis, System.currentTimeMillis())
        purgeExpiredLocked()
    }

    suspend fun expireIfNeededLocked(id: Long, createdAt: Long, retentionHours: Int?, now: Long): Boolean {
        val expiresAt = retentionHours?.let { createdAt + it * 60L * 60L * 1000L } ?: return false
        if (expiresAt > now) {
            return false
        }
        DeliveryCoordinator.cancelRegisteredCallsLocked(setOf(id))
        return dao.expireByIdAndCount(id)
    }

    private suspend fun handleKeyLoss() {
        DeliveryCoordinator.withState { handleKeyLossLocked() }
    }

    private suspend fun handleKeyLossLocked() {
        DeliveryCoordinator.cancelRegisteredCallsLocked()
        dao.clearAllAndCountAsFailed()
        check(settingsStore.disableForSecurity()) { "security_disable_failed" }
        runCatching { QueueCrypto.resetKey() }
    }

    private fun canCaptureWith(settings: com.itsazni.notificationforwarder.settings.AppSettings, packageName: String): Boolean {
        return settings.forwardingEnabled && settings.filterMode == FilterMode.WHITELIST &&
            settings.filterPackages.contains(packageName) && settings.webhookUrl.isNotBlank()
    }

    private fun decrypt(item: QueueItem): DecryptionResult {
        return try {
            DecryptionResult.Success(QueueCrypto.decrypt(item))
        } catch (_: QueueKeyMissingException) {
            DecryptionResult.KeyMissing
        } catch (_: QueuePayloadCorruptException) {
            DecryptionResult.Corrupt
        } catch (_: Exception) {
            DecryptionResult.Corrupt
        }
    }

    private fun calculateBackoff(attemptCount: Int): Long = 30_000L * (1L shl attemptCount.coerceAtMost(6)) + (0..4_000).random()

    private fun digest(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray())
        .joinToString("") { byte -> "%02x".format(byte) }

    private sealed interface DecryptionResult {
        data class Success(val payload: NotificationPayload) : DecryptionResult {
            fun item(row: QueueItem): PendingQueueItem = PendingQueueItem(
                row.id, payload, row.policyRevision, row.status, row.attemptCount, row.nextRetryAt, row.expiresAt, row.createdAt
            )
        }

        data object KeyMissing : DecryptionResult
        data object Corrupt : DecryptionResult
    }
}

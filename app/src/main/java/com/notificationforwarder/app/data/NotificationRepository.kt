package com.notificationforwarder.app.data

import android.content.Context
import com.notificationforwarder.app.settings.FilterMode
import com.notificationforwarder.app.settings.SettingsStore
import com.notificationforwarder.app.worker.DeliveryCoordinator
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.security.MessageDigest
import java.util.UUID

class NotificationRepository(
    context: Context,
    private val dao: QueueDao = AppDatabase.getInstance(context).queueDao(),
    private val settingsStore: SettingsStore = SettingsStore(context)
) {

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
        capturedAt: Long,
        bigText: String? = null
    ) {
        purgeExpired(capturedAt)
        if (SensitiveNotificationFilter.evaluate(packageName, title, text, bigText) != null) return
        val payload = NotificationPayload(packageName, appName, title, text, postedAt, notificationKey, bigText?.takeIf { it.isNotEmpty() }, UUID.randomUUID().toString())
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
            val candidate = QueueItem(
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
            val exactDuplicate = dao.findByNotificationKeyDigest(candidate.notificationKeyDigest).any { row ->
                when (val result = decrypt(row)) {
                    is DecryptionResult.Success -> result.payload.packageName == payload.packageName &&
                        result.payload.notificationKey == payload.notificationKey &&
                        result.payload.postedAt == payload.postedAt &&
                        result.payload.title == payload.title &&
                        result.payload.text == payload.text &&
                        result.payload.bigText?.takeIf { it.isNotEmpty() } == payload.bigText
                    else -> false
                }
            }
            if (!exactDuplicate) dao.insert(candidate)
        }
    }

    suspend fun getPending(limit: Int): List<PendingQueueItem> {
        return DeliveryCoordinator.withState {
        val now = System.currentTimeMillis()
        purgeExpiredLocked(now)
        val pending = mutableListOf<PendingQueueItem>()
        dao.getPending(now, limit).forEach { row ->
            when (val result = decrypt(row)) {
                is DecryptionResult.Success -> migratedItemLocked(row, result.payload)?.let { pending += it }
                DecryptionResult.KeyMissing -> {
                    handleKeyLossLocked()
                    return@withState emptyList()
                }
                DecryptionResult.Corrupt -> {
                    DeliveryCoordinator.cancelRegisteredCallsLocked(setOf(row.id))
                    dao.deleteCorruptAndCount(row.id)
                }
            }
        }
        return@withState pending
        }
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
            is DecryptionResult.Success -> migratedItemLocked(row, result.payload)
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
        val now = System.currentTimeMillis()
        val retention = settingsStore.readAll().retentionHours
        val entries = mutableListOf<QueueEntry>()
        var keyMissing = false
        for (row in rows) {
            val entry = DeliveryCoordinator.withState {
                val current = dao.findById(row.id) ?: return@withState null
                if (expireIfNeededLocked(current.id, current.createdAt, retention, now) ||
                    (current.expiresAt != null && current.expiresAt <= now)) return@withState null
                when (val result = decrypt(current)) {
                    is DecryptionResult.Success -> migratedItemLocked(current, result.payload)?.let { QueueEntry(current.id, it.payload, current.status, current.attemptCount, current.lastErrorCode) }
                    DecryptionResult.KeyMissing -> { keyMissing = true; null }
                    DecryptionResult.Corrupt -> {
                    DeliveryCoordinator.cancelRegisteredCallsLocked(setOf(row.id))
                    dao.deleteCorruptAndCount(row.id); null
                    }
                }
            }
            if (entry != null) entries += entry
        }
        if (keyMissing) { handleKeyLoss(); return@map emptyList() }
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

    private fun canCaptureWith(settings: com.notificationforwarder.app.settings.AppSettings, packageName: String): Boolean {
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

    private suspend fun migratedItemLocked(row: QueueItem, decoded: NotificationPayload): PendingQueueItem? {
        if (decoded.eventId != null && !UUID_V4.matches(decoded.eventId)) {
            DeliveryCoordinator.cancelRegisteredCallsLocked(setOf(row.id))
            dao.deleteCorruptAndCount(row.id)
            return null
        }
        if (SensitiveNotificationFilter.evaluate(decoded.packageName, decoded.title, decoded.text, decoded.bigText) != null) {
            deleteQueueItemLocked(row.id)
            return null
        }
        val eventId = decoded.eventId ?: UUID.randomUUID().toString()
        val payload = decoded.copy(bigText = decoded.bigText?.takeIf { it.isNotEmpty() }, eventId = eventId)
        if (payload != decoded) {
            try {
                val encrypted = QueueCrypto.encrypt(payload, requireExistingKey = true)
                if (dao.updateCiphertext(row.id, encrypted.ciphertext, encrypted.iv, QueueCrypto.FORMAT_VERSION) != 1) return null
            } catch (cancel: kotlinx.coroutines.CancellationException) { throw cancel }
            catch (_: QueueKeyMissingException) { handleKeyLossLocked(); return null }
            catch (_: Exception) { return null }
        }
        return PendingQueueItem(row.id, payload, row.policyRevision, row.status, row.attemptCount, row.nextRetryAt, row.expiresAt, row.createdAt)
    }

    private fun calculateBackoff(attemptCount: Int): Long = 30_000L * (1L shl attemptCount.coerceAtMost(6)) + (0..4_000).random()

    private companion object {
        val UUID_V4 = Regex("^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$", RegexOption.IGNORE_CASE)
    }

    private fun digest(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray())
        .joinToString("") { byte -> "%02x".format(byte) }

    private sealed interface DecryptionResult {
        data class Success(val payload: NotificationPayload) : DecryptionResult

        data object KeyMissing : DecryptionResult
        data object Corrupt : DecryptionResult
    }
}

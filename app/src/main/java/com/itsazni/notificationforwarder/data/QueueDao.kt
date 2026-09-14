package com.itsazni.notificationforwarder.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
abstract class QueueDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insert(item: QueueItem): Long

    @Query("SELECT * FROM notification_queue WHERE status = 'PENDING' AND nextRetryAt <= :now AND (expiresAt IS NULL OR expiresAt > :now) ORDER BY createdAt ASC LIMIT :limit")
    abstract suspend fun getPending(now: Long, limit: Int): List<QueueItem>

    @Query("UPDATE notification_queue SET status = 'SENDING', updatedAt = :now WHERE id IN (:ids) AND status = 'PENDING'")
    abstract suspend fun markSending(ids: List<Long>, now: Long)

    @Query("SELECT * FROM notification_queue WHERE id = :id LIMIT 1")
    abstract suspend fun findById(id: Long): QueueItem?

    @Query("SELECT COUNT(*) FROM notification_queue")
    abstract suspend fun countRows(): Int

    @Query("UPDATE notification_queue SET status = 'PENDING', updatedAt = :now WHERE status = 'SENDING' AND (expiresAt IS NULL OR expiresAt > :now)")
    abstract suspend fun recoverSending(now: Long)

    @Query("UPDATE notification_queue SET status = :status, attemptCount = :attemptCount, nextRetryAt = :nextRetryAt, lastErrorCode = :errorCode, updatedAt = :updatedAt WHERE id = :id")
    abstract suspend fun updateFailure(id: Long, status: QueueStatus, attemptCount: Int, nextRetryAt: Long, errorCode: String, updatedAt: Long)

    @Query("DELETE FROM notification_queue WHERE id = :id")
    abstract suspend fun deleteById(id: Long): Int

    @Query("DELETE FROM notification_queue WHERE expiresAt IS NOT NULL AND expiresAt <= :now")
    abstract suspend fun deleteExpired(now: Long): Int

    @Query("SELECT id FROM notification_queue WHERE expiresAt IS NOT NULL AND expiresAt <= :now")
    abstract suspend fun findExpiredIds(now: Long): List<Long>

    @Query("UPDATE notification_queue SET expiresAt = CASE WHEN :retentionMillis < 0 THEN NULL ELSE createdAt + :retentionMillis END, updatedAt = :now")
    abstract suspend fun updateExpiry(retentionMillis: Long, now: Long)

    @Query("DELETE FROM notification_queue")
    abstract suspend fun clearAll()

    @Query("UPDATE queue_metrics SET sentCount = sentCount + 1 WHERE id = 1")
    abstract suspend fun incrementSent()

    @Query("UPDATE queue_metrics SET failedCount = failedCount + 1 WHERE id = 1")
    abstract suspend fun incrementFailed()

    @Query("UPDATE queue_metrics SET failedCount = failedCount + :count WHERE id = 1")
    abstract suspend fun incrementFailedBy(count: Int)

    @Query("UPDATE queue_metrics SET expiredCount = expiredCount + :count WHERE id = 1")
    abstract suspend fun incrementExpired(count: Int)

    @Query("SELECT (SELECT COUNT(*) FROM notification_queue WHERE status = 'PENDING') AS pendingCount, (SELECT COUNT(*) FROM notification_queue WHERE status = 'SENDING') AS sendingCount, sentCount, failedCount, expiredCount FROM queue_metrics WHERE id = 1")
    abstract fun observeStats(): Flow<QueueStats>

    @Query("SELECT * FROM notification_queue ORDER BY createdAt DESC LIMIT :limit")
    abstract fun observeRecent(limit: Int): Flow<List<QueueItem>>

    @Query("INSERT OR IGNORE INTO queue_metrics(id, sentCount, failedCount, expiredCount) VALUES(1, 0, 0, 0)")
    abstract suspend fun ensureMetrics()

    @Transaction
    open suspend fun deleteExpiredAndCount(now: Long): Int {
        ensureMetrics()
        val count = deleteExpired(now)
        if (count > 0) {
            incrementExpired(count)
        }
        return count
    }

    @Transaction
    open suspend fun markSentAndDelete(id: Long) {
        if (deleteById(id) > 0) {
            incrementSent()
        }
    }

    @Transaction
    open suspend fun markFailedAndDelete(id: Long) {
        if (deleteById(id) > 0) {
            incrementFailed()
        }
    }

    @Transaction
    open suspend fun deleteCorruptAndCount(id: Long) {
        if (deleteById(id) > 0) {
            incrementFailed()
        }
    }

    @Transaction
    open suspend fun expireByIdAndCount(id: Long): Boolean {
        if (deleteById(id) > 0) {
            ensureMetrics()
            incrementExpired(1)
            return true
        }
        return false
    }

    @Transaction
    open suspend fun clearAllAndCountAsFailed() {
        val count = countRows()
        clearAll()
        if (count > 0) {
            ensureMetrics()
            incrementFailedBy(count)
        }
    }
}

package com.itsazni.notificationforwarder.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.TypeConverter

enum class QueueStatus { PENDING, SENDING, SENT, FAILED }

class QueueConverters {
    @TypeConverter
    fun fromStatus(value: QueueStatus): String = value.name

    @TypeConverter
    fun toStatus(value: String): QueueStatus = QueueStatus.valueOf(value)
}

@Entity(
    tableName = "notification_queue",
    indices = [Index(value = ["notificationKeyDigest"], unique = true)]
)
data class QueueItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val encryptedPayload: String,
    val iv: String,
    val encryptionVersion: Int,
    val notificationKeyDigest: String,
    val policyRevision: Long,
    val status: QueueStatus = QueueStatus.PENDING,
    val attemptCount: Int = 0,
    val nextRetryAt: Long = 0,
    val expiresAt: Long? = null,
    val lastErrorCode: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

data class PendingQueueItem(
    val id: Long,
    val payload: NotificationPayload,
    val policyRevision: Long,
    val status: QueueStatus,
    val attemptCount: Int,
    val nextRetryAt: Long,
    val expiresAt: Long?,
    val createdAt: Long
)

data class QueueEntry(
    val id: Long,
    val payload: NotificationPayload,
    val status: QueueStatus,
    val attemptCount: Int,
    val lastErrorCode: String?
)

data class QueueStats(
    val pendingCount: Int,
    val sendingCount: Int,
    val sentCount: Int,
    val failedCount: Int,
    val expiredCount: Int = 0
)

@Entity(tableName = "queue_metrics")
data class QueueMetrics(
    @PrimaryKey val id: Int = 1,
    val sentCount: Int = 0,
    val failedCount: Int = 0,
    val expiredCount: Int = 0
)

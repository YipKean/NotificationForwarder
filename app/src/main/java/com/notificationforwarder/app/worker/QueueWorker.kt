package com.notificationforwarder.app.worker

import android.content.Context
import android.provider.Settings
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.notificationforwarder.app.data.NotificationRepository
import com.notificationforwarder.app.data.RetryPolicy
import com.notificationforwarder.app.network.PreparedWebhookRequest
import com.notificationforwarder.app.network.SendResult
import com.notificationforwarder.app.network.WebhookClient
import com.notificationforwarder.app.settings.AuthMode
import com.notificationforwarder.app.settings.SettingsStore

class QueueWorker(
    appContext: Context,
    workerParameters: WorkerParameters
) : CoroutineWorker(appContext, workerParameters) {

    private val repository = NotificationRepository(appContext)
    private val settings = SettingsStore(appContext)
    private val webhookClient = WebhookClient()

    override suspend fun doWork(): Result = DeliveryCoordinator.withDelivery { doWorkInternal() }

    private suspend fun doWorkInternal(): Result {
        repository.recoverSending()
        repository.purgeExpired()
        DeliveryCoordinator.withState {
            repository.recalculateRetentionLocked(settings.readAll().retentionHours)
        }
        val initial = settings.readAll()
        if (!initial.forwardingEnabled || initial.webhookUrl.isBlank()) {
            return Result.success()
        }

        val itemIds = repository.getPending(initial.batchSize).map { it.id }
        if (itemIds.isEmpty()) {
            return continuationResult(minimumDelayMillis = 30_000)
        }

        repository.markSending(itemIds)
        itemIds.forEach { id ->
            val plan = DeliveryCoordinator.withState {
                val current = settings.readAll()
                val item = repository.getForDeliveryLocked(id)
                if (item == null) {
                    return@withState null
                }
                if (!current.forwardingEnabled ||
                    current.policyRevision != item.policyRevision ||
                    !current.filterPackages.contains(item.payload.packageName)
                ) {
                    repository.deleteQueueItemLocked(id)
                    return@withState null
                }
                if (repository.expireIfNeededLocked(
                        id = item.id,
                        createdAt = item.createdAt,
                        retentionHours = current.retentionHours,
                        now = System.currentTimeMillis()
                    )
                ) {
                    return@withState null
                }

                val prepared = webhookClient.prepare(
                    url = current.webhookUrl,
                    method = current.webhookMethod,
                    headers = buildHeaders(current.authMode, current.bearerToken, settings.parseHeaders(current.customHeadersRaw)),
                    queryParams = settings.parseQueryParams(current.queryParamsRaw),
                    payloadTemplate = current.payloadTemplateRaw,
                    item = item.payload,
                    deviceId = deviceId()
                )
                when (prepared) {
                    is PreparedWebhookRequest.Ready -> {
                        DeliveryCoordinator.registerCallLocked(id, prepared.call)
                        DeliveryPlan.Ready(prepared, current.maxRetries, item.attemptCount)
                    }
                    is PreparedWebhookRequest.Rejected -> DeliveryPlan.Rejected(prepared.result, current.maxRetries, item.attemptCount)
                }
            }

            when (plan) {
                is DeliveryPlan.Ready -> {
                    val result = try {
                        webhookClient.execute(plan.request)
                    } finally {
                        DeliveryCoordinator.unregisterCall(id, plan.request.call)
                    }
                    if (result.success) {
                        repository.markSent(id)
                    } else {
                        repository.markFailure(
                            id = id,
                            attemptCount = RetryPolicy.nextAttempt(plan.attemptCount),
                            maxRetry = plan.maxRetries,
                            errorCode = result.message,
                            permanent = result.isPermanentFailure
                        )
                    }
                }
                is DeliveryPlan.Rejected -> {
                    repository.markFailure(
                        id = id,
                        attemptCount = RetryPolicy.nextAttempt(plan.attemptCount),
                        maxRetry = plan.maxRetries,
                        errorCode = plan.result.message,
                        permanent = plan.result.isPermanentFailure
                    )
                }
                null -> Unit
            }
        }

        return continuationResult()
    }

    private suspend fun continuationResult(minimumDelayMillis: Long = 1_000): Result = DeliveryCoordinator.withState {
        // Include delayed rows and serialize scheduling with policy cancellation.
        val current = settings.readAll()
        if (current.forwardingEnabled && current.webhookUrl.isNotBlank()) {
            repository.nextPendingAtLocked()?.let { next ->
                // A migration write can fail while a row stays due. Avoid a hot loop.
                WorkerScheduler.enqueueContinuation(applicationContext, id, (next - System.currentTimeMillis()).coerceAtLeast(minimumDelayMillis))
            }
        }
        Result.success()
    }

    private fun deviceId(): String = Settings.Secure.getString(
        applicationContext.contentResolver,
        Settings.Secure.ANDROID_ID
    ) ?: "unknown-device"

    private fun buildHeaders(
        authMode: AuthMode,
        bearerToken: String,
        customHeaders: Map<String, String>
    ): Map<String, String> {
        val finalHeaders = linkedMapOf("Content-Type" to "application/json")
        if (authMode == AuthMode.BEARER && bearerToken.isNotBlank()) {
            finalHeaders["Authorization"] = "Bearer $bearerToken"
        }
        finalHeaders.putAll(customHeaders)
        return finalHeaders
    }

    private sealed interface DeliveryPlan {
        data class Ready(val request: PreparedWebhookRequest.Ready, val maxRetries: Int, val attemptCount: Int) : DeliveryPlan
        data class Rejected(val result: SendResult, val maxRetries: Int, val attemptCount: Int) : DeliveryPlan
    }
}

package com.notificationforwarder.app.worker

import android.content.Context
import android.provider.Settings
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.notificationforwarder.app.data.NotificationRepository
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
            return Result.success()
        }

        repository.markSending(itemIds)
        var shouldRetry = false
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
                        val attempt = plan.attemptCount + 1
                        repository.markFailure(
                            id = id,
                            attemptCount = if (result.isPermanentFailure) plan.maxRetries else attempt,
                            maxRetry = plan.maxRetries,
                            errorCode = result.message
                        )
                        if (!result.isPermanentFailure) {
                            shouldRetry = true
                        }
                    }
                }
                is DeliveryPlan.Rejected -> {
                    repository.markFailure(
                        id = id,
                        attemptCount = if (plan.result.isPermanentFailure) plan.maxRetries else plan.attemptCount + 1,
                        maxRetry = plan.maxRetries,
                        errorCode = plan.result.message
                    )
                    if (!plan.result.isPermanentFailure) {
                        shouldRetry = true
                    }
                }
                null -> Unit
            }
        }

        return if (shouldRetry) Result.retry() else Result.success()
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

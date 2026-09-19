package com.notificationforwarder.app.network

import com.google.gson.GsonBuilder
import com.google.gson.JsonParser
import com.notificationforwarder.app.data.NotificationPayload
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Call
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

data class SendResult(
    val success: Boolean,
    val isPermanentFailure: Boolean,
    val message: String
)

sealed class PreparedWebhookRequest {
    data class Ready(val call: Call) : PreparedWebhookRequest()
    data class Rejected(val result: SendResult) : PreparedWebhookRequest()
}

class WebhookClient {
    private val gson = GsonBuilder().serializeNulls().create()
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .followRedirects(false)
        .followSslRedirects(false)
        .build()

    fun prepare(
        url: String,
        method: String,
        headers: Map<String, String>,
        queryParams: Map<String, String>,
        payloadTemplate: String,
        item: NotificationPayload,
        deviceId: String
    ): PreparedWebhookRequest {
        return try {
            if (!EndpointValidator.isValid(url)) {
                return PreparedWebhookRequest.Rejected(SendResult(false, true, "invalid_endpoint"))
            }
            if (method.uppercase() !in SUPPORTED_METHODS) {
                return PreparedWebhookRequest.Rejected(SendResult(false, true, "invalid_method"))
            }
            val eventId = item.eventId?.takeIf { it.matches(UUID_V4) }
                ?: return PreparedWebhookRequest.Rejected(SendResult(false, true, "invalid_event_id"))
            val vars = mapOf(
                "deviceId" to escapeJson(deviceId),
                "packageName" to escapeJson(item.packageName),
                "appName" to escapeJson(item.appName),
                "title" to escapeJson(item.title),
                "text" to escapeJson(item.text),
                "bigText" to escapeJson(item.bigText.orEmpty()),
                "bigTextJson" to gson.toJson(item.bigText),
                "eventId" to escapeJson(eventId),
                "postedAt" to item.postedAt.toString(),
                "notificationKey" to escapeJson(item.notificationKey)
            )

            val finalUrl = buildUrl(url, queryParams)
            val bodyJson = if (payloadTemplate.isBlank()) {
                gson.toJson(
                    mapOf(
                        "schemaVersion" to 2,
                        "eventId" to eventId,
                        "deviceId" to deviceId,
                        "packageName" to item.packageName,
                        "appName" to item.appName,
                        "title" to item.title,
                        "text" to item.text,
                        "bigText" to item.bigText,
                        "postedAt" to item.postedAt,
                        "notificationKey" to item.notificationKey
                    )
                )
            } else {
                renderTemplate(payloadTemplate, vars)
            }

            val isGet = method.equals("GET", ignoreCase = true)
            val requestBuilder = Request.Builder()
                .url(finalUrl)

            if (isGet) {
                requestBuilder.get()
            } else {
                val contentType = headers["Content-Type"] ?: "application/json"
                requestBuilder.method(method.uppercase(), bodyJson.toRequestBody(contentType.toMediaType()))
            }

            headers.forEach { (k, v) ->
                requestBuilder.addHeader(k, v)
            }

            PreparedWebhookRequest.Ready(client.newCall(requestBuilder.build()))
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: com.google.gson.JsonParseException) {
            PreparedWebhookRequest.Rejected(SendResult(false, true, "invalid_payload"))
        } catch (e: IllegalArgumentException) {
            PreparedWebhookRequest.Rejected(SendResult(false, true, "invalid_request"))
        } catch (_: Exception) {
            PreparedWebhookRequest.Rejected(SendResult(false, false, "network_failure"))
        }
    }

    suspend fun execute(request: PreparedWebhookRequest.Ready): SendResult {
        return suspendCancellableCoroutine { continuation ->
            continuation.invokeOnCancellation { request.call.cancel() }
            request.call.enqueue(object : okhttp3.Callback {
                override fun onFailure(call: okhttp3.Call, e: java.io.IOException) {
                    if (continuation.isActive) continuation.resume(SendResult(false, false, "network_failure"))
                }

                override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                    response.use {
                        val result = if (it.code in 300..399) {
                            SendResult(false, true, "redirect_rejected")
                        } else if (it.isSuccessful) {
                            SendResult(true, false, "OK")
                        } else {
                            val permanent = it.code in 400..499 && it.code != 429
                            SendResult(false, permanent, "HTTP ${it.code}")
                        }
                        if (continuation.isActive) continuation.resume(result)
                    }
                }
            })
        }
    }

    suspend fun send(
        url: String,
        method: String,
        headers: Map<String, String>,
        queryParams: Map<String, String>,
        payloadTemplate: String,
        item: NotificationPayload,
        deviceId: String
    ): SendResult {
        return when (val prepared = prepare(url, method, headers, queryParams, payloadTemplate, item, deviceId)) {
            is PreparedWebhookRequest.Ready -> execute(prepared)
            is PreparedWebhookRequest.Rejected -> prepared.result
        }
    }

    private fun buildUrl(baseUrl: String, params: Map<String, String>): String {
        if (params.isEmpty()) return baseUrl
        val httpUrl = baseUrl.toHttpUrlOrNull() ?: return baseUrl
        val builder = httpUrl.newBuilder()
        params.forEach { (k, v) -> builder.addQueryParameter(k, v) }
        return builder.build().toString()
    }

    private fun renderTemplate(template: String, vars: Map<String, String>): String {
        val token = Regex("\\{([A-Za-z][A-Za-z0-9]*)\\}")
        val result = token.replace(template) { match -> vars[match.groupValues[1]] ?: match.value }
        // validate JSON to catch syntax errors early
        JsonParser.parseString(result)
        return result
    }

    private fun escapeJson(text: String): String {
        val encoded = gson.toJson(text)
        return encoded.substring(1, encoded.length - 1)
    }

    companion object {
        private val SUPPORTED_METHODS = setOf("GET", "POST", "PUT", "PATCH")
        private val UUID_V4 = Regex("^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$", RegexOption.IGNORE_CASE)
    }
}

object EndpointValidator {
    fun isValid(value: String): Boolean {
        val url = value.toHttpUrlOrNull() ?: return false
        return url.scheme == "https" && url.host.isNotBlank() &&
            url.username.isEmpty() && url.password.isEmpty() && url.fragment == null
    }
}

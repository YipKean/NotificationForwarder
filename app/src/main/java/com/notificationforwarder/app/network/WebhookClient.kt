package com.notificationforwarder.app.network

import com.google.gson.Gson
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
    private val gson = Gson()
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
            val vars = mapOf(
                "deviceId" to deviceId,
                "packageName" to escapeJson(item.packageName),
                "appName" to escapeJson(item.appName),
                "title" to escapeJson(item.title),
                "text" to escapeJson(item.text),
                "postedAt" to item.postedAt.toString(),
                "notificationKey" to escapeJson(item.notificationKey)
            )

            val finalUrl = buildUrl(url, queryParams)
            val bodyJson = if (payloadTemplate.isBlank()) {
                gson.toJson(
                    mapOf(
                        "deviceId" to deviceId,
                        "packageName" to item.packageName,
                        "appName" to item.appName,
                        "title" to item.title,
                        "text" to item.text,
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
        var result = template
        vars.forEach { (k, v) ->
            result = result.replace("{$k}", v)
        }
        // validate JSON to catch syntax errors early
        JsonParser.parseString(result)
        return result
    }

    private fun escapeJson(text: String): String {
        return text
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\b", "\\b")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
    }

    companion object {
        private val SUPPORTED_METHODS = setOf("GET", "POST", "PUT", "PATCH")
    }
}

object EndpointValidator {
    fun isValid(value: String): Boolean {
        val url = value.toHttpUrlOrNull() ?: return false
        return url.scheme == "https" && url.host.isNotBlank() &&
            url.username.isEmpty() && url.password.isEmpty() && url.fragment == null
    }
}

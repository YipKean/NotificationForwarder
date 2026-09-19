package com.notificationforwarder.app

import com.notificationforwarder.app.data.NotificationPayload
import com.notificationforwarder.app.network.PreparedWebhookRequest
import com.notificationforwarder.app.network.WebhookClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WebhookClientTest {
    private val payload = NotificationPayload("com.test", "Test", "Title", "short \"quoted\"\nline\u0001", 123L, "key", "expanded 😀\n{postedAt}", "4a4a2f3c-929b-4988-896c-790733d68237")
    private fun body(template: String, item: NotificationPayload = payload): String {
        val prepared = WebhookClient().prepare("https://example.test/webhook", "POST", emptyMap(), emptyMap(), template, item, "device")
        assertTrue(prepared is PreparedWebhookRequest.Ready)
        return (prepared as PreparedWebhookRequest.Ready).call.request().body!!.let { body ->
            okio.Buffer().also { body.writeTo(it) }.readUtf8()
        }
    }

    @Test fun defaultPayloadIsSchemaV2AndPreservesExpandedText() {
        val json = body("")
        assertTrue(json.contains("\"schemaVersion\":2"))
        assertTrue(json.contains("\"eventId\":\"4a4a2f3c-929b-4988-896c-790733d68237\""))
        assertTrue(json.contains("expanded 😀\\n{postedAt}"))
        val decoded = com.google.gson.JsonParser.parseString(json).asJsonObject
        assertEquals(payload.text, decoded.get("text").asString)
        assertEquals(payload.bigText, decoded.get("bigText").asString)
        assertEquals(payload.eventId, decoded.get("eventId").asString)
        assertEquals(json, body(""))
    }

    @Test fun customTemplateSupportsQuotedAndJsonExpandedFieldsInOnePass() {
        val json = body("{\"schemaVersion\":2,\"eventId\":\"{eventId}\",\"text\":\"{text}\",\"bigText\":\"{bigText}\",\"bigTextJson\":{bigTextJson}}")
        assertTrue(json.contains("\\\"quoted\\\""))
        assertTrue(json.contains("expanded 😀"))
        assertTrue(json.contains("{postedAt}"))
        val decoded = com.google.gson.JsonParser.parseString(json).asJsonObject
        assertEquals(payload.text, decoded.get("text").asString)
        assertEquals(payload.bigText, decoded.get("bigText").asString)
        assertEquals(payload.bigText, decoded.get("bigTextJson").asString)
    }

    @Test fun absentExpandedTextUsesNullJsonValue() {
        val noBigText = payload.copy(bigText = null)
        val defaultJson = com.google.gson.JsonParser.parseString(body("", noBigText)).asJsonObject
        assertTrue(defaultJson.has("bigText"))
        assertTrue(defaultJson.get("bigText").isJsonNull)
        val prepared = WebhookClient().prepare("https://example.test/webhook", "POST", emptyMap(), emptyMap(), "{\"schemaVersion\":2,\"eventId\":\"{eventId}\",\"bigText\":{bigTextJson}}", noBigText, "device") as PreparedWebhookRequest.Ready
        val buffer = okio.Buffer(); prepared.call.request().body!!.writeTo(buffer)
        assertEquals("null", com.google.gson.JsonParser.parseString(buffer.readUtf8()).asJsonObject.get("bigText").toString())
    }

    @Test fun missingOrInvalidIdentityCannotConstructDelivery() {
        listOf(null, "", "not-a-uuid", "4a4a2f3c-929b-1988-896c-790733d68237").forEach { id ->
            val result = WebhookClient().prepare("https://example.test/webhook", "POST", emptyMap(), emptyMap(), "", payload.copy(eventId = id), "device")
            assertTrue(result is PreparedWebhookRequest.Rejected)
            assertEquals("invalid_event_id", (result as PreparedWebhookRequest.Rejected).result.message)
        }
    }

    @Test fun quotedAbsentExpandedTextIsEmptyAndInvalidJsonIsRejected() {
        val decoded = com.google.gson.JsonParser.parseString(body("{\"bigText\":\"{bigText}\"}", payload.copy(bigText = null))).asJsonObject
        assertEquals("", decoded.get("bigText").asString)
        val result = WebhookClient().prepare("https://example.test/webhook", "POST", emptyMap(), emptyMap(), "{\"text\":", payload, "device")
        assertTrue(result is PreparedWebhookRequest.Rejected)
    }
}

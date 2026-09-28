package com.notificationforwarder.app

import com.google.gson.Gson
import com.notificationforwarder.app.data.NotificationPayload
import com.notificationforwarder.app.data.QueueCrypto
import org.junit.Assert.*
import org.junit.Test

class QueuePayloadTest {
    private val legacy = """{"packageName":"com.test","appName":"Test","title":"Receipt","text":"RM1","postedAt":123,"notificationKey":"key"}"""

    @Test fun missingLegacyFieldsAreExplicitlyNull() {
        val decoded = QueueCrypto.decodePayload(legacy)
        assertNull(decoded.bigText)
        assertNull(decoded.eventId)
        assertEquals(123L, decoded.postedAt)
    }

    @Test fun currentPayloadRoundTripsWithoutAlteringTextOrIdentity() {
        val payload = NotificationPayload("com.test", "Test", "Receipt", "short\n\"quoted\"", 123L, "key",
            "expanded 😀\n{postedAt}\u0001", "4a4a2f3c-929b-4988-896c-790733d68237")
        assertEquals(payload, QueueCrypto.decodePayload(Gson().toJson(payload)))
    }

    @Test fun malformedRequiredFieldsRemainCorruption() {
        listOf("{}", legacy.replace("123", "1.5"), legacy.replace("123", "-1"),
            legacy.replace("\"RM1\"", "123"), legacy.dropLast(1) + ",\"eventId\":12}").forEach { json ->
            assertThrows(Exception::class.java) { QueueCrypto.decodePayload(json) }
        }
    }
}

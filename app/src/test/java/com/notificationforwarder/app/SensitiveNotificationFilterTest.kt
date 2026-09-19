package com.notificationforwarder.app

import com.notificationforwarder.app.data.SensitiveNotificationFilter
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import org.junit.Assert.assertEquals
import org.junit.Test

class SensitiveNotificationFilterTest {
    data class Fixture(val name: String, val title: String, val text: String, val bigText: String?, val blocked: Boolean, val ruleId: String?)

    @Test fun sharedFixturesMatchAndroidEvaluator() {
        val file = listOf("test-fixtures/sensitive-notifications.json", "../test-fixtures/sensitive-notifications.json", "../../test-fixtures/sensitive-notifications.json")
            .map { java.io.File(it) }.firstOrNull { it.exists() } ?: error("shared fixture missing")
        val json = file.readText()
        val fixtures: List<Fixture> = Gson().fromJson(json, object : TypeToken<List<Fixture>>() {}.type)
        fixtures.forEach { fixture ->
            val result = SensitiveNotificationFilter.evaluate("test", fixture.title, fixture.text, fixture.bigText)
            assertEquals(fixture.name, fixture.blocked, result != null)
            assertEquals(fixture.name, fixture.ruleId, result?.ruleId)
        }
    }

    @Test fun blocksSensitiveFieldsAndJoinedContext() {
        assertEquals("otp_one_time_password", SensitiveNotificationFilter.evaluate("test", "Security", "Your OTP is 123456", null)?.ruleId)
        assertEquals("verification_code", SensitiveNotificationFilter.evaluate("test", "Verification", "code 123456", null)?.ruleId)
        assertEquals(null, SensitiveNotificationFilter.evaluate("test", "Payment approved", "MYR 18.90 paid to ABC Kopitiam", null))
        assertEquals(null, SensitiveNotificationFilter.evaluate("test", "MYR 18.90 paid to Taco House", "Receipt 123456", null))
    }

    @Test fun normalizesUnicodeAndZeroWidthFormatting() {
        assertEquals("otp_one_time_password", SensitiveNotificationFilter.evaluate("test", "Your O\u200BTP", "is 123456", null)?.ruleId)
    }

    @Test fun packageConstrainedRulesDoNotApplyToOtherApps() {
        assertEquals(1, SensitiveNotificationFilter.CATALOGUE_VERSION)
        val rules = listOf(SensitiveNotificationFilter.Rule("source-example", "security", Regex("example"), setOf("com.example.bank")))
        assertEquals("source-example", SensitiveNotificationFilter.evaluate("com.example.bank", "", "example", null, rules)?.ruleId)
        assertEquals(null, SensitiveNotificationFilter.evaluate("com.other", "", "example", null, rules))
    }
}

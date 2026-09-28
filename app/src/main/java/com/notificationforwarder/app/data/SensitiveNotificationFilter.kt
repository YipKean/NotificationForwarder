package com.notificationforwarder.app.data

import java.text.Normalizer
import java.util.Locale

data class FilterRejection(val ruleId: String, val category: String)

// Keep definitions and shared fixtures aligned with webhook/sensitive-notification-filter.js.
object SensitiveNotificationFilter {
    const val CATALOGUE_VERSION = 1

    data class Rule(
        val id: String,
        val category: String,
        val pattern: Regex,
        val packages: Set<String>? = null
    )

    private fun wholeTerm(pattern: String) = Regex("""(?<![\p{L}\p{N}_])(?:$pattern)(?![\p{L}\p{N}_])""")

    private val rules = listOf(
        Rule("otp_one_time_password", "otp", wholeTerm("""otp|tac|one[- ]time (?:password|passcode|code)|kata laluan sekali guna""")),
        Rule("verification_code", "verification", wholeTerm("""(?:verification|authentication|login|security|pengesahan) (?:code|kod)|kod pengesahan""")),
        Rule("login_notice", "login_device", wholeTerm("""(?:log ?in|sign[ -]?in)(?: (?:was|is|has been))? (?:successful|success|detected|attempt|alert|approved|denied|request|required)|(?:successful|new|unrecognized|unknown) (?:log ?in|sign[ -]?in)|log masuk(?: (?:anda|telah))? (?:berjaya|dikesan|diluluskan)|signed in""")),
        Rule("device_verification", "login_device", wholeTerm("""(?:new|unrecognized|unknown) device|device (?:verification|registered|registration|linked|activated)|(?:verify|register|link) (?:your |this |a )?device|peranti (?:baharu|baru|tidak dikenali|disahkan|didaftar(?:kan)?|pendaftaran)|peranti.{0,50}(?:didaftar(?:kan)?|disahkan)""")),
        Rule("approval_request", "approval", wholeTerm("""approve|authori[sz]e|approval|authori[sz]ation|luluskan|meluluskan|kelulusan|(?:tap|click|please|sila).{0,40}(?:confirm|sahkan)""")),
        Rule("secure2u_security", "security", wholeTerm("""secure ?2u""")),
        Rule("security_notice", "security", wholeTerm("""security (?:alert|notice|request)|keselamatan (?:akaun|transaksi)"""))
    )

    fun normalize(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFKC)
        .replace(Regex("""\p{Cf}"""), "")
        .replace(Regex("""[\s\p{Z}\u0085]+"""), " ")
        .trim()
        .lowercase(Locale.ROOT)

    fun evaluate(
        packageName: String,
        title: String,
        text: String,
        bigText: String?,
        catalogue: List<Rule> = rules
    ): FilterRejection? {
        val fields = listOf(title, text, bigText.orEmpty())
        val candidates = (fields + fields.joinToString(" ")).map(::normalize)
        for (rule in catalogue) {
            if (rule.packages != null && packageName !in rule.packages) continue
            if (candidates.any { rule.pattern.containsMatchIn(it) }) {
                return FilterRejection(rule.id, rule.category)
            }
        }
        return null
    }
}

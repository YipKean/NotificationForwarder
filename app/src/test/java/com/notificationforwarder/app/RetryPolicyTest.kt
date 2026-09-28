package com.notificationforwarder.app

import com.notificationforwarder.app.data.RetryPolicy
import org.junit.Assert.assertEquals
import org.junit.Test

class RetryPolicyTest {
    @Test fun delayGrowsThenStaysBoundedDuringLongOutage() {
        assertEquals(60_000L, RetryPolicy.backoffMillis(1, 10, 0))
        assertEquals(120_000L, RetryPolicy.backoffMillis(2, 10, 0))
        assertEquals(1_920_000L, RetryPolicy.backoffMillis(6, 10, 0))
        assertEquals(1_924_000L, RetryPolicy.backoffMillis(100, 10, 4_000))
        assertEquals(1_920_000L, RetryPolicy.backoffMillis(Int.MAX_VALUE, 20, 0))
    }

    @Test fun configuredLimitCapsDelayWithoutResettingAttempts() {
        assertEquals(120_000L, RetryPolicy.backoffMillis(20, 2, 0))
        assertEquals(21, RetryPolicy.nextAttempt(20))
        assertEquals(Int.MAX_VALUE, RetryPolicy.nextAttempt(Int.MAX_VALUE))
    }
}

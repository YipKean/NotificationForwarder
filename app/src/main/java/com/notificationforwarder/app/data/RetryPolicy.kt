package com.notificationforwarder.app.data

internal object RetryPolicy {
    fun nextAttempt(previous: Int): Int = if (previous == Int.MAX_VALUE) previous else previous + 1

    // The legacy maxRetries preference now caps exponential growth, never retention.
    fun backoffMillis(attempt: Int, maxRetries: Int, jitterMillis: Int = (0..4_000).random()): Long =
        30_000L * (1L shl attempt.coerceIn(1, maxRetries.coerceIn(1, 6))) + jitterMillis.coerceIn(0, 4_000)
}

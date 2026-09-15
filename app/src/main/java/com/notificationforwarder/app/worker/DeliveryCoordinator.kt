package com.notificationforwarder.app.worker

import okhttp3.Call
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

object DeliveryCoordinator {
    private val deliveryMutex = Mutex()
    private val stateMutex = Mutex()
    private val activeCalls = linkedMapOf<Long, Call>()

    suspend fun <T> withDelivery(block: suspend () -> T): T = deliveryMutex.withLock { block() }

    suspend fun <T> withState(block: suspend () -> T): T = stateMutex.withLock { block() }

    fun registerCallLocked(id: Long, call: Call) {
        activeCalls[id]?.cancel()
        activeCalls[id] = call
    }

    fun cancelRegisteredCallsLocked(ids: Set<Long>? = null) {
        val calls = if (ids == null) {
            activeCalls.values.toList()
        } else {
            ids.mapNotNull { activeCalls[it] }
        }
        calls.forEach { it.cancel() }
        if (ids == null) {
            activeCalls.clear()
        } else {
            ids.forEach { activeCalls.remove(it) }
        }
    }

    suspend fun unregisterCall(id: Long, call: Call) {
        withContext(NonCancellable) {
            stateMutex.withLock {
                if (activeCalls[id] === call) {
                    activeCalls.remove(id)
                }
            }
        }
    }
}

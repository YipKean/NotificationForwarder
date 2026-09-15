package com.notificationforwarder.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.notificationforwarder.app.data.SecureInitialization
import com.notificationforwarder.app.worker.WorkerScheduler

class BootCompletedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_LOCKED_BOOT_COMPLETED -> {
                if (!SecureInitialization.ensure(context)) {
                    return
                }
                WorkerScheduler.ensurePeriodic(context)
                WorkerScheduler.enqueueImmediate(context)
            }
        }
    }
}

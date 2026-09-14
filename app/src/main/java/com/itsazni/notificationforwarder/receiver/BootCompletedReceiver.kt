package com.itsazni.notificationforwarder.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.itsazni.notificationforwarder.data.SecureInitialization
import com.itsazni.notificationforwarder.worker.WorkerScheduler

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

package com.notificationforwarder.app

import android.app.Application
import com.notificationforwarder.app.data.SecureInitialization
import com.notificationforwarder.app.worker.WorkerScheduler

class NotificationForwarderApp : Application() {
    override fun onCreate() {
        super.onCreate()
        if (!SecureInitialization.ensure(this)) {
            return
        }
        WorkerScheduler.ensurePeriodic(this)
    }
}

package com.itsazni.notificationforwarder.data

data class NotificationPayload(
    val packageName: String,
    val appName: String,
    val title: String,
    val text: String,
    val postedAt: Long,
    val notificationKey: String
)

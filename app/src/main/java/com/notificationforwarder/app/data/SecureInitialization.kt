package com.notificationforwarder.app.data

import android.content.Context
import com.notificationforwarder.app.settings.SettingsStore
import java.io.File

object SecureInitialization {
    private const val LEGACY_DATABASE = "notif_forwarder.db"
    private const val SECURE_DATABASE = "notif_secure.db"
    private val lock = Any()

    fun ensure(context: Context): Boolean {
        synchronized(lock) {
            return ensureInternal(context)
        }
    }

    fun <T> withInitialization(context: Context, block: () -> T): T {
        synchronized(lock) {
            check(ensureInternal(context)) { "secure_initialization_failed" }
            return block()
        }
    }

    private fun ensureInternal(context: Context): Boolean {
        val appContext = context.applicationContext
        val settings = SettingsStore(appContext)
        if (settings.hardeningVersion >= SettingsStore.CURRENT_HARDENING_VERSION) {
            return true
        }

        if (!settings.disableAndClearAllowlist()) return false
        val databases = listOf(LEGACY_DATABASE, SECURE_DATABASE)
        if (databases.any { name -> !deleteDatabaseFiles(appContext, name) }) {
            return false
        }
        return true
    }

    private fun deleteDatabaseFiles(context: Context, name: String): Boolean {
        val path = context.getDatabasePath(name)
        val candidates = listOf(
            path,
            File("${path.path}-wal"),
            File("${path.path}-shm"),
            File("${path.path}-journal")
        )
        val existed = candidates.any { it.exists() }
        context.deleteDatabase(name)
        return !candidates.any { it.exists() } && (!existed || !path.exists())
    }
}

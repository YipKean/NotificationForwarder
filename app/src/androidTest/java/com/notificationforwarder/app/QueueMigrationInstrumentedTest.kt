package com.notificationforwarder.app

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.notificationforwarder.app.data.AppDatabase
import com.notificationforwarder.app.data.NotificationPayload
import com.notificationforwarder.app.data.QueueCrypto
import com.notificationforwarder.app.data.QueueItem
import com.notificationforwarder.app.data.QueueStatus
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlinx.coroutines.runBlocking
import java.io.File

@RunWith(AndroidJUnit4::class)
class QueueMigrationInstrumentedTest {
    private lateinit var context: Context
    private lateinit var database: AppDatabase
    private lateinit var databaseFile: File

    @Before fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        databaseFile = context.getDatabasePath("queue-migration-${System.nanoTime()}.db")
        databaseFile.parentFile?.mkdirs()
        val legacy = SQLiteDatabase.openOrCreateDatabase(databaseFile, null)
        legacy.execSQL("CREATE TABLE notification_queue (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, encryptedPayload TEXT NOT NULL, iv TEXT NOT NULL, encryptionVersion INTEGER NOT NULL, notificationKeyDigest TEXT NOT NULL, policyRevision INTEGER NOT NULL, status TEXT NOT NULL, attemptCount INTEGER NOT NULL, nextRetryAt INTEGER NOT NULL, expiresAt INTEGER, lastErrorCode TEXT, createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL)")
        legacy.execSQL("CREATE UNIQUE INDEX index_notification_queue_notificationKeyDigest ON notification_queue(notificationKeyDigest)")
        legacy.execSQL("CREATE TABLE queue_metrics (id INTEGER NOT NULL PRIMARY KEY, sentCount INTEGER NOT NULL, failedCount INTEGER NOT NULL, expiredCount INTEGER NOT NULL)")
        legacy.execSQL("INSERT INTO queue_metrics VALUES (1, 0, 0, 0)")
        legacy.close()
        database = Room.databaseBuilder(context, AppDatabase::class.java, databaseFile.name)
            .addMigrations(AppDatabase.MIGRATION_1_2).build()
    }

    @After fun tearDown() { database.close(); databaseFile.delete() }

    @Test fun v2RoomSchemaUsesNonUniqueDigestIndexAndPreservesRows() = runBlocking {
        val dao = database.queueDao()
        dao.ensureMetrics()
        val encrypted = QueueCrypto.encrypt(NotificationPayload("com.test", "Test", "Payment", "RM1", 1L, "same", null, "4a4a2f3c-929b-4988-896c-790733d68237"))
        dao.insert(QueueItem(encryptedPayload = encrypted.ciphertext, iv = encrypted.iv, encryptionVersion = QueueCrypto.FORMAT_VERSION, notificationKeyDigest = "digest", policyRevision = 1L))
        dao.insert(QueueItem(encryptedPayload = encrypted.ciphertext, iv = encrypted.iv, encryptionVersion = QueueCrypto.FORMAT_VERSION, notificationKeyDigest = "digest", policyRevision = 1L))
        assertEquals(2, dao.countRows())
    }

    @Test fun encryptedLegacyPayloadCanBeReadAndRewrittenWithStableId() = runBlocking {
        val dao = database.queueDao(); dao.ensureMetrics()
        val encrypted = QueueCrypto.encrypt(NotificationPayload("com.test", "Test", "Payment", "RM1", 1L, "legacy", null, null))
        val id = dao.insert(QueueItem(encryptedPayload = encrypted.ciphertext, iv = encrypted.iv, encryptionVersion = QueueCrypto.FORMAT_VERSION, notificationKeyDigest = "legacy", policyRevision = 1L))
        val row = dao.findById(id)
        assertNotNull(row)
        val decoded = QueueCrypto.decrypt(row!!)
        assertEquals(null, decoded.eventId)
        val rewritten = QueueCrypto.encrypt(decoded.copy(eventId = java.util.UUID.randomUUID().toString()))
        dao.updateCiphertext(id, rewritten.ciphertext, rewritten.iv, QueueCrypto.FORMAT_VERSION)
        assertTrue(QueueCrypto.decrypt(dao.findById(id)!!).eventId!!.isNotBlank())
    }
}

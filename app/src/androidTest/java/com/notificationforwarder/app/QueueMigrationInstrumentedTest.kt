package com.notificationforwarder.app

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.notificationforwarder.app.data.AppDatabase
import com.notificationforwarder.app.data.NotificationPayload
import com.notificationforwarder.app.data.NotificationRepository
import com.notificationforwarder.app.data.QueueCrypto
import com.notificationforwarder.app.data.QueueItem
import com.notificationforwarder.app.data.QueueStatus
import com.notificationforwarder.app.settings.SettingsStore
import kotlinx.coroutines.async
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class QueueMigrationInstrumentedTest {
    private lateinit var context: Context
    private lateinit var dbFile: File
    private lateinit var database: AppDatabase
    private lateinit var settings: SettingsStore
    private lateinit var preferenceName: String
    private lateinit var preMigrationRow: QueueItem

    @Before fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        check(context.packageName == "com.notificationforwarder.app.ingestiontest") { "run with -PisolatedIngestionTests=true" }
        dbFile = context.getDatabasePath("ingestion-${System.nanoTime()}.db")
        dbFile.parentFile?.mkdirs()
        val legacy = SQLiteDatabase.openOrCreateDatabase(dbFile, null)
        legacy.execSQL("CREATE TABLE notification_queue (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, encryptedPayload TEXT NOT NULL, iv TEXT NOT NULL, encryptionVersion INTEGER NOT NULL, notificationKeyDigest TEXT NOT NULL, policyRevision INTEGER NOT NULL, status TEXT NOT NULL, attemptCount INTEGER NOT NULL, nextRetryAt INTEGER NOT NULL, expiresAt INTEGER, lastErrorCode TEXT, createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL)")
        legacy.execSQL("CREATE UNIQUE INDEX index_notification_queue_notificationKeyDigest ON notification_queue(notificationKeyDigest)")
        legacy.execSQL("CREATE TABLE queue_metrics (id INTEGER NOT NULL PRIMARY KEY, sentCount INTEGER NOT NULL, failedCount INTEGER NOT NULL, expiredCount INTEGER NOT NULL)")
        legacy.execSQL("INSERT INTO queue_metrics VALUES (1, 0, 0, 0)")
        val preexisting = QueueCrypto.encrypt(legacyPayload())
        val preexistingNow = System.currentTimeMillis()
        preMigrationRow = QueueItem(1L, preexisting.ciphertext, preexisting.iv, QueueCrypto.FORMAT_VERSION,
            "legacy", 7L, QueueStatus.PENDING, 2, preexistingNow - 1000,
            preexistingNow + 3_600_000, "network_failure", preexistingNow - 2000, preexistingNow - 1000)
        legacy.execSQL("INSERT INTO notification_queue VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)", arrayOf(
            preMigrationRow.id, preMigrationRow.encryptedPayload, preMigrationRow.iv, preMigrationRow.encryptionVersion,
            preMigrationRow.notificationKeyDigest, preMigrationRow.policyRevision, preMigrationRow.status.name,
            preMigrationRow.attemptCount, preMigrationRow.nextRetryAt, preMigrationRow.expiresAt,
            preMigrationRow.lastErrorCode, preMigrationRow.createdAt, preMigrationRow.updatedAt))
        legacy.execSQL("PRAGMA user_version=1")
        legacy.close()
        database = Room.databaseBuilder(context, AppDatabase::class.java, dbFile.name).addMigrations(AppDatabase.MIGRATION_1_2).build()
        preferenceName = "ingestion-prefs-${UUID.randomUUID()}"
        val isolatedContext = object : ContextWrapper(context) {
            override fun getApplicationContext(): Context = this
            override fun getSharedPreferences(name: String, mode: Int): SharedPreferences =
                context.getSharedPreferences(preferenceName, mode)
        }
        settings = SettingsStore(isolatedContext)
        settings.webhookUrl = "https://example.test/webhook"
        settings.filterPackages = setOf("com.example.bank")
        settings.forwardingEnabled = true
        settings.retentionHours = null
    }

    @After fun tearDown() {
        if (::database.isInitialized) database.close()
        if (::dbFile.isInitialized) context.deleteDatabase(dbFile.name)
        if (::preferenceName.isInitialized) context.deleteSharedPreferences(preferenceName)
    }

    private fun legacyPayload(bigText: String? = null, eventId: String? = null) = NotificationPayload("com.example.bank", "Bank", "Payment successful", "RM10 paid", 100L, "legacy-key", bigText = bigText, eventId = eventId)

    private suspend fun clearRows() { database.queueDao().clearAll(); database.queueDao().ensureMetrics() }
    private suspend fun insertEncrypted(payload: NotificationPayload, status: QueueStatus = QueueStatus.PENDING, digest: String = "legacy"): Long {
        val encrypted = QueueCrypto.encrypt(payload)
        val now = System.currentTimeMillis()
        return database.queueDao().insert(QueueItem(encryptedPayload = encrypted.ciphertext, iv = encrypted.iv, encryptionVersion = QueueCrypto.FORMAT_VERSION, notificationKeyDigest = digest, policyRevision = settings.policyRevision, status = status, nextRetryAt = now, createdAt = now, updatedAt = now))
    }

    @Test fun v1MigrationPreservesRowAndReplacesUniqueIndex() = runBlocking {
        assertEquals(preMigrationRow, database.queueDao().findById(1L))
        assertEquals(2, database.openHelper.readableDatabase.version)
        assertEquals(legacyPayload(), QueueCrypto.decrypt(database.queueDao().findById(1L)!!))
        val id = preMigrationRow.id
        val second = insertEncrypted(legacyPayload(eventId = "4a4a2f3c-929b-4988-896c-790733d68238"), digest = "legacy")
        assertTrue(id > 0 && second > id)
        val index = database.openHelper.readableDatabase.query("PRAGMA index_list(notification_queue)")
        var unique = -1
        while (index.moveToNext()) if (index.getString(index.getColumnIndexOrThrow("name")).contains("notificationKeyDigest")) unique = index.getInt(index.getColumnIndexOrThrow("unique"))
        index.close()
        assertEquals(0, unique); assertEquals(2, database.queueDao().countRows())
    }

    @Test fun repositoryPersistsMissingIdOnceAcrossConcurrentReadsAndSendingRecovery() = runBlocking {
        val id = preMigrationRow.id
        val repository = NotificationRepository(context, database.queueDao(), settings)
        val first = withTimeout(10_000) {
            val pending = async(Dispatchers.IO) { repository.getPending(10).single().payload.eventId }
            val display = async(Dispatchers.IO) { repository.observeRecent(10).first().single().payload.eventId }
            pending.await().also { assertEquals(it, display.await()) }
        }
        assertNotNull(first)
        assertEquals(4, UUID.fromString(first).version())
        val persisted = database.queueDao().findById(id)!!
        assertEquals(first, QueueCrypto.decrypt(persisted).eventId)
        assertEquals(preMigrationRow.copy(encryptedPayload = persisted.encryptedPayload, iv = persisted.iv,
            updatedAt = persisted.updatedAt), persisted)
        repository.markSending(listOf(id))
        assertEquals(first, repository.getForDelivery(id)!!.payload.eventId)
        database.close()
        database = Room.databaseBuilder(context, AppDatabase::class.java, dbFile.name)
            .addMigrations(AppDatabase.MIGRATION_1_2).build()
        val reopened = NotificationRepository(context, database.queueDao(), settings)
        assertEquals(QueueStatus.SENDING, database.queueDao().findById(id)!!.status)
        reopened.recoverSending()
        assertEquals(first, reopened.getPending(10).single().payload.eventId)
        assertEquals(persisted.encryptedPayload, database.queueDao().findById(id)!!.encryptedPayload)
    }

    @Test fun sensitiveLegacyIsDiscardedWithoutSentCount() = runBlocking {
        clearRows()
        val repository = NotificationRepository(context, database.queueDao(), settings)
        val stats = withTimeout(10_000) { repository.observeStats().first() }
        val displayId = insertEncrypted(legacyPayload(bigText = "Your TAC is 123456"))
        assertTrue(withTimeout(10_000) { repository.observeRecent(10).first() }.isEmpty())
        assertNull(database.queueDao().findById(displayId))
        val pendingId = insertEncrypted(legacyPayload().copy(text = "Your OTP is 123456"))
        assertTrue(repository.getPending(10).isEmpty())
        assertNull(database.queueDao().findById(pendingId))
        val sendingId = insertEncrypted(legacyPayload().copy(title = "New device registered successfully"), QueueStatus.SENDING)
        assertNull(repository.getForDelivery(sendingId))
        assertNull(database.queueDao().findById(sendingId))
        assertEquals(stats, withTimeout(10_000) { repository.observeStats().first() })
    }

    @Test fun failedCiphertextRewriteRetainsLegacyRowAndDoesNotMakeItEligible() = runBlocking {
        clearRows()
        val id = insertEncrypted(legacyPayload())
        val before = database.queueDao().findById(id)!!
        database.openHelper.writableDatabase.execSQL("CREATE TRIGGER fail_rewrite BEFORE UPDATE OF encryptedPayload ON notification_queue BEGIN SELECT RAISE(ABORT, 'rewrite_failed'); END")
        val repository = NotificationRepository(context, database.queueDao(), settings)
        assertTrue(repository.getPending(10).none { it.id == id }); assertNotNull(database.queueDao().findById(id))
        val retained = database.queueDao().findById(id)!!
        assertEquals(before, retained)
        assertNull(QueueCrypto.decrypt(retained).eventId)
        assertTrue(withTimeout(10_000) { repository.observeRecent(10).first() }.isEmpty())
        repository.markSending(listOf(id))
        assertNull(repository.getForDelivery(id))
        assertEquals(before.encryptedPayload, database.queueDao().findById(id)!!.encryptedPayload)
        assertEquals(before.iv, database.queueDao().findById(id)!!.iv)
        database.openHelper.writableDatabase.execSQL("DROP TRIGGER fail_rewrite")
        repository.recoverSending()
        val migrated = repository.getPending(10).single()
        assertEquals(id, migrated.id)
        assertNotNull(migrated.payload.eventId)
        assertEquals(migrated.payload.eventId, QueueCrypto.decrypt(database.queueDao().findById(id)!!).eventId)
    }

    @Test fun exactCaptureIgnoresAppNameButChangedDetailsRemainSeparate() = runBlocking {
        clearRows()
        val repository = NotificationRepository(context, database.queueDao(), settings)
        val now = System.currentTimeMillis()
        val details = "Details \"quoted\" 😀\n{postedAt}\u0001"
        suspend fun capture(appName: String = "Bank A", postedAt: Long = now, bigText: String = details) {
            repository.enqueue("com.example.bank", appName, "Payment", "RM10 paid", postedAt,
                "same-key", settings.policyRevision, System.currentTimeMillis(), bigText)
        }
        capture()
        capture(appName = "Bank B")
        assertEquals(1, database.queueDao().countRows())
        val original = withTimeout(10_000) { database.queueDao().observeRecent(10).first().single() }
        val originalId = original.id
        val originalCipher = original.encryptedPayload
        database.queueDao().markSending(listOf(originalId), System.currentTimeMillis())
        capture(appName = "Bank C")
        assertEquals(1, database.queueDao().countRows())
        capture(bigText = "$details changed")
        capture(postedAt = now + 1)
        assertEquals(3, database.queueDao().countRows())
        val rows = withTimeout(10_000) { database.queueDao().observeRecent(10).first() }
        assertEquals(3, rows.map { it.id }.distinct().size)
        assertEquals(QueueStatus.SENDING, database.queueDao().findById(originalId)!!.status)
        assertEquals(originalCipher, database.queueDao().findById(originalId)!!.encryptedPayload)
        assertEquals(original.iv, database.queueDao().findById(originalId)!!.iv)
        val payloads = rows.map { QueueCrypto.decrypt(it) }
        assertEquals(3, payloads.map { it.eventId }.distinct().size)
        payloads.forEach { assertEquals(4, UUID.fromString(it.eventId).version()) }
        assertEquals(details, QueueCrypto.decrypt(database.queueDao().findById(originalId)!!).bigText)
        assertEquals("Bank A", QueueCrypto.decrypt(database.queueDao().findById(originalId)!!).appName)
        assertEquals(setOf(now, now + 1), payloads.map { it.postedAt }.toSet())
    }

    @Test fun repositoryRejectsSensitiveCapturesBeforeInsertion() = runBlocking {
        clearRows()
        val repository = NotificationRepository(context, database.queueDao(), settings)
        val now = System.currentTimeMillis()
        repository.enqueue("com.example.bank", "Bank", "Payment successful", "RM10 paid", now,
            "sensitive", settings.policyRevision, now, "Your OTP is 123456")
        assertEquals(0, database.queueDao().countRows())
        assertEquals(0, withTimeout(10_000) { repository.observeStats().first() }.sentCount)
    }

    @Test fun transientOutageBeyondOldLimitPreservesEveryEncryptedEvent() = runBlocking {
        clearRows()
        val dao = database.queueDao()
        val repository = NotificationRepository(context, dao, settings)
        val ids = (1..5).map { index ->
            insertEncrypted(legacyPayload(eventId = UUID.randomUUID().toString()).copy(notificationKey = "outage-$index"), digest = "outage-$index")
        }
        val originals = ids.associateWith { dao.findById(it)!! }
        repeat(4) { attempt ->
            for (id in ids) {
                repository.markSending(listOf(id))
                val before = System.currentTimeMillis()
                repository.markFailure(id, attempt + 1, 2, "http_503", permanent = false)
                val row = dao.findById(id)!!
                assertEquals(QueueStatus.PENDING, row.status)
                assertEquals(attempt + 1, row.attemptCount)
                assertTrue(row.nextRetryAt >= before + if (attempt == 0) 60_000 else 120_000)
                assertEquals(originals[id]!!.encryptedPayload, row.encryptedPayload)
                assertEquals(originals[id]!!.iv, row.iv)
            }
        }
        assertTrue(repository.hasPending()) // Future-due rows must keep work alive.
        assertTrue(repository.getPending(2).isEmpty())
        assertEquals(0, withTimeout(10_000) { repository.observeStats().first() }.failedCount)
        database.close()
        database = Room.databaseBuilder(context, AppDatabase::class.java, dbFile.name).addMigrations(AppDatabase.MIGRATION_1_2).build()
        val reopened = NotificationRepository(context, database.queueDao(), settings)
        for (id in ids) {
            assertEquals(originals[id]!!.encryptedPayload, database.queueDao().findById(id)!!.encryptedPayload)
        }
        // Advance eligibility without waiting in real time; this is repository evidence,
        // not an assertion that WorkManager ran automatically on a device.
        database.openHelper.writableDatabase.execSQL("UPDATE notification_queue SET nextRetryAt = 0")
        var batches = 0
        while (reopened.hasPending()) {
            val batch = reopened.getPending(2)
            assertTrue(batch.isNotEmpty())
            reopened.markSending(batch.map { it.id })
            batch.forEach { reopened.markSent(it.id) }
            batches++
        }
        assertEquals(3, batches)
        assertEquals(5, withTimeout(10_000) { reopened.observeStats().first() }.sentCount)
    }

    @Test fun permanentFailureAndExpiryStillDeleteAfterTransientFailure() = runBlocking {
        clearRows()
        val repository = NotificationRepository(context, database.queueDao(), settings)
        val permanent = insertEncrypted(legacyPayload(eventId = UUID.randomUUID().toString()), digest = "permanent")
        repository.markFailure(permanent, 1, 2, "http_401", permanent = true)
        assertNull(database.queueDao().findById(permanent))
        val expired = insertEncrypted(legacyPayload(eventId = UUID.randomUUID().toString()), digest = "expired")
        repository.markFailure(expired, 20, 2, "http_503", permanent = false)
        repository.recalculateRetention(1)
        repository.purgeExpired(System.currentTimeMillis() + 3_600_001)
        assertNull(database.queueDao().findById(expired))
        val stats = withTimeout(10_000) { repository.observeStats().first() }
        assertEquals(1, stats.failedCount)
        assertEquals(1, stats.expiredCount)
    }

    @Test fun invalidExistingIdIsDiscardedRatherThanReassigned() = runBlocking {
        clearRows()
        val id = insertEncrypted(legacyPayload(eventId = "invalid-existing-id"))
        val repository = NotificationRepository(context, database.queueDao(), settings)
        assertTrue(repository.getPending(10).isEmpty())
        assertNull(database.queueDao().findById(id))
        val stats = withTimeout(10_000) { repository.observeStats().first() }
        assertEquals(0, stats.sentCount)
        assertEquals(1, stats.failedCount)
    }
}

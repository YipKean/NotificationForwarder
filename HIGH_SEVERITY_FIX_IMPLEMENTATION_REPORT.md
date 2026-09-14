# High-Severity Fix Implementation Report

**Date:** 2026-09-15  
**Scope:** H1-H7 from [`SECURITY_AUDIT.md`](SECURITY_AUDIT.md)  
**Review target:** Astra  
**Status:** Implemented in the working tree with Astra review corrections applied; Android build and device/runtime verification remain blocked by the local toolchain.

This report follows the approved implementation plan. It records the files changed, the security behavior now enforced, representative code, and the evidence available for review. No commit was created.

## Plan coverage

| Plan step | Findings | Implementation files | Result |
| --- | --- | --- | --- |
| 1. Transport, logging and backups | H1-H4 | `app/src/main/java/com/itsazni/notificationforwarder/network/WebhookClient.kt`, `app/src/main/AndroidManifest.xml`, `app/src/main/res/xml/network_security_config.xml`, `app/src/main/res/xml/backup_rules.xml`, `app/src/main/res/xml/data_extraction_rules.xml` | HTTPS-only requests, no redirect following, safe error codes, no HTTP logging interceptor, backup and transfer exclusions. |
| 2. Secure initialization, encrypted queue and counters | H5-H6 | `app/src/main/java/com/itsazni/notificationforwarder/data/NotificationPayload.kt`, `QueueCrypto.kt`, `QueueItem.kt`, `QueueDao.kt`, `AppDatabase.kt`, `NotificationRepository.kt`, `SecureInitialization.kt`, `NotificationForwarderApp.kt` | New secure database, AES-256-GCM queue payloads, no plaintext notification columns, safe terminal counters, legacy purge and forwarding disablement. |
| 3. Retention and cleanup | H5 | `app/src/main/java/com/itsazni/notificationforwarder/settings/SettingsStore.kt`, `data/NotificationRepository.kt`, `worker/QueueCleanupWorker.kt`, `worker/WorkerScheduler.kt`, `MainActivity.kt` | Default 24-hour retention, validated 1-24 hour choices, explicit Off mode, expiry before capture/display/delivery, offline cleanup, recalculation from capture time. |
| 4. Policy enforcement and cancellation | H6 | `settings/SettingsStore.kt`, `data/NotificationRepository.kt`, `worker/QueueWorker.kt`, `worker/DeliveryCoordinator.kt`, `service/AppNotificationListenerService.kt`, `worker/WorkerScheduler.kt`, `MainActivity.kt` | Whitelist-only capture, consistent settings snapshots, policy revisions, stale-item and row rechecks, delivery serialization, registered-call cancellation on clear/policy/retention changes, listener-side policy check before reading text. |
| 5. Receiver hardening and documentation | H7 | `webhook/server.js`, `webhook/server.test.js`, `webhook/package.json`, `webhook/package-lock.json`, `.env.example`, `README.md` | Required bearer token, loopback default, authentication before JSON parsing, timing-safe comparison, receipt-only output, sanitized errors, no payload log file. |

## 1. Transport, logging and backups (H1-H4)

`WebhookClient` now validates the endpoint at every save/test/delivery boundary through `EndpointValidator`, and the OkHttp client cannot follow either normal or TLS redirects:

```kotlin
private val client = OkHttpClient.Builder()
    .followRedirects(false)
    .followSslRedirects(false)
    .build()

object EndpointValidator {
    fun isValid(value: String): Boolean {
        val url = value.toHttpUrlOrNull() ?: return false
        return url.scheme == "https" && url.host.isNotBlank() &&
            url.username.isEmpty() && url.password.isEmpty() && url.fragment == null
    }
}
```

Every 3xx response is classified as a permanent `redirect_rejected` failure, so a `Location` target receives no request. Request construction and template parsing return fixed codes such as `invalid_endpoint`, `invalid_payload`, `invalid_request`, and `network_failure`; URLs, headers, payloads, identifiers, and exception objects are not logged or surfaced to the UI.

`WebhookClient.send` is cancellable. It registers `Call.cancel()` with `suspendCancellableCoroutine`, allowing policy changes and queue clearing to stop active requests.

The logging interceptor and its dependency were removed from the Gradle dependency graph. The manifest and network resources now enforce cleartext rejection and backup exclusion:

```xml
<application
    android:allowBackup="false"
    android:fullBackupContent="@xml/backup_rules"
    android:dataExtractionRules="@xml/data_extraction_rules"
    android:usesCleartextTraffic="false" />
```

Both backup rule files explicitly exclude root, file, database, shared preference, external, and device-protected domains. These settings do not revoke backups already created by Android or guarantee identical behavior on every OEM; that limitation is documented in `README.md` and `SECURITY_AUDIT.md`.

## 2. Secure initialization and encrypted queue (H5-H6)

The old Room schema is not reused. `AppDatabase` opens `notif_secure.db`; `SecureInitialization.ensure` runs before capture, delivery, or tests. On the first run of the hardening version it disables forwarding, empties the allowlist, deletes `notif_forwarder.db` and the secure database through `Context.deleteDatabase`, opens the secure Room database successfully, and only then writes the completion marker while holding the initialization lock. Failed cleanup or open leaves the marker unset so the next operation retries safely.

The persistent row in `QueueItem.kt` contains ciphertext, IV, format version, a notification-key digest, policy revision, timestamps, retry state, status, and a safe error code. Plaintext title/body/app fields exist only in `NotificationPayload` and are decrypted in memory for delivery or active queue display.

`QueueCrypto` uses an Android Keystore AES-256-GCM key, a fresh IV for each insert, authenticated format-version metadata, and no biometric requirement:

```kotlin
val cipher = Cipher.getInstance("AES/GCM/NoPadding")
cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
cipher.updateAAD(FORMAT_VERSION.toString().toByteArray(StandardCharsets.UTF_8))
val encrypted = cipher.doFinal(gson.toJson(payload).toByteArray(StandardCharsets.UTF_8))
```

Missing or invalidated keys are distinguished from invalid authentication tags. Key loss clears the queue, disables forwarding, and resets the key so a later explicit re-enable can create a fresh Keystore key. An invalid tag deletes only the affected row and increments the safe failed counter. There is no plaintext fallback. `QueueDao.markSentAndDelete` and `markFailedAndDelete` delete the row and increment the aggregate counter in one Room transaction. Expiry increments the aggregate expired counter and leaves no completed notification history.

## 3. Retention and cleanup (H5)

`SettingsStore.retentionHours` defaults to `24`; `null` represents **Off — clear manually**. `MainActivity` exposes every whole hour from `1` through `24`, plus `OFF`; invalid values are rejected at both the UI and settings boundaries. Expiry is calculated from `createdAt`, which is the capture time:

```kotlin
expiresAt = retention?.let { now + it * 60L * 60L * 1000L }
```

`NotificationRepository.purgeExpired` runs before capture, queue display, and delivery. `QueueDao.getPending` also makes expiry a strict delivery eligibility condition. `QueueCleanupWorker` is scheduled without a network constraint, so an offline device can remove expired rows. Changing retention calls `recalculateRetention`, recomputes deadlines from the original capture timestamp, immediately removes over-age rows, and removes deadlines when Off is selected. Delivery and display also calculate age from the current retention setting, so a crash between the settings commit and deadline reconciliation cannot release an over-age row.

Successful delivery, permanent failure, exhausted retries, and expiry delete notification content. The approved residual risk remains: Off mode permits an undelivered encrypted item to remain indefinitely until manual clear, delivery, permanent failure, or retry exhaustion.

## 4. Policy enforcement, revisions, and cancellation (H6)

The hardened mode exposes whitelist-only capture. `AppNotificationListenerService` checks `NotificationRepository.canCapture(packageName)` before reading title/body extras, and `NotificationRepository.enqueue` checks the policy again immediately before encryption and insertion.

`SettingsStore.readAll` builds one settings object from a single `SharedPreferences.all` snapshot. `SettingsStore.saveSnapshot` rejects invalid endpoints and retention values, distinguishes validation/write failures from a successful unchanged or changed save, and writes the complete settings snapshot with one `commit()`. Endpoint, method, authentication, headers, query, template, or allowlist changes increment `policyRevision`, disable forwarding, and cause the caller to clear pending content and cancel delivery. Enabling requires a valid HTTPS endpoint and at least one allowlisted package. Retention and retry-limit changes do not increment the policy revision.

Before every send, `QueueWorker` reloads one complete settings snapshot, rereads the current queue row and verifies enablement, revision, allowlist membership, current expiry, and `SENDING` state. `DeliveryCoordinator` has separate delivery and state mutexes. It creates and registers the OkHttp call under the state mutex, releases the mutex before network I/O, and removes the registration in `finally`. Queue/policy/retention changes cancel registered calls before removing or recalculating rows; WorkManager cancellation is additional scheduling cleanup. Interrupted `SENDING` rows are recovered to `PENDING` on process restart.

This prevents endpoint A's queued messages from being released to endpoint B after a settings change. A request already accepted by a remote endpoint cannot be recalled, so the UI and documentation do not promise remote recall.

## 5. Hardened Node receiver (H7)

`webhook/server.js` now fails closed when `WEBHOOK_BEARER_TOKEN` is absent, binds to `127.0.0.1` by default, authenticates before `express.json`, and compares SHA-256 digests with `crypto.timingSafeEqual`:

```js
const suppliedDigest = crypto.createHash("sha256").update(authHeader.slice(7).trim()).digest();
const expectedDigest = crypto.createHash("sha256").update(BEARER_TOKEN).digest();
if (!crypto.timingSafeEqual(suppliedDigest, expectedDigest)) {
  return res.status(403).json({ ok: false, message: "Forbidden.", code: "forbidden" });
}
```

The receiver keeps the JSON size limit, emits only `receiptId`, timestamp, and outcome to stdout, returns the receipt ID in a successful response, and uses fixed responses for malformed JSON, oversized bodies, unknown paths, and internal errors. `appendLog`, `WEBHOOK_LOG_FILE`, payload file writes, and raw request/error output were removed. `README.md` documents deployment behind an HTTPS reverse proxy and includes a Caddy example with access logging disabled. Existing `webhook.log` files are intentionally not deleted.

## Validation evidence

The following checks were run in the working tree:

```text
npm test --prefix webhook -- --test-isolation=none
4 tests passed, 0 failed

npm audit --prefix webhook --omit=dev
found 0 vulnerabilities

node --check webhook/server.js
node --check webhook/server.test.js
passed
```

The receiver tests cover authentication before parsing, sanitized malformed and oversized JSON errors, and receipt-only output. The standard isolated Node test runner hit the managed Windows sandbox's `spawn EPERM`; the same suite passed with `--test-isolation=none`. A static regression scan found no `HttpLoggingInterceptor`, `logging-interceptor`, `WEBHOOK_LOG_FILE`, `appendLog`, cleartext-enabled network configuration, or `allowBackup="true"`.

The Android build could not be run because the environment has no usable JDK (`JAVA_HOME is not set and no 'java' command could be found in your PATH`); Android SDK availability was not independently verified. Therefore Android compilation, instrumentation tests, Keystore round-trip tests, MockWebServer redirect tests, WAL inspection, release assembly, and merged release manifest inspection are still Astra review tasks. Source inspection alone is not being reported as runtime validation. Android acceptance test sources for those scenarios have not been added yet; this is separate from the missing-Java blocker.

## Astra review follow-up

Astra reviewed the first implementation and identified policy races, incomplete key-loss handling, failed-initialization gating, incomplete backup domains, and retention/save validation gaps. Those corrections are now present in `DeliveryCoordinator.kt`, `QueueWorker.kt`, `NotificationRepository.kt`, `QueueCrypto.kt`, `SecureInitialization.kt`, `AppDatabase.kt`, `SettingsStore.kt`, `MainActivity.kt`, the backup XML files, and the listener. The final follow-up also makes expiry/corruption deletion cancel registered calls first, translates encryption-time key invalidation to key loss, increments the security policy revision when disabling after key loss, and prevents a rejected test from unregistering another active test call. The report was updated to remove the earlier overstatements about cached-row delivery, call cancellation, backup coverage, and retention choices.

The remaining review work is runtime verification: build the Android release, inspect the merged manifest and resolved dependencies, and add/run the Android network, Keystore, upgrade, retention, and concurrency tests described below.

Astra's final source pass closed the three follow-up blockers: expiry/corruption/manual deletion now cancel affected calls before transactional deletion, encryption key invalidation reaches revisioned key-loss handling, and rejected webhook tests cannot unregister another active call. This is source-review closure, not release approval.

## Astra review checklist

- Build a release and inspect the merged manifest and resolved dependencies for backup exclusions, cleartext rejection, and absence of the logging interceptor.
- Exercise HTTP endpoint rejection, invalid credentials/certificates, each 3xx response, and confirm the redirect target receives zero requests.
- Place unique canaries in URL, query, headers, and body; confirm they do not appear in Logcat, worker errors, UI errors, receiver stdout, or receiver responses.
- Seed the legacy database; verify initialization deletes it, preserves endpoint settings, disables forwarding, empties the allowlist, and retries safely after a failed cleanup.
- Verify AES-GCM round trips, distinct IVs, tamper rejection, key-loss behavior, and absence of plaintext canaries in the database/WAL.
- Verify default retention, shortening, Off, Off-to-enabled transition, offline cleanup, and terminal counter updates.
- Verify policy changes, disabling during delivery, overlapping workers, stale listener callbacks, and process restart recovery.
- Run `node --test --test-isolation=none webhook/server.test.js` with malformed/oversized JSON and secret-bearing URL/header/body; separately verify missing-token startup and confirm receipt-only output.

## Remaining scope and limitations

Medium and low findings from `SECURITY_AUDIT.md` were intentionally left out of this handoff, including credential-at-rest hardening in preferences, UI masking/accessibility changes, and the broader device-identifier/raw-notification-key minimization work. An injectable clock, Android acceptance test tree, and an expiry-triggered refresh for an idle queue screen remain to be added during Astra's build-enabled review. Recovery currently resets interrupted `SENDING` rows before the worker's eligibility pass; the current retention reconciliation and per-row checks still prevent over-age delivery. The approved H5 caveat for indefinite encrypted retention in Off mode remains. No deployment, commit, or unrelated cleanup was performed.

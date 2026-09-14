# Sensitive notification security audit

Audit date: 2026-09-14. Scope: all application Kotlin sources, resources, manifest, Gradle configuration, and the bundled Node webhook receiver. Existing Graphify graph used for navigation; findings verified against source. No app code changed.

**Initial verdict:** unsuitable for highly sensitive banking notifications without changes. No critical vulnerability demonstrated.

**Implementation status (2026-09-15):** H1–H7 remediation is implemented in the working tree: HTTPS-only delivery with redirects disabled, production HTTP logging removed, backup disabled/excluded, encrypted AES-GCM queue with 24-hour configurable retention, upgrade purge/disable initialization, whitelist-only policy checks, and a receipt-only authenticated receiver. Astra completed a source-review pass after follow-up fixes for policy races, key loss, expiry cancellation, and initialization ordering. The Android release build and runtime/merged-manifest checks are still pending because this environment has no usable JDK; Android acceptance coverage is also not yet present. The receiver tests pass with the managed-runner isolation workaround, and `npm audit --omit=dev` reports zero vulnerabilities after refreshing the lockfile. The medium findings M1–M5 remain outside this implementation scope unless called out by the new behavior.

Severity: high = substantial disclosure under a realistic configuration or access condition; medium = narrower exposure or defense missing; low = unnecessary surface or hardening. Authorized delivery to a deliberately selected webhook is the product's intended behavior, not itself a vulnerability. Severity does not imply another ordinary Android app can bypass the sandbox.

## Findings

### H1 — High: HTTP can transmit banking contents and credentials in cleartext

- `app/src/main/res/xml/network_security_config.xml:3` permits cleartext globally; `app/src/main/AndroidManifest.xml:12` selects it.
- `network/WebhookClient.kt:31`, `send()`, accepts an arbitrary URL. `buildUrl()` at line 96 adds configured query parameters without requiring HTTPS. Lines 76–83 attach the JSON body and headers and execute the request.
- Both `worker/QueueWorker.kt:43`, `doWork()`, and `MainActivity.kt:183`, `MainScreen()`'s test callback, reach this sink.
- With an HTTP endpoint, network observers can read notification contents, device ID, notification key, bearer/custom credentials, URL tokens and query values. The test uses synthetic notification data but real configured credentials.
- Fix: require HTTPS when saving and sending; deny cleartext in the network security configuration. Keep local HTTP testing out of the production configuration.

### H2 — High: redirects can disclose the payload and custom secrets to another destination

- `network/WebhookClient.kt:22`, client initialization, retains OkHttp's default redirect behavior; `send()` executes at line 83.
- A configured endpoint returning a 307/308 can forward the body to another host. HTTPS-to-HTTP redirects are also permitted by the client defaults and H1. Custom headers such as `X-Api-Key` remain on redirected requests.
- Important boundary: OkHttp removes `Authorization` when the destination cannot reuse the original connection; do not interpret this finding as unconditional cross-host bearer-header forwarding. Other custom secrets and body contents remain exposed.
- Fix: disable redirects for webhooks, or explicitly validate each redirect's scheme and destination and rebuild an allowlisted set of headers.
- Verified against [OkHttp 4.12 redirect implementation](https://raw.githubusercontent.com/square/okhttp/parent-4.12.0/okhttp/src/main/kotlin/okhttp3/internal/http/RetryAndFollowUpInterceptor.kt) and [client defaults](https://raw.githubusercontent.com/square/okhttp/parent-4.12.0/okhttp/src/main/kotlin/okhttp3/OkHttpClient.kt).

### H3 — High: production HTTP logging exposes URL credentials

- `network/WebhookClient.kt:26` installs `HttpLoggingInterceptor` at `BASIC` unconditionally. `app/build.gradle.kts:77` includes it in release builds.
- Request URLs include the configured path and query values from `buildUrl()` at line 96. Tokens embedded in Telegram bot URLs or Discord webhook URLs therefore enter Android logs, as do API keys supplied in query parameters. These URL forms are explicitly documented in `README.md:61` and `README.md:69`.
- BASIC does not routinely log the body or Authorization header. It does log URLs, status/timing/size metadata and network failure text. The interceptor itself receives the entire request object regardless of its chosen log level.
- Log access requires an applicable privileged, debugging, diagnostic or compromised-device path; ordinary third-party apps do not universally have logcat access.
- Fix: remove the interceptor from production. If diagnostics are needed, emit fixed event codes and status classes without URLs or raw exceptions.
- Verified against [the logging interceptor implementation](https://raw.githubusercontent.com/square/okhttp/parent-4.12.0/okhttp-logging-interceptor/src/main/kotlin/okhttp3/logging/HttpLoggingInterceptor.kt).

### H4 — High: notification history and credentials are eligible for backup and transfer

- `app/src/main/AndroidManifest.xml:9` sets `allowBackup="true"`. No `fullBackupContent` or `dataExtractionRules` exclusions exist in application source.
- `settings/SettingsStore.kt:27` stores `notif_settings` preferences; `data/AppDatabase.kt:18` creates `notif_forwarder.db`. Both locations are included by default in Android backup.
- This creates a separate off-device path for titles, bodies, notification identifiers, endpoint URLs, bearer tokens, custom headers, queries and templates. Actual execution depends on device/user backup settings, platform/OEM behavior and quotas. Backup eligibility does not mean the backup is public or unencrypted by the platform.
- Restoring these settings and pending rows can also re-enable delivery using restored credentials through application startup scheduling.
- Fix: exclude sensitive preferences and databases from cloud backup and device transfer using rules appropriate to supported versions; disable backup as defense in depth. Verify OEM transfer behavior.
- Platform behavior: [Android Auto Backup](https://developer.android.com/identity/data/autobackup).

### H5 — High: full banking notifications persist indefinitely without application-level encryption

- `service/AppNotificationListenerService.kt:45`, `onNotificationPosted()`, passes title/text into `data/NotificationRepository.kt:11`, `enqueue()`.
- `enqueue()` constructs a `QueueItem` at line 28 and inserts it at line 39. `data/QueueItem.kt:18` stores package name, app name, title, text, timestamp, notification key and last error.
- `data/AppDatabase.kt:16`, `getInstance()`, opens ordinary Room/SQLite storage; no encryption layer is configured.
- `data/QueueDao.kt:34`, `markSent()`, only changes status and clears the error. Failed rows also retain contents. There is no age limit, size cap or automatic purge. `observeRecent(30)` in `MainActivity.kt:143` limits display, not retention.
- Manual deletion runs SQL DELETE through `QueueDao.deleteById()` at line 72 or `clearAll()` at line 75. The app provides no secure-erasure guarantee for database free pages, journal/WAL remnants or existing backups.
- Exposure requires backup access, application-UID execution, a suitable debugger/privileged device access, or another compromise; this is not world-readable external storage.
- Fix: delete delivered content promptly, impose a short expiry on unsent/failed records, minimize stored fields and encrypt required retained content with keys protected by Android Keystore. Do not promise physical erasure from SQL DELETE alone.

### H6 — High: broad default capture and policy changes can release old sensitive notifications

- `settings/SettingsStore.kt:34` defaults forwarding to enabled; line 38 defaults the filter to `ALL_APPS`. The URL defaults to empty at line 30.
- `data/NotificationRepository.kt:19` checks enabled state and package filter but not endpoint readiness. Granting notification access can therefore accumulate banking messages before an endpoint is configured.
- `MainActivity.kt:178` saves webhook settings and immediately schedules work. `worker/QueueWorker.kt:22` reads the current endpoint/credentials/template and sends pending rows without rechecking their package against the current filter. `QueueItem` records no destination or policy binding.
- Concrete cases: configure a URL after days of capture and historical notifications are sent; switch endpoint A to B and pending A-era messages go to B; blacklist a bank and previously queued messages still send.
- The enabled flag is checked once at the start of a batch, not before each item. Disabling forwarding, deleting a row or clearing the queue does not revoke rows already loaded in a running worker. Re-enabling also resumes surviving pending rows.
- Fix: default off with an explicit allowlist; require delivery configuration before capture; define and enforce pending-queue behavior on policy/destination changes; recheck policy before sending and coordinate cancellation of in-flight work.

### H7 — High, conditional on using the bundled receiver: server logs retain complete payloads and custom secrets

- `webhook/server.js:51`, POST handler, records `req.originalUrl`, IP, headers and the entire body at lines 55–62. Only the `authorization` header is removed at line 53.
- `appendLog()` at line 17 creates the directory and appends JSON to `logs/webhook.log` or the configured path. Custom API-key/cookie headers, query-string credentials, title/body and device ID can all persist there. No retention, redaction, encryption or explicit restrictive file mode is configured; effective access depends on host permissions/umask.
- `requireBearerAuth()` at line 23 accepts every request when the configured token is empty. `app.listen()` at line 90 defaults to HTTP on all interfaces through lines 8–9. Authentication being absent permits injection/log flooding, not an implemented HTTP log-reading endpoint. TLS may exist at a separately deployed reverse proxy; none is configured here.
- Express and its parsing middleware receive the webhook body and headers in the server process. These are not Android dependencies.
- Fix: keep this receiver away from real banking data until HTTPS, required authentication, body/header minimization and bounded retention are configured. Log delivery metadata only.

### M1 — Medium: credentials are stored as ordinary preference strings

- `settings/SettingsStore.kt:25` uses private SharedPreferences. Setters at lines 29, 49, 53, 61 and 65 persist endpoint, bearer token, custom headers, query parameters and payload template. `MainActivity.kt:675`, `saveSettings()`, writes them.
- `MODE_PRIVATE` provides sandbox access control, not application-level encryption. Device filesystem encryption still applies. H4 is the additional off-device exposure; this finding covers local credential extraction and unnecessary lifetime.
- Changing auth mode does not clear an old bearer token. Credentials can also live inside URLs, queries, custom headers or literal template fields, so protecting only `bearerToken` is insufficient.
- Fix: protect all secret-bearing configuration with Keystore-backed encryption, exclude it from backup and clear obsolete secrets.

### M2 — Medium: UI exposes stored messages and unmasked credentials

- `MainActivity.onCreate()` at `MainActivity.kt:117` has no authentication gate or secure-window flag.
- `MainScreen()` at line 138 reads all settings, including credentials; line 143 continuously observes recent full queue rows even while another tab is visible.
- `WebhookScreen()` at line 357 renders ordinary text inputs for URL (386), bearer token (413), headers (421), query parameters (430) and template (439). There is no password transformation or password-specific keyboard configuration.
- `QueueScreen()` at line 527 renders full title/text at lines 567–568 and `lastError` at line 572.
- Exposure surfaces include an unlocked-device viewer, screenshots/recents/media projection under applicable OS rules, enabled accessibility services, and the selected input method for editable secret fields. User copying of text fields can create clipboard exposure. The app itself has no explicit clipboard/share/export code.
- Fix: authenticate entry/re-entry, conceal content by default, use secure-window protection and secret-aware fields, and minimize sensitive accessibility/IME exposure. Secure-window protection alone does not solve accessibility or keyboard trust.
- Platform reference: [secure sensitive activities](https://developer.android.com/security/fraud-prevention/activities).

### M3 — Medium: stable device and notification identifiers accompany content

- `worker/QueueWorker.kt:33`, `doWork()`, reads `Settings.Secure.ANDROID_ID`.
- `network/WebhookClient.kt:41`, `send()`, exposes it as a template variable and includes it in the default JSON at line 55. The default also includes package/app name, posting time and raw notification key at lines 56–61.
- This permits longitudinal association of banking activity with an app/device/user identifier. On Android 8+, ANDROID_ID is scoped to the signing key, user and device; it is not an IMEI or universally shared hardware identifier.
- The raw `sbn.key` is stored in the queue and sent by default. `buildStableKey()` at listener line 103 additionally constructs package/id/tag/user-derived material for local deduplication; its fallback is not what `enqueue()` stores.
- No IMEI, advertising ID, phone number, location or account API access was found. Device ID is not separately persisted by application code; the receiver may persist it.
- Fix: omit identifiers unless necessary or use a resettable, destination-specific random identifier. Send only fields the receiver requires.
- Platform reference: [ANDROID_ID](https://developer.android.com/reference/android/provider/Settings.Secure#ANDROID_ID).

### M4 — Medium: raw exception messages become persisted and displayed data

- `network/WebhookClient.kt:91`, `send()` catch block, returns `e.message` without sanitization.
- `worker/QueueWorker.kt:56` passes it into `NotificationRepository.markFailure()` at line 54 and `QueueDao.updateFailure()` at line 47, storing it as `lastError`. `QueueScreen()` displays it at `MainActivity.kt:572`; the test callback displays it in a snackbar at line 208.
- Invalid custom header values can appear in OkHttp validation exceptions; parser errors may include data-dependent paths/fragments. Do not assume every exception contains a secret or every banking body is logged. This is an uncontrolled secondary sink for whatever a dependency puts in its message.
- HTTP non-success handling itself records only `HTTP <code>`; response bodies are not copied into the database. Network exceptions occurring inside the logging interceptor may also be logged under H3.
- Fix: map exceptions to fixed safe codes; keep detailed production diagnostics free of request-derived values.
- Example dependency behavior: [OkHttp header validation](https://raw.githubusercontent.com/square/okhttp/parent-4.12.0/okhttp/src/main/kotlin/okhttp3/Headers.kt).

### M5 — Medium: “None” authentication still sends custom credentials

- `worker/QueueWorker.kt:71`, `buildHeaders()`, always applies `customHeaders` at line 80, including in `AuthMode.NONE` and `BEARER`.
- `MainActivity.kt:714`, `buildHeadersPreview()`, does the same for tests. An exact-case custom Authorization key overrides the generated bearer header; differently cased names can coexist in the Kotlin map and become duplicate HTTP headers.
- Selecting None does not suppress a previously configured custom Authorization/API-key/cookie header. Changing destinations can silently reuse those secrets. This also expands redirect and server-log exposure.
- Fix: distinguish ordinary headers from secret authentication fields, make the selected mode authoritative and bind credentials to their intended destination.

## Complete application data-flow inventory

Paths below are relative to `app/src/main/java/com/itsazni/notificationforwarder/` unless prefixed otherwise.

| Source / trigger | Processing and sink | Exposure / finding |
|---|---|---|
| `service/AppNotificationListenerService.kt:26`, `onNotificationPosted()` | Reads EXTRA_TITLE, EXTRA_TEXT and EXTRA_BIG_TEXT at lines 35–37; `shouldSkip()` at 65 hashes their concatenation; `recentEvents` retains key/hash/timestamps for at most 512 entries | In-process data, including notifications later rejected by repository policy. BIG_TEXT is used for dedup only, not stored or transmitted as the body. No log call here. |
| Same callback, line 43 | Coroutine captures notification data; `resolveAppName()` at 57 consults PackageManager; `NotificationRepository.enqueue()` writes Room | H5/H6. PackageManager lookup does not send title/body. |
| `NotificationForwarderApp.kt:7`, `onCreate()`; listener line 53; `MainActivity.kt:178`, 247 and 302; boot receiver line 9 | `WorkerScheduler.ensurePeriodic()` at 35 or `enqueueImmediate()` at 18 starts `QueueWorker.doWork()` | Automatic periodic, capture, save, manual-sync and boot delivery triggers all converge on the same network sink. Work requests contain no notification/token input Data. WorkManager persists scheduling metadata. |
| `QueueWorker.kt:21`, `doWork()` | Read preferences and database, read ANDROID_ID, build headers/query, call `WebhookClient.send()` | H1–H6, M1/M3/M5. Retry/backoff can repeat transmission; no idempotency protocol is enforced. |
| `MainActivity.kt:183`, test callback | Current UI URL/headers/query/template -> `WebhookClient.send()` | Synthetic title/body and `test-device`; real configured secrets and literal template contents leave even when forwarding is disabled. No queue row is created for the test. |
| `WebhookClient.kt:31`, `send()` | Gson default serialization or `renderTemplate()` at 104 -> request body for POST/PUT/PATCH; headers attached; OkHttp executes | Gson sees body fields; OkHttp/Okio and the interceptor see the outgoing request. Template variables are substituted only in the payload, not URLs/headers/query values. GET constructs/validates body JSON but sends no body; configured URL/query/headers still leave. |
| `WebhookClient.kt:96`, `buildUrl()` | Configured URL plus static query values -> HTTP request and logs | H1/H3. No automatic notification-to-query substitution. DNS/network infrastructure can observe destination metadata; TLS protects contents when used correctly. |
| `WebhookClient.kt:83`, response handling | Success -> markSent; error status/exception -> markFailure | H5/M4. No configured HTTP disk cache, cookie persistence, body-log interceptor or response-body storage. |
| `MainActivity.kt:675`, `saveSettings()` | Editable UI values -> preference setters | M1, then H4 backup. Raw payload template persists; normally rendered request JSON is transient rather than stored as a separate queue field. |
| `MainActivity.kt:131`, `MainScreen()` | Settings and Room Flow -> Compose UI/state -> secret fields and queue display | M2. Uses `remember`, not an explicit `rememberSaveable`/saved-state export of these values. |
| Android backup subsystem | Preference/database files -> backup/transfer | H4; independent of webhook delivery. |
| `webhook/server.js:51` | HTTP POST -> Express -> `appendLog()` | H7; off-device receiver path, only if this receiver is deployed. |

All bundled runtime libraries execute within the app's UID/process trust boundary; Android does not isolate each library from private app files. Directly observed handlers are Room/SQLite for queue content, Compose/Material for displayed data, Gson for JSON, OkHttp/Okio/logging interceptor for network requests and coroutines for in-memory work. WorkManager runs code that reads those values but is not passed the contents in its persisted input/output Data. No analytics, ads, crash-upload SDK, WebView bridge, third-party content provider or explicit external-storage export was found in application source. This is not proof that every transitive artifact is benign.

## Exported components and permissions

| Component / permission | Assessment |
|---|---|
| `AndroidManifest.xml:17`, exported `MainActivity` | Required launcher entry. No incoming-extra/deep-link handling, `onNewIntent` data path or result-return API found. Another app can launch it, but no direct programmatic database/token extraction demonstrated. UI exposure is M2. |
| `AndroidManifest.xml:27`, exported listener service | Protected by `android.permission.BIND_NOTIFICATION_LISTENER_SERVICE`. Exported does not mean arbitrary apps can bind and retrieve banking notifications. No custom unprotected binder API. Retain the permission. |
| `AndroidManifest.xml:37`, exported `BootCompletedReceiver`; `receiver/BootCompletedReceiver.kt:9`, `onReceive()` | **Low:** unnecessary public component surface. Handler accepts only BOOT_COMPLETED/LOCKED_BOOT_COMPLETED and schedules work; these system actions are protected broadcasts. Arbitrary actions are ignored, so no demonstrated spoofed data extraction. Prefer non-exported if compatible with supported system delivery. |
| `LOCKED_BOOT_COMPLETED` filter, manifest line 43 | **Low:** redundant/inoperative as configured: receiver lacks `directBootAware`, and storage is credential-protected. Remove unless a deliberate direct-boot design is needed; do not move banking records into device-protected storage just to enable it. |
| `INTERNET`, manifest line 4 | Necessary for webhook delivery. No unnecessary SMS, contacts, storage, location, phone-state, account or notification-posting permission declared. |
| `RECEIVE_BOOT_COMPLETED`, manifest line 5 | Used by the custom receiver; not an unused permission. The receiver may be redundant with WorkManager's rescheduling. If relying entirely on WorkManager, remove the duplicate receiver and explicit declaration only after verifying the merged manifest; the library can still require this permission. |
| Battery settings helpers, `MainActivity.kt:733` | Launch OS settings; fallback sends only the app's package URI. No banking payload or token is attached. No REQUEST_IGNORE_BATTERY_OPTIMIZATIONS permission is declared. |

Library-added providers/services/receivers and permissions require inspection of the release merged manifest. WorkManager/AndroidX may add scheduling/network/wakelock-related permissions and components; their exact packaged declarations and protection were not verified here. Do not classify those as unnecessary merely because absent from application source.

## Dependency minimization

All references below are to `app/build.gradle.kts`.

| Dependency | Severity / decision |
|---|---|
| `logging-interceptor:4.12.0`, line 77 | **High:** remove from production; concrete leakage in H3. |
| `lifecycle-runtime-compose:2.8.5`, line 61 | **Low:** no lifecycle-compose API is used; code uses runtime `collectAsState()`. Remove, or intentionally use its lifecycle-aware collector. |
| `ui-tooling-preview`, line 66 | **Low:** no preview annotations used; unnecessary direct release dependency. Actual tooling is already debug-only at line 68. |
| `material-icons-extended`, line 64 | **Low:** only List/Home/Link/Tune are imported (`MainActivity.kt:23`–27). Replace the broad pack with those specific assets if minimizing the binary. It is used, not completely dead. Release shrinking is disabled at line 23. |
| `lifecycle-runtime-ktx`, line 60 | **Low, candidate:** no direct lifecycleScope/repeatOnLifecycle usage. Remove the direct declaration only after build verification; lifecycle runtime can still be required transitively. |
| `com.google.android.material:material`, line 62 | Not unused: `res/values/themes.xml:3` inherits `Theme.Material3.DayNight.NoActionBar`. Removable only with a deliberate window-theme replacement. |
| Gson 2.11.0, line 78 | Used in `WebhookClient.send()` and `renderTemplate()`. Optional simplification with platform JSON would require rewriting behavior; not an unused dependency or established vulnerability. |
| Room, WorkManager, activity-compose, Compose UI/Material3, core-ktx | Used for implemented functionality. Removing them wholesale is not a minimal security fix. KSP/Room compiler is build-time, not a runtime reader of notifications. Compose BOM is version management, not an SDK. |
| `webhook/package.json:18`–19, dotenv / Express | Both used by the separate receiver. dotenv is optional if relying only on injected environment variables. Express sees payloads by design. Lockfile resolves Express 4.22.1; do not mistake the declared range for the exact installed version. |

No current CVE claim is made from version age alone. A resolved release dependency/SBOM and advisory scan remain necessary to cover all transitive versions.

## Verification limits and priority

This was a static source audit, not a penetration test. No local JDK/ADB command or built APK/merged manifest was found in the inspected environment, so no Android build, emulator run, backup extraction, proxy test, runtime logging capture or complete transitive binary audit was performed. Graph results are an index, not proof of security behavior. Only Graphify audit memory and this report were added.

Priority: enforce HTTPS and restrict redirects; remove production HTTP logging; exclude backups; disable broad default capture and enforce queue policy; expire/delete and encrypt retained data; protect credential storage and UI; replace full receiver logging. Then validate the actual release artifact with synthetic banking-like fixtures, including redirects, malformed secret headers, policy changes during delivery, backup/transfer and screenshots.

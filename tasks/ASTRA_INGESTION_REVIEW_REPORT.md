# Independent ingestion implementation sign-off

Date: 2026-09-24
Target: `notification-receiver`, HEAD `c76efd10aab28612b4241ca79090967ab39070bf`, including the existing uncommitted test/build changes.

## Verdict

**Repository implementation signed off. No confirmed actionable blocker found.** Next: coordinated synthetic rollout and end-to-end phone acceptance using `SETUP.md`. This review does not establish deployment, live background reliability, or readiness for real banking data.

Independently inspected the source and test assertions behind Luna's requirement matrix. No production correction was justified. Existing user changes remain intact; no commit, installation, deployment, live Hermes invocation or production database operation was performed.

## Source review

| Area | Files checked | Assessment |
| --- | --- | --- |
| Capture and privacy | `AppNotificationListenerService.kt`, `SensitiveNotificationFilter.kt`, `NotificationRepository.kt`, Node filter and shared fixtures | Allowlisting precedes text extraction. Sensitive filtering precedes callback caching/encryption and runs again before stored-item display/delivery and receiver insertion. Catalogues are aligned; unknown source formats remain outside proof. |
| Persistent identity | `NotificationRepository.kt`, `QueueCrypto.kt`, `NotificationPayload.kt`, `QueueDao.kt` | IDs are encrypted and persisted before delivery. Legacy rewrite failure returns no eligible item. Shared state locking and current-row reads serialize migration. Exact queued captures are suppressed; changed captures retain separate immutable rows, including while an earlier row is SENDING. |
| Migration and test isolation | `AppDatabase.kt`, `QueueMigrationInstrumentedTest.kt`, `app/build.gradle.kts`, `INGESTION_TEST_STEPS.md` | Migration changes the digest index without resetting rows. Tests use an actual v1 database with encrypted data and nondefault metadata. The isolated package guard precedes storage/key access. Databases/preferences are temporary; tests do not reset the Keystore key. |
| Serialization and delivery | `WebhookClient.kt`, `WebhookClientTest.kt`, `QueuePayloadTest.kt`, `MainActivity.kt`, `QueueWorker.kt`, `WorkerScheduler.kt`, `DeliveryCoordinator.kt` | Schema v2 preserves both text fields and stable identity. Template substitution is one-pass and JSON-escaped; absent expanded text is null. Invalid IDs cannot construct delivery. Connectivity tests create synthetic IDs. Retry/cancellation/recovery retain their documented bounds. |
| Durable acceptance | `webhook/server.js`, `webhook/storage.js`, `server.test.js`, `ingestion.test.js` | Authentication precedes parsing; validation and filtering precede insertion. Server source plus canonical UUID identifies retries. BEGIN IMMEDIATE, a unique index and commit-before-response resolve duplicates without overwrite. Migration preserves historical receipts and worker state. |
| Asynchronous processing | `webhook/hermes-worker.js`, `hermes-worker.test.js` | Transactional claims, lease-token checks and bounded retries cover stale completion and recovery. Model latency is outside acknowledgement. Text travels through stdin with shell disabled; complete JSON is validated and errors sanitized. Live profile/provider behavior remains a deployment check. |

Android filenames refer to the corresponding `app/src/main`, `test` or `androidTest` source set under `com/notificationforwarder/app`.

## Evidence and limitations

- Newly checked: production Android code, JVM tests, receiver/worker code and shared fixtures have no diff from `c76efd1`. Reviewed the outstanding instrumentation and isolated-build diff in full. No production code or test implementation changed in this review.
- Reused historical validation: 38 receiver/worker tests passed on 2026-09-22; 12 JVM tests and recorded Gradle build/lint checks passed previously. Luna's independent synthetic receiver exercise is historical evidence. These were not rerun without changed source or a new concern.
- The user reported seven isolated instrumentation tests passing on 2026-09-22; raw device output is unavailable. This is not independently observed runtime evidence. Concurrent-read tests do not force every possible interleaving.
- The test named “failed commit” injects a BEFORE INSERT abort, proving persistence-failure handling rather than an OS-level commit fault. Separate-process receiver restart coverage exists in `server.test.js`; the ingestion replay test also reopens storage.
- Newly checked graph baseline: 647 nodes, 1,049 edges, zero missing source files and zero dangling endpoints; 17 self-loops remain. Weak connectivity and suppressed AST relationships limit navigation. Documentation refresh results belong in `graphify-out/GRAPH_REPORT.md`.
- `rules.md` remains absent; available project guidance was followed. No frontend, architecture, dependency or runtime behavior change was made. Final documentation validation is recorded in `Dairy.md`.

## Remaining acceptance gates

1. Upgrade receiver and main app together, preserving signing identity, app data, source ID and pending queue. Follow the offline/force-stop procedure; saving delivery settings or toggling forwarding can clear pending items. Drain custom-v1 queues against the old receiver before replacing that path.
2. Verify synthetic filtering, expanded text, UUID reuse, offline retry, lost-response replay, process restart and reboot on the upgraded pair.
3. Verify a backlog larger than one Android batch drains without a new notification. The worker handles one batch per run and relies on scheduled fallback for remaining work. Include screen-off/idle, network transitions and laptop sleep/resume.
4. Diagnose the historical failed Hermes receipts before deciding on retries. Historical laptop reboot confirmation does not establish upgraded Android reboot acceptance.
5. Decide receiver storage protection and raw-event retention before real financial data. Transaction reconciliation, a verified expense ledger and Telegram querying remain later work.

No actionable inline code findings were produced.

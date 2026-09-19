# Ingestion hardening implementation report

Date: 2026-09-19

## 1. Requirement completion and deviations

Implemented across Android and the receiver:

- Allowlisted notifications are filtered before callback-cache insertion, then filtered again at the Android repository boundary and on stored-item reads.
- Android encrypted payloads preserve short `text`, nullable expanded `bigText`, and one persisted UUID-v4 `eventId` generated at capture time.
- Room schema version 2 replaces the unique notification-key digest index with a nonunique index. Exact queued captures are suppressed using decrypted package, notification key, posting time, title, short text and expanded text; changed captures remain separate immutable rows.
- Legacy encrypted payload decoding handles absent fields explicitly. Missing event IDs are assigned and persisted before delivery; invalid existing IDs are discarded as corrupt content. Sensitive legacy rows are deliberately deleted.
- Receiver schema v2 requires a valid UUID-v4 event ID and validates nullable/string expanded text with the existing field limits.
- Receiver filtering uses the versioned production catalogue after authentication and structural validation and before SQLite insertion.
- Receiver SQLite migration is transactional and repeatable. `(source_id,event_id)` uniqueness, payload hashes, source isolation, atomic duplicate resolution, conflict responses, restart durability and Hermes receipt identity are implemented.
- Hermes continues to process by receipt ID and receives stored expanded text unchanged.

The implementation deliberately has no schema-v1 receiver compatibility mode, remote filtering rules, finance transaction deduplication, receiver encryption-at-rest, deployment, commit, phone installation or live Hermes execution.

The repository integration tests requested for failed ciphertext rewrite, sensitive legacy discard through `NotificationRepository`, stable IDs across recovery/reopen, exact-vs-changed captures while an earlier row is `SENDING`, and changed `appName` suppression are not yet complete. The current instrumentation source compiles and contains a legacy-schema fixture, but does not yet set the database user version to 1 or insert a premigration row, so it cannot establish the actual `MIGRATION_1_2` behavior. The Keystore/manual rewrite checks are written but unexecuted. This is the principal implementation-test gap.

`rules.md` is absent. Work followed the available project guidance and surrounding conventions; no replacement rules file was invented.

## 2. Changed behavior and interfaces

The blank Android payload path emits schema 2 with `eventId`, `bigText` and existing optional `deviceId`/`notificationKey`. Custom templates support quoted `{eventId}` and `{bigText}` and unquoted `{bigTextJson}`. Replacement is one pass over the original template and Gson performs string encoding. Empty expanded text normalizes to JSON `null`; nonempty text is preserved exactly, including quotes, emoji, control characters, newlines and literal placeholder-looking text.

The receiver adds `WEBHOOK_SOURCE_ID`, defaulting to `personal-phone`. The source is configured independently of bearer-token rotation and is never derived from request fields or token hashes. A same-source/event retry with the same canonical payload returns the original receipt and `duplicate: true`; changed content returns `409 event_id_conflict`; failed storage returns sanitized `503`.

## 3. Filter catalogue and coverage

The receiver catalogue is version 1 and supports optional package constraints. It normalizes NFKC, removes Unicode format characters, collapses Unicode whitespace/separators, and evaluates title, text, expanded text, and their joined content. Rule IDs cover OTP/TAC, verification codes, login/device notices, approval requests, Secure2u and security notices. Sensitive content wins over payment wording and drops the complete notification.

The 81 shared synthetic fixtures cover English and Malay OTP/TAC, verification, login/device registration, approval/authorization, Secure2u/security, mixed case, Unicode formatting, joined fields, expanded-only sensitive content, safe receipts, numeric false positives, and literal substrings. Node tests exercise the production receiver catalogue and HTTP filter. Android tests consume the same fixtures and assert matching decisions and rule IDs. These generic rules are not verified bank formats; unknown wording can evade them, and package-specific rules require verified IDs and redacted fixtures.

## 4. Validation commands and results

Verified:

- `npm test` — 38 tests passed.
- `gradlew.bat :app:testDebugUnitTest` — final rerun passed, 12 JVM tests with zero failures.
- `gradlew.bat :app:assembleDebug` — final rerun passed.
- `gradlew.bat :app:lintDebug` — final rerun passed.
- `gradlew.bat :app:assembleDebugAndroidTest` — passed; instrumentation execution and genuine v1-to-v2 migration assertions remain pending.
- `git diff --check` passed during implementation.

The Android instrumentation tests have not executed because no emulator or physical device is available or authorized. No live Hermes profile, production database, deployment or phone installation was used.

The parent ran all four Gradle tasks together after the final source changes; the command completed successfully in 1m 9s. Environment: `JAVA_HOME=C:\Program Files\Android\Android Studio\jbr`, `ANDROID_HOME=C:\Users\kean5\AppData\Local\Android\Sdk`, `GRADLE_USER_HOME=C:\Users\kean5\.gradle`. The existing AGP 8.5.2 warning about compile SDK 36 remains. The parent independently reran the 38-test receiver suite and inspected the diff; final acceptance awaits the unfinished integration checks.

## 5. Stable UUID, replay and update evidence

Receiver tests verify response-loss replay, process restart, sequential retries, concurrent retries across SQLite connections, source isolation, token rotation, same-payload canonicalization, and changed-payload conflicts. Hermes tests verify duplicate ingestion produces one receipt/processing identity and expanded text reaches mocked inference.

Android source persists the UUID in encrypted payload ciphertext and reuses it when rereading delivery rows. JVM tests prove repeated request construction preserves the supplied ID and rejects missing/invalid IDs. Manual encrypted rewrite instrumentation is written but unexecuted, and complete `NotificationRepository` recovery and failed-write scenarios remain open test work.

## 6. Upgrade, rollback and device acceptance

Prepared runbook: stop receiver and worker, back up SQLite with services stopped, keep the phone offline or force-stopped during the coordinated upgrade, upgrade receiver and app together, verify synthetic v2 delivery/replay/conflict behavior, then resume. Do not toggle forwarding settings as a queue-preserving pause because existing settings changes can clear the queue. Keep source ID stable through bearer-token rotation.

Receiver database rollback can restore a stopped-service SQLite backup. Room v2 cannot be opened by the old v1 app; Android downgrade therefore requires a deliberate supported migration or queue reset and is outside this change. Keep the upgraded app and strict-v2 receiver paired during rollback planning.

Pending device checklist: synthetic sensitive rejection, exact expanded-text delivery, offline retry, lost-response replay, process restart, phone reboot, HTTPS acceptance, and Samsung background/idle behavior.

## 7. Locations and unresolved risks

Filtering and payload model: `app/src/main/java/com/notificationforwarder/app/data/SensitiveNotificationFilter.kt`, `NotificationPayload.kt`, and `QueueCrypto.kt`.

Queue and migration: `QueueItem.kt`, `QueueDao.kt`, `AppDatabase.kt`, and `NotificationRepository.kt`.

Serialization and test action: `network/WebhookClient.kt` and `MainActivity.kt`.

Receiver and worker: `webhook/sensitive-notification-filter.js`, `webhook/server.js`, `webhook/storage.js`, and `webhook/hermes-worker.js`.

Tests: `webhook/ingestion.test.js`, existing receiver/worker suites, `app/src/test`, and `app/src/androidTest`.

The remaining material risks are the unfinished repository integration test scenarios, physical-device acceptance, and deployment coordination. Graph refresh is prepared but unfinished; existing graph paths remain stale and extraction health has not received final review. See `INGESTION_HARDENING_STATUS.md` for the continuation checklist. No final all-requirements acceptance is claimed.

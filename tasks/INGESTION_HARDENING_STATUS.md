# Ingestion hardening — progress and continuation

## Current checkpoint — 2026-09-26

Latest follow-up: the first patched APK was confirmed installed by SHA-256, but
the user reported immediate Failed entries when the laptop lost network or slept.
Safe metadata showed an empty queue, 26 Sent, 12 Failed, 0 Expired, forwarding
enabled and 24-hour retention. Deleted items' original response codes are unknown.
A read-only request to an unused ngrok hostname reproduced HTTP 404 with
`ngrok-error-code: ERR_NGROK_3200`, which the client still treated as permanent.
The client now retries that exact combination; ordinary 404 and permanent
authentication/validation errors remain terminal. Safe QueueDelivery logs record
only local row ID, attempts, permanent flag and fixed error code.

The corrected main build passed 17 JVM tests, build and lint, then was installed
in place. Installed APK hash matched; settings and queue DB/WAL bytes were
unchanged. Temporarily paused phone connectivity was restored. No uninstall or
data clearing. Main APK SHA-256:
`1141e6d3b464fa1c1cccfb4b46804cc54ffc001b7e9e5cee5bd216af05dbab35`.

The user requested no further agent testing to conserve tokens. They will repeat
the laptop outage with new synthetic notifications, checking that items stay
Pending and automatically drain after recovery without Sync Queue. Existing
Failed counts are cumulative and deleted items cannot be restored. Graph refresh
remains due before committing. Older checkpoint statements below are historical.

The checkout now retains transient failures until configured expiry instead of
deleting at the attempt limit. Permanent failures remain terminal. The old
`maxRetries` preference now caps backoff growth; the UI calls it **Backoff growth
limit**. Delay is 60 seconds initially, doubling up to 32 minutes plus 0–4 seconds
jitter. After each batch the worker persists a successor for the earliest pending
retry time, including when all rows are future-due. Periodic recovery avoids
adding a redundant successor while the one-time chain exists.

Validation: 14 JVM tests passed; isolated debug APK, instrumentation APK and lint
passed. The new synthetic receiver test observed four 503 attempts for each of
five events, followed by five distinct durable receipts and idempotent replay.
This is receiver evidence, not execution of Android WorkManager. All nine isolated
repository instrumentation tests subsequently passed on the connected SM-S918B
(Android 16), observed through Gradle. Main-app build and lint also passed after
adding dismissible, swipeable snackbars that replace old messages rather than queue.

Snackbar close, both swipe directions and ten rapid Save taps were checked on the
isolated phone app using validation feedback. No queued backlog remained after
five seconds. Screenshots are black because the app's secure-window protection
remains enabled; verification used the UI hierarchy. Large-font/TalkBack checks
remain separate.

Next: install the compatible main-app build and run its
controlled outage checklist without Sync Queue. `webhook/outage-receiver.js`
provides localhost:3301 with a separate temporary database and console commands
`recover` / `outage`; it never loads production .env or starts Hermes. Do not
uninstall the main app or clear its queue to upgrade. Only the isolated test app
was installed; the main forwarder and live receiver are unchanged. No commit or
push occurred in this checkpoint. The main APK is preserved at
`app/build/outputs/apk/main-debug/app-debug.apk`; the normal debug output was
subsequently rebuilt as the isolated test app. The graph still needs an
incremental refresh before a future commit.

The sections below describe the previous deployed version and historical evidence.

Updated 2026-09-25. The main Android app and local v2 receiver are deployed for synthetic testing. Normal Discord forwarding, OTP blocking and automatic delivery of surviving pending items are user-confirmed. Outage testing exposed queue loss after repeated 503 responses; investigate this next. Independent repository sign-off and the seven user-confirmed isolated instrumentation passes remain historical evidence, not full live acceptance.

## Next-session handoff: outage retry loss

The server stayed offline until most queued items had moved to Failed. The user reported repeated 503s and said only the oldest item remained pending. One manual Sync Queue attempt succeeded; subsequently the user explicitly confirmed pending content delivered automatically shortly after server recovery. Automatic retry therefore works for surviving pending content, but full backlog recovery is not accepted.

Current code treats 503 as transient, increments each item's attempt count, and deletes content when the configured retry limit is reached. Failed is a counter, not a recoverable queue. This is consistent with retry exhaustion during the outage; the exact per-item attempt history and why the oldest survived were not captured. Do not claim a proven "only the first item is processed" bug. The inspected ngrok history contained two 200 responses and no retained 503, so the origin of the reported 503s is unverified.

Next session should inspect `QueueWorker.kt`, `WorkerScheduler.kt`, `NotificationRepository.kt`, `QueueDao.kt` and retry settings. Reproduce a controlled outage with multiple synthetic items; capture sanitized attempt counts, next-attempt timestamps and HTTP status without payloads or credentials. Determine the retry schedule and deletion cause, then address transient-outage data loss with explicit retention behavior. Verify automatic drain, including more than one batch, without Sync Queue. Preserve filtering, encryption, persistent event IDs and intentional policy/expiry deletion. No retry-policy fix was made in this session.

The checkout is already on the old laptop hosting the receiver. Do not repeat a cross-machine copy or reinstall without a new code change. The database is `webhook/data/notifications.sqlite`; ngrok inspection is `http://127.0.0.1:4040/inspect/http`. The phone endpoint is the active HTTPS tunnel plus `/webhook`. Stop Forwarder stops ngrok as well as the receiver and worker, so it does not isolate receiver downtime; use a controlled failure setup for diagnosis.

## Implemented in the checkout

- Versioned Android and Node security filters cover OTP/TAC, authentication codes, login/device notices, approval requests and Secure2u, including Malay wording and Unicode normalization. Original accepted text is preserved. Both platforms use 81 shared synthetic fixture cases.
- Android filtering runs before callback caching and encryption, and again for stored items before display/delivery. Sensitive legacy items are intentionally discarded without being counted as sent.
- Encrypted payloads preserve `text`, nullable `bigText`, and a UUID-v4 `eventId`. Safe legacy payloads acquire a persisted ID before delivery; failed writes do not expose temporary IDs to the sender.
- Room v2 removes notification-key uniqueness. Exact queued captures are suppressed; changed content and different posting times remain separate immutable events, including when earlier content is sending.
- Default HTTPS payloads use schema v2. Custom templates support `{eventId}`, `{bigText}` and `{bigTextJson}`, with one-pass replacement and Gson string encoding. Connectivity tests explicitly create a synthetic UUID.
- Receiver authentication and strict validation precede filtering and SQLite insertion. Stable `WEBHOOK_SOURCE_ID` plus event ID identifies retries. Equal retries return the original receipt; changed content returns 409; sensitive content returns 422; storage failures return sanitized 503.
- SQLite migration preserves historical receipts and worker state. Hermes continues to use receipt IDs; tests use mocked inference and prove expanded text reaches the prompt.
- Setup, architecture, project status and template documentation describe these changes and the coordinated upgrade procedure.

## Verified

- `npm test` in `webhook/`: **38 passed**, including concurrent independent SQLite connections, lost acknowledgements, restart durability, token rotation, source isolation, conflict handling, migration rollback, log privacy and worker regression.
- Android JVM reports: **12 passed**, covering shared filtering fixtures, legacy decoding, round trips, template encoding and request identity validation.
- Final combined Gradle rerun passed: Android JVM tests, debug app build, lint and instrumentation APK build.
- On 2026-09-22 the user confirmed all seven migration/repository instrumentation tests passed using the supplied test steps. This is user-reported runtime evidence, not an agent-observed run.
- `git diff --check`: passed at the checkpoint, with informational Windows line-ending notices.

## Unfinished work — do not treat as accepted

1. Resolve the outage retry-loss issue above. Deployment and basic live capture/OTP rejection are confirmed; expanded-text live verification, response-loss replay, process restart and phone reboot remain pending. A surviving pending item automatically delivering does not establish lossless outage recovery.
2. Verify backlog recovery beyond one Android batch, screen-off/idle behavior and laptop sleep/resume; diagnose historical failed Hermes receipts before deciding on retries. Storage protection, retention and finance-ledger work remain separate.

## Operational constraints

The earlier checkpoint was committed as `c76efd1` (`WIP message filter`); subsequent test and documentation changes remain uncommitted. On 2026-09-25 the main debug app (`com.notificationforwarder.app`) was built and installed. A signature mismatch prevented replacement; the user authorized discarding old app data and uninstalled the original before fresh installation. Settings and notification access were restored. The stopped receiver's SQLite/WAL/SHM backup was verified before services resumed in this checkout. Keep local backups and credentials out of commits. Receiver encryption at rest, retention, transaction deduplication and banking certification remain outside scope. No verified bank-specific package rules were invented. `rules.md` is absent.

For upgrade, keep the phone offline and force-stop it while components are replaced; do not toggle forwarding or save template changes to pause delivery because those settings changes can clear the queue. Back up SQLite with services stopped and keep `WEBHOOK_SOURCE_ID` stable. Drain a v1 custom-template queue against the old receiver before changing its template. Do not downgrade the old app onto Room v2 data. Full instructions are in `SETUP.md`.

## Main records

- `ASTRA_INGESTION_REVIEW_REPORT.md`: independent repository sign-off and remaining acceptance gates.
- `INGESTION_HARDENING_IMPLEMENTATION_REPORT.md`: Luna's detailed requirement and validation handoff.
- `Dairy.md`: dated implementation and parent-review history.
- `Architecture.md`: current boundaries and migration design.
- `README.md` and `SETUP.md`: schema-v2 template and deployment/device checklists.

Repository review is complete and synthetic rollout is underway. Continue with the outage retry-loss handoff above, then finish live acceptance. Graph refresh and extraction limitations are recorded in `graphify-out/GRAPH_REPORT.md`; these documentation edits require an incremental refresh before a future commit. Do not repeat completed tests without a new change or concern.

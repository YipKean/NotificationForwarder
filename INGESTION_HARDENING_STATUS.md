# Ingestion hardening — progress and continuation

Updated 2026-09-19 at the user's request to summarize work in Markdown.

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
- Final combined Gradle rerun passed: Android JVM tests, debug app build, lint and instrumentation APK build. Instrumentation tests were compiled, not executed.
- `git diff --check`: passed at the checkpoint, with informational Windows line-ending notices.

## Unfinished work — do not treat as accepted

1. Repair and complete Android instrumentation tests. The current legacy database fixture does not set `user_version=1`, so it does not prove the actual Room migration. Include all v1 indices and a pre-migration row with metadata.
2. Exercise the actual repository with isolated preferences and temporary Room databases: failed ciphertext updates must retain rows without delivery; concurrent display/delivery migration must reuse one persisted UUID; SENDING recovery/reopen must preserve identity; sensitive legacy rows must disappear without sent increments; exact/changed capture behavior must preserve the original sending event.
3. Run instrumentation only on an authorized emulator/test device. Compilation alone does not verify Keystore, Room or runtime behavior.
4. Refresh graphify incrementally and inspect health warnings. The ignored local runtime, runner and static-document extraction are prepared; existing graph paths are still stale.
5. Complete independent parent review after remaining test corrections and update the implementation report. Receiver review found no blocking issue; Android integration coverage remains incomplete.
6. Perform separate synthetic device acceptance: filtering, expanded text, offline retry, response-loss replay, process restart and reboot. Deployment has not been performed.

## Operational constraints

Work remains uncommitted in the active checkout. No production database, phone installation, model configuration or live Hermes profile was changed. Receiver encryption at rest, retention, transaction deduplication and banking certification remain outside scope. No verified bank-specific package rules were invented. `rules.md` is absent.

For upgrade, keep the phone offline and force-stop it while components are replaced; do not toggle forwarding or save template changes to pause delivery because those settings changes can clear the queue. Back up SQLite with services stopped and keep `WEBHOOK_SOURCE_ID` stable. Drain a v1 custom-template queue against the old receiver before changing its template. Do not downgrade the old app onto Room v2 data. Full instructions are in `SETUP.md`.

## Main records

- `INGESTION_HARDENING_IMPLEMENTATION_REPORT.md`: Luna's detailed requirement and validation handoff.
- `Dairy.md`: dated implementation and parent-review history.
- `Architecture.md`: current boundaries and migration design.
- `README.md` and `SETUP.md`: schema-v2 template and deployment/device checklists.

The next agent should start with this file and the implementation report, inspect the current diff, and finish the outstanding tests and graph refresh without repeating completed receiver work.

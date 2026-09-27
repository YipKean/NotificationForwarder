# Luna ingestion hardening review

Date: 2026-09-22  
Reviewer: Luna  
Review target: `notification-receiver` at `c76efd10aab28612b4241ca79090967ab39070bf` (`WIP message filter`)

## Verdict

**Ready for Astra’s independent repository review; not approved for deployment or banking use.**

The Android ingestion, encrypted queue, Room migration, schema-v2 serialization, receiver durability and transport deduplication paths were reviewed against the current source. No confirmed implementation blocker remains in the reviewed code or automated receiver checks. The isolated Android instrumentation suite was reported as passing by the user, but raw device logs were not supplied, so Luna cannot independently verify that runtime evidence. The receiver has not been deployed by this review, the production phone app has not been upgraded by this review, and finance-ledger or banking readiness is outside this handoff.

## Reviewed baseline

- Branch: `notification-receiver`.
- HEAD: `c76efd10aab28612b4241ca79090967ab39070bf`.
- Historical implementation checkpoint: `c76efd1` (`WIP message filter`), verified locally with `git show`.
- Pre-existing uncommitted changes at review start: `Architecture.md`, `Dairy.md`, `INGESTION_HARDENING_IMPLEMENTATION_REPORT.md`, `INGESTION_HARDENING_STATUS.md`, `PROJECT.md`, `app/build.gradle.kts`, `app/src/androidTest/java/com/notificationforwarder/app/QueueMigrationInstrumentedTest.kt`, and untracked `INGESTION_TEST_STEPS.md`.
- No `rules.md` or `Design.md` exists. The review followed `AGENTS.md`, `PROJECT.md`, `Architecture.md`, and the relevant diary entries; no replacement rules file was invented.
- The current diff is preserved. Luna added this report and the final diary/status synchronization; no production source correction was required after review.

## Requirement matrix

| Requirement | Current source and tests | Evidence type | Result and limitation |
| --- | --- | --- | --- |
| Filter sensitive notifications before persistence and delivery | `AppNotificationListenerService.kt:52`, `NotificationRepository.kt:38,261`, `SensitiveNotificationFilter.kt`; shared fixtures and receiver ingestion tests | Source review; automated tests; user-reported device run | Pass for the catalogue and fixtures. Unknown bank wording and unverified package-specific formats remain outside proof. |
| Preserve short and expanded text, including quotes, Unicode, newlines and literal placeholder text | `NotificationPayload.kt`, `QueueCrypto.kt`, `WebhookClient.kt:53-87`; `QueuePayloadTest`, receiver expanded-text tests | Source review; `npm test` 38/38; prior JVM evidence | Pass for tested values. The isolated device execution is user-reported without raw logs. |
| Persist one UUID-v4 before delivery and reuse it across retries/reopen | `NotificationRepository.kt:44,255-278`, `WebhookClient.kt:53`, `QueueMigrationInstrumentedTest.kt:102-133` | Source review; prior JVM/instrumentation evidence; user-reported seven-test pass | Pass by source and reported test result. Delivery is not independently exercised on a production APK here. |
| Migrate Room v1 to v2 without resetting encrypted rows | `AppDatabase.kt:10-31`, `QueueMigrationInstrumentedTest.kt:42-92` | Source review; user-reported instrumentation pass | Fixture sets `user_version=1`, inserts encrypted data and nondefault metadata, and checks the nonunique index. Raw device output was not available to Luna. |
| Leave safe legacy rows queued when ciphertext rewrite fails | `NotificationRepository.kt:255-278`, `QueueMigrationInstrumentedTest.kt:143-171` | Source review; user-reported instrumentation pass | Pass by reviewed failure path and reported test. A failed rewrite is not delivered until a later successful migration read. |
| Suppress exact queued captures while retaining changed content, including when the original is `SENDING` | `NotificationRepository.kt:44-79`, `QueueDao.kt:16`, `QueueMigrationInstrumentedTest.kt:174-204` | Source review; user-reported instrumentation pass | Pass for the reviewed comparison fields. This remains app-level event identity, not financial transaction reconciliation. |
| Discard invalid IDs and sensitive legacy rows without false sent counts | `NotificationRepository.kt:255-266`, `QueueMigrationInstrumentedTest.kt:134-173,205-231` | Source review; user-reported instrumentation pass | Pass by reviewed code and reported tests. Corrupt-row behavior is intentionally terminal. |
| Keep tests isolated from the installed application | `app/build.gradle.kts:18-25`, `QueueMigrationInstrumentedTest.kt:35-75`, `INGESTION_TEST_STEPS.md` | Source review; APK/manifest build evidence; user-reported device run | Pass for package, temporary database, preferences and Keystore boundaries. No production app installation was performed in this handoff. |
| Authenticate, validate, filter, then durably accept schema-v2 events | `webhook/server.js:112-215` | Source review; `npm test` 38/38 | Pass. Validation rejects schema-v1 and body-selected source identities; sensitive content returns 422 before insert. |
| Deduplicate by authenticated source and event ID, preserve conflicts and token rotation | `webhook/storage.js:26-77`, `server.js:205`; `ingestion.test.js`, `server.test.js` | Source review; `npm test` 38/38; independent review script | Pass. Same-source retries resolve to one receipt; changed content is 409; another source is independent. |
| Acknowledge only durable acceptance and recover after response loss or restart | `server.js:193-219`, `storage.js:52-77`; receiver tests | Source review; `npm test` 38/38; independent review script | Pass for tested SQLite transactions and replay. The test titled “failed commit” injects a `BEFORE INSERT` abort, so it proves pre-insert persistence failure handling rather than an operating-system commit I/O fault. |
| Process receipts asynchronously with leases, bounded retries and tool-disabled Hermes input | `webhook/hermes-worker.js:23-75,167-220`, `HERMES_SETUP.md`; worker tests | Source review; `npm test` 38/38 | Pass for mocked inference and worker state. Live Hermes execution and provider availability are outside this handoff. |

## Findings and dispositions

### No confirmed implementation blocker

The current source review, receiver suite, and independent receiver exercise did not produce a confirmed defect requiring a code correction. No new public API, dependency, payload schema, or database migration was added by Luna during this review.

### Evidence limitations retained as unresolved review items

1. **Device evidence is user-reported.** The user reported that all seven isolated instrumentation tests passed on 2026-09-22. Raw output and run-specific device details were not supplied. Concurrent display/pending reads exercise the shared state lock, but their scheduling does not force every possible interleaving. These are evidence limitations, not confirmed implementation defects.
2. **Receiver failure injection is narrower than its test name.** The “failed commit” case installs a SQLite `BEFORE INSERT` abort. It validates sanitized 503 handling and replay after a persistence failure, but it does not simulate a disk or OS-level failure after SQLite has begun committing. No code change is justified by that distinction.
3. **Generic filter coverage is finite.** The 81 shared fixtures cover documented English/Malay and Unicode patterns, including expanded-only sensitive text. They cannot establish that every bank’s future notification wording will match. Bank-specific additions require verified package IDs and redacted fixtures.
4. **Live and operational acceptance is pending.** Offline retry, lost-response replay through the upgraded phone, process restart, reboot, sleep/resume, Samsung background limits, HTTPS certificate behavior and coordinated receiver/app rollout still require a synthetic end-to-end run. Android `QueueWorker` processes at most one configured batch per run and has periodic fallback. Separately, Hermes `--once` processes one receipt and `--watch` repeatedly attempts processing. Neither establishes immediate draining of a phone backlog larger than one batch.
5. **Storage and finance scope remains open.** Receiver SQLite is not application-encrypted and has no verified finance ledger or financial transaction deduplication. Keep data synthetic until those separate decisions and acceptance checks are complete.

## Changes made in this handoff

Luna did not change production Android or receiver behavior after review because no confirmed defect was found. The pre-existing uncommitted implementation/test/documentation changes listed in the baseline remain intact. This handoff adds the report itself and synchronizes the project status/diary with the reviewed evidence and graph refresh outcome.

## Validation and evidence log

- `npm test` from `webhook/` — **38 passed, 0 failed**, agent-observed on 2026-09-22.
- `node .gradle/parent-ingestion-review.cjs` — passed; agent-observed synthetic review of filtering, Unicode normalization, joined fields, restart durability, bearer-token rotation, concurrent retries, source isolation, conflicts, serialization and log privacy.
- `gradlew.bat :app:testDebugUnitTest` — 12 JVM tests passed in the previously recorded final rerun; historical/parent-observed evidence, not rerun for this documentation-only handoff.
- `gradlew.bat :app:assembleDebug` and `gradlew.bat :app:lintDebug` — passed in the previously recorded final rerun; historical/parent-observed evidence.
- `gradlew.bat :app:assembleDebugAndroidTest` — passed in the previously recorded final rerun; compilation evidence only.
- `gradlew.bat -PisolatedIngestionTests=true :app:assembleDebug :app:testDebugUnitTest :app:lintDebug`, followed by `gradlew.bat -PisolatedIngestionTests=true :app:assembleDebugAndroidTest :app:lintDebug` — historical 2026-09-21 checks passed; JVM tasks were up-to-date. Separate generated APK/manifest inspection confirmed `.ingestiontest` and `.ingestiontest.test` targeting. These checks do not establish device execution.
- USB command from `INGESTION_TEST_STEPS.md` — user reported all seven tests passed on 2026-09-22; raw output unavailable to Luna.
- `git diff --check` — passed at the review checkpoint; Windows line-ending notices are informational.

No deployment, main-app installation, production database change, live Hermes invocation, Telegram configuration, commit or push was performed by this review.

## Graph outcome

The graph refresh used the prepared ignored runtime at `.gradle/graphify-runtime` and the repository’s incremental manifest. The previous graph was stale: its report was built from `b2f7e9e3`, its manifest referenced the old `com/itsazni` namespace and `C:\Users\Joe\Desktop\Kean\Project\NotificationForwarder`, and its historical screenshot nodes were excluded by `.graphifyignore`. Luna refreshed the portable graph outputs after this report and regenerated `graph.json`, `GRAPH_REPORT.md`, `graph.html`, `manifest.json`, community analysis and labels against the current checkout.

The first refresh exposed ten documents without semantic extraction; a second pass added current-document anchors. Final graph counts, coverage checks and health warnings are recorded in `graphify-out/GRAPH_REPORT.md`, avoiding conflicting counts across successive refreshes. The build commit identifies the checkout base, while the manifest includes uncommitted source and documentation. Raw extraction produced unresolved endpoint candidates, repeated endpoint groups and producer-suppression warnings; final endpoint integrity does not prove complete extraction. Semantic token counters are unavailable, so displayed zero values are placeholders rather than measured zero cost. Graph output is a navigation aid; source review remains authoritative.

## Remaining rollout and live acceptance

Astra should treat these as a separate stage after repository review:

- Upgrade the main phone app and receiver together using the queue-preserving runbook; keep `WEBHOOK_SOURCE_ID` stable through bearer-token rotation.
- Confirm sensitive listener filtering, expanded-text HTTPS delivery, UUID reuse across retry/restart, and strict-v2 duplicate/conflict responses with synthetic notifications.
- Exercise offline queue retention and retry, a lost response after durable receiver acceptance, receiver/Hermes outage recovery, and a phone backlog larger than one configured batch without generating a new notification.
- Exercise Android process restart, phone reboot, force-stop boundaries, screen-off/idle and laptop sleep/resume; separately verify HTTPS certificate and redirect behavior.
- Keep real banking data out of the receiver until SQLite protection/retention and a reviewed finance ledger exist.

## Astra review request

> Astra: independently review the complete ingestion implementation and Luna’s changes. Verify the requirement matrix against source, examine migration and event-identity failure paths, confirm receiver durability and deduplication, inspect test isolation and graph health, and assess whether the evidence supports repository sign-off. Report actionable findings with severity and locations. Do not infer deployment or end-to-end acceptance from the isolated instrumentation pass.

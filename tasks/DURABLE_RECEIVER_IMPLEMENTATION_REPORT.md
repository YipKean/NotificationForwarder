# Durable receiver implementation report

## 1. Outcome

The correction pass closes the three review findings while preserving the durable receiver behavior. The `webhook/` receiver authenticates and validates notification requests, parses every accepted JSON media type, commits each accepted payload to SQLite, and returns `200` only after the insert succeeds. Environment loading is explicit to the standalone start path, tests inject their configuration, and server close and listen-error paths clean up storage. Hermes processing, Android changes, banking-content filters, HTTPS provisioning, scheduled tasks, laptop deployment and device validation remain unfinished.

## 2. Baseline

- Starting branch: `master`
- Starting HEAD: `50faec088109a892727fe3f0a23c8bf1a18ee872`
- Pre-existing worktree changes: the earlier uncommitted durable-receiver implementation changes and untracked root `AGENTS.md`; the correction pass preserved the existing scope and did not edit `AGENTS.md`.
- No commit or push was performed.

## 3. Changes

- `webhook/storage.js`: adds the small SQLite storage module. It resolves relative paths from `webhook/`, creates the parent directory, enables WAL, `synchronous=FULL`, and a 5,000 ms busy timeout, creates `notification_events`, and uses a parameterized insert.
- `webhook/server.js`: loads `.env` only when the standalone `start()` path needs it, keeps explicit test configuration isolated, aligns Express parsing with the accepted `+json` content types, and handles storage cleanup on direct server close, signals, synchronous startup errors and asynchronous listen errors with fixed messages.
- `webhook/server.test.js`: contains seventeen Node tests covering explicit configuration isolation, authentication order and wrong-token rejection, the full default Android payload, schema-v1 payloads, `+json` parsing, validation, size/content errors, real SQLite failure, log safety, duplicate behavior, path resolution, direct-close and signal cleanup, sanitized synchronous and asynchronous bind failures, and controlled process-restart durability.
- `webhook/.env.example`, `webhook/.gitignore`, `webhook/package.json`, and `webhook/package-lock.json`: add the database setting, broad `.env` and SQLite/sidecar ignores while preserving `.env.example`, and the Node `>=24.13.0 <25` engine declaration.
- `README.md`, `SETUP.md`, and `PROJECT.md`: describe SQLite retention, Windows setup and migration, the local-only synthetic scope, and the current lack of processing/deduplication.
- `Architecture.md` and `Dairy.md`: document the receiver boundary and record this correction pass and its validation evidence.

## 4. Contract

Accepted payloads are JSON objects with required string fields `packageName`, `appName`, `title`, `text`, and a nonnegative safe-integer `postedAt`. Optional `schemaVersion` must be `1`; optional `deviceId` and `notificationKey` are strings with the documented limits. Unknown fields and invalid types are rejected without echoing input. Text is not trimmed or altered.

The receiver preserves `401`, `403`, and `413`, returns `415` for unsupported content types, `400` for malformed or invalid JSON, and fixed `503 storage_unavailable` responses when storage fails. A successful response contains `ok`, the fixed message, and the persisted UUID receipt ID. The database table has `receipt_id` (primary key), `received_at` (UTC ISO string), and `payload_json`.

Each request creates a row. There is intentionally no notification-key or content deduplication because the installed Android app has no persistent event ID. There is no automatic deletion, processing state, or public read endpoint. The database is not application-encrypted and is for synthetic testing only.

## 5. Verification table

| Requirement | Test or check | Command | Result |
| --- | --- | --- | --- |
| Locked dependencies install | Dependency installation | `npm ci` in `webhook/` | Passed; 69 packages added |
| Runtime version | Version check | `node --version`, `npm --version` | Node `v24.13.0`, npm `11.6.2` |
| Auth before parsing and wrong-token rejection | `requires bearer authentication before parsing the body` | `npm test` | Passed; missing token `401`, wrong token `403`, no insert |
| Full default Android and schema-v1 payloads persist | Two persistence tests | `npm test` | Passed; default includes `deviceId` and `notificationKey` |
| Accepted JSON media types parse consistently | `parses accepted application plus-json media types` | `npm test` | Passed; `application/vnd.notification+json` persisted |
| Validation and fixed status codes | Invalid, malformed, unsupported content, and oversized tests | `npm test` | Passed |
| Commit failure is not acknowledged | Real closed SQLite connection test | `npm test` | Passed; `503`, no acceptance log |
| Secrets and payloads stay out of logs | Log safety test | `npm test` | Passed |
| Duplicate limitation is explicit | Repeated request test | `npm test` | Passed; two receipts and rows |
| Receiver restart preserves data and controlled close is usable | Child-process restart test with explicit config and application close | `npm test` | Passed; no `child.kill()` used for the evidence path |
| Startup fails closed | Missing/example token and unusable-path test | `npm test` | Passed |
| Direct close releases storage without signals | `closes storage when server.close is called without signal handlers` | `npm test` | Passed; `DatabaseSync.close` called once |
| Signal shutdown releases storage and listeners | `closes storage and removes signal handlers on signal shutdown` | `npm test` | Passed; `DatabaseSync.close` called once and listener counts restored |
| Synchronous listen failures are sanitized | `closes storage and sanitizes synchronous listen failures` | `npm test` | Passed; storage closed once, fixed log, no raw error |
| Asynchronous bind failures are sanitized | `sanitizes asynchronous listen failures` | `npm test` | Passed; both startup databases closed, fixed log, no raw error |
| Explicit configuration is isolated from `.env` loading | `uses explicit configuration without loading an environment file` | `npm test` | Passed |
| JavaScript syntax | Three source checks | `node --check server.js; node --check storage.js; node --check server.test.js` | Passed |
| Whitespace | Repository diff check | `git diff --check` | Passed; Git emitted only line-ending normalization warnings |

The final test run reported 17 tests, 17 passed, 0 failed, 0 skipped. Node printed its expected warning that `node:sqlite` is experimental.

## 6. Durability evidence

The route validates the body, generates `receiptId` and `receivedAt`, calls the parameterized SQLite insert, and only then writes the acceptance log and sends `200`. SQLite is in autocommit mode, so the successful `run()` returns after the insert transaction commits. The persistence test parses the response receipt and matches it to the stored row; the full default Android payload and schema-v1 payload round-trip, including quotes, Unicode and newlines. The restart test starts a child receiver with explicit configuration, sends a payload, asks the application to call `server.close()`, starts a second child against the same database, and confirms the row remains. The test does not claim that Windows `child.kill()` is a graceful shutdown mechanism. Prototype tracking around `DatabaseSync.close` proves direct server close, signal shutdown and both startup failure paths release their database connections. Closing the SQLite connection before a request produces the fixed `503` response and no acceptance log. A port bind failure is reported with a fixed message and closes startup storage through the listen-error path.

## 7. Limitations and deviations

- Node `>=24.13.0 <25` is declared in `webhook/package.json` because the implementation uses built-in `node:sqlite`; Node reports that module as experimental. The `engines` field documents the requirement but npm does not enforce it by default.
- Android still sends no persistent event ID, so retries or repeated callbacks can create duplicate rows.
- The database is local, unencrypted application storage with no retention or processing worker. This is synthetic transport storage, not a finance ledger.
- HTTPS reverse-proxy setup, phone reachability, Hermes processing, laptop migration, Android builds and S23 Ultra tests were not performed.
- `Architecture.md` and `Dairy.md` were added because the repository instructions require those project records for architecture changes and meaningful implementation work.

## 8. Review pointers

- Authentication order and timing-safe comparison: `webhook/server.js:57-80`.
- Environment loading, content-type parsing and payload validation: `webhook/server.js:11-143`.
- Insert-before-response ordering and storage failure handling: `webhook/server.js:162-191`.
- Startup configuration, database initialization, direct-close cleanup and listen-error handling: `webhook/server.js:214-305`.
- SQLite path, pragmas, schema and parameterized insert: `webhook/storage.js:7-54`.
- End-to-end evidence and failure cases: `webhook/server.test.js:117-623`.

## 9. Parent review — 2026-09-16

The parent independently reviewed the corrected HTTP parsing, storage lifecycle, startup errors, configuration loading and Git ignore rules. No blocking findings remain for the synthetic receiver milestone. The parent reran all 17 tests successfully and verified `git diff --check`. A separate filesystem-read trap confirmed that importing the receiver and using explicit configuration perform zero `.env` reads. `git check-ignore` confirmed custom SQLite paths, sidecars and `.env.local` are ignored while `.env.example` is not.

This review accepts the local receiver implementation only. Laptop deployment, real operating-system signal delivery, phone-to-receiver HTTPS connectivity, Hermes processing and banking use remain unverified or deferred as described above.

## 10. Follow-up status — 2026-09-18

The historical receiver review above predates the laptop handoff. The user has since verified the loopback receiver on the old Windows laptop, phone reachability through an ephemeral ngrok HTTPS tunnel, and a separate `finance-notifications` Hermes Agent `v0.21.0` profile configured for Luna. The new `webhook/hermes-worker.js` adds asynchronous synthetic classification with durable leases, bounded retries and validated `hermes_processing` rows. The worker and receiver suites now report 27 passing tests together.

Two live synthetic receipts completed as `non_transaction` classifications. This follow-up does not change the original limitations: the Android app has no persistent event ID, duplicate delivery is possible, the SQLite database is not application-encrypted, the worker output is a draft rather than a verified transaction, and unattended startup, sensitive-content filtering and finance-ledger storage remain unimplemented.

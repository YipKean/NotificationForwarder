# Architecture

Updated 2026-09-28. This repository owns Android transport and the notification receiver. The independent general-data-dashboard project owns browser presentation and deployment. No dashboard checkout is required here.

## Boundaries

The Android application filters allowlisted notifications before persistence, stores accepted captures with UUID-v4 event IDs and optional expanded text in its encrypted Room database, and delivers them through the configured HTTPS client. The receiver owns durable transport deduplication; Hermes owns later classification and future financial transaction reconciliation.

The `webhook/` directory is a separate Node.js synthetic receiver and Hermes bridge. `server.js` owns configuration, HTTP authentication, strict schema-v2 validation, sensitive-content rejection and the HTTP lifecycle. `storage.js` owns the local SQLite connection, transactional schema migration, atomic insert-or-resolve operations and read projections. `readRoutes.js` exposes separately authenticated, read-only receipt APIs. `hermes-worker.js` claims saved receipts, invokes the dedicated Hermes profile, validates draft classifications and records processing state. Application-level receiver database encryption and a financial transaction ledger remain unimplemented.

## Data flow

```text
Android notification listener
    -> encrypted Android queue
    -> authenticated HTTP request
    -> webhook/server.js validation
    -> webhook/storage.js SQLite commit
    -> success response with receipt ID
    -> webhook/hermes-worker.js lease and bounded retry
    -> finance-notifications Hermes profile
    -> validated draft classification in hermes_processing
```

The receiver returns success only after SQLite commits the event or resolves an already durable copy. Uniqueness is `(source_id, event_id)`; an identical retry returns the original receipt with `duplicate: true`. Reusing an ID with changed content returns `409 event_id_conflict`. The worker asynchronously processes each receipt at least once and persists a constrained draft classification; duplicate ingestion does not reset worker state.

Android Room version 2 changes the notification-key digest index from unique to nonunique. Exact queued captures are compared under delivery coordination; changed captures retain separate immutable event IDs. Missing IDs in safe legacy encrypted payloads are assigned and persisted before use. Sensitive legacy items are deliberately removed, while transient rewrite failures leave the item queued. The existing destructive plaintext-upgrade marker is unchanged.

Instrumentation uses `-PisolatedIngestionTests=true` to build the debug app as `com.notificationforwarder.app.ingestiontest`, isolating its Android UID, storage and Keystore from the installed forwarder. Repository tests inject temporary Room databases and isolated preferences. This opt-in build property does not change normal debug or release application IDs. See `tasks/INGESTION_TEST_STEPS.md` for USB-device execution; no emulator is required.

## Runtime and configuration

Android transient delivery failures retain encrypted rows until configured expiry;
ngrok's offline response (`404` plus `ngrok-error-code: ERR_NGROK_3200`) is also
transient, because sleeping/disconnecting the tunnel host otherwise looks like
a missing route. Ordinary 404 and other permanent failures remain terminal.
`QueueDelivery` logs failed attempts using only local row ID, count, permanent
flag and the client's fixed error code; no content, URL, token or response body.
attempt counts no longer trigger deletion. The legacy `maxRetries` preference
caps exponential backoff growth (60 seconds initially, capped at 32 minutes,
plus 0–4 seconds jitter). Permanent failures still delete and increment Failed.
After each batch, QueueWorker reads the earliest pending retry timestamp and
persists a network-constrained successor with APPEND_OR_REPLACE before completing.
Due batches use a one-second minimum delay; an empty eligible batch uses a
30-second floor to avoid spinning on a failed legacy rewrite. Future rows use
their persisted due time. Periodic recovery uses KEEP if it is outside the
one-time chain, preventing duplicate continuations. Scheduling is serialized with policy changes. Periodic work and boot recovery
remain fallbacks; Android may defer execution. The retry-policy change itself needs no additional Room migration.

`webhook/outage-receiver.js` is a separate synthetic test entrypoint on localhost
port 3301. It requires its own environment token, uses a fresh temporary SQLite
database and wraps the real receiver storage adapter with a console-controlled
503 failure switch. It does not load `.env`, start Hermes or alter live services.

The receiver uses Express, dotenv and Node's built-in `node:sqlite` module. `WEBHOOK_SOURCE_ID` defaults to `personal-phone` and identifies the sender authenticated by the single configured bearer credential; it must remain stable across token rotation. Body fields cannot select the source. The worker invokes `%LOCALAPPDATA%\hermes\bin\hermes.exe` on Windows with the `finance-notifications` profile and quiet stdin chat; Hermes 0.21 does not support stream-json output. Relative `DATABASE_PATH` values resolve from `webhook/`. The default listener remains `127.0.0.1:3000`.

Local `.env` variants and SQLite database files are ignored by `webhook/.gitignore`; `.env.example` remains tracked. Do not commit credentials or synthetic event databases.

## Repository ownership

- `app/`: Android capture, encrypted queue, settings and delivery.
- `gradle/` and root Gradle files: Android builds.
- `webhook/`: notification ingestion, SQLite, schema migrations, read authorization, API tests and standalone receiver image.
- `.github/workflows/`: independent Android and receiver checks.
- `screenshots/`: Android reference images.
- `graphify-out/`: derived navigation, currently stale; source takes precedence. Local caches are ignored.
- Root Markdown: scope, architecture, setup, security evidence and work history.

## External dashboard boundary

The sibling general-data-dashboard project builds, tests and deploys independently. It connects to a configured HTTP(S) API origin; neither project imports the other's source or accesses the other's storage. Its current notification feature is one client of this receiver. Other data sources belong behind their own dashboard feature adapters.

Receiver-owned GET routes are `/api/notifications`, `/api/notifications/:receiptId` and `/api/notification-apps`. Lists accept bounded `limit`, optional `packageName` and opaque `before`, and return `items` plus `nextCursor`. Details return full text. Read responses disable caching. Receipt IDs identify stored arrivals, not financial transactions.

Optional DASHBOARD_BEARER_TOKEN authorizes reads and must differ from WEBHOOK_BEARER_TOKEN. Missing read configuration disables reads. Do not configure the ingestion token in the dashboard. SQL, input validation and authorization stay in this repository.

Storage migrates legacy unversioned databases to user_version 1 with transactional arrival and source/event indexes; unknown future versions are rejected. The receiver has no automatic retention. Continue using synthetic data until banking-readiness requirements are resolved.

## Deployment and verification

The receiver Docker image runs as a non-root user with DATABASE_PATH=/data/notifications.sqlite. Its operator owns the persistent volume, tokens, HTTPS proxy and listener exposure. Deploy it separately from the dashboard; dashboard restarts must not manage receiver lifecycle.

Run `npm ci --prefix webhook`, `npm test --prefix webhook`, and `git diff --check`. Receiver CI also builds its Docker image. Existing Android CI remains unchanged. Coordinate contract checks with clients when changing the HTTP API.

Work only in the active checkout. Preserve local secrets, databases and Windows tools during source cleanup. Portable graph outputs are tracked; caches and local metadata are ignored. Refresh the graph before a requested commit when graphify is available. PROJECT.md owns delivery scope and Dairy.md records validation and decisions.

On the hosting laptop, the `NotificationForwarder-Laptop` scheduled task runs `webhook/windows/supervise.ps1` at sign-in with a one-minute repeating recovery trigger. The supervisor owns receiver, ngrok and worker processes through a Windows Job Object, restarts exited services and checks receiver health. It keeps the configured ngrok hostname. Machine settings and the standalone ngrok copy are ignored by Git. See [Windows operations](webhook/windows/README.md). Startup before sign-in, operation during sleep, and Telegram access are not implemented.

## Verification and records

Receiver/worker tests use Node's built-in runner, temporary SQLite files and mocked inference. Android tests cover filtering, payload serialization and queue migration. Shared synthetic filter cases live in `test-fixtures/`; production matching stays local and deterministic. Source-specific rules require verified package IDs and redacted formats; generic rules are not a guarantee against every banking notification format.

`PROJECT.md` remains the product plan, `Dairy.md` records chronological evidence, and `tasks/` contains task reports, reviews, status handoffs, test steps and plans. `tasks/INGESTION_HARDENING_IMPLEMENTATION_REPORT.md` records actual checks and pending device acceptance. Graph outputs describe current source/docs; generated files, caches, machine settings, databases and historical screenshots are excluded from extraction. The read API is an optional client of stored synthetic receipts; it does not establish a finance ledger.

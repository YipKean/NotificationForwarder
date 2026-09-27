# Architecture

## Boundaries

The Android application filters allowlisted notifications before persistence, stores accepted captures with UUID-v4 event IDs and optional expanded text in its encrypted Room database, and delivers them through the configured HTTPS client. The receiver owns durable transport deduplication; Hermes owns later classification and future financial transaction reconciliation.

The `webhook/` directory is a separate Node.js synthetic receiver and Hermes bridge. `server.js` owns configuration, HTTP authentication, strict schema-v2 validation, sensitive-content rejection and the HTTP lifecycle. `storage.js` owns the local SQLite connection, transactional schema migration and atomic insert-or-resolve operations. `hermes-worker.js` claims saved receipts, invokes the dedicated Hermes profile, validates draft classifications and records processing state. The system still has no public read route, application-level receiver database encryption or financial transaction ledger.

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
remain fallbacks; Android may defer execution. No Room schema migration is needed.

`webhook/outage-receiver.js` is a separate synthetic test entrypoint on localhost
port 3301. It requires its own environment token, uses a fresh temporary SQLite
database and wraps the real receiver storage adapter with a console-controlled
503 failure switch. It does not load `.env`, start Hermes or alter live services.

The receiver uses Express, dotenv and Node's built-in `node:sqlite` module. `WEBHOOK_SOURCE_ID` defaults to `personal-phone` and identifies the sender authenticated by the single configured bearer credential; it must remain stable across token rotation. Body fields cannot select the source. The worker invokes `%LOCALAPPDATA%\hermes\bin\hermes.exe` on Windows with the `finance-notifications` profile and quiet stdin chat; Hermes 0.21 does not support stream-json output. Relative `DATABASE_PATH` values resolve from `webhook/`. The default listener remains `127.0.0.1:3000`.

Local `.env` variants and SQLite database files are ignored by `webhook/.gitignore`; `.env.example` remains tracked. Do not commit credentials or synthetic event databases.

On the hosting laptop, the `NotificationForwarder-Laptop` scheduled task runs `webhook/windows/supervise.ps1` at sign-in with a one-minute repeating recovery trigger. The supervisor owns receiver, ngrok and worker processes through a Windows Job Object, restarts exited services and checks receiver health. It keeps the configured ngrok hostname. Machine settings and the standalone ngrok copy are ignored by Git. See [Windows operations](webhook/windows/README.md). Startup before sign-in, operation during sleep, and Telegram access are not implemented.

## Verification and records

Receiver/worker tests use Node's built-in runner, temporary SQLite files and mocked inference. Android tests cover filtering, payload serialization and queue migration. Shared synthetic filter cases live in `test-fixtures/`; production matching stays local and deterministic. Source-specific rules require verified package IDs and redacted formats; generic rules are not a guarantee against every banking notification format.

`PROJECT.md` remains the product plan, `Dairy.md` records chronological evidence, and `tasks/` contains task reports, reviews, status handoffs, test steps and plans. `tasks/INGESTION_HARDENING_IMPLEMENTATION_REPORT.md` records actual checks and pending device acceptance. Graph outputs describe current source/docs; generated files, caches, machine settings, databases and historical screenshots are excluded from extraction. No new production dependency or integration is required by this hardening.

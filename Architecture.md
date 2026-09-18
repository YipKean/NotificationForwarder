# Architecture

## Boundaries

The Android application captures allowlisted notifications, stores queued payloads in its encrypted Room database, and delivers them through the configured HTTPS client. Hermes owns later validation, processing, deduplication and finance storage.

The `webhook/` directory is a separate Node.js synthetic receiver and Hermes bridge. `server.js` owns configuration, HTTP authentication, request validation and the HTTP lifecycle. `storage.js` owns the local SQLite connection, schema initialization and parameterized event inserts. `hermes-worker.js` claims saved receipts, invokes the dedicated Hermes profile, validates draft classifications and records processing state. The system still has no public read route, application-level database encryption or event-level deduplication.

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

The receiver writes the response only after SQLite accepts the event. Each request receives a generated UUID and creates one `notification_events` row. The worker asynchronously processes each receipt at least once and persists a constrained draft classification. The Android app currently has no persistent event ID, so repeated delivery can create duplicate rows even when the worker state is durable.

## Runtime and configuration

The receiver uses Express, dotenv and Node's built-in `node:sqlite` module. The worker invokes `%LOCALAPPDATA%\hermes\bin\hermes.exe` on Windows with the `finance-notifications` profile and quiet stdin chat; Hermes 0.21 does not support stream-json output. The current laptop exposes the loopback listener through an ephemeral ngrok HTTPS URL. Relative `DATABASE_PATH` values resolve from `webhook/`. The default listener remains `127.0.0.1:3000`.

Local `.env` variants and SQLite database files are ignored by `webhook/.gitignore`; `.env.example` remains tracked. Do not commit credentials or synthetic event databases.

On the hosting laptop, the `NotificationForwarder-Laptop` scheduled task runs `webhook/windows/supervise.ps1` at sign-in with a one-minute repeating recovery trigger. The supervisor owns receiver, ngrok and worker processes through a Windows Job Object, restarts exited services and checks receiver health. It keeps the configured ngrok hostname. Machine settings and the standalone ngrok copy are ignored by Git. See [Windows operations](webhook/windows/README.md). Startup before sign-in, operation during sleep, and Telegram access are not implemented.

## Verification and records

`webhook/server.test.js` and `webhook/hermes-worker.test.js` use Node's built-in test runner with temporary SQLite files and subprocesses. The current worker suite plus receiver suite report 27 passing tests. `PROJECT.md` remains the product and delivery plan. `Dairy.md` records implementation decisions and validation evidence.

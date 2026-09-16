# Architecture

## Boundaries

The Android application captures allowlisted notifications, stores queued payloads in its encrypted Room database, and delivers them through the configured HTTPS client. Hermes owns later validation, processing, deduplication and finance storage.

The `webhook/` directory is a separate Node.js synthetic receiver. `server.js` owns configuration, HTTP authentication, request validation and the HTTP lifecycle. `storage.js` owns the local SQLite connection, schema initialization and parameterized event inserts. The receiver stores raw validated payloads and has no processing worker, public read route, application encryption or deduplication.

## Data flow

```text
Android notification listener
    -> encrypted Android queue
    -> authenticated HTTP request
    -> webhook/server.js validation
    -> webhook/storage.js SQLite commit
    -> success response with receipt ID
```

The receiver writes the response only after SQLite accepts the event. Each request receives a generated UUID and creates one `notification_events` row. The Android app currently has no persistent event ID, so repeated delivery can create duplicate rows.

## Runtime and configuration

The receiver uses Express, dotenv and Node's built-in `node:sqlite` module. It loads `webhook/.env` only when the standalone `start()` path needs environment configuration; tests pass explicit configuration and temporary databases. Relative `DATABASE_PATH` values resolve from `webhook/`. The default listener is `127.0.0.1:3000`; phone access requires a later HTTPS reverse proxy.

Local `.env` variants and SQLite database files are ignored by `webhook/.gitignore`; `.env.example` remains tracked. Do not commit credentials or synthetic event databases.

## Verification and records

`webhook/server.test.js` uses Node's built-in test runner with temporary SQLite files and ephemeral ports. `PROJECT.md` remains the product and delivery plan. `Dairy.md` records implementation decisions and validation evidence.
